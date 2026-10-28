import fs from 'node:fs';
import path from 'node:path';
import { spawn, spawnSync } from 'node:child_process';
import { performance } from 'node:perf_hooks';
import { fileURLToPath } from 'node:url';
import { fetchGitHubPool } from './execution-loop-source.mjs';
import { fetchNotionPool } from './execution-loop-notion.mjs';
import { runWorkflow } from './execution-loop-workflow.mjs';
import { createGitHubPublisher } from './execution-loop-publish.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const read = file => JSON.parse(fs.readFileSync(file, 'utf8'));

export function metrics(rows, elapsedMs) {
    const done = rows.filter(row => row.status === 'DONE');
    return {
        uninterruptedMinutes: Number((elapsedMs / 60000).toFixed(2)),
        consecutiveDone: rows.findIndex(row => row.status !== 'DONE') < 0
            ? rows.length : rows.findIndex(row => row.status !== 'DONE'),
        attempted: rows.length,
        averageDoneMinutes: done.length ? done.reduce((sum, row) => sum + row.durationMs, 0) / done.length / 60000 : null,
        firstPassPercent: rows.length ? 100 * rows.filter(row => row.status === 'DONE' && row.firstPass).length / rows.length : null,
    };
}

export function validatePool(pool) {
    if (!Array.isArray(pool.tasks) || pool.tasks.length < 15 || pool.tasks.length > 20) {
        throw new Error('Pool must contain 15–20 tasks');
    }
    const ids = new Set();
    for (const task of pool.tasks) {
        if (!/^EL-\d{2}$/.test(task.id) || ids.has(task.id) || !task.title || !task.acceptance ||
            !['bug', 'feature', 'refactor', 'test', 'docs'].includes(task.kind)) {
            throw new Error(`Invalid task: ${task.id}`);
        }
        ids.add(task.id);
    }
    return pool;
}

async function command(program, args, cwd, timeout, log, input) {
    const fd = fs.openSync(log, 'a');
    const stderr = args.includes('--json') ? fs.openSync(log + '.stderr.log', 'a') : fd;
    try {
        await new Promise((resolve, reject) => {
            const child = spawn(program, args, {
                cwd, windowsHide: true, detached: process.platform !== 'win32',
                stdio: [input === undefined ? 'ignore' : 'pipe', fd, stderr],
            });
            let stopped;
            const stop = reason => {
                stopped = reason;
                if (child.pid) {
                    if (process.platform === 'win32') {
                        spawnSync('taskkill.exe', ['/PID', String(child.pid), '/T', '/F'], { windowsHide: true, stdio: 'ignore' });
                    } else { try { process.kill(-child.pid, 'SIGKILL'); } catch {} }
                }
            };
            const interrupt = () => stop('Human interruption');
            const timer = setTimeout(() => stop('Task timeout'), timeout);
            process.once('SIGINT', interrupt);
            child.once('error', reject);
            child.once('close', code => {
                clearTimeout(timer);
                process.removeListener('SIGINT', interrupt);
                if (stopped || code !== 0) reject(new Error(stopped || `${program} exit ${code}; see ${log}`));
                else resolve();
            });
            if (input !== undefined) {
                child.stdin.on('error', () => {});
                child.stdin.end(input);
            }
        });
    } finally { if (stderr !== fd) fs.closeSync(stderr); fs.closeSync(fd); }
}

function git(cwd, ...args) {
    const result = spawnSync('git', args, { cwd, encoding: 'utf8', windowsHide: true });
    if (result.error || result.status !== 0) throw new Error(result.stderr || result.error?.message || 'git failed');
    return result.stdout.trim();
}

export async function runLoop(options) {
    const lock = path.resolve(options.cwd, git(options.cwd, 'rev-parse', '--git-path', 'execution-loop.lock'));
    let fd;
    try { fd = fs.openSync(lock, 'wx'); }
    catch (error) {
        if (error.code === 'EEXIST') throw new Error('Execution Loop lock exists; another run or an interrupted run requires audit');
        throw error;
    }
    try { return await runWorkflow({ execute: command, ...options, metrics, validatePool }); }
    finally { fs.closeSync(fd); fs.unlinkSync(lock); }
}

async function main() {
    const [mode, ...values] = process.argv.slice(2);
    const loadSource = async () => {
        const config = read(path.join(root, 'docs/ai-agent/execution-loop/source.json'));
        if (config.type === 'github') return validatePool(await fetchGitHubPool(config));
        if (config.type === 'notion') return validatePool(await fetchNotionPool(config, { cwd: root }));
        throw new Error('Unsupported source.type; use github, notion or an explicit pool snapshot');
    };
    if (mode === 'fetch') {
        if (values.length !== 1) throw new Error('Usage: node scripts/execution-loop.mjs fetch <new-snapshot.json>');
        const file = path.resolve(values[0]);
        if (fs.existsSync(file)) throw new Error('Snapshot already exists; use a new file');
        const pool = await loadSource();
        fs.mkdirSync(path.dirname(file), { recursive: true });
        fs.writeFileSync(file, JSON.stringify(pool, null, 2) + '\n', { flag: 'wx' });
        console.log(`Saved ${pool.tasks.length} tasks from ${pool.source?.type || 'snapshot'} to ${file}`);
        return;
    }
    if (mode === 'validate') {
        const pool = validatePool(read(values[0] ? path.resolve(values[0]) : path.join(root, 'docs/ai-agent/execution-loop/tasks.json')));
        console.log(`${pool.tasks.length}-task pool valid`);
        return;
    }
    if (mode === 'preflight') {
        if (git(root, 'status', '--porcelain')) throw new Error('Use a clean dedicated checkout; preserve existing work');
        const pool = await loadSource();
        const config = read(path.join(root, 'docs/ai-agent/execution-loop/publication.json'));
        const publisher = config.mode === 'local' ? null : await createGitHubPublisher({ cwd: root, config,
            runId: 'preflight', baseRevision: git(root, 'rev-parse', 'HEAD') });
        console.log(`Ready: ${pool.tasks.length} tasks; publication=${config.mode}; base=${publisher?.baseBranch || 'local'}; model=inherited Codex default. No model calls, push or PR creation performed.`);
        return;
    }
    if (mode === 'compare') {
        const runs = values.map(directory => read(path.join(directory, 'run.json')));
        if (runs.length < 2 || runs.some(run => run.state === 'RUNNING') || new Set(runs.map(run => run.poolHash)).size !== 1 ||
            new Set(runs.map(run => run.version || 1)).size !== 1) {
            throw new Error('Need at least two finished runs with the same pool and execution protocol version');
        }
        console.log('| Прогон | Провайдер | Модель | Без пауз, мин | Подряд | Среднее, мин | С первого раза, % |');
        console.log('| --- | --- | --- | --- | --- | --- | --- |');
        runs.forEach((run, index) => {
            console.log(`| ${index + 1} | ${run.provider} | ${run.model} | ${run.metrics.uninterruptedMinutes} | ${run.metrics.consecutiveDone} | ${run.metrics.averageDoneMinutes ?? 'нет данных'} | ${run.metrics.firstPassPercent} |`);
            const failure = run.rows.find(row => row.status !== 'DONE');
            if (failure) console.log(`\nОстановка прогона ${index + 1}: ${failure.id}, ${failure.status}, ${failure.reason}\n`);
        });
        const first = runs[0].metrics.uninterruptedMinutes;
        const second = runs[1].metrics.uninterruptedMinutes;
        console.log(`Изменение времени (прогон 2 − прогон 1): ${(second - first).toFixed(2)} мин; ${first ? ((second / first - 1) * 100).toFixed(2) + '%' : 'процент не определён при нулевой базе'}.`);
        if (new Set(runs.map(run => run.baseRevision)).size !== 1) console.log('Разные baseRevision: проверьте, что отличается только конфигурация инструкций; сравнение не контролируемое автоматически.');
        return;
    }
    if (mode !== 'run' || values.length < 1) {
        throw new Error('Usage: node scripts/execution-loop.mjs preflight | fetch <new-snapshot.json> | validate [snapshot.json] | run <unique-id> [model=default] [provider=cloud] [timeout-minutes=30] [snapshot.json] | compare <run-dir> <run-dir> ...');
    }
    const [id, model = 'default', provider = 'cloud', timeout = '30', snapshot] = values;
    if (provider !== 'cloud' && model === 'default') throw new Error('Specify an exact local model for ollama/lmstudio');
    if (!/^[a-z0-9][a-z0-9-]*$/.test(id) || !['cloud', 'ollama', 'lmstudio'].includes(provider) || !Number.isFinite(Number(timeout)) || Number(timeout) <= 0) {
        throw new Error('Invalid run id, provider or timeout');
    }
    if (git(root, 'status', '--porcelain')) throw new Error('Use a clean dedicated checkout; preserve existing work');
    const pool = snapshot ? validatePool(read(path.resolve(snapshot))) : await loadSource();
    const run = await runLoop({ cwd: root, output: path.join(root, 'build/execution-loop', id),
        pool, model, provider, timeoutMinutes: Number(timeout),
        economy: read(path.join(root, 'docs/ai-agent/execution-loop/economy.json')),
        publication: read(path.join(root, 'docs/ai-agent/execution-loop/publication.json')) });
    console.log(`Количество времени без пауз: ${run.metrics.uninterruptedMinutes} мин; ${run.state}`);
    process.exitCode = run.state === 'COMPLETED' ? 0 : 1;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    try { await main(); } catch (error) { console.error(error.message); process.exitCode = 1; }
}

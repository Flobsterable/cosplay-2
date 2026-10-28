import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { metrics, runLoop, validatePool } from './execution-loop.mjs';
import { validatePlan, validateReview } from './execution-loop-workflow.mjs';
import { taskDigest } from './execution-loop-checks.mjs';

function fixture(t) {
    const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'cosplay-loop-'));
    const cwd = path.join(directory, 'repo');
    fs.mkdirSync(cwd);
    const git = (...args) => {
        const result = spawnSync('git', args, { cwd, encoding: 'utf8', windowsHide: true });
        assert.equal(result.status, 0, result.stderr);
        return result.stdout.trim();
    };
    git('init');
    git('config', 'user.email', 'fixture@example.test');
    git('config', 'user.name', 'Loop Fixture');
    for (const skill of ['fixture', 'execution-loop-plan', 'execution-loop-step', 'execution-loop-recover',
        'execution-loop-review', 'planning-and-task-breakdown', 'incremental-implementation',
        'debugging-and-error-recovery', 'context-engineering', 'code-review-and-quality', 'profile-review']) {
        fs.mkdirSync(path.join(cwd, '.agents/skills', skill), { recursive: true });
        fs.writeFileSync(path.join(cwd, '.agents/skills', skill, 'SKILL.md'), 'fixture\n');
    }
    fs.writeFileSync(path.join(cwd, 'README.md'), 'baseline\n');
    git('add', '.');
    git('commit', '-m', 'fixture baseline');
    t.after(() => {
        const worktrees = git('worktree', 'list', '--porcelain').split('\n').filter(line => line.startsWith('worktree ')).map(line => line.slice(9));
        // Only explicitly created temp fixtures are removed; real evidence is retained.
        for (const worktree of worktrees.slice(1)) git('worktree', 'remove', '--force', worktree);
        fs.rmSync(directory, { recursive: true, force: true });
    });
    const pool = { tasks: Array.from({ length: 15 }, (_, i) => ({
        id: `EL-${String(i + 1).padStart(2, '0')}`, kind: 'docs', title: `Task ${i + 1}`, acceptance: 'Documented change',
    })) };
    return { cwd, git, pool, output: path.join(directory, 'run'), model: 'fixture', provider: 'cloud', economy: { directDocs: false } };
}

function agent(options = {}) {
    const calls = [];
    const failures = new Set();
    const reviewRequests = new Set();
    const execute = async (program, args, cwd) => {
        if (program === 'git') return;
        if (program === 'powershell.exe') { if (options.verifyFailure) throw new Error('verification failed'); return; }
        const resultPath = args[args.indexOf('-o') + 1];
        const stage = path.basename(path.dirname(resultPath)).split('-')[1];
        const id = path.basename(path.dirname(cwd));
        calls.push({ stage, id });
        if (options.crash && options.crash(stage, id)) throw new Error('simulated CLI failure');
        let result;
        if (stage === 'plan') {
            assert.deepEqual(args.slice(args.indexOf('-s'), args.indexOf('-s') + 2), ['-s', 'read-only']);
            result = { profile: 'fixture-docs', dependsOn: options.depends?.[id] || [], steps:
                Array.from({ length: options.steps || 1 }, (_, index) => ({ title: `step ${index}`, skills: ['fixture'], acceptance: 'verified' })) };
            if (options.planWrite) fs.writeFileSync(path.join(cwd, 'unwanted.md'), 'unwanted\n');
        } else if (stage === 'recover') {
            result = options.recovery || { action: 'stop', scope: 'shared', fingerprint: 'tool-unavailable', reason: 'Tool unavailable' };
        } else if (stage === 'review') {
            assert.deepEqual(args.slice(args.indexOf('-s'), args.indexOf('-s') + 2), ['-s', 'read-only']);
            const requestChanges = options.rejectReview?.includes(id) || options.requestReviewOnce === id && !reviewRequests.has(id);
            reviewRequests.add(id);
            result = { verdict: requestChanges ? 'REQUEST_CHANGES' : 'APPROVE', summary: requestChanges ? 'Concrete defect' : 'No regressions found',
                findings: requestChanges ? [{ priority: 'P2', file: `${id}.md`, line: 1, trigger: 'Specific input', impact: 'Wrong result', fix: 'Correct this case' }] : [],
                coverage: 'Fixture verification', limitations: 'Simulated model' };
            if (options.reviewWrite) fs.appendFileSync(path.join(cwd, `${id}.md`), 'unexpected reviewer edit\n');
            if (options.reviewWhitespace) fs.writeFileSync(path.join(cwd, 'README.md'), 'changed ');
            if (options.reviewCrash) throw new Error('review CLI crashed');
            if (options.invalidApproval) result.verdict = 'UNKNOWN';
        } else {
            assert.deepEqual(args.slice(args.indexOf('-s'), args.indexOf('-s') + 2), ['-s', 'workspace-write']);
            assert.ok(args.includes('sandbox_workspace_write.network_access=true'));
            const fail = options.fail?.includes(id) && (!options.once || !failures.has(id));
            if (fail) failures.add(id);
            fs.appendFileSync(path.join(cwd, `${id}.md`), 'step output\n');
            if (options.reviewWhitespace) fs.writeFileSync(path.join(cwd, 'README.md'), 'changed\n');
            result = { status: fail ? 'BLOCKED' : 'DONE', profile: 'fixture-docs', firstPass: !fail,
                reason: fail ? 'task failure' : '', verificationDirectory: options.evidence || '',
                handoff: { summary: 'Step completed in fixture', files: [`${id}.md`], decisions: [], risks: [],
                    verification: [{ command: 'fixture check', result: 'PASS (simulated)' }] } };
            if (options.mutateRules) fs.writeFileSync(path.join(cwd, 'AGENTS.md'), 'weakened rules\n');
        }
        fs.writeFileSync(resultPath, JSON.stringify(result));
    };
    return { execute, calls };
}

test('metrics preserve failed final attempts and successful averages', () => {
    const result = metrics([{ status: 'DONE', firstPass: true, durationMs: 60000 },
        { status: 'DONE', firstPass: false, durationMs: 120000 }, { status: 'BLOCKED', durationMs: 60000 }], 240000);
    assert.equal(result.uninterruptedMinutes, 4);
    assert.equal(result.consecutiveDone, 2);
    assert.equal(result.averageDoneMinutes, 1.5);
    assert.equal(result.firstPassPercent, 100 / 3);
});

test('reject invalid pools and unbounded plans/dependencies', () => {
    assert.throws(() => validatePool({ tasks: [] }));
    const step = { title: 'step', skills: ['fixture'], acceptance: 'done' };
    assert.throws(() => validatePlan({ profile: 'docs', dependsOn: [], steps: Array(7).fill(step) }, []));
    assert.throws(() => validatePlan({ profile: 'docs', dependsOn: ['EL-02'], steps: [step] }, ['EL-01']));
    assert.throws(() => validatePlan({ profile: 'docs', dependsOn: [], steps: [{ ...step, skills: ['../escape'] }] }, []));
});

test('pool dependencies are rejected before model execution when unknown, forward or duplicate', () => {
    const tasks = Array.from({ length: 15 }, (_, index) => ({ id: `EL-${String(index + 1).padStart(2, '0')}`,
        kind: 'docs', title: 'Task', acceptance: 'Written' }));
    for (const dependsOn of [['EL-02'], ['EL-99'], ['EL-01', 'EL-01'], 'EL-01']) {
        assert.throws(() => validatePool({ tasks: tasks.map((task, index) => index === 0 ? { ...task, dependsOn } : task) }), /dependencies/);
    }
    assert.doesNotThrow(() => validatePool({ tasks: tasks.map((task, index) => index === 1 ? { ...task, dependsOn: ['EL-01'] } : task) }));
});

test('planner and each step are separate sessions; commits integrate from isolated worktrees', async t => {
    const f = fixture(t);
    const fake = agent({ steps: 2 });
    const result = await runLoop({ ...f, execute: fake.execute });
    assert.equal(result.state, 'COMPLETED');
    assert.equal(result.metrics.totalDone, 15);
    assert.equal(result.rows[0].steps.length, 2);
    assert.deepEqual(fake.calls.slice(0, 4).map(call => call.stage), ['plan', 'work', 'work', 'review']);
    assert.equal(result.rows[0].reviews[0].verdict, 'APPROVE');
    assert.equal(result.rows[0].steps[0].handoff.verification[0].result, 'PASS (simulated)');
    assert.equal(f.git('rev-list', '--count', 'HEAD'), '16');
    assert.equal(f.git('status', '--porcelain'), '');
    await assert.rejects(runLoop({ ...f, execute: fake.execute }), /Output already exists/);
});

test('recovery retries failed step without hiding first failure', async t => {
    const f = fixture(t);
    const fake = agent({ fail: ['EL-01'], once: true,
        recovery: { action: 'retry', scope: 'task', fingerprint: 'specific-test', reason: 'Fixed task failure' } });
    const result = await runLoop({ ...f, execute: fake.execute });
    assert.equal(result.state, 'COMPLETED');
    assert.equal(result.rows[0].firstPass, false);
    assert.equal(result.rows[0].attempts, 2);
    assert.equal(result.incidents.length, 1);
    assert.equal(result.rows[0].status, 'DONE');
});

test('skip isolates failed patch, blocks dependents and continues independent tasks', async t => {
    const f = fixture(t);
    const fake = agent({ fail: ['EL-01'], depends: { 'EL-02': ['EL-01'] },
        recovery: { action: 'skip', scope: 'task', fingerprint: 'missing-contract', reason: 'Needs specification' } });
    const result = await runLoop({ ...f, execute: fake.execute });
    assert.equal(result.state, 'COMPLETED_WITH_BLOCKERS');
    assert.deepEqual(result.rows.slice(0, 3).map(row => row.status), ['BLOCKED', 'BLOCKED', 'DONE']);
    assert.ok(fs.existsSync(path.join(result.rows[0].workspace, 'EL-01.md')));
    assert.ok(!fs.existsSync(path.join(f.cwd, 'EL-01.md')));
    assert.ok(!fake.calls.some(call => call.id === 'EL-02' && call.stage === 'work'));
    assert.equal(result.metrics.totalDone, 13);
    assert.equal(f.git('status', '--porcelain'), '');
});

test('repeated shared failure trips circuit breaker before entire queue fails', async t => {
    const f = fixture(t);
    const fake = agent({ fail: ['EL-01', 'EL-02'], once: true,
        recovery: { action: 'retry', scope: 'shared', fingerprint: 'sdk-unavailable', reason: 'Reinitialized SDK' } });
    const result = await runLoop({ ...f, execute: fake.execute });
    assert.equal(result.state, 'STOPPED');
    assert.equal(result.rows.length, 2);
    assert.equal(result.rows[0].status, 'DONE');
    assert.match(result.incidents[1].reason, /repeated shared/);
});

test('repeated task-classified fingerprint cannot fail the entire queue', async t => {
    const f = fixture(t);
    const fake = agent({ fail: ['EL-01', 'EL-02', 'EL-03', 'EL-04'],
        recovery: { action: 'skip', scope: 'task', fingerprint: 'same-hidden-global-error', reason: 'Task blocked' } });
    const result = await runLoop({ ...f, execute: fake.execute });
    assert.equal(result.state, 'STOPPED');
    assert.equal(result.rows.length, 3);
    assert.match(result.incidents[2].reason, /repeated failure fingerprint/);
});

test('recovery budget prevents infinite retry while independent tasks continue', async t => {
    const f = fixture(t);
    const fake = agent({ fail: ['EL-01'], recovery: { action: 'retry', scope: 'task', fingerprint: 'same-test', reason: 'Attempted fix' } });
    const result = await runLoop({ ...f, execute: fake.execute });
    assert.equal(result.state, 'COMPLETED_WITH_BLOCKERS');
    assert.equal(result.rows[0].attempts, 3);
    assert.equal(result.rows[0].status, 'BLOCKED');
    assert.equal(result.incidents.length, 2);
});

test('broken recovery process stops and persists terminal journal', async t => {
    const f = fixture(t);
    const fake = agent({ fail: ['EL-01'], crash: stage => stage === 'recover' });
    const result = await runLoop({ ...f, execute: fake.execute });
    assert.equal(result.state, 'STOPPED');
    assert.equal(result.rows.length, 1);
    assert.match(result.rows[0].reason, /simulated CLI/);
    assert.equal(JSON.parse(fs.readFileSync(path.join(f.output, 'run.json'))).state, 'STOPPED');
});

test('code DONE without evidence cannot commit', async t => {
    const f = fixture(t);
    f.pool.tasks[0].kind = 'test';
    const result = await runLoop({ ...f, execute: agent().execute, maxRecoveries: 0 });
    assert.match(result.rows[0].reason, /Missing verification/);
    assert.equal(f.git('rev-list', '--count', 'HEAD'), '1');
});

test('failed external verifier reruns last step and checks before commit', async t => {
    const f = fixture(t);
    f.pool.tasks[0].kind = 'test';
    const fake = agent({ evidence: 'build/verification/fixture',
        recovery: { action: 'retry', scope: 'task', fingerprint: 'verification-failure', reason: 'Fixed failed verification' } });
    let checks = 0;
    const result = await runLoop({ ...f, execute: async (...args) => {
        if (args[0] === 'powershell.exe') {
            checks++;
            assert.equal(args[1][args[1].indexOf('-OutputDirectory') + 1], path.join('build', 'verification', 'fixture'));
            if (checks === 1) throw new Error('verification exit 2');
        }
        return fake.execute(...args);
    } });
    assert.equal(checks, 2);
    assert.equal(result.rows[0].status, 'DONE');
    assert.equal(result.rows[0].firstPass, false);
    assert.equal(result.rows[0].steps.length, 1);
});

test('planner writes and instruction mutations stop without recovery', async t => {
    for (const options of [{ planWrite: true }, { mutateRules: true }]) {
        const f = fixture(t);
        const fake = agent(options);
        const result = await runLoop({ ...f, execute: fake.execute });
        assert.equal(result.state, 'STOPPED');
        assert.match(result.rows[0].reason, /Integrity:/);
        assert.ok(!fake.calls.some(call => call.stage === 'recover'));
    }
});

test('deadline and runner diagnosis cannot cause unbounded retries', async t => {
    const f = fixture(t);
    const fake = agent({ recovery: { action: 'retry', scope: 'runner', fingerprint: 'runner-bug', reason: 'Needs patched runner' } });
    const result = await runLoop({ ...f, timeoutMinutes: 0.000001, execute: fake.execute });
    assert.equal(result.state, 'STOPPED');
    assert.equal(result.incidents[0].action, 'stop');
    assert.equal(result.rows.length, 1);
});

test('exclusive lock and dirty checkout preserve external work', async t => {
    const f = fixture(t);
    const lock = path.join(f.cwd, '.git', 'execution-loop.lock');
    fs.writeFileSync(lock, 'other process');
    await assert.rejects(runLoop({ ...f, execute: agent().execute }), /lock exists/);
    fs.unlinkSync(lock);
    fs.writeFileSync(path.join(f.cwd, 'external.md'), 'external work\n');
    await assert.rejects(runLoop({ ...f, execute: agent().execute }), /clean dedicated/);
    assert.equal(fs.readFileSync(path.join(f.cwd, 'external.md'), 'utf8'), 'external work\n');
});

test('review findings trigger repair, final checks and new review before commit', async t => {
    const f = fixture(t);
    const fake = agent({ requestReviewOnce: 'EL-01',
        recovery: { action: 'retry', scope: 'task', fingerprint: 'review-defect', reason: 'Fixed confirmed regression' } });
    let feedbackReceived = false;
    let validations = 0;
    const result = await runLoop({ ...f, execute: async (...args) => {
        if (args[0] === 'git' && args[2].endsWith(path.join('EL-01', 'workspace'))) validations++;
        if (args[5]?.includes('reviewFeedback') && args[5].includes('Specific input')) feedbackReceived = true;
        return fake.execute(...args);
    } });
    assert.equal(result.rows[0].status, 'DONE');
    assert.equal(result.rows[0].firstPass, false);
    assert.deepEqual(result.rows[0].reviews.map(review => review.verdict), ['REQUEST_CHANGES', 'APPROVE']);
    assert.equal(validations, 2);
    assert.ok(feedbackReceived);
    assert.deepEqual(fake.calls.filter(call => call.id === 'EL-01').map(call => call.stage),
        ['plan', 'work', 'review', 'recover', 'work', 'review']);
});

test('rejected review cannot produce a commit or claim DONE', async t => {
    const f = fixture(t);
    const fake = agent({ rejectReview: ['EL-01'] });
    const result = await runLoop({ ...f, execute: fake.execute, maxRecoveries: 0 });
    assert.equal(result.state, 'STOPPED');
    assert.equal(result.rows[0].status, 'FAIL');
    assert.match(result.rows[0].reason, /REQUEST_CHANGES/);
    assert.equal(f.git('rev-list', '--count', 'HEAD'), '1');
});

test('reviewer mutation is detected even when reviewer process crashes', async t => {
    const f = fixture(t);
    const fake = agent({ reviewWrite: true, reviewCrash: true });
    const result = await runLoop({ ...f, execute: fake.execute });
    assert.equal(result.state, 'STOPPED');
    assert.match(result.rows[0].reason, /Integrity: reviewer changed/);
    assert.ok(!fake.calls.some(call => call.stage === 'recover'));
    assert.equal(f.git('rev-list', '--count', 'HEAD'), '1');
});

test('malformed review is not accepted as approval', async t => {
    const f = fixture(t);
    const result = await runLoop({ ...f, execute: agent({ invalidApproval: true }).execute, maxRecoveries: 0 });
    assert.equal(result.state, 'STOPPED');
    assert.match(result.rows[0].reason, /Invalid review/);
    assert.equal(f.git('rev-list', '--count', 'HEAD'), '1');
});

test('review schema rejects contradictory verdicts and unsafe finding paths', () => {
    const finding = { priority: 'P1', file: 'file.kt', line: 1, trigger: 'Input', impact: 'Data loss', fix: 'Guard state' };
    const review = { verdict: 'APPROVE', summary: 'Reviewed', findings: [], coverage: '', limitations: '' };
    assert.equal(validateReview(review), review);
    assert.throws(() => validateReview({ ...review, findings: [finding] }), /Contradictory/);
    assert.throws(() => validateReview({ ...review, verdict: 'REQUEST_CHANGES' }), /Contradictory/);
    assert.throws(() => validateReview({ ...review, verdict: 'REQUEST_CHANGES', findings: [{ ...finding, file: '../outside' }] }), /Invalid review finding/);
});

test('reviewer changes to trailing whitespace in tracked source are detected', async t => {
    const f = fixture(t);
    const result = await runLoop({ ...f, execute: agent({ reviewWhitespace: true }).execute });
    assert.equal(result.state, 'STOPPED');
    assert.match(result.rows[0].reason, /Integrity: reviewer changed/);
    assert.equal(f.git('rev-list', '--count', 'HEAD'), '1');
});

test('documentation fast path removes planner calls and preserves independent review', async t => {
    const f = fixture(t);
    const fake = agent();
    const result = await runLoop({ ...f, economy: { directDocs: true }, execute: fake.execute });
    assert.equal(result.state, 'COMPLETED');
    assert.equal(fake.calls.length, 30);
    assert.ok(!fake.calls.some(call => call.stage === 'plan'));
    assert.equal(fake.calls.filter(call => call.stage === 'review').length, 15);
    assert.equal(result.rows[0].planning, 'local-docs');
});

test('call budget stops without spending another request on recovery', async t => {
    const f = fixture(t);
    const fake = agent();
    const result = await runLoop({ ...f, economy: { directDocs: false, maxCallsPerRun: 1 }, execute: fake.execute });
    assert.equal(fake.calls.length, 1);
    assert.equal(result.state, 'STOPPED');
    assert.match(result.rows[0].reason, /Budget:/);
    assert.equal(result.incidents.length, 0);
    assert.equal(f.git('rev-list', '--count', 'HEAD'), '1');
});

test('prompt fallback keeps full oversized criteria on disk and records reported usage', async t => {
    const f = fixture(t);
    f.pool.tasks[0].acceptance = 'criterion '.repeat(5000);
    const fake = agent();
    const result = await runLoop({ ...f, economy: { directDocs: false, maxPromptChars: 2000, maxReportedTokens: 1 }, execute: async (...args) => {
        if (args[0] !== 'git' && args[0] !== 'powershell.exe') {
            assert.ok(args[5].length <= 2000);
            assert.ok(args[1].includes('--json'));
            const file = path.join(path.dirname(args[1][args[1].indexOf('-o') + 1]), 'context.json');
            assert.equal(JSON.parse(fs.readFileSync(file, 'utf8')).task.acceptance, f.pool.tasks[0].acceptance);
            fs.writeFileSync(args[4], JSON.stringify({ type: 'turn.completed', usage: { input_tokens: 100, cached_input_tokens: 10, output_tokens: 5 } }) + '\n');
        }
        return fake.execute(...args);
    } });
    assert.equal(result.state, 'STOPPED');
    assert.equal(result.modelCalls.length, 1);
    assert.equal(result.metrics.reportedTokens, 105);
    assert.equal(result.metrics.callsWithoutUsage, 0);
    assert.match(result.rows[0].reason, /Budget:/);
});

test('explicit tracker dependencies cannot be dropped by the model plan', async t => {
    const f = fixture(t);
    f.pool.tasks[1].dependsOn = ['EL-01'];
    const fake = agent({ fail: ['EL-01'], recovery: { action: 'skip', scope: 'task', fingerprint: 'missing-input', reason: 'Needs input' } });
    const result = await runLoop({ ...f, economy: { directDocs: false, maxCallsPerRun: 4 }, execute: fake.execute });
    assert.deepEqual(result.rows.slice(0, 2).map(row => row.status), ['BLOCKED', 'BLOCKED']);
    assert.match(result.rows[1].reason, /Dependency EL-01/);
    assert.ok(!fake.calls.some(call => call.id === 'EL-02' && call.stage === 'work'));
});

test('publication retries without another model call and journals PR artifacts', async t => {
    const f = fixture(t);
    const fake = agent();
    let attempts = 0;
    const result = await runLoop({ ...f, execute: fake.execute, economy: { directDocs: false, maxCallsPerRun: 3 },
        publication: { mode: 'draft-pr' }, publisherFactory: async () => ({ prepare: async () => {},
            publish: async ({ onProgress }) => {
                if (++attempts === 1) throw new Error('temporary transport failure');
                const published = { url: 'https://github.com/fixture/project/pull/1', number: 1 };
                onProgress({ ...published, phase: 'PR_CREATED' });
                return published;
            } }) });
    assert.equal(result.rows[0].status, 'DONE');
    assert.equal(result.rows[0].publicationAttempts, 2);
    assert.equal(result.artifacts.length, 1);
    assert.equal(JSON.parse(fs.readFileSync(path.join(f.output, 'artifacts.json'))).length, 1);
    assert.equal(fake.calls.length, 3);
    assert.ok(!fake.calls.some(call => call.stage === 'recover'));
});

test('persistent publication failure preserves commit and stops without model recovery', async t => {
    const f = fixture(t);
    const fake = agent();
    const result = await runLoop({ ...f, execute: fake.execute, publication: { mode: 'draft-pr' },
        publisherFactory: async () => ({ prepare: async () => {}, publish: async () => { throw new Error('access denied'); } }) });
    assert.equal(result.state, 'STOPPED');
    assert.match(result.rows[0].reason, /Publication:/);
    assert.ok(result.rows[0].commit);
    assert.equal(result.rows[0].publicationAttempts, 2);
    assert.equal(f.git('rev-list', '--count', 'HEAD'), '2');
    assert.equal(fake.calls.length, 3);
});

test('baseline publication failure stops before any model call', async t => {
    const f = fixture(t);
    const fake = agent();
    const result = await runLoop({ ...f, execute: fake.execute, publication: { mode: 'draft-pr' },
        publisherFactory: async () => ({ prepare: async () => { throw new Error('push unavailable'); } }) });
    assert.equal(result.state, 'STOPPED');
    assert.equal(fake.calls.length, 0);
    assert.equal(result.rows.length, 0);
});

test('pinned mini recipes remove planner calls while preserving independent review', async t => {
    const f = fixture(t);
    f.pool.source = { pool: 'fixture-mini', dataSourceId: 'fixture-source' };
    const recipes = { version: 1, ...f.pool.source, tasks: f.pool.tasks.map(task => ({ id: task.id,
        taskDigest: taskDigest(task), profile: 'Fixture', skills: ['fixture'], checks: [] })) };
    const fake = agent();
    const run = await runLoop({ ...f, recipes, execute: fake.execute });
    assert.equal(run.state, 'COMPLETED');
    assert.equal(fake.calls.length, 30);
    assert.ok(run.rows.every(row => row.planning === 'local-recipe'));
    assert.ok(fake.calls.every(call => call.stage !== 'plan'));
    assert.equal(fake.calls.filter(call => call.stage === 'review').length, 15);
});

test('strict usage policy stops before a second paid call when usage is unknown', async t => {
    const f = fixture(t);
    const fake = agent();
    const run = await runLoop({ ...f, economy: { directDocs: false, stopOnMissingUsage: true }, execute: fake.execute });
    assert.equal(fake.calls.length, 1);
    assert.match(run.rows[0].reason, /Budget:/);
    assert.equal(run.incidents.length, 0);
    assert.equal(f.git('rev-list', '--count', 'HEAD'), '1');
});

test('local gate rejects bad new-file whitespace and retries without model diagnosis', async t => {
    const f = fixture(t);
    const fake = agent();
    let work = 0;
    const run = await runLoop({ ...f, economy: { directDocs: true, maxCallsPerRun: 3 }, execute: async (...args) => {
        await fake.execute(...args);
        if (args[0] !== 'git' && args[0] !== 'powershell.exe') {
            const result = args[1][args[1].indexOf('-o') + 1];
            if (path.basename(path.dirname(result)).endsWith('-work')) {
                fs.writeFileSync(path.join(args[2], 'EL-01.md'), ++work === 1 ? 'trailing space  \n' : 'fixed\n');
            }
        }
    } });
    assert.equal(run.rows[0].status, 'DONE');
    assert.equal(run.rows[0].firstPass, false);
    assert.equal(run.incidents[0].diagnosis, 'deterministic-local-checks');
    assert.deepEqual(fake.calls.map(call => call.stage), ['work', 'work', 'review']);
    assert.equal(run.rows[0].localChecks.patchFailures.length, 0);
});

test('each model call receives a tool-output cap and a bounded execution time', async t => {
    const f = fixture(t);
    const fake = agent();
    await runLoop({ ...f, economy: { directDocs: false, maxCallsPerRun: 1, maxCallMinutes: 2 }, execute: async (...args) => {
        if (args[0] !== 'git' && args[0] !== 'powershell.exe') {
            assert.ok(args[1].includes('tool_output_token_limit=1500'));
            assert.ok(args[3] <= 120000);
        }
        await fake.execute(...args);
    } });
    assert.equal(fake.calls.length, 1);
});

test('git whitespace failures use the same local retry without a diagnosis session', async t => {
    const f = fixture(t);
    const fake = agent();
    let failed = false;
    const run = await runLoop({ ...f, economy: { directDocs: true, maxCallsPerRun: 3 }, execute: async (...args) => {
        if (args[0] === 'git' && !failed) { failed = true; throw new Error('git exit 2'); }
        return fake.execute(...args);
    } });
    assert.equal(run.rows[0].status, 'DONE');
    assert.equal(run.rows[0].firstPass, false);
    assert.deepEqual(fake.calls.map(call => call.stage), ['work', 'work', 'review']);
    assert.equal(run.incidents[0].diagnosis, 'deterministic-local-checks');
});

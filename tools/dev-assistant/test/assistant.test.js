import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, mkdir, writeFile, readFile, rm, symlink } from 'node:fs/promises';
import { resolve, sep } from 'node:path';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { createServer } from 'node:http';
import { toolRoot } from '../src/config.js';
import { buildIndex, search } from '../src/rag.js';
import { connectProject } from '../src/mcp-client.js';
import { complete } from '../src/assistant.js';
import { orchestrate, validatePlan } from '../src/orchestrator.js';
import { syncDocs, validateUpdates, editableDocs } from '../src/sync-docs.js';
import { readProjectDiff } from '../src/project-diff.js';

const exec = promisify(execFile);
const scratch = resolve(toolRoot, '.cache');
const document = '# Архитектура\n\nМодуль data/festival получает фестивали из API.\n';
async function fixture(t) {
  await mkdir(scratch, { recursive: true });
  const root = await mkdtemp(resolve(scratch, 'test-'));
  t.after(async () => {
    assert.ok(root.startsWith(scratch + sep));
    await rm(root, { recursive: true, force: true });
  });
  await mkdir(resolve(root, 'docs'));
  await writeFile(resolve(root, 'README.md'), '# Cosplay\n\nKotlin приложение.\n');
  for (const path of editableDocs) await writeFile(resolve(root, path), document);
  await writeFile(resolve(root, 'Main.kt'), 'data class Festival(val title: String)\n');
  await writeFile(resolve(root, '.env'), 'SECRET=fixture-only-not-a-real-key');
  const git = async (...args) => (await exec('git', ['-C', root, ...args])).stdout.trim();
  await git('init', '-b', 'fixture-main');
  await git('config', 'user.email', 'test@example.invalid');
  await git('config', 'user.name', 'Assistant Test');
  await git('add', '.');
  await git('commit', '-m', 'initial');
  const base = await git('rev-parse', 'HEAD');
  await writeFile(resolve(root, 'Main.kt'), 'data class Festival(val title: String, val city: String)\n');
  await writeFile(resolve(root, '.env'), 'SECRET=changed-fixture-only');
  await git('add', '.');
  await git('commit', '-m', 'add city');
  const mcp = await connectProject(root);
  t.after(() => mcp.close());
  const config = { projectRoot: root, apiKey: 'test-key', model: 'test-model', baseUrl: 'http://127.0.0.1' };
  return { root, git, base, mcp, config };
}
const reply = content => new Response(JSON.stringify({ choices: [{ message: { content } }] }), { status: 200, headers: { 'Content-Type': 'application/json' } });

test('RAG searches Markdown only and sees edits without a stale index', async t => {
  const { root } = await fixture(t);
  const first = await buildIndex(root);
  assert.ok(first.documents.includes('README.md'));
  assert.equal(first.documents.length, editableDocs.length + 1);
  assert.ok(search(first, 'фестивали API').every(s => s.path.startsWith('docs/')));
  assert.equal(search(first, 'квантозебра').length, 0);
  assert.ok(!JSON.stringify(first).includes('SECRET'));
  await writeFile(resolve(root, 'docs/architecture.md'), document + 'Квантозебра — новое описание.\n');
  const second = await buildIndex(root);
  assert.notEqual(second.fingerprint, first.fingerprint);
  assert.equal(search(second, 'Квантозебра')[0].path, 'docs/architecture.md');
});

test('RAG skips symlinked documents outside the repository', async t => {
  const { root } = await fixture(t);
  const outside = resolve(scratch, 'external-fixture.md');
  await writeFile(outside, '# Outside\nsecret sentinel\n');
  t.after(() => rm(outside, { force: true }));
  try { await symlink(outside, resolve(root, 'docs/external.md')); }
  catch (error) { if (error.code === 'EPERM') { t.skip('Windows has no symlink privilege'); return; } throw error; }
  assert.ok(!(await buildIndex(root)).documents.includes('docs/external.md'));
});

test('real MCP lists tools, reads changed branch and limits diff to code', async t => {
  const { git, base, mcp } = await fixture(t);
  const tools = await mcp.client.listTools();
  assert.deepEqual(tools.tools.map(x => x.name).sort(), ['get_current_branch', 'get_project_diff']);
  assert.equal((await mcp.context()).files, undefined);
  assert.equal((await mcp.context()).branch, 'fixture-main');
  await git('switch', '-c', 'feature/demo');
  assert.equal((await mcp.context()).branch, 'feature/demo');
  const diff = await mcp.diff(base);
  assert.deepEqual(diff.files, ['Main.kt']);
  assert.match(diff.diff, /val city/);
  assert.ok(!diff.diff.includes('SECRET'));
  await git('checkout', '--detach');
  assert.match((await mcp.context()).branch, /^detached:/);
});

test('parallel orchestration actually starts both agents and isolates evidence', async t => {
  const { config, mcp } = await fixture(t);
  const requests = [];
  let release;
  const gate = new Promise(resolve => { release = resolve; });
  const fake = async (_url, init) => {
    const body = JSON.parse(init.body);
    requests.push(body);
    const system = body.messages[0].content;
    if (system.includes('Выбери только')) return reply(JSON.stringify({ mode: 'parallel', tasks: [{ agent: 'docs_agent', task: 'Объясни архитектуру' }, { agent: 'reviewer_agent', task: 'Проверь описание по документам' }] }));
    if (system.startsWith('Ты сабагент')) {
      if (requests.filter(r => r.messages[0].content.startsWith('Ты сабагент')).length === 2) release();
      await gate;
      return reply('Факты агента [1]');
    }
    return reply('Итог с источником [1]');
  };
  const result = await orchestrate(config, mcp, 'Архитектура фестивалей', { fetchImpl: fake });
  assert.equal(result.plan.mode, 'parallel');
  assert.equal(requests.length, 4);
  const docs = JSON.parse(requests.find(r => r.messages[0].content.startsWith('Ты сабагент документации')).messages[1].content);
  const reviewer = JSON.parse(requests.find(r => r.messages[0].content.startsWith('Ты сабагент проверки')).messages[1].content);
  assert.ok(docs.documentation.length);
  assert.equal(docs.mcp, undefined);
  assert.ok(reviewer.documentation.length);
  assert.equal(reviewer.mcp, undefined);
  assert.ok(requests.every(r => !r.messages[1].content.includes('Main.kt')));
  assert.match(result.answer, /Итог/);
});

test('sequential orchestration passes first report to the next agent', async t => {
  const { config, mcp } = await fixture(t);
  const fake = async (_url, init) => {
    const body = JSON.parse(init.body);
    const system = body.messages[0].content;
    if (system.includes('Выбери только')) return reply(JSON.stringify({ mode: 'sequential', tasks: [{ agent: 'docs_agent', task: 'Найди правила' }, { agent: 'reviewer_agent', task: 'Проверь по документации' }] }));
    if (system.startsWith('Ты сабагент документации')) return reply('Первый отчёт');
    if (system.startsWith('Ты сабагент проверки')) assert.equal(JSON.parse(body.messages[1].content).previous[0].answer, 'Первый отчёт');
    return reply('Ответ');
  };
  const result = await orchestrate(config, mcp, 'Архитектура', { fetchImpl: fake });
  assert.equal(result.plan.mode, 'sequential');
  assert.ok(result.trace.findIndex(e => e.agent === 'docs_agent' && e.status === 'done') < result.trace.findIndex(e => e.agent === 'reviewer_agent' && e.status === 'running'));
});

test('router and document writer reject unknown agents and paths', () => {
  assert.throws(() => validatePlan({ mode: 'sequential', tasks: [{ agent: 'project_agent', task: 'Посмотри файлы' }] }));
  assert.throws(() => validatePlan({ mode: 'parallel', tasks: [{ agent: 'shell_agent', task: 'run' }] }));
  assert.throws(() => validatePlan({ mode: 'parallel', tasks: [{ agent: 'docs_agent', task: 'a' }, { agent: 'docs_agent', task: 'b' }] }));
  assert.throws(() => validateUpdates({ summary: 'x', updates: [{ path: '../README.md', content: document }] }));
  assert.throws(() => validateUpdates({ summary: 'x', updates: [{ path: 'Main.kt', content: document }] }));
  assert.throws(() => validateUpdates({ summary: 'x', updates: [{ path: 'docs/architecture.md', content: '' }] }));
});

test('/help sends only documentation to models and obtains only the branch from MCP', async t => {
  const { config } = await fixture(t);
  const mcp = { context: async () => ({ branch: 'docs-only', tools: ['get_current_branch'] }), diff: () => { throw new Error('must not read code'); } };
  const calls = [];
  const fake = async (_url, init) => {
    const body = JSON.parse(init.body);
    calls.push(body);
    if (body.messages[0].content.includes('Выбери только')) return reply(JSON.stringify({ mode: 'sequential', tasks: [{ agent: 'docs_agent', task: 'Ответ по docs' }] }));
    return reply('Ответ по документации [1].');
  };
  const result = await orchestrate(config, mcp, 'Фестивали API', { fetchImpl: fake });
  assert.equal(result.evidence.context.branch, 'docs-only');
  assert.ok(calls.every(call => !JSON.stringify(call).includes('Main.kt')));
  assert.ok(calls.every(call => !JSON.stringify(call).includes('list_project_files')));
});

test('unknown documentation questions and branch-only questions do not call the model', async t => {
  const { config, mcp } = await fixture(t);
  const fetchImpl = () => { throw new Error('must not call model'); };
  const unknown = await orchestrate(config, mcp, 'Квантозебра', { fetchImpl });
  assert.match(unknown.answer, /нет ответа/);
  const branch = await orchestrate(config, mcp, 'Какая сейчас Git-ветка?', { fetchImpl });
  assert.match(branch.answer, /fixture-main/);
  assert.deepEqual(branch.evidence.context.tools, ['get_current_branch']);
});

test('retrieval prioritises application architecture over assistant command examples', async t => {
  const { root } = await fixture(t);
  await writeFile(resolve(root, 'docs/architecture.md'), '# Архитектура\n\n## Модули и структура проекта\nПриложение включает composeApp, core, data и feature.\n');
  await writeFile(resolve(root, 'docs/dev-assistant.md'), '# Помощник разработчика\n\n/help Как устроена структура проекта?\n'.repeat(5));
  const index = await buildIndex(root);
  const result = search(index, 'Как устроена структура проекта?');
  assert.equal(result[0].path, 'docs/architecture.md');
  assert.equal(result[0].heading, 'Модули и структура проекта');
});

test('documentation sync previews an applicable patch then saves only docs', async t => {
  const { config, root, git, base, mcp } = await fixture(t);
  const updated = document + '\nFestival теперь содержит поле city.\n';
  const fake = async (_url, init) => {
    const body = JSON.parse(init.body);
    if (body.messages[0].content.startsWith('Ты change_agent')) return reply('В Main.kt добавлено city.');
    assert.match(JSON.parse(body.messages[1].content).analysis, /city/);
    return reply(JSON.stringify({ summary: 'Добавлено описание city', updates: [{ path: 'docs/architecture.md', content: updated }] }));
  };
  const preview = await syncDocs(config, mcp, base, { fetchImpl: fake });
  assert.equal(await readFile(resolve(root, 'docs/architecture.md'), 'utf8'), document);
  assert.match(preview.patch, /\+Festival теперь/);
  await writeFile(resolve(root, 'proposal.patch'), preview.patch);
  await git('apply', '--check', 'proposal.patch');
  const codeBefore = await readFile(resolve(root, 'Main.kt'), 'utf8');
  await syncDocs(config, mcp, base, { apply: true, fetchImpl: fake });
  assert.equal(await readFile(resolve(root, 'docs/architecture.md'), 'utf8'), updated);
  assert.equal(await readFile(resolve(root, 'Main.kt'), 'utf8'), codeBefore);
  assert.match(await git('diff', '--name-only'), /^docs\/architecture.md$/);
  const fresh = await buildIndex(root);
  assert.ok(search(fresh, 'city').length);
});

test('documentation sync refuses dirty docs before calling the model', async t => {
  const { config, root, base, mcp } = await fixture(t);
  await writeFile(resolve(root, 'docs/architecture.md'), document + 'Моя несохранённая правка.\n');
  await assert.rejects(syncDocs(config, mcp, base, { apply: true, fetchImpl: () => { throw new Error('must not call'); } }), /незакоммиченные/);
});

test('documentation sync skips empty diff without any model calls', async t => {
  const { config, mcp } = await fixture(t);
  const result = await syncDocs(config, mcp, 'HEAD', { fetchImpl: () => { throw new Error('must not call'); } });
  assert.equal(result.patch, '');
});

test('invalid refs cannot become Git options', async t => {
  const { root } = await fixture(t);
  await assert.rejects(readProjectDiff(root, '--output=evil'), /Git ref/);
  await assert.rejects(readProjectDiff(root, 'HEAD -- .env'), /Git ref/);
});

test('concurrent documentation edits are preserved during sync generation', async t => {
  const { config, root, base, mcp } = await fixture(t);
  const userEdit = document + 'Изменение пользователя во время работы агента.\n';
  const fake = async (_url, init) => {
    if (JSON.parse(init.body).messages[0].content.startsWith('Ты change_agent')) return reply('Добавлено city');
    await writeFile(resolve(root, 'docs/architecture.md'), userEdit);
    return reply(JSON.stringify({ summary: 'city', updates: [{ path: 'docs/architecture.md', content: document + 'Предложение агента.\n' }] }));
  };
  await assert.rejects(syncDocs(config, mcp, base, { apply: true, fetchImpl: fake }), /изменилась/);
  assert.equal(await readFile(resolve(root, 'docs/architecture.md'), 'utf8'), userEdit);
});

test('truncated model outputs are rejected rather than treated as valid answers', async () => {
  const config = { apiKey: 'fake', model: 'test', baseUrl: 'https://api.deepseek.com' };
  await assert.rejects(complete(config, [], async (_url, init) => {
    assert.equal(JSON.parse(init.body).thinking.type, 'disabled');
    return new Response(JSON.stringify({ choices: [{ finish_reason: 'length', message: { content: 'partial' } }] }));
  }), /обрезан/);
});

test('Chat Completions HTTP integration and API errors never expose credentials', async t => {
  const server = createServer(async (req, res) => {
    let text = '';
    for await (const chunk of req) text += chunk;
    assert.equal(req.url, '/v1/chat/completions');
    assert.equal(req.headers.authorization, 'Bearer fake-key');
    assert.equal(JSON.parse(text).model, 'fake-model');
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ choices: [{ message: { content: 'Проверенный ответ' } }] }));
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  t.after(() => new Promise(resolve => server.close(resolve)));
  const config = { baseUrl: `http://127.0.0.1:${server.address().port}/v1`, apiKey: 'fake-key', model: 'fake-model' };
  assert.equal(await complete(config, [{ role: 'user', content: 'test' }]), 'Проверенный ответ');
  await assert.rejects(complete(config, [], async () => new Response('fake-key', { status: 401 })), error => !error.message.includes('fake-key') && error.message.includes('401'));
  await assert.rejects(complete({ ...config, apiKey: '' }, []), /LLM_API_KEY/);
});

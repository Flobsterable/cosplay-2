const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const AsyncFunction = Object.getPrototypeOf(async function () {}).constructor;
const workflows = Object.fromEntries(['ai-review', 'docs-sync'].map(name => [name,
  readFileSync(resolve(__dirname, `../workflows/${name}.yml`), 'utf8').replaceAll('\r\n', '\n')]));

function scripts(name) {
  return [...workflows[name].matchAll(/          script: \|\n((?:            .*\n?)*)/g)]
    .map(match => match[1].split('\n').filter(Boolean).map(line => line.slice(12)).join('\n'));
}
function fixture({ files = [{ filename: 'core/Foo.kt' }], changed = files.length } = {}) {
  const dispatches = [], notices = [], failures = [], reads = [], outputs = {};
  const pull = { number: 42, draft: false, changed_files: changed,
    base: { sha: 'a'.repeat(40), ref: 'develop' }, head: { sha: 'b'.repeat(40), repo: { full_name: 'Flobsterable/cosplay-2' } } };
  const context = { repo: { owner: 'Flobsterable', repo: 'cosplay-2' }, payload: { pull_request: pull, before: 'c'.repeat(40) }, ref: 'refs/heads/develop', sha: 'd'.repeat(40) };
  const listFiles = () => {};
  return { dispatches, notices, failures, reads, context, outputs,
    core: { notice: value => notices.push(value), setFailed: value => failures.push(value), setOutput: (key, value) => { outputs[key] = value; } },
    github: {
      paginate: async (method, params) => { assert.equal(method, listFiles); reads.push(params); return files; },
      rest: { pulls: { listFiles }, actions: { createWorkflowDispatch: async payload => dispatches.push(payload) } }
    }
  };
}
async function execute(name, index, f, token = 'non-secret-test-credential') {
  return new AsyncFunction('github', 'context', 'core', 'process', scripts(name)[index])(
    f.github, f.context, f.core, { env: { AGENT_DISPATCH_TOKEN: token } });
}

async function review(f) {
  await execute('ai-review', 1, f);
  if (f.outputs.changed === 'true') await execute('ai-review', 2, f);
}

test('project workflows only dispatch tasks: no model credentials, runtime, writes, or code execution', () => {
  for (const [name, source] of Object.entries(workflows)) {
    assert.equal((source.match(/uses: actions\/github-script@v8/g) || []).length, name === 'ai-review' ? 3 : 2);
    assert.match(source, /github-token: \$\{\{ secrets.AGENT_DISPATCH_TOKEN \}\}/);
    assert.doesNotMatch(source, /LLM_|GEMINI_|DEEPSEEK|OPENROUTER|ASSISTANT_RUNTIME_REF|actions\/checkout|setup-node|npm|actions\/cache|upload-artifact|working-directory|\brun:/);
    assert.doesNotMatch(source, /contents: write|pull-requests: write|issues: write|require\(|import\s|exec\(|spawn\(/);
    assert.doesNotMatch(source, /secrets\.(?!AGENT_DISPATCH_TOKEN)[A-Z_]+/);
  }
  assert.match(workflows['ai-review'], /pull_request_target:/);
  assert.doesNotMatch(workflows['ai-review'], /\n  pull_request:/);
  const metadataStep = workflows['ai-review'].split('      - name: Inspect changed source metadata')[1].split('      - name: Send review task')[0];
  assert.doesNotMatch(metadataStep, /AGENT_DISPATCH_TOKEN|github-token:/);
  assert.match(workflows['ai-review'], /if: steps.source.outputs.changed == 'true'/);
  assert.match(workflows['ai-review'], /!github.event.pull_request.draft/);
  assert.match(workflows['ai-review'], /head.repo.full_name == github.repository/);
  assert.match(workflows['docs-sync'], /cancel-in-progress: false/);
  assert.doesNotMatch(workflows['docs-sync'], /\n  status:|recovery issue|docs-sync-status.cjs/);
});

test('missing dispatch credential fails clearly before dispatch in both workflows', async () => {
  for (const name of Object.keys(workflows)) {
    const f = fixture();
    await execute(name, 0, f, '');
    assert.equal(f.failures.length, 1);
    assert.match(f.failures[0], /AGENT_DISPATCH_TOKEN/);
    assert.match(f.failures[0], /GITHUB_TOKEN cannot dispatch across repositories/);
    assert.deepEqual(f.dispatches, []);
    assert.deepEqual(f.reads, []);
    const valid = fixture(); await execute(name, 0, valid);
    assert.deepEqual(valid.failures, []);
  }
});

test('review sends exact trusted repository/base/head/PR/branch metadata', async () => {
  const f = fixture();
  await review(f);
  assert.deepEqual(f.dispatches, [{ owner: 'Flobsterable', repo: 'cosplay-dev-assistant', workflow_id: 'agent.yml', ref: 'main',
    inputs: { type: 'review', repository: 'Flobsterable/cosplay-2', base: 'a'.repeat(40), head: 'b'.repeat(40), pr: '42', branch: 'develop' } }]);
  assert.deepEqual(f.reads, [{ owner: 'Flobsterable', repo: 'cosplay-2', pull_number: 42, per_page: 100 }]);
  assert.deepEqual(f.failures, []);
});

test('review skips drafts, external PRs and documentation-only changes', async () => {
  for (const scenario of ['draft', 'fork', 'docs']) {
    const f = fixture({ files: [{ filename: 'docs/features.md' }, { filename: 'README.md' }] });
    if (scenario === 'draft') f.context.payload.pull_request.draft = true;
    if (scenario === 'fork') f.context.payload.pull_request.head.repo.full_name = 'external/cosplay-2';
    await review(f);
    assert.deepEqual(f.dispatches, [], scenario);
    if (scenario !== 'docs') assert.deepEqual(f.reads, []);
  }
});

test('source gate includes XML/properties/workflows, source renames and large PRs', async () => {
  for (const file of ['app/AndroidManifest.xml', 'nested/config.properties', '.github/workflows/ci.yaml', 'foo/Code.kts', 'gradle/libs.versions.toml']) {
    const f = fixture({ files: [{ filename: file }] });
    await review(f);
    assert.equal(f.dispatches.length, 1, file);
  }
  const renamed = fixture({ files: [{ filename: 'notes.txt', previous_filename: 'core/Removed.kt' }] });
  await review(renamed);
  assert.equal(renamed.dispatches.length, 1);
  const large = fixture({ files: [], changed: 3001 });
  await review(large);
  assert.equal(large.dispatches.length, 1, 'API listing limit must not silently omit source changes');
  assert.deepEqual(large.reads, []);
});

test('documentation push sends exact push range and branch', async () => {
  const f = fixture();
  await execute('docs-sync', 1, f);
  assert.deepEqual(f.dispatches, [{ owner: 'Flobsterable', repo: 'cosplay-dev-assistant', workflow_id: 'agent.yml', ref: 'main',
    inputs: { type: 'docs-sync', repository: 'Flobsterable/cosplay-2', base: 'c'.repeat(40), head: 'd'.repeat(40), branch: 'develop' } }]);
  assert.deepEqual(f.failures, []);
});

test('documentation manual dispatch prefers supplied base/head and supports main', async () => {
  const f = fixture(); f.context.ref = 'refs/heads/main';
  f.context.payload.inputs = { base: 'e'.repeat(40), head: 'f'.repeat(40) };
  await execute('docs-sync', 1, f);
  assert.deepEqual(f.dispatches[0].inputs, { type: 'docs-sync', repository: 'Flobsterable/cosplay-2', base: 'e'.repeat(40), head: 'f'.repeat(40), branch: 'main' });
});

test('documentation rejects invalid range/branch without sending a task', async () => {
  for (const scenario of ['branch', 'base', 'head']) {
    const f = fixture();
    if (scenario === 'branch') f.context.ref = 'refs/heads/feature/example';
    if (scenario === 'base') f.context.payload.before = 'bad';
    if (scenario === 'head') f.context.sha = 'bad';
    await execute('docs-sync', 1, f);
    assert.deepEqual(f.dispatches, []);
    assert.match(f.failures[0], /develop\/main.*base\/head/);
  }
});

test('documentation trigger excludes Markdown-only commits to avoid recursion', () => {
  const source = workflows['docs-sync'];
  const paths = source.split('    paths:\n')[1].split('  workflow_dispatch:')[0];
  assert.match(source, /branches: \[develop, main\]/);
  assert.match(paths, /'\*\*\/\*\.kt'/);
  assert.match(paths, /'\.github\/workflows\/docs-sync.yml'/);
  assert.doesNotMatch(paths, /\.md|docs\/|README/);
  assert.match(source, /workflow_dispatch:[\s\S]*base:[\s\S]*required: true[\s\S]*head:[\s\S]*required: true/);
});

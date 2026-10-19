const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve, posix } = require('node:path');
const { parseEnv } = require('node:util');
const { runInNewContext } = require('node:vm');
const { spawnSync } = require('node:child_process');
const bash = process.platform === 'win32' ? 'C:/Program Files/Git/bin/bash.exe' : 'bash';
const workflow = readFileSync(resolve(__dirname, '../workflows/docs-sync.yml'), 'utf8').replaceAll('\r\n', '\n');
const loader = workflow.split("          node <<'NODE'\n")[1].split('          NODE')[0]
  .split('\n').map(line => line.replace(/^          /, '')).join('\n');

function load(text) {
  const events = [];
  runInNewContext(loader, { require(name) {
    if (name === 'node:fs') return {
      readFileSync(file, encoding) { assert.equal(file, '/server/cosplay-dev-assistant/tools/dev-assistant/.env'); assert.equal(encoding, 'utf8'); return text; },
      mkdirSync(file) { assert.equal(file, '/server/.cache/cosplay-dev-assistant/docs-responses'); },
      chmodSync(file, mode) { assert.equal(file, '/server/.cache/cosplay-dev-assistant/docs-responses'); assert.equal(mode, 0o700); },
      appendFileSync(file, value) { assert.equal(file, '/runner/github-env'); events.push({ type: 'env', value }); }
    };
    if (name === 'node:os') return { homedir: () => '/server' };
    if (name === 'node:path') return posix;
    if (name === 'node:util') return { parseEnv };
    throw new Error('Unexpected loader module');
  }, process: { env: { GITHUB_ENV: '/runner/github-env' } }, console: { log: value => events.push({ type: 'mask', value }) } });
  return events;
}

test('docs runs on the registered VPS, uses job GitHub token and trusted pinned runtime', () => {
  assert.equal((workflow.match(/runs-on: \[self-hosted, linux, x64, cosplay-agent\]/g) || []).length, 2);
  assert.equal((workflow.match(/ref: 33dec973ada96042bf036d8efa47538ae95c38e2/g) || []).length, 2);
  assert.doesNotMatch(workflow, /secrets\.|AGENT_DISPATCH_TOKEN|createWorkflowDispatch|ubuntu-latest/);
  assert.match(workflow, /PROJECT_GITHUB_TOKEN: \$\{\{ github.token \}\}/);
  assert.match(workflow, /contents: write/); assert.match(workflow, /issues: write/);
  assert.match(workflow, /queue: max/); assert.match(workflow, /cancel-in-progress: false/);
  assert.match(workflow, /persist-credentials: true/);
  assert.match(workflow, /ref: \$\{\{ env.TARGET_BRANCH \}\}/);
  assert.match(workflow, /bash "\$ASSISTANT_ROOT\/\.github\/scripts\/docs-sync-run.sh"/);
  assert.match(workflow, /run: node assistant\/\.github\/scripts\/agent-request.cjs status/);
  assert.doesNotMatch(workflow, /run:.*(?:project\/\.github|\.\/gradlew)|source .*\.env|\. "\$HOME/);
});

test('environment imports only model settings and masks credentials before exporting them', () => {
  const events = load('LLM_API_KEY=primary-test\nGEMINI_API_KEY="small-test"\nGEMINI_MODEL=example\nPROJECT_ROOT=/evil\nPATH=/evil\nPROJECT_GITHUB_TOKEN=not-imported\n');
  assert.deepEqual(events.slice(0, 2).sort((a, b) => a.value.localeCompare(b.value)), [{ type: 'mask', value: '::add-mask::primary-test' }, { type: 'mask', value: '::add-mask::small-test' }]);
  assert.equal(events[2].type, 'env');
  const values = parseEnv(events[2].value);
  assert.equal(values.LLM_API_KEY, 'primary-test'); assert.equal(values.GEMINI_API_KEY, 'small-test');
  assert.equal(values.GEMINI_MODEL, 'example');
  assert.equal(values.LLM_CACHE_DIR, '/server/.cache/cosplay-dev-assistant/docs-responses');
  assert.equal(values.PROJECT_ROOT, undefined); assert.equal(values.PATH, undefined); assert.equal(values.PROJECT_GITHUB_TOKEN, undefined);
});

test('dotenv contents are data and never execute shell substitutions', () => {
  const events = load('LLM_MODEL=$(touch /forbidden)\nGEMINI_MODEL=`whoami`\n');
  const values = parseEnv(events[0].value);
  assert.equal(values.LLM_MODEL, '$(touch /forbidden)'); assert.equal(values.GEMINI_MODEL, 'whoami');
});

test('invalid pasted commands and multiline values fail without exposing their contents', () => {
  assert.throws(() => load('LLM_API_KEY=hidden-secret\ncurl private-command\n'), error => /line 2/.test(error.message) && !/hidden-secret|private-command/.test(error.message));
  assert.throws(() => load('GEMINI_API_KEY="first\nsecond"\n'), error => !/first|second/.test(error.message));
});

test('primary-only flag is explicit, validated and disabled by default', () => {
  assert.match(workflow, /DOCS_SYNC_USE_PRIMARY_ONLY: 'false'/);
  for (const value of ['true', 'false']) {
    assert.equal(parseEnv(load('DOCS_SYNC_USE_PRIMARY_ONLY=' + value)[0].value).DOCS_SYNC_USE_PRIMARY_ONLY, value);
  }
  assert.throws(() => load('DOCS_SYNC_USE_PRIMARY_ONLY=anything'), /must be true or false/);
});

test('credentials fail before model calls and Gemini is disabled only with the explicit server flag', () => {
  const check = workflow.split('      - name: Check model credentials without exposing values')[1]
    .split('      - name: Checkout project as data')[0].split('        run: |\n')[1]
    .split('\n').map(line => line.replace(/^          /, '')).join('\n');
  for (const [flag, primary, gemini, status] of [['false', '', 'small-test', 0], ['false', 'primary-test', '', 1], ['true', 'primary-test', '', 0], ['true', '', 'small-test', 1]]) {
    const result = spawnSync(bash, ['-c', check], { env: { ...process.env, DOCS_SYNC_USE_PRIMARY_ONLY: flag, LLM_API_KEY: primary, GEMINI_API_KEY: gemini }, encoding: 'utf8' });
    assert.equal(result.status, status);
    assert.doesNotMatch(result.stdout + result.stderr, /primary-test|small-test/);
  }
  const generation = workflow.split('      - name: Generate, verify and commit documentation')[1]
    .split('      - name: Preserve diagnostics')[0].split('        run: |\n')[1]
    .split('\n').map(line => line.replace(/^          /, '')).join('\n')
    .replace('bash "$ASSISTANT_ROOT/.github/scripts/docs-sync-run.sh"', 'printf "%s|%s" "${GEMINI_API_KEY-unset}" "${LLM_SMALL_TASK_PROVIDER-unset}"');
  for (const flag of ['true', 'false']) {
    const result = spawnSync(bash, ['-c', generation], { env: { ...process.env, DOCS_SYNC_USE_PRIMARY_ONLY: flag, GEMINI_API_KEY: 'small-test', LLM_SMALL_TASK_PROVIDER: 'gemini' }, encoding: 'utf8' });
    assert.equal(result.status, 0);
    assert.match(result.stdout, flag === 'true' ? /unset\|unset$/ : /small-test\|gemini$/);
    if (flag === 'true') assert.match(result.stdout, /explicitly uses the primary model/);
  }
});

test('model credentials load after Node setup and no model call precedes credential validation', () => {
  assert.ok(workflow.indexOf('actions/setup-node@v4') < workflow.indexOf('Load model configuration'));
  assert.ok(workflow.indexOf('Load model configuration') < workflow.indexOf('Check model credentials'));
  assert.ok(workflow.indexOf('Check model credentials') < workflow.indexOf('Generate, verify'));
  assert.match(workflow, /PROJECT_ROOT: \$\{\{ github.workspace \}\}\/project/);
  assert.match(workflow, /ASSISTANT_ROOT: \$\{\{ github.workspace \}\}\/assistant/);
  assert.match(workflow, /git merge-base --is-ancestor "\$BASE_SHA" "\$MERGED_SHA"/);
  assert.match(workflow, /git merge-base --is-ancestor "\$MERGED_SHA" HEAD/);
});

test('docs-only commits cannot recurse and failures retain diagnostics and recovery', () => {
  const paths = workflow.split('    paths:\n')[1].split('  workflow_dispatch:')[0];
  assert.doesNotMatch(paths, /\.md|docs\/|README/);
  assert.match(workflow, /REQUEST_BASE: \$\{\{ inputs.base \|\| github.event.before \}\}/);
  assert.match(workflow, /REQUEST_HEAD: \$\{\{ inputs.head \|\| github.sha \}\}/);
  assert.match(workflow, /needs.sync.result == 'failure'/);
  assert.match(workflow, /docs-sync-generated.patch/);
  assert.match(workflow, /LLM_MAX_CALLS: '24'/);
  assert.match(workflow, /LLM_MAX_INPUT_CHARACTERS: '1000000'/);
  assert.match(workflow, /LLM_EXECUTION_MODE: economy/);
});

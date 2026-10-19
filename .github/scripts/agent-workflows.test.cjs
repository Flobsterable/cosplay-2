const { test } = require('node:test');
const assert = require('node:assert/strict');
const { mkdtempSync, mkdirSync, writeFileSync, readFileSync, existsSync, rmSync } = require('node:fs');
const { tmpdir } = require('node:os');
const { resolve, join } = require('node:path');
const { spawnSync } = require('node:child_process');
const bash = process.platform === 'win32' ? 'C:/Program Files/Git/bin/bash.exe' : 'bash';
const script = resolve(__dirname, 'docs-sync-run.sh');

function runScenario(t, failModel) {
  const dir = mkdtempSync(join(tmpdir(), 'docs-workflow-'));
  t.after(() => rmSync(dir, { recursive: true, force: true }));
  mkdirSync(join(dir, 'bin'));
  writeFileSync(join(dir, 'bin', 'node'), `#!/usr/bin/env bash
echo request >> "$MOCK_ROOT/calls"
echo '${failModel ? 'HTTP 402: insufficient balance' : 'Documents generated and verified'}'
exit ${failModel ? 1 : 0}
`, { mode: 0o755 });
  writeFileSync(join(dir, 'bin', 'git'), `#!/usr/bin/env bash
case "$1" in
  rev-parse) if [ "$2" = FETCH_HEAD ]; then echo remote-head; else echo generation-head; fi ;;
  log) : ;;
  merge-base) if [ "$4" = FETCH_HEAD ]; then exit 1; fi ;;
  diff)
    case "$3" in
      --quiet) exit 1 ;;
      --name-only) printf 'docs/features.md\\0' ;;
      --binary) echo 'saved generated changes' ;;
    esac ;;
  push) echo rejected >&2; exit 1 ;;
esac
`, { mode: 0o755 });
  const result = spawnSync(bash, ['-c', 'export MOCK_ROOT="$(cd "$MOCK_ROOT" && pwd)"; export PATH="$MOCK_ROOT/bin:$PATH"; export RUNNER_TEMP="$MOCK_ROOT"; export GITHUB_STEP_SUMMARY="$MOCK_ROOT/summary"; bash "$TEST_SCRIPT"'], {
    env: { ...process.env, MOCK_ROOT: dir, TEST_SCRIPT: script.replaceAll('\\', '/'), TARGET_BRANCH: 'develop', BASE_SHA: 'a'.repeat(40), MERGED_SHA: 'b'.repeat(40) }, encoding: 'utf8'
  });
  assert.equal(result.status, 1, result.stderr);
  assert.equal(readFileSync(join(dir, 'calls'), 'utf8'), 'request\n', 'exactly one paid generation');
  assert.match(readFileSync(join(dir, 'docs-sync-generation.txt'), 'utf8'), failModel ? /HTTP 402/ : /verified/);
  return { dir, result };
}

test('permanent model failure stops immediately and preserves its report', t => {
  const { dir, result } = runScenario(t, true);
  assert.match(result.stdout, /No automatic regeneration/);
  assert.equal(existsSync(join(dir, 'docs-sync-generated.patch')), false);
});

test('push conflict preserves the patch without regenerating documentation', t => {
  const { dir, result } = runScenario(t, false);
  assert.match(result.stdout, /Target branch moved/);
  assert.match(readFileSync(join(dir, 'docs-sync-generated.patch'), 'utf8'), /saved generated changes/);
});

test('review preflight skips docs but includes XML, properties, and workflow YAML', t => {
  const dir = mkdtempSync(join(tmpdir(), 'review-gate-'));
  t.after(() => rmSync(dir, { recursive: true, force: true }));
  mkdirSync(join(dir, 'project'));
  const git = (...args) => {
    const result = spawnSync('git', ['-C', join(dir, 'project'), ...args], { encoding: 'utf8' });
    assert.equal(result.status, 0, result.stderr);
    return result.stdout.trim();
  };
  git('init'); git('config', 'user.name', 'Workflow Test'); git('config', 'user.email', 'test@example.com');
  const workflow = readFileSync(resolve(__dirname, '../workflows/ai-review.yml'), 'utf8');
  const block = workflow.split('      - name: Skip changes outside supported source files')[1]
    .split('      - name: Checkout trusted review runtime')[0].split('        run: |\n')[1];
  const command = block.split('\n').map(line => line.replace(/^          /, '')).join('\n');
  writeFileSync(join(dir, 'project', 'README.md'), 'Initial documentation');
  git('add', '.'); git('-c', 'commit.gpgsign=false', 'commit', '-m', 'initial');
  for (const [file, expected] of [['docs/features.md', false], ['composeApp/src/main/AndroidManifest.xml', true], ['core/resources/config.properties', true], ['.github/workflows/build.yaml', true]]) {
    const base = git('rev-parse', 'HEAD');
    mkdirSync(resolve(dir, 'project', file, '..'), { recursive: true });
    writeFileSync(join(dir, 'project', file), 'Changed test data');
    git('add', '.'); git('-c', 'commit.gpgsign=false', 'commit', '-m', file);
    const output = join(dir, 'output'); writeFileSync(output, '');
    const result = spawnSync(bash, ['-c', command], { cwd: dir, encoding: 'utf8', env: {
      ...process.env, BASE_SHA: base, HEAD_SHA: git('rev-parse', 'HEAD'), GITHUB_OUTPUT: output.replaceAll('\\', '/'), GITHUB_STEP_SUMMARY: join(dir, 'summary').replaceAll('\\', '/')
    } });
    assert.equal(result.status, 0, result.stderr);
    assert.equal(readFileSync(output, 'utf8'), `changed=${expected}\n`, file);
  }
});

test('workflows retain recovery, skip drafts/docs-only review, and bound paid requests', () => {
  const docs = readFileSync(resolve(__dirname, '../workflows/docs-sync.yml'), 'utf8');
  const review = readFileSync(resolve(__dirname, '../workflows/ai-review.yml'), 'utf8');
  for (const workflow of [docs, review]) {
    assert.match(workflow, /LLM_EXECUTION_MODE: economy/);
    assert.match(workflow, /LLM_MAX_CALLS.*'24'/);
    assert.match(workflow, /LLM_MAX_INPUT_CHARACTERS.*'1000000'/);
    assert.match(workflow, /LLM_CACHE_DIR: \$\{\{ runner.temp \}\}\/assistant-cache/);
    assert.match(workflow, /always\(\) && steps.responses.outcome == 'success'/);
    assert.match(workflow, /LLM_SMALL_TASK_PROVIDER: gemini/);
    assert.match(workflow, /GEMINI_MODEL:.*gemini-3.5-flash-lite/);
    assert.match(workflow, /GEMINI_BASE_URL:.*generativelanguage.googleapis.com\/v1beta\/openai/);
    assert.match(workflow, /LLM_SMALL_TASK_MAX_INPUT_CHARACTERS:.*'24000'/);
    assert.match(workflow, /LLM_SMALL_TASK_MAX_OUTPUT_TOKENS:.*'6000'/);
    assert.match(workflow, /LLM_API_KEY: \$\{\{ secrets.LLM_API_KEY \}\}/);
    assert.match(workflow, /GEMINI_API_KEY: \$\{\{ secrets.GEMINI_API_KEY \}\}/);
    const hashLine = workflow.split('\n').find(line => line.includes('const keys ='));
    for (const key of ['LLM_SMALL_TASK_PROVIDER', 'GEMINI_MODEL', 'GEMINI_BASE_URL', 'LLM_SMALL_TASK_MAX_INPUT_CHARACTERS', 'LLM_SMALL_TASK_MAX_OUTPUT_TOKENS']) {
      assert.ok(hashLine.includes(`'${key}'`), `routing setting ${key} is included in cache configuration hash`);
    }
    assert.doesNotMatch(hashLine, /API_KEY/, 'secret names and values do not enter the cache configuration hash');
  }
  assert.match(docs, /needs: sync/);
  assert.match(docs, /docs-sync-generated.patch/);
  assert.match(docs, /group: docs-sync-\$\{\{ github.ref_name \}\}-\$\{\{ inputs.head \|\| github.sha \}\}/);
  assert.match(review, /!github.event.pull_request.draft/);
  assert.match(review, /git -C project diff --quiet/);
  assert.doesNotMatch(review, /review-complete|steps\.completed/);
  assert.match(review, /'\*\.xml'/);
  assert.match(review, /'\*\.properties'/);
  assert.match(review, /'\.github\/workflows\/\*\.ya?ml'/);
  assert.match(review, /review-responses-v1-.*ASSISTANT_RUNTIME_REF.*cache-config.outputs.hash.*base.sha.*head.sha/);
});

test('both model prechecks allow Gemini-only credentials and fail fast when Gemini is missing', () => {
  for (const name of ['docs-sync.yml', 'ai-review.yml']) {
    const workflow = readFileSync(resolve(__dirname, '../workflows', name), 'utf8');
    const check = workflow.match(/          if \[ -z "\$GEMINI_API_KEY" \]; then\n[\s\S]*?          fi/)[0];
    const command = check.split('\n').map(line => line.slice(10)).join('\n');
    const missing = spawnSync(bash, ['-c', command], { env: { ...process.env, LLM_API_KEY: '', GEMINI_API_KEY: '' }, encoding: 'utf8' });
    assert.equal(missing.status, 1, name);
    assert.match(missing.stdout, /Save GEMINI_API_KEY/);
    const geminiOnly = spawnSync(bash, ['-c', command], { env: { ...process.env, LLM_API_KEY: '', GEMINI_API_KEY: 'non-secret-test-value' }, encoding: 'utf8' });
    assert.equal(geminiOnly.status, 0, name);
    assert.equal(geminiOnly.stdout, '');
    assert.equal(geminiOnly.stderr, '');
  }
});

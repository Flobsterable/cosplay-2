const test = require('node:test');
const assert = require('node:assert/strict');
const track = require('./docs-sync-status.cjs');

function fixture(result, issues = [], head = 'b'.repeat(40)) {
  const writes = [], notices = [];
  const summary = { addHeading() { return this; }, addLink() { return this; }, async write() {} };
  return {
    writes, notices,
    args: {
      env: { TARGET_BRANCH: 'develop', BASE_SHA: 'a'.repeat(40), MERGED_SHA: head, SYNC_RESULT: result },
      context: { repo: { owner: 'owner', repo: 'project' }, serverUrl: 'https://github.com', runId: 123, runAttempt: 2 },
      core: { summary, notice: value => notices.push(value), warning: value => notices.push(value) },
      github: {
        paginate: async () => issues,
        rest: { issues: { listForRepo() {},
          create: async value => { writes.push({ action: 'create', ...value }); return { data: { html_url: 'https://github.com/owner/project/issues/1' } }; },
          update: async value => { writes.push({ action: 'update', ...value }); return { data: { html_url: 'https://github.com/owner/project/issues/1' } }; }
        } }
      }
    }
  };
}
const issue = { number: 1, body: `<!-- docs-sync-recovery:develop:${'b'.repeat(40)} -->\nFailed` };

test('failure creates a durable task with exact recovery inputs and no model logs', async () => {
  const f = fixture('failure'); await track(f.args);
  assert.equal(f.writes.length, 1); assert.equal(f.writes[0].action, 'create');
  assert.ok(f.writes[0].body.includes('a'.repeat(40))); assert.ok(f.writes[0].body.includes('b'.repeat(40)));
  assert.match(f.writes[0].body, /actions\/runs\/123/); assert.match(f.writes[0].body, /Run workflow/);
});
test('a repeat updates the existing task instead of creating duplicates', async () => {
  const f = fixture('failure', [issue]); await track(f.args);
  assert.equal(f.writes[0].action, 'update'); assert.equal(f.writes[0].issue_number, 1);
});
test('success closes only the recovery task for the same branch and SHA', async () => {
  const f = fixture('success', [issue]); await track(f.args);
  assert.equal(f.writes[0].state, 'closed'); assert.equal(f.writes[0].state_reason, 'completed');
});
test('another merge does not close unresolved documentation debt', async () => {
  const f = fixture('success', [issue], 'c'.repeat(40)); await track(f.args);
  assert.deepEqual(f.writes, []);
});
test('successful runs without debt do not create noise', async () => {
  const f = fixture('success'); await track(f.args); assert.deepEqual(f.writes, []);
});
test('cancelled generation also records unresolved work', async () => {
  const f = fixture('cancelled'); await track(f.args); assert.equal(f.writes[0].action, 'create');
});
test('a PR body containing the marker cannot be modified as a recovery task', async () => {
  const f = fixture('failure', [{ ...issue, pull_request: {} }]); await track(f.args);
  assert.equal(f.writes[0].action, 'create');
});
test('invalid ranges or branches are rejected before API writes', async () => {
  for (const replacement of [{ TARGET_BRANCH: 'feature/unsafe' }, { BASE_SHA: 'invalid' }, { MERGED_SHA: 'bad' }, { SYNC_RESULT: 'skipped' }]) {
    const f = fixture('failure'); Object.assign(f.args.env, replacement);
    await assert.rejects(track(f.args)); assert.deepEqual(f.writes, []);
  }
});

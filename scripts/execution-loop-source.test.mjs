import { test } from 'node:test';
import assert from 'node:assert/strict';
import { fetchGitHubPool, issueToTask } from './execution-loop-source.mjs';

const repository = 'example/repo';
function issue(number, extra = {}) {
    return { number, title: `Task ${number}`, state: 'open', body: '## Описание\nКонтекст\n\n## Критерии готовности\n- [ ] Тест проходит\n\n## Примечания\nНе критерий',
        labels: [{ name: 'execution-loop' }, { name: 'type:bug' }], updated_at: '2026-10-24T00:00:00Z', ...extra };
}
const config = { repository, label: 'execution-loop', limit: 15 };
const response = items => ({ ok: true, json: async () => items });

test('converts issue with complete context, criteria and canonical provenance', () => {
    const task = issueToTask(issue(101), 0, repository);
    assert.equal(task.id, 'EL-01');
    assert.equal(task.kind, 'bug');
    assert.equal(task.acceptance, '- [ ] Тест проходит');
    assert.match(task.description, /Контекст/);
    assert.equal(task.source.url, 'https://github.com/example/repo/issues/101');
    assert.equal(task.source.updatedAt, '2026-10-24T00:00:00Z');
});

test('supports English criteria and ignores headings inside fenced examples', () => {
    const task = issueToTask(issue(1, { body: '```md\n## Критерии готовности\nfake\n```\n## Acceptance criteria\nActual criteria' }), 1, repository);
    assert.equal(task.acceptance, 'Actual criteria');
    assert.equal(task.id, 'EL-02');
});

test('rejects missing, empty, duplicated or unfinished criteria and ambiguous types', () => {
    for (const body of ['', '## Критерии готовности\n ', '## Acceptance criteria\n<!-- TODO -->',
        '## Acceptance criteria\nA\n## Acceptance criteria\nB']) {
        assert.throws(() => issueToTask(issue(1, { body }), 0, repository));
    }
    assert.throws(() => issueToTask(issue(1, { labels: [] }), 0, repository), /exactly one/);
    assert.throws(() => issueToTask(issue(1, { labels: ['type:docs', 'type:bug'] }), 0, repository), /exactly one/);
});

test('fetch filters PRs, closed and other-label issues, paginates and orders deterministically', async () => {
    const calls = [];
    const pool = await fetchGitHubPool(config, { token: undefined, request: async (url, options) => {
        calls.push(url);
        assert.equal(url.origin, 'https://api.github.com');
        assert.equal(url.searchParams.get('labels'), 'execution-loop');
        assert.equal(url.searchParams.get('state'), 'open');
        assert.equal(options.redirect, 'error');
        if (url.searchParams.get('page') === '1') return response(Array.from({ length: 100 }, (_, i) => issue(i + 1, { pull_request: {} })));
        return response([...Array.from({ length: 15 }, (_, i) => issue(200 - i)),
            issue(1, { state: 'closed' }), issue(2, { labels: ['type:bug'] })]);
    } });
    assert.equal(calls.length, 2);
    assert.equal(pool.tasks.length, 15);
    assert.equal(pool.tasks[0].source.number, 186);
    assert.equal(pool.tasks[14].source.number, 200);
});

test('credentials used only as request header; HTTP errors contain no token or server body', async () => {
    const token = 'fixture-sensitive-value';
    await assert.rejects(fetchGitHubPool(config, { token, request: async (_url, options) => {
        assert.equal(options.headers.Authorization, `Bearer ${token}`);
        return { ok: false, status: 403, text: async () => token };
    } }), error => /GitHub HTTP 403/.test(error.message) && !error.message.includes(token));
});

test('empty or undersized queue fails explicitly without fallback', async () => {
    await assert.rejects(fetchGitHubPool(config, { request: async () => response([]) }), /Only 0/);
    await assert.rejects(fetchGitHubPool(config, { request: async () => response([issue(1)]) }), /at least 15/);
});

test('network failure hides request internals and invalid config never reaches network', async () => {
    await assert.rejects(fetchGitHubPool(config, { request: async () => { throw new Error('secret header'); } }),
        error => /request failed/.test(error.message) && !error.message.includes('secret'));
    for (const invalid of [{ ...config, repository: '../escape' }, { ...config, label: '' }, { ...config, limit: 21 }]) {
        await assert.rejects(fetchGitHubPool(invalid, { request: async () => { assert.fail('must not fetch'); } }), /GitHub source needs/);
    }
});

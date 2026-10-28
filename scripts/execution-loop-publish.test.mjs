import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createGitHubPublisher, publicationOptions, taskPullRequestBody } from './execution-loop-publish.mjs';

const config = { mode: 'draft-pr', repository: 'fixture/project', remote: 'origin',
    baseBranch: 'codex/baseline', branchPrefix: 'codex/execution-loop', strategy: 'stacked' };
const baseline = 'a'.repeat(40);
function fixture(options = {}) {
    const calls = [], pushes = [], prs = [], refs = new Map(), bases = new Map([[config.baseBranch, baseline]]);
    let timeout = options.timeout;
    const git = (_cwd, args) => {
        calls.push(args);
        if (args[0] === 'remote') return options.remote || 'https://github.com/fixture/project.git';
        if (args[0] === 'branch' && args[1] === '--show-current') return config.baseBranch;
        if (args[0] === 'rev-parse') return baseline;
        if (args[0] === 'for-each-ref') return refs.get(args.at(-1).replace('refs/heads/', '')) || '';
        if (args[0] === 'branch') refs.set(args[1], args[2]);
        if (args[0] === 'push') {
            pushes.push(args);
            const [src, dst] = args[2].split(':');
            bases.set(dst.replace('refs/heads/', ''), refs.get(src) || src);
        }
        return '';
    };
    const request = async (url, init) => {
        calls.push([init.method, url]);
        if (options.denied) return { ok: false, status: 403, json: async () => ({ message: 'private token details' }) };
        let data;
        const parsed = new URL(url);
        if (parsed.pathname.endsWith('/project')) data = { permissions: { push: true } };
        else if (parsed.pathname.includes('/branches/')) data = { commit: { sha: bases.get(decodeURIComponent(parsed.pathname.split('/branches/')[1])) } };
        else if (init.method === 'GET') {
            const branch = parsed.searchParams.get('head').split(':')[1];
            data = prs.filter(pr => pr.head.ref === branch);
        } else {
            const body = JSON.parse(init.body);
            data = { number: prs.length + 1, state: 'open', draft: true, head: { ref: body.head, sha: bases.get(body.head) }, base: { ref: body.base },
                html_url: `https://github.com/fixture/project/pull/${prs.length + 1}`, body: body.body };
            prs.push(data);
            if (timeout) { timeout = false; throw new Error('secret in transport error'); }
        }
        return { ok: true, json: async () => data };
    };
    const create = () => createGitHubPublisher({ cwd: '.', config, runId: 'test-run', baseRevision: baseline, token: 'private-test-token', git, request });
    return { create, calls, pushes, prs };
}
const task = { id: 'EL-01', title: 'Small change', kind: 'docs', acceptance: 'Documented', source: { url: 'https://www.notion.so/task' } };
const row = { commit: 'b'.repeat(40), steps: [{ handoff: { verification: [{ command: 'SECRET COMMAND', result: 'SECRET LOG' }] } }], reviews: [{ verdict: 'APPROVE', findings: [] }] };

test('preflight is read-only and every published task has its own stacked draft PR', async () => {
    const f = fixture();
    const publisher = await f.create();
    assert.equal(f.pushes.length, 0);
    await publisher.prepare();
    const first = await publisher.publish({ task, row });
    const second = await publisher.publish({ task: { ...task, id: 'EL-02' }, row: { ...row, commit: 'c'.repeat(40) } });
    assert.equal(first.base, config.baseBranch);
    assert.equal(second.base, first.branch);
    assert.equal(second.parentUrl, first.url);
    assert.equal(f.prs.length, 2);
    assert.equal(f.pushes.length, 3);
    assert.ok(f.pushes.every(args => !args.some(arg => /force|tags/.test(arg))));
    assert.match(f.prs[0].body, /notion.so\/task/);
    assert.doesNotMatch(f.prs[0].body, /SECRET/);
});

test('ambiguous POST timeout reads back existing PR without duplication', async () => {
    const f = fixture({ timeout: true });
    const publisher = await f.create();
    await publisher.prepare();
    const result = await publisher.publish({ task, row });
    assert.equal(result.number, 1);
    assert.equal(f.prs.length, 1);
});

test('publication requires approval and prepared baseline', async () => {
    const f = fixture();
    const publisher = await f.create();
    await assert.rejects(publisher.publish({ task, row }), /not prepared/);
    await publisher.prepare();
    await assert.rejects(publisher.publish({ task, row: { ...row, reviews: [] } }), /APPROVE/);
    assert.equal(f.prs.length, 0);
});

test('wrong repository and denied credentials fail without leaking response bodies', async () => {
    await assert.rejects(fixture({ remote: 'https://github.com/other/repo.git' }).create(), /does not match/);
    await assert.rejects(fixture({ denied: true }).create(), error => error.message === 'GitHub publication HTTP 403');
    assert.throws(() => publicationOptions({ ...config, branchPrefix: 'other/branch' }), /codex/);
});

test('closed existing PR is not reused or silently replaced', async () => {
    const f = fixture();
    const publisher = await f.create();
    await publisher.prepare();
    await publisher.publish({ task, row });
    // A fresh publisher models retry before the parent pointer advanced.
    f.prs[0].state = 'closed';
    const retry = await f.create();
    await retry.prepare();
    await assert.rejects(retry.publish({ task, row }), /does not match/);
    assert.equal(f.prs.length, 1);
});

test('PR description publishes check summaries rather than arbitrary command logs', () => {
    const body = taskPullRequestBody({ task, row, runId: 'test', verificationDirectory: 'build/checks' });
    assert.doesNotMatch(body, /SECRET/);
    assert.match(body, /APPROVE/);
    assert.match(body, /не опубликован/);
});

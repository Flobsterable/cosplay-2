import { spawnSync } from 'node:child_process';

export function runGit(cwd, args, input) {
    const result = spawnSync('git', args, { cwd, input, encoding: 'utf8', windowsHide: true, timeout: 60000,
        env: { ...process.env, GIT_TERMINAL_PROMPT: '0', GCM_INTERACTIVE: 'never' } });
    if (result.error || result.status !== 0) throw new Error(`Git operation failed (${args[0]}). Check repository access and branch state`);
    return result.stdout.trim();
}

export function publicationOptions(config = { mode: 'local' }) {
    if (config.mode === 'local') return { mode: 'local' };
    if (config.mode !== 'draft-pr' || !/^[\w-]+\/[\w.-]+$/.test(config.repository) ||
        !/^[a-zA-Z0-9][\w-]*$/.test(config.remote || '') || !config.baseBranch ||
        !(config.branchPrefix || '').startsWith('codex/') || config.strategy !== 'stacked') {
        throw new Error('Publication needs mode=draft-pr, owner/repo, remote, baseBranch, codex/ branchPrefix and strategy=stacked');
    }
    return config;
}

export function taskPullRequestBody({ task, row, runId, parent, verificationDirectory }) {
    const lines = [`${task.title}.`, '', `Критерии готовности: ${task.acceptance}`, '',
        task.source?.url ? `Задача: ${task.source.url}` : `Задача: ${task.id}`, '',
        `Execution Loop: ${runId}; ${task.id}.`,
        parent?.url ? `Основание цепочки: ${parent.url}. Сначала сливается родительский PR.` : 'Первый PR цепочки; основание — исходная ветка запуска.', '',
        'Проверки выполнены локально до коммита:',
        '- git diff --check: успешно.',
        ...(task.kind === 'docs' ? [] : ['- Проверка актуальности отчёта code tests и MCP UI smoke: успешно.']),
        `- Независимое ревью: ${row.reviews.at(-1)?.verdict || 'не подтверждено'}.`,
        verificationDirectory ? `- Полный локальный отчёт: ${verificationDirectory}/report.md (не опубликован как CI-артефакт).` : '', '',
        'Результаты GitHub Actions проверяются отдельно. Автослияние не включено.',
        `<!-- cosplay-execution-loop:${runId}:${task.id} -->`];
    return lines.filter(line => line !== undefined).join('\n');
}

export async function createGitHubPublisher({ cwd, config, runId, baseRevision, request = fetch,
    token = process.env.GITHUB_TOKEN || process.env.GH_TOKEN, git = runGit }) {
    publicationOptions(config);
    if (config.mode === 'local') return null;
    if (!/^[a-z0-9][a-z0-9-]*$/.test(runId)) throw new Error('Invalid publication run ID');
    const origin = git(cwd, ['remote', 'get-url', config.remote]);
    const expected = `github.com/${config.repository}`.toLowerCase();
    const normalized = origin.replace(/^https?:\/\//, '').replace(/^git@github.com:/, 'github.com/').replace(/\.git$/, '').replace(/\/$/, '').toLowerCase();
    if (normalized !== expected) throw new Error('Publication repository does not match the configured Git remote');
    git(cwd, ['check-ref-format', '--branch', config.baseBranch]);
    git(cwd, ['check-ref-format', '--branch', `${config.branchPrefix}/${runId}/el-01`]);
    if (git(cwd, ['branch', '--show-current']) !== config.baseBranch) throw new Error(`Start publication from ${config.baseBranch}; current branch differs`);
    if (!token) {
        let credential;
        try { credential = git(cwd, ['credential', 'fill'], `protocol=https\nhost=github.com\npath=${config.repository}.git\n\n`); }
        catch { throw new Error('GitHub credentials unavailable. Sign in through Git Credential Manager or set GH_TOKEN/GITHUB_TOKEN outside Git'); }
        token = credential.split(/\r?\n/).find(line => line.startsWith('password='))?.slice(9);
    }
    if (!token) throw new Error('GitHub credentials unavailable for draft PR creation');
    const api = async (method, route, body) => {
        let response;
        try {
            response = await request(`https://api.github.com/repos/${config.repository}${route}`, {
                method, headers: { Accept: 'application/vnd.github+json', Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', 'User-Agent': 'cosplay-2-execution-loop' },
                ...(body ? { body: JSON.stringify(body) } : {}), redirect: 'error', signal: AbortSignal.timeout(30000),
            });
        } catch { throw new Error('GitHub publication request failed or timed out'); }
        if (!response.ok) throw new Error(`GitHub publication HTTP ${response.status}`);
        try { return await response.json(); } catch { throw new Error('Invalid GitHub publication response'); }
    };
    const repo = await api('GET', '');
    if (!repo.permissions?.push || repo.archived) throw new Error('GitHub repository is archived or credentials have no push permission');
    let parent = null;
    let prepared = false;
    const validatePr = (pr, branch, base, commit) => {
        if (!pr || pr.state !== 'open' || !pr.draft || pr.head?.ref !== branch || pr.base?.ref !== base || pr.head?.sha !== commit ||
            !Number.isSafeInteger(pr.number) || pr.html_url !== `https://github.com/${config.repository}/pull/${pr.number}`) {
            throw new Error('Existing/created PR does not match expected draft, branch, base or commit');
        }
        return { url: pr.html_url, number: pr.number, branch, base, commit, parentUrl: parent?.url || null, draft: true };
    };
    return {
        repository: config.repository, baseBranch: config.baseBranch,
        async prepare() {
            if (git(cwd, ['rev-parse', 'HEAD']) !== baseRevision) throw new Error('Publication baseline changed');
            // No force, tags, merge or release. The first PR needs this exact
            // preparation baseline as its remote base, not an unrelated develop.
            git(cwd, ['push', config.remote, `${baseRevision}:refs/heads/${config.baseBranch}`]);
            prepared = true;
        },
        async publish({ task, row, verificationDirectory, onProgress = () => {} }) {
            if (!prepared) throw new Error('Publication baseline is not prepared');
            const review = row.reviews?.at(-1);
            if (review?.verdict !== 'APPROVE' || review.findings?.length !== 0 || !/^[a-f0-9]{40,64}$/.test(row.commit || '')) {
                throw new Error('Publication requires a committed result and independent APPROVE without findings');
            }
            const branch = `${config.branchPrefix}/${runId}/${task.id.toLowerCase()}`;
            const base = parent?.branch || config.baseBranch;
            const commit = row.commit;
            const remoteBase = await api('GET', `/branches/${encodeURIComponent(base)}`);
            if (remoteBase.commit?.sha !== (parent?.commit || baseRevision)) throw new Error('Remote PR base changed during the run');
            const existingLocal = git(cwd, ['for-each-ref', '--format=%(objectname)', `refs/heads/${branch}`]);
            if (existingLocal && existingLocal !== commit) throw new Error('Publication branch already contains a different commit');
            if (!existingLocal) git(cwd, ['branch', branch, commit]);
            onProgress({ branch, base, commit, phase: 'BRANCH_CREATED' });
            git(cwd, ['push', config.remote, `${branch}:refs/heads/${branch}`]);
            onProgress({ branch, base, commit, phase: 'PUSHED' });
            const owner = config.repository.split('/')[0];
            const query = `/pulls?state=all&head=${encodeURIComponent(`${owner}:${branch}`)}&base=${encodeURIComponent(base)}`;
            const find = async () => {
                const matches = await api('GET', query);
                if (!Array.isArray(matches) || matches.length > 1) throw new Error('Ambiguous existing PR for task branch');
                return matches[0];
            };
            let pr = await find();
            if (!pr) {
                try {
                    pr = await api('POST', '/pulls', { title: `${task.id}: ${task.title}`, head: branch, base, draft: true,
                        body: taskPullRequestBody({ task, row, runId, parent, verificationDirectory }) });
                } catch (error) {
                    // A timed-out POST may already have created the PR. Read back
                    // before any retry; never blindly create a duplicate.
                    pr = await find();
                    if (!pr) throw error;
                }
            }
            const published = validatePr(pr, branch, base, commit);
            onProgress({ ...published, phase: 'PR_CREATED' });
            parent = published;
            return published;
        },
    };
}

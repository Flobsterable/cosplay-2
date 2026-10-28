const kinds = ['bug', 'feature', 'refactor', 'test', 'docs'];

export function issueToTask(issue, index, repository) {
    const prefix = `Issue #${issue.number}`;
    const labels = (issue.labels || []).map(label => typeof label === 'string' ? label : label.name);
    const types = kinds.filter(kind => labels.includes(`type:${kind}`));
    if (types.length !== 1) throw new Error(`${prefix}: exactly one type:bug/feature/refactor/test/docs label required`);
    const body = typeof issue.body === 'string' ? issue.body : '';
    const lines = body.split(/\r?\n/);
    const headings = [];
    let fence;
    for (let i = 0; i < lines.length; i++) {
        const marker = lines[i].match(/^\s{0,3}(`{3,}|~{3,})/);
        if (marker) {
            if (!fence) fence = marker[1][0];
            else if (marker[1][0] === fence) fence = undefined;
            continue;
        }
        if (!fence && /^#{1,6}\s+/.test(lines[i])) headings.push(i);
    }
    const starts = headings.filter(i => /^##\s+(Критерии готовности|Acceptance criteria)\s*#*\s*$/i.test(lines[i]));
    if (starts.length !== 1) throw new Error(`${prefix}: one ## Критерии готовности / ## Acceptance criteria section required`);
    const end = headings.find(i => i > starts[0] && /^#{1,2}\s+/.test(lines[i])) ?? lines.length;
    const acceptance = lines.slice(starts[0] + 1, end).join('\n').trim();
    if (!acceptance || /<!--/.test(acceptance)) throw new Error(`${prefix}: acceptance criteria must be nonempty and contain no template comments`);
    if (!Number.isSafeInteger(issue.number) || issue.number <= 0 || !issue.title?.trim()) throw new Error('Invalid GitHub issue');
    return {
        id: `EL-${String(index + 1).padStart(2, '0')}`, kind: types[0], title: issue.title.trim(),
        acceptance, description: body,
        source: { type: 'github', repository, number: issue.number,
            url: `https://github.com/${repository}/issues/${issue.number}`, updatedAt: issue.updated_at },
    };
}

export async function fetchGitHubPool(config, { request = fetch, token = process.env.GITHUB_TOKEN || process.env.GH_TOKEN } = {}) {
    const { repository, label, limit = 20 } = config;
    if (!/^[A-Za-z0-9][A-Za-z0-9-]*\/[A-Za-z0-9_.-]+$/.test(repository) ||
        ['.', '..'].includes(repository.split('/')[1]) || !label?.trim() || label.includes(',') ||
        !Number.isInteger(limit) || limit < 15 || limit > 20) {
        throw new Error('GitHub source needs owner/repo, one nonempty label and limit 15–20');
    }
    const headers = { Accept: 'application/vnd.github+json', 'User-Agent': 'cosplay-2-execution-loop' };
    if (token) headers.Authorization = `Bearer ${token}`;
    const issues = [];
    // The issues endpoint also returns PRs. Filter them and paginate before taking
    // the oldest eligible issues, rather than treating PRs as queue entries.
    for (let page = 1; issues.length < limit; page++) {
        const url = new URL(`https://api.github.com/repos/${repository}/issues`);
        url.search = new URLSearchParams({ state: 'open', labels: label, sort: 'created', direction: 'asc', per_page: '100', page: String(page) });
        let response;
        try { response = await request(url, { headers, signal: AbortSignal.timeout(30000), redirect: 'error' }); }
        catch { throw new Error('GitHub request failed or timed out; no local fallback used'); }
        if (!response.ok) throw new Error(`GitHub HTTP ${response.status}; check repository access, credentials and rate limits`);
        const items = await response.json();
        if (!Array.isArray(items)) throw new Error('Invalid GitHub issues response');
        for (const item of items) {
            const labels = (item.labels || []).map(value => typeof value === 'string' ? value : value.name);
            if (!item.pull_request && item.state === 'open' && labels.includes(label) && !issues.some(issue => issue.number === item.number)) issues.push(item);
        }
        if (items.length < 100) break;
    }
    const selected = issues.sort((a, b) => a.number - b.number).slice(0, limit);
    if (selected.length < 15) {
        throw new Error(`Only ${selected.length} open issues with label ${label}; experiment requires at least 15. No tasks started`);
    }
    return {
        version: 1,
        source: { type: 'github', repository, label, limit, fetchedAt: new Date().toISOString() },
        tasks: selected.map((issue, index) => issueToTask(issue, index, repository)),
    };
}

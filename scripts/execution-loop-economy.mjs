export const defaultEconomy = Object.freeze({ directDocs: true, maxCallsPerRun: 60, maxCallsPerTask: 10,
    maxPromptChars: 6000, maxReportedTokens: 120000, tokenReserve: 10000, stopOnMissingUsage: false,
    maxCallMinutes: 8, toolOutputTokens: 1500, serviceTier: 'default' });

export function economyOptions(options = {}) {
    const result = { ...defaultEconomy, ...options };
    if (typeof result.directDocs !== 'boolean' || !Number.isInteger(result.maxCallsPerRun) || result.maxCallsPerRun < 1 ||
        !Number.isInteger(result.maxCallsPerTask) || result.maxCallsPerTask < 1 ||
        !Number.isInteger(result.maxPromptChars) || result.maxPromptChars < 2000 ||
        !Number.isInteger(result.maxReportedTokens) || result.maxReportedTokens < 0 ||
        !Number.isInteger(result.tokenReserve) || result.tokenReserve < 0 ||
        typeof result.stopOnMissingUsage !== 'boolean' || !Number.isFinite(result.maxCallMinutes) || result.maxCallMinutes <= 0 ||
        !Number.isInteger(result.toolOutputTokens) || result.toolOutputTokens < 256 || result.serviceTier !== 'default') throw new Error('Invalid economy limits');
    if (result.stageCallMinutes !== undefined && (!result.stageCallMinutes || typeof result.stageCallMinutes !== 'object' ||
        Array.isArray(result.stageCallMinutes) || Object.entries(result.stageCallMinutes).some(([stage, minutes]) =>
            !['plan', 'work', 'review', 'recover'].includes(stage) || !Number.isFinite(minutes) || minutes <= 0))) {
        throw new Error('Invalid stage call limits');
    }
    return result;
}

export function callTimeout(limits, stage, remainingMs) {
    return Math.min(remainingMs, (limits.stageCallMinutes?.[stage] ?? limits.maxCallMinutes) * 60000);
}

export function assertCallBudget(limits, calls, taskId) {
    const spent = calls.reduce((sum, call) => sum + (call.usage?.totalTokens || 0), 0);
    if (calls.length >= limits.maxCallsPerRun || calls.filter(call => call.task === taskId).length >= limits.maxCallsPerTask ||
        (calls.length > 0 && limits.maxReportedTokens && spent >= Math.max(0, limits.maxReportedTokens - limits.tokenReserve)) ||
        (limits.stopOnMissingUsage && calls.some(call => !call.usage))) {
        throw new Error('Budget: call/token reserve reached or previous usage is unknown');
    }
}

export function directDocumentationPlan(task, earlierIds) {
    if (task.kind !== 'docs') return null;
    const references = `${task.title}\n${task.description || ''}\n${task.acceptance}`.match(/\bEL-\d{2}\b/g) || [];
    const dependsOn = [...new Set([...(task.dependsOn || []), ...references])].filter(id => id !== task.id);
    if (dependsOn.some(id => !earlierIds.includes(id))) return null;
    return { profile: 'Documentation', dependsOn, steps: [{ title: task.title,
        skills: ['context-engineering'], acceptance: task.acceptance }] };
}

export function compactContext(stage, payload, contextFile) {
    const { task } = payload;
    const result = { contextFile, task: { id: task.id, kind: task.kind, title: task.title, acceptance: task.acceptance },
        fullDescription: task.description ? 'См. task.description в contextFile; прочитай перед изменением' : undefined };
    if (stage === 'plan') result.history = payload.history.map(row => ({ id: row.id, status: row.status, title: row.title }));
    if (stage === 'work') {
        result.step = payload.step.acceptance === task.acceptance
            ? { ...payload.step, acceptance: 'См. task.acceptance' } : payload.step;
        result.index = payload.index;
        result.finalStep = payload.finalStep;
        result.plan = { profile: payload.plan.profile, dependsOn: payload.plan.dependsOn,
            steps: payload.plan.steps.map((step, index) => ({ index, title: step.title })) };
        const completed = payload.completedSteps || [];
        result.completed = completed.map(step => ({ index: step.index, title: step.title }));
        const latest = completed.at(-1);
        if (latest) result.latestHandoff = { index: latest.index, summary: latest.handoff?.summary,
            decisions: latest.handoff?.decisions, risks: latest.handoff?.risks };
        if (payload.reviewFeedback) result.reviewFeedback = payload.reviewFeedback;
    }
    if (stage === 'review') {
        result.baseRevision = payload.baseRevision;
        result.files = payload.files;
        result.verificationDirectory = payload.verificationDirectory;
        result.previousReview = payload.previousReviews?.at(-1) || null;
        result.localChecks = payload.localChecks;
    }
    if (stage === 'recover') {
        result.error = payload.error;
        result.failedStep = payload.failedStep;
        result.remainingTaskMs = payload.remainingTaskMs;
        result.evidenceDirectory = payload.evidenceDirectory;
        result.previousIncidents = payload.previousIncidents.map(event => ({ task: event.task,
            scope: event.scope, action: event.action, fingerprint: event.fingerprint }));
        if (payload.reviewFeedback) result.reviewFeedback = payload.reviewFeedback;
    }
    return result;
}

// Usage is emitted by the CLI, not inferred from characters or monetary prices.
// Ignore tool-output lines, stderr and unknown event shapes in the mixed log.
export function reportedUsage(log) {
    let turns = 0;
    const totals = { inputTokens: 0, cachedInputTokens: 0, outputTokens: 0 };
    for (const line of log.split('\n')) {
        let event;
        try { event = JSON.parse(line); } catch { continue; }
        const usage = event.usage;
        if (event.type !== 'turn.completed' || !usage ||
            !['input_tokens', 'output_tokens'].every(key => Number.isSafeInteger(usage[key]) && usage[key] >= 0)) continue;
        turns++;
        totals.inputTokens += usage.input_tokens;
        totals.outputTokens += usage.output_tokens;
        if (Number.isSafeInteger(usage.cached_input_tokens) && usage.cached_input_tokens >= 0) totals.cachedInputTokens += usage.cached_input_tokens;
    }
    return turns ? { ...totals, turns, totalTokens: totals.inputTokens + totals.outputTokens } : null;
}

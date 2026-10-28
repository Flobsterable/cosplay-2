import { test } from 'node:test';
import assert from 'node:assert/strict';
import { compactContext, directDocumentationPlan, reportedUsage, economyOptions } from './execution-loop-economy.mjs';

test('documentation fast path preserves criteria and explicit earlier dependencies', () => {
    const task = { id: 'EL-03', kind: 'docs', title: 'Update docs', acceptance: 'Describe EL-01 behavior', dependsOn: ['EL-02'] };
    const plan = directDocumentationPlan(task, ['EL-01', 'EL-02']);
    assert.deepEqual(plan.dependsOn, ['EL-02', 'EL-01']);
    assert.equal(plan.steps[0].acceptance, task.acceptance);
    assert.equal(directDocumentationPlan({ ...task, kind: 'feature' }, ['EL-01', 'EL-02']), null);
    assert.equal(directDocumentationPlan(task, ['EL-01']), null);
});

test('compact planner context omits duplicated history criteria but keeps current acceptance', () => {
    const task = { id: 'EL-02', kind: 'feature', title: 'Next task', acceptance: 'All current criteria', description: 'Detailed input' };
    const result = compactContext('plan', { task, history: [{ id: 'EL-01', title: 'Previous', status: 'DONE', acceptance: 'old '.repeat(10000) }] }, '/full/context.json');
    assert.equal(result.task.acceptance, task.acceptance);
    assert.equal(result.contextFile, '/full/context.json');
    assert.ok(JSON.stringify(result).length < 1000);
    assert.ok(result.fullDescription);
});

test('step context carries last handoff and current review without replaying old verification logs', () => {
    const task = { id: 'EL-01', kind: 'feature', title: 'Task', acceptance: 'Verified behavior' };
    const plan = { profile: 'feature', dependsOn: [], steps: [{ title: 'Current', acceptance: task.acceptance }] };
    const completedSteps = [0, 1, 2].map(index => ({ index, title: `Step ${index}`, handoff: { summary: `Result ${index}`,
        decisions: ['Keep callbacks'], risks: [], verification: [{ command: 'gradle', result: 'PASS '.repeat(10000) }] } }));
    const result = compactContext('work', { task, plan, step: plan.steps[0], index: 3, finalStep: true,
        completedSteps, reviewFeedback: { verdict: 'REQUEST_CHANGES', findings: ['Concrete regression'] } }, '/context.json');
    assert.equal(result.latestHandoff.summary, 'Result 2');
    assert.equal(result.reviewFeedback.findings[0], 'Concrete regression');
    assert.equal(result.step.acceptance, 'См. task.acceptance');
    assert.ok(JSON.stringify(result).length < 2000);
});

test('usage comes only from valid CLI completion events and does not double-count reasoning', () => {
    const log = ['stderr text', JSON.stringify({ type: 'turn.completed', usage: { input_tokens: 100, cached_input_tokens: 80, output_tokens: 10, reasoning_output_tokens: 5 } }),
        JSON.stringify({ type: 'item.completed', usage: { input_tokens: 999, output_tokens: 999 } }),
        JSON.stringify({ type: 'turn.completed', usage: { input_tokens: -1, output_tokens: 1 } }),
        JSON.stringify({ type: 'turn.completed', usage: { input_tokens: 50, output_tokens: 3 } })].join('\n');
    assert.deepEqual(reportedUsage(log), { inputTokens: 150, cachedInputTokens: 80, outputTokens: 13, totalTokens: 163, turns: 2 });
    assert.equal(reportedUsage('missing usage'), null);
});

test('budget validation rejects invalid or unbounded call limits', () => {
    assert.throws(() => economyOptions({ maxCallsPerRun: 0 }));
    assert.throws(() => economyOptions({ maxPromptChars: 100 }));
    assert.throws(() => economyOptions({ maxReportedTokens: -1 }));
    assert.equal(economyOptions().maxCallsPerRun, 60);
});

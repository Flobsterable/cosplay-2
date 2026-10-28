import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { taskDigest, matchingRecipe, recipePlan, checkRecipe, checkAddedText, checkPatch } from './execution-loop-checks.mjs';

test('local plans require exact task contract and source rather than matching only an ID', () => {
    const task = { id: 'EL-01', title: 'Change', kind: 'feature', acceptance: 'All criteria', dependsOn: [] };
    const pool = { source: { pool: 'mini', dataSourceId: 'source' } };
    const recipe = { id: task.id, taskDigest: taskDigest(task), profile: 'Compose UI', skills: ['compose-multiplatform'] };
    const recipes = { version: 1, ...pool.source, tasks: [recipe] };
    assert.equal(matchingRecipe(task, pool, recipes), recipe);
    for (const changed of [{ acceptance: 'Changed' }, { title: 'Changed' }, { description: 'New detail' }, { dependsOn: ['EL-00'] }]) {
        assert.equal(matchingRecipe({ ...task, ...changed }, pool, recipes), null);
    }
    assert.equal(matchingRecipe(task, { source: { ...pool.source, dataSourceId: 'other' } }, recipes), null);
    assert.equal(recipePlan(task, recipe).steps.length, 1);
    assert.equal(recipePlan(task, recipe).steps[0].acceptance, task.acceptance);
});

test('local checks find conflict markers, whitespace and missing local documentation links', () => {
    assert.equal(checkAddedText(process.cwd(), 'README.md', '<<<<<<< branch\ntext  \n[missing](missing-local-file.md)').length, 3);
    assert.equal(checkAddedText(process.cwd(), 'docs/README.md', '[valid](../README.md)\n[web](https://example.test/a)').length, 0);
    assert.equal(checkAddedText(process.cwd(), 'README.md', '[broken](%not-url)').length, 1);
    assert.equal(checkPatch(process.cwd(), '+++ b/README.md\n@@ -1 +1 @@\n+>>>>>>> branch', []).length, 1);
});

test('resource check rejects absent, commented and unused resources and accepts a referenced text', t => {
    const cwd = fs.mkdtempSync(path.join(os.tmpdir(), 'loop-checks-'));
    t.after(() => fs.rmSync(cwd, { recursive: true, force: true }));
    const recipe = { checks: [{ type: 'resource-text', module: 'feature/settings', text: 'Очистить ввод города' }] };
    const root = path.join(cwd, 'feature/settings/src/commonMain');
    fs.mkdirSync(path.join(root, 'kotlin'), { recursive: true });
    fs.mkdirSync(path.join(root, 'composeResources/values'), { recursive: true });
    const xml = path.join(root, 'composeResources/values/strings.xml');
    const kt = path.join(root, 'kotlin/Screen.kt');
    const resource = '<string name="clear_city">Очистить ввод города</string>';
    assert.equal(checkRecipe(cwd, recipe)[0].passed, false);
    fs.writeFileSync(xml, '<resources>' + resource + '</resources>');
    assert.equal(checkRecipe(cwd, recipe)[0].passed, false);
    fs.writeFileSync(kt, 'Text(stringResource(Res.string.clear_city))');
    assert.equal(checkRecipe(cwd, recipe)[0].passed, true);
    fs.writeFileSync(xml, '<resources><!--' + resource + '--></resources>');
    assert.equal(checkRecipe(cwd, recipe)[0].passed, false);
    assert.throws(() => checkRecipe(cwd, { checks: [{ type: 'shell', module: '../outside' }] }), /Invalid/);
});

test('committed mini pool has 15 pinned one-step recipes and eight structural checks', () => {
    const recipes = JSON.parse(fs.readFileSync(new URL('../docs/ai-agent/execution-loop/mini-recipes.json', import.meta.url)));
    assert.equal(recipes.tasks.length, 15);
    assert.equal(new Set(recipes.tasks.map(recipe => recipe.id)).size, 15);
    assert.equal(recipes.tasks.flatMap(recipe => recipe.checks).length, 8);
    assert.ok(recipes.tasks.every(recipe => /^[a-f0-9]{64}$/.test(recipe.taskDigest)));
});

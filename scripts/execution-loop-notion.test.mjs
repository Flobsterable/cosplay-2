import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { notionPool, fetchNotionPool } from './execution-loop-notion.mjs';

const config = { dataSourceId: 'c9a64c93-0a34-4e32-b486-58ef22f6148b', project: 'cosplay-2', pool: 'mini-test', limit: 15, transport: 'rest' };
const row = i => ({ 'userDefined:ID': `EL-${String(i).padStart(2, '0')}`, 'Проект': 'cosplay-2', 'Пул': 'mini-test',
    'Порядок': i, 'Задача': `Task ${i}`, 'Тип': 'feature', 'Критерии': 'Expected behavior', 'Файлы': 'file.kt', 'Проверка': 'Focused test',
    'Зависимости': i > 1 ? 'EL-01' : '', 'Статус': 'Готово к работе', url: `https://www.notion.so/task-${i}` });
const rows = () => Array.from({ length: 15 }, (_, i) => row(i + 1));

test('Notion snapshot preserves explicit IDs, source links, order and dependencies', () => {
    const result = notionPool(config, rows().reverse(), { exportedAt: '2026-10-28T00:00:00Z' });
    assert.equal(result.tasks.length, 15);
    assert.equal(result.tasks[0].id, 'EL-01');
    assert.deepEqual(result.tasks[1].dependsOn, ['EL-01']);
    assert.match(result.tasks[0].description, /file.kt/);
    assert.equal(result.tasks[0].source.type, 'notion');
});

test('not-ready and unrelated project/pool rows are excluded', () => {
    const extra = [row(16), row(17), row(18), row(19)];
    extra[0]['Статус'] = 'Готово'; extra[1]['Проект'] = 'other'; extra[2]['Пул'] = 'other'; extra[3].archived = true;
    assert.equal(notionPool(config, [...rows(), ...extra]).tasks.length, 15);
    assert.throws(() => notionPool(config, [row(1)]), /at least 15/);
});

test('invalid criteria, duplicate order and missing prerequisites fail before execution', () => {
    for (const mutate of [list => list[0]['Критерии'] = '', list => list[1]['Порядок'] = 1,
        list => list[0]['Зависимости'] = 'EL-99', list => list[1]['userDefined:ID'] = 'EL-01']) {
        const list = rows(); mutate(list);
        assert.throws(() => notionPool(config, list));
    }
});

test('REST pages are normalized from rich-text/title/select/status properties and paginated', async () => {
    const pages = rows().map(item => ({ url: item.url, properties: Object.fromEntries(Object.entries(item).filter(([key]) => key !== 'url').map(([key, value]) => [key === 'userDefined:ID' ? 'ID' : key,
        typeof value === 'number' ? { type: 'number', number: value } : key === 'Задача' ? { type: 'title', title: [{ plain_text: value }] } :
        ['Проект', 'Тип'].includes(key) ? { type: 'select', select: { name: value } } : key === 'Статус' ? { type: 'status', status: { name: value } } :
        { type: 'rich_text', rich_text: [{ text: { content: value } }] }])) }));
    let calls = 0;
    const result = await fetchNotionPool(config, { token: 'fixture-token', request: async (url, options) => {
        calls++;
        assert.equal(url, `https://api.notion.com/v1/data_sources/${config.dataSourceId}/query`);
        assert.equal(options.headers.Authorization, 'Bearer fixture-token');
        assert.equal(options.headers['Notion-Version'], '2025-09-03');
        const body = JSON.parse(options.body);
        assert.equal(body.filter.and[0].select.equals, 'cosplay-2');
        if (calls === 2) assert.equal(body.start_cursor, 'next');
        return { ok: true, json: async () => ({ results: calls === 1 ? pages.slice(0, 8) : pages.slice(8), has_more: calls === 1, next_cursor: calls === 1 ? 'next' : null }) };
    } });
    assert.equal(calls, 2);
    assert.equal(result.tasks.length, 15);
    assert.equal(result.tasks[0].title, 'Task 1');
});

test('MCP snapshot requires exact source and completeness; no API credential needed', async t => {
    const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'cosplay-notion-'));
    t.after(() => fs.rmSync(directory, { recursive: true, force: true }));
    const file = path.join(directory, 'export.json');
    const input = { dataSourceId: config.dataSourceId, results: rows(), has_more: false, exportedAt: '2026-10-28T00:00:00Z' };
    fs.writeFileSync(file, JSON.stringify(input));
    const source = { ...config, transport: 'mcp-snapshot', snapshotFile: file };
    const result = await fetchNotionPool(source, { token: undefined });
    assert.equal(result.tasks.length, 15);
    fs.writeFileSync(file, JSON.stringify({ ...input, has_more: true }));
    await assert.rejects(fetchNotionPool(source), /include all rows/);
    fs.writeFileSync(file, JSON.stringify({ ...input, dataSourceId: 'other' }));
    await assert.rejects(fetchNotionPool(source), /match dataSourceId/);
});

test('auth, HTTP and transport failures do not disclose tokens or switch sources', async () => {
    await assert.rejects(fetchNotionPool(config, { token: '' }), /NOTION_TOKEN required/);
    await assert.rejects(fetchNotionPool(config, { token: 'sensitive', request: async () => ({ ok: false, status: 403 }) }), /Notion HTTP 403/);
    await assert.rejects(fetchNotionPool(config, { token: 'sensitive', request: async () => { throw new Error('sensitive'); } }),
        error => /no snapshot fallback/.test(error.message) && !error.message.includes('sensitive'));
});

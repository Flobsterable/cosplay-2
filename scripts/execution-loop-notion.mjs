import fs from 'node:fs';
import path from 'node:path';

const uuid = /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i;
const kinds = new Set(['feature', 'bug', 'refactor', 'test', 'docs']);

function value(property) {
    if (typeof property === 'string' || typeof property === 'number') return property;
    if (!property) return '';
    if (property.type === 'number') return property.number;
    if (property.select || property.status) return (property.select || property.status).name;
    const parts = property.title || property.rich_text;
    return parts ? parts.map(part => part.plain_text ?? part.text?.content ?? '').join('') : '';
}

export function notionPool(config, rows, metadata = {}) {
    if (!uuid.test(config.dataSourceId) || !config.project || !config.pool || !Number.isInteger(config.limit) || config.limit < 15 || config.limit > 20) {
        throw new Error('Notion source needs dataSourceId, project, pool and limit 15–20');
    }
    const selected = rows.filter(row => !row.archived && !row.in_trash).map(row => {
        const properties = row.properties || row;
        const get = key => value(properties[key]);
        return { id: get('userDefined:ID') || get('ID'), title: get('Задача'), kind: get('Тип'), acceptance: get('Критерии'),
            project: get('Проект'), pool: get('Пул'), status: get('Статус'), order: get('Порядок'),
            files: get('Файлы'), verification: get('Проверка'), dependencies: get('Зависимости'),
            url: row.url, updatedAt: row.last_edited_time || metadata.exportedAt };
    }).filter(row => row.project === config.project && row.pool === config.pool && row.status === (config.readyStatus || 'Готово к работе'));
    if (selected.some(row => !Number.isSafeInteger(row.order) || row.order < 1)) throw new Error('Notion task order must be a positive integer');
    selected.sort((a, b) => a.order - b.order);
    const tasks = [];
    const orders = new Set();
    for (const row of selected.slice(0, config.limit)) {
        if (!/^EL-\d{2}$/.test(row.id) || !row.title?.trim() || !kinds.has(row.kind) || !row.acceptance?.trim() || !row.url ||
            orders.has(row.order) || tasks.some(task => task.id === row.id)) throw new Error(`Invalid or duplicate Notion task: ${row.id}`);
        const dependsOn = row.dependencies ? row.dependencies.split(/[,;\s]+/).filter(Boolean) : [];
        if (dependsOn.some(id => !tasks.some(task => task.id === id))) throw new Error(`Notion ${row.id}: dependency must be an earlier ready task in the same pool`);
        orders.add(row.order);
        tasks.push({ id: row.id, kind: row.kind, title: row.title, acceptance: row.acceptance, dependsOn,
            description: `Файлы: ${row.files}\nПроверка: ${row.verification}\nСохрани callbacks/навигацию/данные; изменённые UI-строки — Compose Resources владельца UI.`,
            source: { type: 'notion', dataSourceId: config.dataSourceId, url: row.url, updatedAt: row.updatedAt } });
    }
    if (tasks.length < 15) throw new Error(`Only ${tasks.length} ready Notion tasks in ${config.pool}; at least 15 required. No tasks started`);
    return { version: 1, source: { type: 'notion', dataSourceId: config.dataSourceId, pool: config.pool,
        transport: config.transport, exportedAt: metadata.exportedAt || new Date().toISOString() }, tasks };
}

export async function fetchNotionPool(config, { cwd = process.cwd(), request = fetch, token = process.env.NOTION_TOKEN } = {}) {
    if (!uuid.test(config.dataSourceId)) throw new Error('Invalid Notion dataSourceId');
    if (config.transport === 'mcp-snapshot') {
        if (!config.snapshotFile) throw new Error('Missing Notion MCP snapshotFile');
        let snapshot;
        try { snapshot = JSON.parse(fs.readFileSync(path.resolve(cwd, config.snapshotFile), 'utf8')); }
        catch { throw new Error('Notion MCP snapshot missing/invalid; export the configured data source through the connected Notion tools first'); }
        if (snapshot.dataSourceId !== config.dataSourceId || snapshot.has_more !== false || !snapshot.exportedAt || !Array.isArray(snapshot.results)) {
            throw new Error('Notion snapshot must match dataSourceId and include all rows (has_more=false)');
        }
        return notionPool(config, snapshot.results, snapshot);
    }
    if (config.transport !== 'rest') throw new Error('Notion transport must be mcp-snapshot or rest');
    if (!token) throw new Error('NOTION_TOKEN required for REST; connected Notion OAuth is not exported to CLI. Use mcp-snapshot instead');
    const rows = [];
    let cursor;
    const seen = new Set();
    do {
        const body = { page_size: 100, sorts: [{ property: 'Порядок', direction: 'ascending' }], filter: { and: [
            { property: 'Проект', select: { equals: config.project } },
            { property: 'Пул', rich_text: { equals: config.pool } },
            { property: 'Статус', status: { equals: config.readyStatus || 'Готово к работе' } },
        ] } };
        if (cursor) body.start_cursor = cursor;
        let response;
        try {
            response = await request(`https://api.notion.com/v1/data_sources/${config.dataSourceId}/query`, {
                method: 'POST', headers: { Authorization: `Bearer ${token}`, 'Notion-Version': '2025-09-03', 'Content-Type': 'application/json' },
                body: JSON.stringify(body), redirect: 'error', signal: AbortSignal.timeout(30000),
            });
        } catch { throw new Error('Notion request failed or timed out; no snapshot fallback used'); }
        if (!response.ok) throw new Error(`Notion HTTP ${response.status}; check access to the data source and API limits`);
        let page;
        try { page = await response.json(); } catch { throw new Error('Invalid Notion JSON response'); }
        if (!Array.isArray(page.results) || typeof page.has_more !== 'boolean') throw new Error('Invalid Notion response');
        rows.push(...page.results);
        cursor = page.has_more ? page.next_cursor : null;
        if (page.has_more && (!cursor || seen.has(cursor))) throw new Error('Invalid/repeated Notion pagination cursor');
        if (cursor) seen.add(cursor);
    } while (cursor && rows.length < config.limit);
    return notionPool(config, rows);
}

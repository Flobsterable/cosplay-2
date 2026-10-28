import fs from 'node:fs';
import path from 'node:path';
import { createHash } from 'node:crypto';

export function taskDigest(task) {
    return createHash('sha256').update(JSON.stringify([task.id, task.kind, task.title,
        task.acceptance, task.description || '', task.dependsOn || []])).digest('hex');
}

export function matchingRecipe(task, pool, recipes) {
    if (!recipes || recipes.version !== 1 || recipes.pool !== pool.source?.pool ||
        recipes.dataSourceId !== pool.source?.dataSourceId) return null;
    const recipe = recipes.tasks.find(item => item.id === task.id);
    return recipe?.taskDigest === taskDigest(task) ? recipe : null;
}

export function recipePlan(task, recipe) {
    return { profile: recipe.profile, dependsOn: task.dependsOn || [],
        steps: [{ title: task.title, skills: recipe.skills, acceptance: task.acceptance }] };
}

// These are deterministic structural checks, not a replacement for UI smoke
// or semantic review. No task-supplied executable expressions are evaluated.
export function checkAddedText(cwd, file, text) {
    const failures = [];
    if (/^(?:<{7} |>{7} )/m.test(text)) failures.push('unresolved merge marker');
    if (/[\t ]+\r?$/m.test(text)) failures.push('trailing whitespace');
    if (file.endsWith('.md')) {
        for (const match of text.matchAll(/\]\(<?([^\s)>]+)>?(?:\s+"[^"]*")?\)/g)) {
            const href = match[1];
            if (/^(?:[a-z][a-z0-9+.-]*:|\/\/|#)/i.test(href)) continue;
            let target;
            try { target = decodeURIComponent(href.split(/[?#]/)[0]); } catch { failures.push('invalid local link encoding'); continue; }
            const resolved = target.startsWith('/') ? path.resolve(cwd, '.' + target) : path.resolve(cwd, path.dirname(file), target);
            if (!fs.existsSync(resolved)) failures.push(`missing local link: ${target}`);
        }
    }
    return failures.map(reason => ({ file, reason }));
}

function filesUnder(directory, extension) {
    if (!fs.existsSync(directory)) return [];
    return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
        const target = path.join(directory, entry.name);
        if (entry.isSymbolicLink()) return [];
        return entry.isDirectory() ? filesUnder(target, extension) : entry.name.endsWith(extension) ? [target] : [];
    });
}

export function checkRecipe(cwd, recipe) {
    const results = [];
    for (const check of recipe?.checks || []) {
        if (check.type !== 'resource-text' || !/^feature\/(settings|festival|update)$/.test(check.module) || !check.text) {
            throw new Error('Invalid local recipe check');
        }
        const module = path.join(cwd, check.module, 'src/commonMain');
        const kotlin = filesUnder(path.join(module, 'kotlin'), '.kt').map(file => fs.readFileSync(file, 'utf8')).join('\n');
        const resources = filesUnder(path.join(module, 'composeResources/values'), '.xml');
        const found = resources.some(file => {
            const xml = fs.readFileSync(file, 'utf8').replace(/<!--[\s\S]*?-->/g, '');
            return [...xml.matchAll(/<string\s+name="([a-z][a-z0-9_]*)"[^>]*>([\s\S]*?)<\/string>/g)].some(([, name, value]) =>
                value.includes(check.text) && kotlin.includes(`Res.string.${name}`) && /\bstringResource\s*\(/.test(kotlin));
        });
        results.push({ type: check.type, module: check.module, text: check.text, passed: found });
    }
    return results;
}

export function checkPatch(cwd, diff, untracked) {
    const failures = [];
    let file, added = [];
    const flush = () => { if (file) failures.push(...checkAddedText(cwd, file, added.join('\n'))); added = []; };
    for (const line of diff.split('\n')) {
        if (line.startsWith('+++ ')) { flush(); file = line.startsWith('+++ b/') ? line.slice(6).trim() : null; }
        else if (line.startsWith('+')) added.push(line.slice(1));
    }
    flush();
    for (const relative of untracked) {
        const target = path.resolve(cwd, relative);
        const realRoot = fs.realpathSync(cwd) + path.sep;
        if (fs.lstatSync(target).isSymbolicLink() || !fs.realpathSync(target).startsWith(realRoot)) {
            failures.push({ file: relative, reason: 'untracked symlink or path escape' }); continue;
        }
        if (/\.(?:md|kt|kts|xml|json|mjs|ps1)$/.test(relative)) failures.push(...checkAddedText(cwd, relative, fs.readFileSync(target, 'utf8')));
    }
    return failures;
}

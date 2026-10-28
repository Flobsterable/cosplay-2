import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const manifest = JSON.parse(fs.readFileSync(path.join(root, 'docs/ai-agent/execution-loop/upstream-skills.json'), 'utf8'));

test('external skill source is pinned to an immutable revision for reproducible runs', () => {
    assert.equal(manifest.repository, 'https://github.com/addyosmani/agent-skills');
    assert.match(manifest.revision, /^[a-f0-9]{40}$/);
    assert.equal(manifest.license, 'MIT');
    assert.equal(manifest.digestNormalization, 'UTF-8 with LF line endings');
    assert.equal(manifest.skills.length, 5);
    assert.equal(new Set(manifest.skills.map(skill => skill.name)).size, 5);
});

test('installed adapted skill content matches frozen manifest across line-ending conventions', () => {
    for (const skill of manifest.skills) {
        const file = path.resolve(root, skill.path);
        assert.equal(file, path.join(root, '.agents/skills', skill.name, 'SKILL.md'));
        const content = fs.readFileSync(file, 'utf8').replaceAll('\r\n', '\n');
        const digest = createHash('sha256').update(content).digest('hex');
        assert.equal(digest, skill.adaptedSha256, `${skill.name}: update provenance when changing the adaptation`);
        assert.match(skill.upstreamBlobSha1, /^[a-f0-9]{40}$/);
        const reference = fs.readFileSync(path.join(root, skill.referencePath), 'utf8').replaceAll('\r\n', '\n');
        assert.equal(createHash('sha256').update(reference).digest('hex'), skill.referenceSha256);
    }
});

test('redistributed skill copies retain MIT permission and copyright notices', () => {
    for (const skill of manifest.skills) {
        const license = fs.readFileSync(path.join(root, '.agents/skills', skill.name, 'LICENSE.txt'), 'utf8');
        assert.match(license, /MIT License/);
        assert.match(license, /Copyright/);
        assert.match(license, /Permission is hereby granted/);
    }
});

const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');

test('verification installs the published base SDK package for compileSdk 37', () => {
  const catalog = readFileSync(resolve(__dirname, '../../gradle/libs.versions.toml'), 'utf8');
  const workflow = readFileSync(resolve(__dirname, '../workflows/verification.yml'), 'utf8');
  const compileSdk = catalog.match(/^android-compileSdk\s*=\s*"([^"]+)"/m)?.[1];
  assert.equal(compileSdk, '37', 'Update the package contract when compileSdk changes');
  // sdkmanager --list publishes API 37 base as android-37.0, not android-37.
  const install = workflow.match(/sdkmanager\.bat"\s+"([^"]+)"/);
  assert.ok(install, 'The CI SDK installation command must be present');
  assert.equal(install[1], `platforms;android-${compileSdk}.0`);
});

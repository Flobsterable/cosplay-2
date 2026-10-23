param([string]$OutputPath = 'docs/ai-agent/discovery-checks.json')

$ErrorActionPreference = 'Stop'
$repositoryPath = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$expectedNames = @('kmp-architecture', 'kotlin-code-style', 'compose-multiplatform', 'kmp-testing', 'code-reviewer', 'test-engineer')
$contexts = @{
    '.' = '# Cosplay 2'
    'androidApp' = '# Android application'
    'composeApp' = '# App shell'
    'core/platform' = '# Platform integrations'
    'data/festival' = '# Festival data'
    'feature/festival' = '# Festival UI'
}
$results = foreach ($context in $contexts.GetEnumerator()) {
    $contextPath = Join-Path $repositoryPath $context.Key
    $promptLines = & codex -C $contextPath debug prompt-input 'Configuration discovery probe only' 2>$null
    if ($LASTEXITCODE -ne 0) { throw "Codex discovery failed for $($context.Key)" }
    $promptText = $promptLines -join "`n"
    $parsedPrompt = $promptText | ConvertFrom-Json
    if (-not $parsedPrompt) { throw 'Empty Codex prompt' }
    foreach ($expectedName in $expectedNames + @($context.Value, '# Личные глобальные инструкции Codex')) {
        [pscustomobject]@{ context = $context.Key; expected = $expectedName; detected = $promptText.Contains($expectedName) }
    }
}
$destinationPath = Join-Path $repositoryPath $OutputPath
$results | ConvertTo-Json | Set-Content -LiteralPath $destinationPath -Encoding utf8
if ($results.detected -contains $false) { throw 'Some instructions, skills or agents were not discovered' }
Write-Output "PASS: $($results.Count) discovery assertions across $($contexts.Count) contexts"

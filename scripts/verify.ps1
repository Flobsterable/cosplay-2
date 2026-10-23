[CmdletBinding()]
param(
    [string]$OutputDirectory = 'build/verification/latest',
    [string]$GradleInitScript,
    [switch]$ReportOnly,
    [switch]$CodeOnly
)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
Set-Location $repoRoot
$outputPath = [IO.Path]::GetFullPath((Join-Path $repoRoot $OutputDirectory))
New-Item -ItemType Directory -Path $outputPath -Force | Out-Null
$utf8 = [Text.UTF8Encoding]::new($false)
function Write-JsonFile($Path, $Value) {
    [IO.File]::WriteAllText($Path, ($Value | ConvertTo-Json -Depth 15), $utf8)
}
function Get-SourceHash {
    $paths = @(& git ls-files --cached --others --exclude-standard) |
        Where-Object { $_ -match '\.(kt|kts|properties|toml|ps1|ya?ml)$' -or $_ -eq 'docs/testing/smoke.json' } |
        Sort-Object -Unique
    $entries = foreach ($path in $paths) {
        if (Test-Path -LiteralPath $path -PathType Leaf) {
            "$path $((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash)"
        } else { "$path DELETED" }
    }
    $bytes = [Text.Encoding]::UTF8.GetBytes(($entries -join "`n"))
    $sha = [Security.Cryptography.SHA256]::Create()
    try { ([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-', '').ToLowerInvariant() }
    finally { $sha.Dispose() }
}
$runFile = Join-Path $outputPath 'run.json'
$modules = @('core/model', 'data/festival', 'data/update', 'core/platform', 'composeApp')
$wrapper = if ($env:OS -eq 'Windows_NT') { "$repoRoot/gradlew.bat" } else { "$repoRoot/gradlew" }
if (!$ReportOnly) {
    if (Test-Path -LiteralPath $runFile) {
        throw 'Use a new OutputDirectory for each run; previous evidence must be preserved.'
    }
    $run = [ordered]@{
        startedAt = [DateTimeOffset]::Now.ToString('o')
        revision = (& git rev-parse HEAD | Out-String).Trim()
        sourceHash = Get-SourceHash
        commands = @()
    }
    Write-JsonFile $runFile $run
    $groups = [ordered]@{
        tests = @(':core:model:desktopTest', ':data:festival:desktopTest', ':data:update:desktopTest',
            ':core:platform:desktopTest', ':composeApp:desktopTest')
        compile = @(':composeApp:compileKotlinDesktop', ':composeApp:compileAndroidMain')
        lint = @(':androidApp:lintDebug')
    }
    foreach ($name in $groups.Keys) {
        $arguments = @($groups[$name]) + @('--console=plain')
        if ($GradleInitScript) { $arguments += @('-I', $GradleInitScript) }
        $log = Join-Path $outputPath "$name.log"
        $exitCode = 1
        try {
            # Windows PowerShell must not convert native stderr to a terminating error.
            $ErrorActionPreference = 'Continue'
            & $wrapper @arguments *> $log
            $exitCode = $LASTEXITCODE
        } finally { $ErrorActionPreference = 'Stop' }
        $run.commands += [ordered]@{ name = $name; arguments = $arguments; exitCode = $exitCode; log = "$name.log" }
        Write-JsonFile $runFile $run
        Write-Output "$name exit=$exitCode; log=$log"
    }
    foreach ($module in $modules) {
        $source = Join-Path $repoRoot "$module/build/test-results/desktopTest"
        $target = Join-Path $outputPath "test-results/$module"
        New-Item -ItemType Directory -Path $target -Force | Out-Null
        Get-ChildItem -LiteralPath $source -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue |
            Copy-Item -Destination $target
    }
}
$run = Get-Content -LiteralPath $runFile -Raw | ConvertFrom-Json
$codePassed = @($run.commands).Count -eq 3 -and @($run.commands | Where-Object { $_.exitCode -ne 0 }).Count -eq 0
$lines = [Collections.Generic.List[string]]::new()
$lines.Add('# Проверка Cosplay 2')
$lines.Add('')
$lines.Add("Запуск: $($run.startedAt); commit: $($run.revision); source SHA-256: $($run.sourceHash).")
$lines.Add('')
$lines.Add('## Уровень 1 — код')
$lines.Add('')
foreach ($command in $run.commands) {
    $lines.Add("- $($command.name): exit $($command.exitCode), [журнал]($($command.log)); ``$($command.arguments -join ' ')``.")
}
$lines.Add('')
$lines.Add('| Модуль | Тесты | Failures | Errors | Skipped |')
$lines.Add('| --- | ---: | ---: | ---: | ---: |')
$totalTests = 0
foreach ($module in $modules) {
    $xmlDirectory = Join-Path $outputPath "test-results/$module"
    $xmlFiles = @(Get-ChildItem -LiteralPath $xmlDirectory -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue)
    $counts = @{ tests = 0; failures = 0; errors = 0; skipped = 0 }
    foreach ($file in $xmlFiles) {
        [xml]$document = Get-Content -LiteralPath $file.FullName -Raw
        foreach ($key in @('tests', 'failures', 'errors', 'skipped')) {
            $counts[$key] += [int]$document.testsuite.GetAttribute($key)
        }
    }
    $totalTests += $counts.tests
    if ($counts.tests -eq 0 -or $counts.failures -gt 0 -or $counts.errors -gt 0 -or $counts.skipped -gt 0) { $codePassed = $false }
    $lines.Add("| $module | $($counts.tests) | $($counts.failures) | $($counts.errors) | $($counts.skipped) |")
}
$lines.Add('')
$lines.Add("Всего тестов: $totalTests. При неуспешной команде XML может относиться к прошлой сборке и не подтверждает текущий запуск.")
$lines.Add('')
$lines.Add('## Уровень 2 — UI через MCP')
$lines.Add('')
$smokePassed = $false
$smokeFile = Join-Path $outputPath 'smoke.json'
if (Test-Path -LiteralPath $smokeFile) {
    $smoke = Get-Content -LiteralPath $smokeFile -Raw | ConvertFrom-Json
    $plan = Get-Content docs/testing/smoke.json -Raw | ConvertFrom-Json
    $smokePassed = $smoke.sourceHash -eq $run.sourceHash -and $smoke.revision -eq $run.revision -and
        $smoke.transport -match 'MCP' -and @($smoke.scenarios).Count -eq @($plan.scenarios).Count
    $lines.Add("Платформа: $($smoke.platform). Инструмент: $($smoke.transport). Сборка: $($smoke.build).")
    $lines.Add('')
    foreach ($scenario in $plan.scenarios) {
        $matches = @($smoke.scenarios | Where-Object { $_.id -eq $scenario.id })
        if ($matches.Count -ne 1) {
            $smokePassed = $false
            $lines.Add("- $($scenario.id): BLOCKED — нет единственного результата.")
            continue
        }
        $result = $matches[0]
        $valid = $result.status -eq 'PASS' -and @($result.steps).Count -eq @($scenario.steps).Count
        $lines.Add("### $($scenario.id) — $($scenario.title): $($result.status)")
        $lines.Add('')
        foreach ($expected in $scenario.steps) {
            $steps = @($result.steps | Where-Object { $_.id -eq $expected.id })
            if ($steps.Count -ne 1) { $valid = $false; $lines.Add("- $($expected.id): BLOCKED — нет результата шага."); continue }
            $step = $steps[0]
            $screenshotValid = $false
            if ($step.screenshot -and ![IO.Path]::IsPathRooted($step.screenshot)) {
                $screenshotPath = [IO.Path]::GetFullPath((Join-Path $outputPath $step.screenshot))
                $screenshotValid = $screenshotPath.StartsWith($outputPath + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -and
                    (Test-Path -LiteralPath $screenshotPath -PathType Leaf) -and (Get-Item -LiteralPath $screenshotPath).Length -gt 0
            }
            if ($step.status -ne 'PASS' -or !$screenshotValid -or !$step.actual) { $valid = $false }
            $lines.Add("- $($step.id) $($step.status): $($step.actual)")
            if ($screenshotValid) { $lines.Add("  ![Шаг $($step.id)]($($step.screenshot))") }
            elseif ($step.status -eq 'PASS') { $lines.Add('  BLOCKED: отсутствует снимок шага.') }
        }
        if ($result.diagnosis) { $lines.Add("Диагностика: $($result.diagnosis)") }
        $lines.Add('')
        if (!$valid) { $smokePassed = $false }
    }
} else { $lines.Add('BLOCKED: MCP smoke ещё не выполнен. CI без интерактивного MCP не подтверждает UI.'); $lines.Add('') }
if ((Get-SourceHash) -ne $run.sourceHash -or ((& git rev-parse HEAD | Out-String).Trim()) -ne $run.revision) {
    $codePassed = $false
    $smokePassed = $false
    $lines.Add('BLOCKED: исходники/сценарии изменились после запуска; требуется новый цикл.')
}
$overall = if ($codePassed -and $smokePassed) { 'PASS' } elseif (!$codePassed) { 'FAIL — код/сборка; см. журналы' } else { 'BLOCKED/FAIL — UI не подтверждён полностью' }
$lines.Add("Полный цикл: $overall.")
[IO.File]::WriteAllLines((Join-Path $outputPath 'report.md'), $lines, $utf8)
Write-Output "Full cycle: $overall; report=$outputPath/report.md"
if (!$codePassed) { exit 1 }
if (!$smokePassed -and !$CodeOnly) { exit 2 }
exit 0

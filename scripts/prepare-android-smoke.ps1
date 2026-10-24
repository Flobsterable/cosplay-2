[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$OutputDirectory,
    [Parameter(Mandatory)][string]$Device,
    [switch]$ResetSmokeData,
    [switch]$SyncClock,
    [string]$SdkDirectory = $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' })
)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
Set-Location $repoRoot
if ($Device -notmatch '^emulator-\d+$') { throw 'Use an explicitly selected test emulator, not a personal device.' }
$outputPath = if ([IO.Path]::IsPathRooted($OutputDirectory)) { [IO.Path]::GetFullPath($OutputDirectory) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $OutputDirectory)) }
$run = Get-Content -LiteralPath (Join-Path $outputPath 'run.json') -Raw | ConvertFrom-Json
if ($run.targetPlatform -ne 'Android') { throw 'First run verify.ps1 -TargetPlatform Android.' }
if (@($run.commands).Count -ne 3 -or @($run.commands | Where-Object { $_.exitCode -ne 0 }).Count -gt 0) { throw 'Code verification must pass before APK installation.' }
if ((& git rev-parse HEAD | Out-String).Trim() -ne $run.revision) { throw 'Commit changed; start a new verification run.' }
& (Join-Path $PSScriptRoot 'verify.ps1') -OutputDirectory $OutputDirectory -ReportOnly -CodeOnly
if ($LASTEXITCODE -ne 0) { throw 'Verification evidence is stale or failed.' }
$adb = Join-Path $SdkDirectory 'platform-tools/adb.exe'
function Invoke-Adb([string[]]$Arguments) {
    $result = & $adb -s $Device @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $($result -join ' ')" }
    $result
}
if ((Invoke-Adb @('shell', 'getprop', 'sys.boot_completed') | Out-String).Trim() -ne '1') { throw 'Emulator has not finished booting.' }
$guestSeconds = [long](Invoke-Adb @('shell', 'date', '+%s') | Out-String).Trim()
$clockDriftSeconds = [Math]::Abs([DateTimeOffset]::Now.ToUnixTimeSeconds() - $guestSeconds)
if ($SyncClock) {
    Invoke-Adb @('shell', 'cmd', 'alarm', 'set-time', [string][DateTimeOffset]::Now.ToUnixTimeMilliseconds()) | Write-Output
    $guestSeconds = [long](Invoke-Adb @('shell', 'date', '+%s') | Out-String).Trim()
    $clockDriftSeconds = [Math]::Abs([DateTimeOffset]::Now.ToUnixTimeSeconds() - $guestSeconds)
}
if ($clockDriftSeconds -gt 300) { throw 'Emulator clock differs by more than 5 minutes; rerun with -SyncClock. TLS validation must remain enabled.' }
$apkPath = Join-Path $repoRoot 'androidApp/build/outputs/apk/debug/androidApp-debug.apk'
$aapt = Get-ChildItem -LiteralPath (Join-Path $SdkDirectory 'build-tools') -Directory |
    Sort-Object Name -Descending | ForEach-Object { Join-Path $_.FullName 'aapt.exe' } |
    Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
if (!$aapt) { throw 'Android SDK build-tools/aapt.exe not found.' }
$badging = & $aapt dump badging $apkPath
if ($LASTEXITCODE -ne 0 -or ($badging -join "`n") -notmatch "package: name='ru\.flobsterable\.cosplay2\.smoke'") {
    throw 'APK is not the isolated .smoke build. Run verify.ps1 -TargetPlatform Android again in a new directory.'
}
$packageName = 'ru.flobsterable.cosplay2.smoke'
Invoke-Adb @('install', '-r', $apkPath) | Write-Output
if ($ResetSmokeData) { Invoke-Adb @('shell', 'pm', 'clear', $packageName) | Write-Output }
$metadata = [ordered]@{
    device = $Device
    mcpDevice = @((Invoke-Adb @('emu', 'avd', 'name')))[0].Trim()
    model = (Invoke-Adb @('shell', 'getprop', 'ro.product.model') | Out-String).Trim()
    apiLevel = (Invoke-Adb @('shell', 'getprop', 'ro.build.version.sdk') | Out-String).Trim()
    packageName = $packageName
    apkSha256 = (Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash
    sourceHash = $run.sourceHash
    revision = $run.revision
    installedAt = [DateTimeOffset]::Now.ToString('o')
    clockDriftSeconds = $clockDriftSeconds
}
[IO.File]::WriteAllText((Join-Path $outputPath 'android-device.json'), ($metadata | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
Write-Output "Installed $packageName on $Device. Launch and drive UI through cosplay_android MCP."

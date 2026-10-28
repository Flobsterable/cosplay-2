[CmdletBinding()]
param(
    [string]$SdkDirectory = $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }),
    [string]$RuntimeDirectory = (Join-Path $env:LOCALAPPDATA 'Cosplay2/testing/mobile-mcp-1.0.12')
)
$ErrorActionPreference = 'Stop'
$node = (Get-Command node -ErrorAction Stop).Source
if ([int]((& $node --version).TrimStart('v').Split('.')[0]) -lt 20) { throw 'Mobile MCP requires Node.js 20+.' }
if (!(Test-Path -LiteralPath (Join-Path $SdkDirectory 'platform-tools/adb.exe'))) { throw 'Android SDK platform-tools/adb.exe not found.' }
$SdkDirectory = [IO.Path]::GetFullPath($SdkDirectory)
$RuntimeDirectory = [IO.Path]::GetFullPath($RuntimeDirectory)
& npm.cmd install --prefix $RuntimeDirectory --save-exact --no-audit --no-fund '@mobilenext/mobile-mcp@1.0.12' '@modelcontextprotocol/sdk@1.29.0'
if ($LASTEXITCODE -ne 0) { throw 'Mobile MCP installation failed.' }
$entry = Join-Path $RuntimeDirectory 'node_modules/@mobilenext/mobile-mcp/lib/index.js'
& codex mcp add cosplay_android --env "ANDROID_HOME=$SdkDirectory" --env "ANDROID_SDK_ROOT=$SdkDirectory" --env 'MOBILEMCP_DISABLE_TELEMETRY=1' -- $node $entry
if ($LASTEXITCODE -ne 0) { throw 'Codex MCP registration failed.' }
& (Join-Path $PSScriptRoot 'install-testing.ps1')
Write-Output 'Registered cosplay_android. Reopen the Codex chat to load its native tools.'
Write-Output 'Probe now: node scripts/android-mcp.mjs --probe'

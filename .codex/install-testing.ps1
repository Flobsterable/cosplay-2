[CmdletBinding()]
param(
    [string]$Destination = $(if ($env:CODEX_HOME) { $env:CODEX_HOME } else { Join-Path $env:USERPROFILE '.codex' })
)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
$source = Join-Path $repoRoot '.agents/skills/two-level-testing'
$target = Join-Path $Destination 'skills/two-level-testing'
New-Item -ItemType Directory -Path (Join-Path $target 'agents') -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $source 'SKILL.md') -Destination (Join-Path $target 'SKILL.md') -Force
Copy-Item -LiteralPath (Join-Path $source 'agents/openai.yaml') -Destination (Join-Path $target 'agents/openai.yaml') -Force
Write-Output "Installed skill: $target"

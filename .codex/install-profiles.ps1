# Installs only the named profiles/skills; leaves config.toml and other skills intact.
[CmdletBinding()]
param(
    [string]$Destination = $(if ($env:CODEX_HOME) { $env:CODEX_HOME } else { Join-Path $env:USERPROFILE '.codex' })
)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
$profileNames = @('bug-fix', 'research', 'build-upgrade', 'review')
$utf8 = [System.Text.UTF8Encoding]::new($false)
New-Item -ItemType Directory -Path $Destination -Force | Out-Null
foreach ($profileName in $profileNames) {
    $skillName = "profile-$profileName"
    $skillSource = Join-Path $repoRoot ".agents/skills/$skillName"
    $skillText = [System.IO.File]::ReadAllText((Join-Path $skillSource 'SKILL.md'))
    $body = [regex]::Replace($skillText, '\A---\r?\n[\s\S]*?\r?\n---\r?\n', '').Trim()
    if ($body.Contains("'''")) { throw "Unsupported TOML delimiter in $skillName" }
    $configText = "# Generated from .agents/skills/$skillName/SKILL.md. Reinstall after editing.`n"
    if ($profileName -in @('research', 'review')) { $configText += "sandbox_mode = `"read-only`"`n" }
    $configText += "developer_instructions = '''`n$body`n'''`n"
    [System.IO.File]::WriteAllText((Join-Path $Destination "$profileName.config.toml"), $configText, $utf8)
    $skillTarget = Join-Path $Destination "skills/$skillName"
    New-Item -ItemType Directory -Path (Join-Path $skillTarget 'agents') -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $skillSource 'SKILL.md') -Destination (Join-Path $skillTarget 'SKILL.md') -Force
    Copy-Item -LiteralPath (Join-Path $skillSource 'agents/openai.yaml') -Destination (Join-Path $skillTarget 'agents/openai.yaml') -Force
    Write-Output "Installed $profileName.config.toml and skills/$skillName"
}

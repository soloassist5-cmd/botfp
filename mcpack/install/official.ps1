<#
=============================================================================
  LS City Life - setup for the official Minecraft Launcher (Mojang/Microsoft).

  The official launcher cannot import modpacks, so this script does by hand
  what Prism or Modrinth do on import:
    1. installs Forge into the launcher's .minecraft folder
       (the Forge installer is run in --installClient mode);
    2. installs the pack into its own game folder via install.ps1, so the
       mods never mix with the player's other worlds and versions;
    3. adds a ready "LS City Life" profile to launcher_profiles.json with
       that game folder, the Forge version and 6 GB of memory.

  Started by the .bat next to the pack. Pure ASCII on purpose: the Russian
  text lives in install\messages.ru.txt (see install.ps1 for the reason).
=============================================================================
#>
[CmdletBinding()]
param(
    [string]$Minecraft = (Join-Path $env:APPDATA '.minecraft'),
    [string]$GameDir = (Join-Path $env:APPDATA '.minecraft-ls-city'),
    [string]$Memory = '6G',
    [string]$ModsDir
)

$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }
$here = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Definition }
$installer = Join-Path $here 'install.ps1'

$Fallback = @{
    'official.title'        = 'LS CITY LIFE - setup for the official launcher'
    'official.no_installer' = 'install.ps1 is missing next to this script. Extract the whole archive.'
    'official.no_launcher'  = 'Official launcher data not found: {0}'
    'official.run_once'     = 'Start the official Minecraft Launcher once, log in, close it and run this file again.'
    'official.game_dir'     = 'Game folder of the pack: {0}'
    'official.head_forge'   = 'Forge'
    'official.forge_have'   = 'Forge {0} is already installed'
    'official.forge_get'    = 'downloading the Forge installer...'
    'official.forge_run'    = 'installing Forge into the launcher (1-3 minutes)...'
    'official.forge_ok'     = 'Forge {0} installed'
    'official.forge_fail'   = 'Forge could not be installed automatically.'
    'official.forge_manual' = 'Run {0} by hand: Install client -> OK, then this file again.'
    'official.no_java'      = 'Java not found: neither the launcher runtime nor java in PATH.'
    'official.java_hint'    = 'Install Java 17 (Adoptium Temurin 17) or start Minecraft 1.20.1 once in the launcher.'
    'official.java_use'     = 'Java: {0}'
    'official.head_pack'    = 'Pack'
    'official.head_profile' = 'Launcher profile'
    'official.profile_ok'   = 'profile "{0}" added'
    'official.backup'       = 'backup of the old profiles: {0}'
    'official.head_done'    = 'Done'
    'official.done1'        = 'Open the official launcher -> Play -> pick "LS City Life" next to the Play button.'
    'official.done2'        = 'The Los Santos world is in Singleplayer.'
}

$Msg = @{}
$catalog = Join-Path $here 'messages.ru.txt'
if (Test-Path -LiteralPath $catalog) {
    foreach ($line in Get-Content -LiteralPath $catalog -Encoding UTF8) {
        $at = $line.IndexOf('=')
        if ($at -lt 1 -or $line.TrimStart().StartsWith('#')) { continue }
        $key = $line.Substring(0, $at).Trim()
        if ($key) { $Msg[$key] = $line.Substring($at + 1).Trim() }
    }
}
function T([string]$key) {
    if ($Msg.ContainsKey($key))      { return $Msg[$key] }
    if ($Fallback.ContainsKey($key)) { return $Fallback[$key] }
    return $key
}
function Say($text, $colour = 'Gray') { Write-Host $text -ForegroundColor $colour }
function Head($text) { Say ''; Say ('== ' + $text) 'White' }

Say ''
Say ('  ' + (T 'official.title')) 'White'
Say ''

if (-not (Test-Path -LiteralPath $installer)) { Say (T 'official.no_installer') 'Red'; return }
$profiles = Join-Path $Minecraft 'launcher_profiles.json'
if (-not (Test-Path -LiteralPath $profiles)) {
    Say ((T 'official.no_launcher') -f $profiles) 'Red'
    Say (T 'official.run_once') 'Yellow'
    return
}

# Versions are taken from the mods list, the same way install.ps1 does it.
$header = Get-Content -LiteralPath (Join-Path $here 'mods.list') -TotalCount 3 | Out-String
$McVersion    = if ($header -match '# MC ([0-9.]+)') { $Matches[1] } else { '1.20.1' }
$ForgeVersion = if ($header -match 'forge ([0-9.]+)') { $Matches[1] } else { '47.4.23' }
$VersionId = "$McVersion-forge-$ForgeVersion"
Say ((T 'official.game_dir') -f $GameDir) 'Cyan'

# --- 1. Forge ------------------------------------------------------------------
Head (T 'official.head_forge')
$versionJson = Join-Path $Minecraft "versions\$VersionId\$VersionId.json"
if (Test-Path -LiteralPath $versionJson) {
    Say ('  ' + ((T 'official.forge_have') -f $VersionId)) 'Green'
} else {
    # Java: the one the launcher ships for 1.20.1 (Java 17), then PATH.
    $java = $null
    # The launcher keeps its runtime in different places depending on how it
    # was installed (classic installer, Microsoft Store, old .msi). Any of the
    # base folders may be missing, so each one is checked before joining.
    $gamma = 'runtime\java-runtime-gamma\windows-x64\java-runtime-gamma\bin\java.exe'
    $bases = @(
        $Minecraft,
        $(if ($env:LOCALAPPDATA) { Join-Path $env:LOCALAPPDATA 'Packages\Microsoft.4297127D64EC6_8wekyb3d8bbwe\LocalCache\Local' }),
        $(if (${env:ProgramFiles(x86)}) { Join-Path ${env:ProgramFiles(x86)} 'Minecraft Launcher' })
    )
    foreach ($base in $bases) {
        if (-not $base) { continue }
        $candidate = Join-Path $base $gamma
        if (Test-Path -LiteralPath $candidate) { $java = $candidate; break }
    }
    if (-not $java) {
        $cmd = Get-Command java -ErrorAction SilentlyContinue
        if ($cmd) { $java = $cmd.Source }
    }
    $jar = Join-Path ([System.IO.Path]::GetTempPath()) "forge-$McVersion-$ForgeVersion-installer.jar"
    if (-not (Test-Path -LiteralPath $jar)) {
        Say ('  ' + (T 'official.forge_get'))
        $url = "https://maven.minecraftforge.net/net/minecraftforge/forge/$McVersion-$ForgeVersion/forge-$McVersion-$ForgeVersion-installer.jar"
        Invoke-WebRequest -Uri $url -OutFile $jar -UseBasicParsing -TimeoutSec 180
    }
    if (-not $java) {
        Say ('  ' + (T 'official.no_java')) 'Red'
        Say ('  ' + (T 'official.java_hint')) 'Yellow'
        Say ('  ' + ((T 'official.forge_manual') -f $jar)) 'Yellow'
        return
    }
    Say ('  ' + ((T 'official.java_use') -f $java)) 'DarkGray'
    Say ('  ' + (T 'official.forge_run'))
    & $java -jar $jar --installClient $Minecraft | Out-Null
    if (-not (Test-Path -LiteralPath $versionJson)) {
        Say ('  ' + (T 'official.forge_fail')) 'Red'
        Say ('  ' + ((T 'official.forge_manual') -f $jar)) 'Yellow'
        return
    }
    Remove-Item -LiteralPath (Join-Path $Minecraft 'installer.log') -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $jar -ErrorAction SilentlyContinue
    Say ('  ' + ((T 'official.forge_ok') -f $VersionId)) 'Green'
}

# --- 2. The pack itself --------------------------------------------------------------
Head (T 'official.head_pack')
if ($ModsDir) { & $installer -Target client -Path $GameDir -ModsDir $ModsDir -NoLauncherHint }
else { & $installer -Target client -Path $GameDir -NoLauncherHint }

# --- 3. Profile in the official launcher ---------------------------------------------
Head (T 'official.head_profile')
$backup = "$profiles.ls-city-backup"
Copy-Item -LiteralPath $profiles -Destination $backup -Force
Say ('  ' + ((T 'official.backup') -f $backup)) 'DarkGray'
$data = Get-Content -LiteralPath $profiles -Raw -Encoding UTF8 | ConvertFrom-Json
if (-not $data.profiles) {
    $data | Add-Member -NotePropertyName profiles -NotePropertyValue ([pscustomobject]@{}) -Force
}
$now = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ss.fffZ')
$entry = [pscustomobject]@{
    name          = 'LS City Life'
    type          = 'custom'
    icon          = 'Furnace'
    created       = $now
    lastUsed      = $now
    lastVersionId = $VersionId
    gameDir       = $GameDir
    javaArgs      = "-Xmx$Memory -Xms2G -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200"
}
$data.profiles | Add-Member -NotePropertyName 'ls-city-life' -NotePropertyValue $entry -Force
# The launcher wants UTF-8 without BOM; Set-Content in PowerShell 5.1 would add one.
$json = $data | ConvertTo-Json -Depth 32
[System.IO.File]::WriteAllText($profiles, $json, (New-Object System.Text.UTF8Encoding $false))
Say ('  ' + ((T 'official.profile_ok') -f 'LS City Life')) 'Green'

Head (T 'official.head_done')
Say (T 'official.done1') 'White'
Say (T 'official.done2')

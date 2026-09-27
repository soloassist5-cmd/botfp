<#
=============================================================================
  LS City Life - one-click setup (build 2).

  Finds a launcher profile, asks which one to use, then hands the job to
  install.ps1. Started by USTANOVIT.bat / UNICODE-named .bat next to the pack.

  IMPORTANT: this file is intentionally pure ASCII. Windows PowerShell 5.1
  reads a .ps1 without a byte order mark in the system ANSI codepage, so a
  single Cyrillic letter in the source turns every string into garbage and
  breaks the parser before the first line runs. All Russian text lives in
  install\messages.ru.txt, which is read explicitly as UTF-8.
=============================================================================
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$here = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Definition }
$installer = Join-Path $here 'install.ps1'

# Make the console print UTF-8, otherwise the Russian text below shows up as
# question marks under the default OEM codepage.
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }

$Fallback = @{
    'setup.title'        = 'LS CITY LIFE - setup'
    'setup.no_installer' = 'install.ps1 is missing next to this script.'
    'setup.no_installer2' = 'Extract the whole ls-city-life-1.0.0.zip and start the .bat from it.'
    'setup.found'        = 'Profiles found:'
    'setup.manual'       = '[0] type the game folder path by hand'
    'setup.which_enter'  = 'Which one? Enter = [{0}]'
    'setup.which'        = 'Which one? Type a number'
    'setup.path'         = 'Path to the game folder'
    'setup.none1'        = 'No launcher profiles found.'
    'setup.none2'        = 'Create one first: CurseForge -> Create Custom Profile ->'
    'setup.none3'        = 'Minecraft 1.20.1 -> Forge 47.4.23, then run this file again.'
    'setup.path_or_exit' = 'Or type the game folder path by hand (Enter to quit)'
    'setup.nothing'      = 'Nothing picked, quitting.'
    'setup.missing'      = 'No such folder: {0}'
    'setup.installing'   = 'Installing into: {0}'
    'setup.size_note'    = 'About 342 MB will be downloaded, this can take a few minutes.'
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

Say ''
Say ('  ' + (T 'setup.title')) 'White'
Say '  ========================' 'DarkGray'
# Printed so a stale copy of the pack is recognizable from a screenshot.
Say '  build 2' 'DarkGray'
Say ''

if (-not (Test-Path -LiteralPath $installer)) {
    Say (T 'setup.no_installer') 'Red'
    Say (T 'setup.no_installer2') 'Red'
    return
}

# Look for profile folders of the popular launchers.
$candidates = @()
$roots = @(
    @{ Path = Join-Path $env:USERPROFILE 'curseforge\minecraft\Instances'; Name = 'CurseForge' },
    @{ Path = Join-Path $env:APPDATA 'PrismLauncher\instances';            Name = 'Prism' },
    @{ Path = Join-Path $env:APPDATA 'ModrinthApp\profiles';               Name = 'Modrinth' }
)
foreach ($root in $roots) {
    if (Test-Path -LiteralPath $root.Path) {
        foreach ($dir in Get-ChildItem -LiteralPath $root.Path -Directory -ErrorAction SilentlyContinue) {
            # Prism and MultiMC keep the game folder one level deeper.
            $game = $dir.FullName
            foreach ($inner in @('.minecraft', 'minecraft')) {
                $probe = Join-Path $dir.FullName $inner
                if (Test-Path -LiteralPath $probe) { $game = $probe; break }
            }
            $candidates += [pscustomobject]@{
                Launcher = $root.Name
                Name     = $dir.Name
                Path     = $game
            }
        }
    }
}

$target = $null
if ($candidates.Count -gt 0) {
    Say (T 'setup.found') 'White'
    for ($i = 0; $i -lt $candidates.Count; $i++) {
        $c = $candidates[$i]
        Say ("  [{0}] {1} - {2}" -f ($i + 1), $c.Launcher, $c.Name) 'Cyan'
    }
    Say ('  ' + (T 'setup.manual')) 'DarkGray'
    Say ''
    # The pack's own profile is usually named after the city, so offer it first.
    $guess = 0
    for ($i = 0; $i -lt $candidates.Count; $i++) {
        if ($candidates[$i].Name -match 'city|santos') { $guess = $i + 1; break }
    }
    $prompt = if ($guess -gt 0) { (T 'setup.which_enter') -f $guess } else { T 'setup.which' }
    $answer = Read-Host $prompt
    if ([string]::IsNullOrWhiteSpace($answer) -and $guess -gt 0) { $answer = "$guess" }
    if ($answer -eq '0') {
        $target = (Read-Host (T 'setup.path')).Trim('"', ' ')
    } elseif ($answer -match '^\d+$' -and [int]$answer -ge 1 -and [int]$answer -le $candidates.Count) {
        $target = $candidates[[int]$answer - 1].Path
    }
} else {
    Say (T 'setup.none1') 'Yellow'
    Say (T 'setup.none2') 'Yellow'
    Say (T 'setup.none3') 'Yellow'
    Say ''
    $target = (Read-Host (T 'setup.path_or_exit')).Trim('"', ' ')
}

if ([string]::IsNullOrWhiteSpace($target)) {
    Say (T 'setup.nothing') 'Yellow'
    return
}
if (-not (Test-Path -LiteralPath $target)) {
    Say ((T 'setup.missing') -f $target) 'Red'
    return
}

Say ''
Say ((T 'setup.installing') -f $target) 'Green'
Say (T 'setup.size_note') 'DarkGray'
Say ''

& $installer -Target client -Path $target

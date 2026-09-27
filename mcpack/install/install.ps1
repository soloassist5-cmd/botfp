<#
=============================================================================
  LS City Life - installer for Windows (PowerShell 5.1 and newer).

  Client:  .\install.ps1 -Target client -Path "$env:APPDATA\.minecraft-ls-city"
  Server:  .\install.ps1 -Target server -Path "C:\ls-city-server"

  Downloads the mods from Modrinth using install\mods.list and checks sha512.
  Running it again only fetches what is missing.

  If PowerShell refuses to run the file, do this once:
     Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass

  IMPORTANT: this file is intentionally pure ASCII. Windows PowerShell 5.1
  reads a .ps1 without a byte order mark in the system ANSI codepage, so a
  single Cyrillic letter in the source breaks the parser before the first
  line runs. The Russian text lives in messages.ru.txt, read as UTF-8.
=============================================================================
#>
[CmdletBinding()]
param(
    [ValidateSet('client', 'server')]
    [string]$Target = 'client',
    [Parameter(Mandatory = $true)]
    [string]$Path,
    [string]$ModsDir,
    [switch]$NoWorld,
    [switch]$WithOptional,
    [switch]$Clean
)

$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }

$ScriptDir = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Definition }
$PackDir   = Split-Path -Parent $ScriptDir
$ModsList  = Join-Path $ScriptDir 'mods.list'

$Fallback = @{
    'install.error'          = 'ERROR'
    'install.no_modslist'    = 'not found: {0}'
    'install.head_install'   = 'LS City Life - install ({0})'
    'install.dir'            = 'Folder: {0}'
    'install.bundle'         = 'Mods are taken from the bundle: {0} (no network needed)'
    'install.head_mods'      = 'Mods'
    'install.local_hash_mismatch' = 'bundled copy has a different hash, downloading: {0}'
    'install.progress'       = 'Downloading mods'
    'install.not_downloaded' = 'download failed: {0}'
    'install.hash_mismatch'  = 'hash mismatch: {0}'
    'install.failed_list'    = 'Could not install: {0}'
    'install.retry_hint'     = 'Run the installer again - it will only fetch those.'
    'install.from_bundle'    = '(from bundle)'
    'install.custom_mod'     = '(custom mod)'
    'install.extra'          = '(not in the pack)'
    'install.mods_count'     = 'Mods in the mods folder: {0}'
    'install.head_configs'   = 'Configs'
    'install.head_world'     = 'Los Santos world'
    'install.no_world'       = 'world archive not found - skipping'
    'install.world_exists'   = 'world is already there - keeping it'
    'install.world_ok'       = 'world unpacked into saves\los-santos'
    'install.server_world_exists' = 'server world is already there - keeping it'
    'install.server_world_ok' = 'world unpacked into world\'
    'install.head_forge'     = 'Forge'
    'install.forge_have'     = 'server Forge is already installed'
    'install.need_java'      = 'Java 17 is required'
    'install.forge_get'      = 'downloading the Forge installer...'
    'install.forge_run'      = 'installing server Forge (1-3 minutes)...'
    'install.forge_ok'       = 'server Forge installed'
    'install.next_server'    = 'Next: open eula.txt, set eula=true and run start.bat'
    'install.forge_client1'  = 'Forge for the client is installed by the launcher itself.'
    'install.forge_client2'  = 'Legacy Launcher / TLauncher: pick Forge {0}'
    'install.forge_client3'  = 'and point it at this folder as the game folder:'
    'install.head_done'      = 'Done'
    'install.done_note'      = 'Give the game 6 GB of RAM (-Xmx6G) and use Java 17.'
}

$Msg = @{}
$catalog = Join-Path $ScriptDir 'messages.ru.txt'
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
function Write-Head($text) { Write-Host "`n== $text ==" -ForegroundColor White }
function Write-Ok  ($text) { Write-Host "OK  $text" -ForegroundColor Green }
function Write-Warn($text) { Write-Host " !  $text" -ForegroundColor Yellow }
function Die      ($text) { Write-Host ((T 'install.error') + ": $text") -ForegroundColor Red; exit 1 }

if (-not (Test-Path $ModsList)) { Die ((T 'install.no_modslist') -f $ModsList) }

# A folder of already downloaded jars: when present, no network is needed.
if (-not $ModsDir) {
    $bundled = Join-Path $PackDir 'mods-bundle'
    if (Test-Path $bundled) { $ModsDir = $bundled }
}

$lines = Get-Content -LiteralPath $ModsList -Encoding UTF8
$header = ($lines | Where-Object { $_ -match '^# MC ' } | Select-Object -First 1)
$McVersion    = if ($header -match '^# MC ([0-9.]+)') { $Matches[1] } else { '1.20.1' }
$ForgeVersion = if ($header -match 'forge ([0-9.]+)') { $Matches[1] } else { '47.4.23' }

New-Item -ItemType Directory -Force -Path $Path | Out-Null
$Dest    = (Resolve-Path -LiteralPath $Path).Path
$TargetMods = Join-Path $Dest 'mods'
New-Item -ItemType Directory -Force -Path $TargetMods | Out-Null

Write-Head ((T 'install.head_install') -f $Target)
Write-Host "Minecraft $McVersion + Forge $ForgeVersion"
Write-Host ((T 'install.dir') -f $Dest)
if ($ModsDir) { Write-Host ((T 'install.bundle') -f $ModsDir) }

# --- 1. Mods -----------------------------------------------------------------
Write-Head (T 'install.head_mods')
$entries = @()
foreach ($line in $lines) {
    if ($line -match '^#' -or [string]::IsNullOrWhiteSpace($line)) { continue }
    $f = $line -split "`t"
    if ($f.Count -lt 7) { continue }
    $entries += [pscustomobject]@{
        Side = $f[0]; Group = $f[1]; Optional = ($f[2] -eq '1')
        FileName = $f[3]; Sha512 = $f[4]; Size = [int64]$f[5]; Url = $f[6]
    }
}
if ($Target -eq 'server')  { $entries = $entries | Where-Object { $_.Side -ne 'client' } }
if (-not $WithOptional)    { $entries = $entries | Where-Object { -not $_.Optional } }

$expected = New-Object System.Collections.Generic.List[string]
$failed   = New-Object System.Collections.Generic.List[string]
$i = 0
foreach ($m in $entries) {
    $i++
    $expected.Add($m.FileName)
    # The variable name must not clash with $Dest: PowerShell ignores case.
    $jarPath = Join-Path $TargetMods $m.FileName
    if (Test-Path -LiteralPath $jarPath) {
        $have = (Get-FileHash -LiteralPath $jarPath -Algorithm SHA512).Hash
        if ($have -ieq $m.Sha512) { Write-Host ("  = " + $m.FileName); continue }
        Remove-Item -LiteralPath $jarPath -Force
    }
    # A local copy shipped next to the pack: check the hash and copy, no network.
    if ($ModsDir) {
        $local = Join-Path $ModsDir $m.FileName
        if (Test-Path -LiteralPath $local) {
            $localHash = (Get-FileHash -LiteralPath $local -Algorithm SHA512).Hash
            if ($localHash -ieq $m.Sha512) {
                Copy-Item -LiteralPath $local -Destination $jarPath -Force
                Write-Host ("  * " + $m.FileName + " " + (T 'install.from_bundle'))
                continue
            }
            Write-Warn ((T 'install.local_hash_mismatch') -f $m.FileName)
        }
    }
    $tmp = "$jarPath.part"
    Write-Progress -Activity (T 'install.progress') -Status $m.FileName `
                   -PercentComplete ([int](100 * $i / [Math]::Max($entries.Count, 1)))
    $downloaded = $false
    for ($try = 1; $try -le 4; $try++) {
        try {
            Invoke-WebRequest -Uri $m.Url -OutFile $tmp -UseBasicParsing -TimeoutSec 120
            $downloaded = $true; break
        } catch { Start-Sleep -Seconds ([int][Math]::Pow(2, $try)) }
    }
    if (-not $downloaded) {
        Write-Host ("  X " + ((T 'install.not_downloaded') -f $m.FileName)) -ForegroundColor Red
        $failed.Add($m.FileName); Remove-Item -LiteralPath $tmp -Force -ErrorAction SilentlyContinue
        continue
    }
    $got = (Get-FileHash -LiteralPath $tmp -Algorithm SHA512).Hash
    if ($got -ine $m.Sha512) {
        Write-Host ("  X " + ((T 'install.hash_mismatch') -f $m.FileName)) -ForegroundColor Red
        $failed.Add($m.FileName); Remove-Item -LiteralPath $tmp -Force
        continue
    }
    Move-Item -LiteralPath $tmp -Destination $jarPath -Force
    Write-Host ("  + " + $m.FileName)
}
Write-Progress -Activity (T 'install.progress') -Completed
if ($failed.Count -gt 0) {
    Write-Warn ((T 'install.failed_list') -f ($failed -join ', '))
    Write-Warn (T 'install.retry_hint')
}

# The custom mod that lives in the repository.
$localDir = Join-Path $PackDir 'mods-local'
if (Test-Path $localDir) {
    foreach ($jar in Get-ChildItem -LiteralPath $localDir -Filter *.jar) {
        Copy-Item -LiteralPath $jar.FullName -Destination $TargetMods -Force
        $expected.Add($jar.Name)
        Write-Host ("  + " + $jar.Name + " " + (T 'install.custom_mod'))
    }
}

if ($Clean) {
    foreach ($jar in Get-ChildItem -LiteralPath $TargetMods -Filter *.jar) {
        if ($expected -notcontains $jar.Name) {
            Remove-Item -LiteralPath $jar.FullName -Force
            Write-Host ("  - " + $jar.Name + " " + (T 'install.extra'))
        }
    }
}
Write-Ok ((T 'install.mods_count') -f (Get-ChildItem -LiteralPath $TargetMods -Filter *.jar).Count)

# --- 2. Configs --------------------------------------------------------------
Write-Head (T 'install.head_configs')
function Copy-Tree($src, $dst) {
    # File by file: Copy-Item -Recurse fails when the folder already exists.
    if (-not (Test-Path -LiteralPath $src)) { return }
    $srcFull = (Resolve-Path -LiteralPath $src).Path
    New-Item -ItemType Directory -Force -Path $dst | Out-Null
    foreach ($file in Get-ChildItem -LiteralPath $srcFull -Recurse -File) {
        $rel = $file.FullName.Substring($srcFull.Length).TrimStart('\', '/')
        # The name must not clash with the -Target parameter: case is ignored.
        $destFile = Join-Path $dst $rel
        $destDir = Split-Path -Parent $destFile
        if ($destDir -and -not (Test-Path -LiteralPath $destDir)) {
            New-Item -ItemType Directory -Force -Path $destDir | Out-Null
        }
        Copy-Item -LiteralPath $file.FullName -Destination $destFile -Force
    }
    Write-Host ("  + " + (Split-Path -Leaf $srcFull) + "\")
}
if ($Target -eq 'client') {
    Copy-Tree (Join-Path $PackDir 'overrides') $Dest
} else {
    foreach ($sub in @('config', 'kubejs', 'defaultconfigs')) {
        Copy-Tree (Join-Path $PackDir "overrides\$sub") (Join-Path $Dest $sub)
    }
    Copy-Tree (Join-Path $PackDir 'server') $Dest
}

# --- 3. World ----------------------------------------------------------------
if (-not $NoWorld) {
    Write-Head (T 'install.head_world')
    $zip = Get-ChildItem -Path (Join-Path $PackDir 'world') -Filter 'los-santos*.zip' `
           -ErrorAction SilentlyContinue | Sort-Object Name | Select-Object -First 1
    if (-not $zip) {
        Write-Warn (T 'install.no_world')
    } elseif ($Target -eq 'client') {
        $saves = Join-Path $Dest 'saves'
        New-Item -ItemType Directory -Force -Path $saves | Out-Null
        if (Test-Path (Join-Path $saves 'los-santos')) {
            Write-Warn (T 'install.world_exists')
        } else {
            Expand-Archive -LiteralPath $zip.FullName -DestinationPath $saves -Force
            Write-Ok (T 'install.world_ok')
        }
    } else {
        if (Test-Path (Join-Path $Dest 'world')) {
            Write-Warn (T 'install.server_world_exists')
        } else {
            # GetTempPath is safer than $env:TEMP: the variable is not always set.
            $tmp = Join-Path ([System.IO.Path]::GetTempPath()) ('ls-city-' + [guid]::NewGuid())
            Expand-Archive -LiteralPath $zip.FullName -DestinationPath $tmp -Force
            Move-Item -LiteralPath (Join-Path $tmp 'los-santos') -Destination (Join-Path $Dest 'world')
            Remove-Item -LiteralPath $tmp -Recurse -Force
            Write-Ok (T 'install.server_world_ok')
        }
    }
}

# --- 4. Forge ----------------------------------------------------------------
Write-Head (T 'install.head_forge')
if ($Target -eq 'server') {
    $marker = Join-Path $Dest "libraries\net\minecraftforge\forge\$McVersion-$ForgeVersion\win_args.txt"
    if (Test-Path $marker) {
        Write-Ok (T 'install.forge_have')
    } else {
        if (-not (Get-Command java -ErrorAction SilentlyContinue)) { Die (T 'install.need_java') }
        $inst = Join-Path $Dest 'forge-installer.jar'
        $url = "https://maven.minecraftforge.net/net/minecraftforge/forge/$McVersion-$ForgeVersion/forge-$McVersion-$ForgeVersion-installer.jar"
        Write-Host ('  ' + (T 'install.forge_get'))
        Invoke-WebRequest -Uri $url -OutFile $inst -UseBasicParsing -TimeoutSec 180
        Write-Host ('  ' + (T 'install.forge_run'))
        Push-Location $Dest
        try { & java -jar $inst --installServer | Out-Null } finally { Pop-Location }
        Remove-Item -LiteralPath $inst -Force -ErrorAction SilentlyContinue
        Write-Ok (T 'install.forge_ok')
    }
    Write-Host ''
    Write-Host (T 'install.next_server')
} else {
    Write-Host ('  ' + (T 'install.forge_client1'))
    Write-Host ('  ' + ((T 'install.forge_client2') -f "$McVersion-$ForgeVersion"))
    Write-Host ('  ' + (T 'install.forge_client3'))
    Write-Host "     $Dest"
}

Write-Head (T 'install.head_done')
Write-Host (T 'install.done_note')

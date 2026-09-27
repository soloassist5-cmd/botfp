<#
=============================================================================
  LS City Life — установщик для Windows (PowerShell 5.1 и новее).

  Клиент:  .\install.ps1 -Target client -Path "$env:APPDATA\.minecraft-ls-city"
  Сервер:  .\install.ps1 -Target server -Path "C:\ls-city-server"

  Скачивает моды с Modrinth по install\mods.list и проверяет sha512.
  Повторный запуск докачивает только недостающее.

  Если PowerShell отказывается запускать файл, выполни один раз:
     Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
=============================================================================
#>
[CmdletBinding()]
param(
    [ValidateSet('client', 'server')]
    [string]$Target = 'client',
    [Parameter(Mandatory = $true)]
    [string]$Path,
    [switch]$NoWorld,
    [switch]$WithOptional,
    [switch]$Clean
)

$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$ScriptDir = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Definition }
$PackDir   = Split-Path -Parent $ScriptDir
$ModsList  = Join-Path $ScriptDir 'mods.list'

function Write-Head($text) { Write-Host "`n== $text ==" -ForegroundColor White }
function Write-Ok  ($text) { Write-Host "OK  $text" -ForegroundColor Green }
function Write-Warn($text) { Write-Host " !  $text" -ForegroundColor Yellow }
function Die      ($text) { Write-Host "ОШИБКА: $text" -ForegroundColor Red; exit 1 }

if (-not (Test-Path $ModsList)) { Die "не найден $ModsList" }

$lines = Get-Content -LiteralPath $ModsList -Encoding UTF8
$header = ($lines | Where-Object { $_ -match '^# MC ' } | Select-Object -First 1)
$McVersion    = if ($header -match '^# MC ([0-9.]+)') { $Matches[1] } else { '1.20.1' }
$ForgeVersion = if ($header -match 'forge ([0-9.]+)') { $Matches[1] } else { '47.4.23' }

New-Item -ItemType Directory -Force -Path $Path | Out-Null
$Dest    = (Resolve-Path -LiteralPath $Path).Path
$ModsDir = Join-Path $Dest 'mods'
New-Item -ItemType Directory -Force -Path $ModsDir | Out-Null

Write-Head "LS City Life — установка ($Target)"
Write-Host "Minecraft $McVersion + Forge $ForgeVersion"
Write-Host "Каталог: $Dest"

# --- 1. Загрузка модов -------------------------------------------------------
Write-Head 'Моды'
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
    # Имя переменной не должно совпадать с $Dest: в PowerShell регистр не важен.
    $jarPath = Join-Path $ModsDir $m.FileName
    if (Test-Path -LiteralPath $jarPath) {
        $have = (Get-FileHash -LiteralPath $jarPath -Algorithm SHA512).Hash
        if ($have -ieq $m.Sha512) { Write-Host ("  = " + $m.FileName); continue }
        Remove-Item -LiteralPath $jarPath -Force
    }
    $tmp = "$jarPath.part"
    Write-Progress -Activity 'Загрузка модов' -Status $m.FileName `
                   -PercentComplete ([int](100 * $i / [Math]::Max($entries.Count, 1)))
    $downloaded = $false
    for ($try = 1; $try -le 4; $try++) {
        try {
            Invoke-WebRequest -Uri $m.Url -OutFile $tmp -UseBasicParsing -TimeoutSec 120
            $downloaded = $true; break
        } catch { Start-Sleep -Seconds ([int][Math]::Pow(2, $try)) }
    }
    if (-not $downloaded) {
        Write-Host ("  X не скачался: " + $m.FileName) -ForegroundColor Red
        $failed.Add($m.FileName); Remove-Item -LiteralPath $tmp -Force -ErrorAction SilentlyContinue
        continue
    }
    $got = (Get-FileHash -LiteralPath $tmp -Algorithm SHA512).Hash
    if ($got -ine $m.Sha512) {
        Write-Host ("  X хэш не совпал: " + $m.FileName) -ForegroundColor Red
        $failed.Add($m.FileName); Remove-Item -LiteralPath $tmp -Force
        continue
    }
    Move-Item -LiteralPath $tmp -Destination $jarPath -Force
    Write-Host ("  + " + $m.FileName)
}
Write-Progress -Activity 'Загрузка модов' -Completed
if ($failed.Count -gt 0) {
    Write-Warn ("Не удалось поставить: " + ($failed -join ', '))
    Write-Warn 'Запусти установщик снова — докачает только их.'
}

# Самописный мод из репозитория.
$localDir = Join-Path $PackDir 'mods-local'
if (Test-Path $localDir) {
    foreach ($jar in Get-ChildItem -LiteralPath $localDir -Filter *.jar) {
        Copy-Item -LiteralPath $jar.FullName -Destination $ModsDir -Force
        $expected.Add($jar.Name)
        Write-Host ("  + " + $jar.Name + " (самописный)")
    }
}

if ($Clean) {
    foreach ($jar in Get-ChildItem -LiteralPath $ModsDir -Filter *.jar) {
        if ($expected -notcontains $jar.Name) {
            Remove-Item -LiteralPath $jar.FullName -Force
            Write-Host ("  - " + $jar.Name + " (лишний)")
        }
    }
}
Write-Ok ("Модов в mods\: " + (Get-ChildItem -LiteralPath $ModsDir -Filter *.jar).Count)

# --- 2. Конфиги --------------------------------------------------------------
Write-Head 'Конфиги'
function Copy-Tree($src, $dst) {
    # Копируем по файлам: Copy-Item -Recurse падает, если каталог уже существует.
    if (-not (Test-Path -LiteralPath $src)) { return }
    $srcFull = (Resolve-Path -LiteralPath $src).Path
    New-Item -ItemType Directory -Force -Path $dst | Out-Null
    foreach ($file in Get-ChildItem -LiteralPath $srcFull -Recurse -File) {
        $rel = $file.FullName.Substring($srcFull.Length).TrimStart('\', '/')
        $target = Join-Path $dst $rel
        $targetDir = Split-Path -Parent $target
        if ($targetDir -and -not (Test-Path -LiteralPath $targetDir)) {
            New-Item -ItemType Directory -Force -Path $targetDir | Out-Null
        }
        Copy-Item -LiteralPath $file.FullName -Destination $target -Force
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

# --- 3. Мир ------------------------------------------------------------------
if (-not $NoWorld) {
    Write-Head 'Мир Los Santos'
    $zip = Get-ChildItem -Path (Join-Path $PackDir 'world') -Filter 'los-santos*.zip' `
           -ErrorAction SilentlyContinue | Sort-Object Name | Select-Object -First 1
    if (-not $zip) {
        Write-Warn 'архив мира не найден — пропускаю'
    } elseif ($Target -eq 'client') {
        $saves = Join-Path $Dest 'saves'
        New-Item -ItemType Directory -Force -Path $saves | Out-Null
        if (Test-Path (Join-Path $saves 'los-santos')) {
            Write-Warn 'мир уже есть — не перезаписываю'
        } else {
            Expand-Archive -LiteralPath $zip.FullName -DestinationPath $saves -Force
            Write-Ok 'мир распакован в saves\los-santos'
        }
    } else {
        if (Test-Path (Join-Path $Dest 'world')) {
            Write-Warn 'мир сервера уже есть — не перезаписываю'
        } else {
            $tmp = Join-Path $env:TEMP ('ls-city-' + [guid]::NewGuid())
            Expand-Archive -LiteralPath $zip.FullName -DestinationPath $tmp -Force
            Move-Item -LiteralPath (Join-Path $tmp 'los-santos') -Destination (Join-Path $Dest 'world')
            Remove-Item -LiteralPath $tmp -Recurse -Force
            Write-Ok 'мир распакован в world\'
        }
    }
}

# --- 4. Forge ----------------------------------------------------------------
Write-Head 'Forge'
if ($Target -eq 'server') {
    $marker = Join-Path $Dest "libraries\net\minecraftforge\forge\$McVersion-$ForgeVersion\win_args.txt"
    if (Test-Path $marker) {
        Write-Ok 'серверный Forge уже установлен'
    } else {
        if (-not (Get-Command java -ErrorAction SilentlyContinue)) { Die 'нужна Java 17' }
        $inst = Join-Path $Dest 'forge-installer.jar'
        $url = "https://maven.minecraftforge.net/net/minecraftforge/forge/$McVersion-$ForgeVersion/forge-$McVersion-$ForgeVersion-installer.jar"
        Write-Host '  скачиваю установщик Forge...'
        Invoke-WebRequest -Uri $url -OutFile $inst -UseBasicParsing -TimeoutSec 180
        Write-Host '  устанавливаю серверный Forge (1-3 минуты)...'
        Push-Location $Dest
        try { & java -jar $inst --installServer | Out-Null } finally { Pop-Location }
        Remove-Item -LiteralPath $inst -Force -ErrorAction SilentlyContinue
        Write-Ok 'серверный Forge установлен'
    }
    Write-Host ''
    Write-Host 'Дальше: открой eula.txt, поставь eula=true и запусти start.bat'
} else {
    Write-Host '  Forge для клиента ставит сам лаунчер.'
    Write-Host "  Legacy Launcher / TLauncher: выбери Forge $McVersion-$ForgeVersion"
    Write-Host '  и укажи этот каталог как папку игры:'
    Write-Host "     $Dest"
}

Write-Head 'Готово'
Write-Host 'Не забудь выделить игре 6 ГБ ОЗУ (-Xmx6G) и поставить Java 17.'

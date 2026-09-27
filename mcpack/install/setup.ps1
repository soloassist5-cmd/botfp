<#
  Установка LS City Life в один клик.

  Скрипт лежит рядом с install.ps1, поэтому ничего искать не нужно: он знает,
  где установщик и где файлы сборки. От пользователя требуется только выбрать
  профиль лаунчера (или ввести путь к папке игры).

  Запускается через УСТАНОВИТЬ.bat в корне распакованного архива.
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$here = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Definition }
$installer = Join-Path $here 'install.ps1'

function Say($text, $colour = 'Gray') { Write-Host $text -ForegroundColor $colour }

Say ''
Say '  LS CITY LIFE — установка' 'White'
Say '  ========================' 'DarkGray'
Say ''

if (-not (Test-Path -LiteralPath $installer)) {
    Say "Рядом нет install.ps1. Похоже, архив распакован не полностью." 'Red'
    Say "Распакуй ls-city-life-1.0.0.zip целиком и запусти УСТАНОВИТЬ.bat из него." 'Red'
    return
}

# Ищем папки профилей популярных лаунчеров.
$candidates = @()
$roots = @(
    @{ Path = Join-Path $env:USERPROFILE 'curseforge\minecraft\Instances'; Name = 'CurseForge' },
    @{ Path = Join-Path $env:APPDATA 'PrismLauncher\instances';            Name = 'Prism' },
    @{ Path = Join-Path $env:APPDATA 'ModrinthApp\profiles';               Name = 'Modrinth' }
)
foreach ($root in $roots) {
    if (Test-Path -LiteralPath $root.Path) {
        foreach ($dir in Get-ChildItem -LiteralPath $root.Path -Directory -ErrorAction SilentlyContinue) {
            # У Prism и MultiMC папка игры лежит внутри инстанса.
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
    Say 'Найденные профили:' 'White'
    for ($i = 0; $i -lt $candidates.Count; $i++) {
        $c = $candidates[$i]
        Say ("  [{0}] {1} — {2}" -f ($i + 1), $c.Launcher, $c.Name) 'Cyan'
    }
    Say ("  [0] ввести путь к папке игры вручную") 'DarkGray'
    Say ''
    $guess = 0
    for ($i = 0; $i -lt $candidates.Count; $i++) {
        if ($candidates[$i].Name -match 'city|санто|santos') { $guess = $i + 1; break }
    }
    $prompt = if ($guess -gt 0) { "Куда ставим? Enter = [$guess]" } else { 'Куда ставим? Введи номер' }
    $answer = Read-Host $prompt
    if ([string]::IsNullOrWhiteSpace($answer) -and $guess -gt 0) { $answer = "$guess" }
    if ($answer -eq '0') {
        $target = (Read-Host 'Путь к папке игры').Trim('"', ' ')
    } elseif ($answer -match '^\d+$' -and [int]$answer -ge 1 -and [int]$answer -le $candidates.Count) {
        $target = $candidates[[int]$answer - 1].Path
    }
} else {
    Say 'Профили лаунчеров не найдены.' 'Yellow'
    Say 'Сначала создай профиль: CurseForge -> Create Custom Profile ->' 'Yellow'
    Say 'Minecraft 1.20.1 -> Forge 47.4.23. Потом запусти этот файл снова.' 'Yellow'
    Say ''
    $target = (Read-Host 'Либо введи путь к папке игры вручную (Enter — выйти)').Trim('"', ' ')
}

if ([string]::IsNullOrWhiteSpace($target)) {
    Say 'Ничего не выбрано, выходим.' 'Yellow'
    return
}
if (-not (Test-Path -LiteralPath $target)) {
    Say "Папки нет: $target" 'Red'
    return
}

Say ''
Say "Ставлю в: $target" 'Green'
Say 'Скачивание примерно 342 МБ, это может занять несколько минут.' 'DarkGray'
Say ''

& $installer -Target client -Path $target

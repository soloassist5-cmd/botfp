<#
  LS City Life - roll the world back to a backup made by the server.
  Pure ASCII on purpose (Windows PowerShell 5.1 reads files in ANSI).
  Usage: restore-backup.ps1 [backups\world-2026-01-01_12-00.zip]
#>
param([string]$Zip)
$ErrorActionPreference = 'Stop'
$here = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Definition }
Set-Location -LiteralPath $here

$world = 'world'
if (Test-Path -LiteralPath 'server.properties') {
    foreach ($line in Get-Content -LiteralPath 'server.properties') {
        if ($line -match '^level-name=(.+)$') { $world = $Matches[1].Trim() }
    }
}

if (-not $Zip) {
    $list = @(Get-ChildItem -LiteralPath 'backups' -Filter '*.zip' -ErrorAction SilentlyContinue |
              Sort-Object Name -Descending)
    if ($list.Count -eq 0) { Write-Host 'No backups in the backups folder.' -ForegroundColor Red; exit 1 }
    for ($i = 0; $i -lt $list.Count; $i++) { Write-Host ('  [{0}] {1}' -f ($i + 1), $list[$i].Name) }
    $n = Read-Host 'Which backup to restore? Number (Enter = newest)'
    if ([string]::IsNullOrWhiteSpace($n)) { $n = '1' }
    $Zip = $list[[int]$n - 1].FullName
}
if (-not (Test-Path -LiteralPath $Zip)) { Write-Host "No such file: $Zip" -ForegroundColor Red; exit 1 }

$ok = Read-Host ("Is the server stopped? Replace world '{0}' with {1}? [y/N]" -f $world, (Split-Path -Leaf $Zip))
if ($ok -ne 'y' -and $ok -ne 'Y') { Write-Host 'Cancelled.'; exit 0 }

$tmp = Join-Path ([System.IO.Path]::GetTempPath()) ('ls-restore-' + [guid]::NewGuid())
Expand-Archive -LiteralPath $Zip -DestinationPath $tmp -Force
$src = Get-ChildItem -LiteralPath $tmp -Directory | Select-Object -First 1
if (-not $src -or -not (Test-Path -LiteralPath (Join-Path $src.FullName 'level.dat'))) {
    Remove-Item -LiteralPath $tmp -Recurse -Force
    Write-Host 'The archive has no world in it (level.dat).' -ForegroundColor Red; exit 1
}
if (Test-Path -LiteralPath $world) {
    $old = $world + '-before-restore-' + (Get-Date -Format 'yyyy-MM-dd_HH-mm-ss')
    Rename-Item -LiteralPath $world -NewName $old
    Write-Host "Current world kept as $old"
}
Move-Item -LiteralPath $src.FullName -Destination $world
Remove-Item -LiteralPath $tmp -Recurse -Force
Write-Host ("Done: world '{0}' restored from {1}. Start the server with start.bat" -f $world, (Split-Path -Leaf $Zip)) -ForegroundColor Green

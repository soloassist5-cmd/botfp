@echo off
rem ===========================================================================
rem  Запуск сервера LS City Life (Windows)
rem
rem  Перед первым запуском:
rem    1. открой eula.txt и поставь eula=true
rem    2. поставь Java 17 (например, Adoptium Temurin 17)
rem
rem  Память меняется в переменной RAM ниже.
rem ===========================================================================
setlocal
cd /d "%~dp0"

set RAM=4G
set MC=1.20.1
set FORGE=47.4.23
set ARGS=libraries\net\minecraftforge\forge\%MC%-%FORGE%\win_args.txt

where java >nul 2>nul
if errorlevel 1 (
  echo Не найдена Java. Поставь Java 17 и запусти снова.
  pause
  exit /b 1
)
if not exist "%ARGS%" (
  echo Не установлен серверный Forge. Запусти установщик:
  echo    powershell -ExecutionPolicy Bypass -File install.ps1 -Target server -Path "%cd%"
  pause
  exit /b 1
)
findstr /i "eula=false" eula.txt >nul 2>nul
if not errorlevel 1 (
  echo Открой eula.txt и поставь eula=true.
  pause
  exit /b 1
)

java -Xmx%RAM% -Xms1G ^
  -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 ^
  -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC ^
  -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M ^
  -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 ^
  -XX:InitiatingHeapOccupancyPercent=15 -Dfile.encoding=UTF-8 ^
  @%ARGS% nogui

echo.
echo Сервер остановлен.
pause

@echo off
rem ===========================================================================
rem  LS City Life dedicated server launcher (Windows)
rem
rem  Before the first run:
rem    1. open eula.txt and set eula=true
rem    2. install Java 17 (Adoptium Temurin 17 is a good pick)
rem
rem  RAM below can be raised if the machine has more memory.
rem ===========================================================================
setlocal
cd /d "%~dp0"

set RAM=4G
set MC=1.20.1
set FORGE=47.4.23
set ARGS=libraries\net\minecraftforge\forge\%MC%-%FORGE%\win_args.txt

where java >nul 2>nul
if errorlevel 1 (
  echo Java not found. Install Java 17, then run this file again.
  pause
  exit /b 1
)
if not exist "%ARGS%" (
  echo Forge server is not installed yet. Run the installer first:
  echo    powershell -ExecutionPolicy Bypass -File install.ps1 -Target server -Path "%cd%"
  pause
  exit /b 1
)
findstr /i "eula=false" eula.txt >nul 2>nul
if not errorlevel 1 (
  echo Open eula.txt and set eula=true, then run this file again.
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
echo Server stopped.
pause

@echo off
rem ===========================================================================
rem  LS City Life - roll the world back to a backup (Windows)
rem
rem  The server makes backups by itself: every hour into the backups folder
rem  ([backup] in config\citylife-common.toml), or on demand: /citylife backup
rem
rem  Stop the server first (type stop), then run this file.
rem  The current world is not deleted: it is renamed to world-before-restore-...
rem ===========================================================================
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0restore-backup.ps1" %*
pause

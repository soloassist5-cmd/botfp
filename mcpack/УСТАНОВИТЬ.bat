@echo off
title LS City Life - setup (build 2)
rem Runs the installer. PowerShell execution policy is bypassed explicitly,
rem so nothing needs to be changed in the system settings.
rem The scripts themselves are pure ASCII on purpose, see install\setup.ps1.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0install\setup.ps1"
echo.
pause

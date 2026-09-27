@echo off
title LS City Life - setup
rem Runs the installer. PowerShell execution policy is bypassed explicitly,
rem so nothing needs to be changed in the system settings.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0install\setup.ps1"
echo.
pause

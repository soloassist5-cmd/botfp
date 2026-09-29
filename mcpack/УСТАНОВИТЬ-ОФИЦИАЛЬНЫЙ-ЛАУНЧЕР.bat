@echo off
title LS City Life - official launcher setup
rem Setup for the official Minecraft Launcher: Forge, the pack in its own
rem folder and a ready profile. Scripts are pure ASCII, see install\official.ps1.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0install\official.ps1"
echo.
pause

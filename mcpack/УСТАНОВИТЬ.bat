@echo off
chcp 65001 >nul
title LS City Life - установка
rem Запускает установку сборки. Политика выполнения PowerShell тут не мешает:
rem скрипт вызывается с ключом Bypass явно.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0install\setup.ps1"
echo.
pause

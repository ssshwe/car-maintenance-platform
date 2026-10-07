@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"
title Stop Car Care Platform
where pwsh.exe >nul 2>&1
if errorlevel 1 (
  powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\stop.ps1"
) else (
  pwsh.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\stop.ps1"
)
if errorlevel 1 (
  echo.
  echo Shutdown failed. Please check the message above.
  pause
  exit /b 1
)
echo.
echo The project is stopped. You may close this window.
timeout /t 3 /nobreak >nul 2>&1
exit /b 0

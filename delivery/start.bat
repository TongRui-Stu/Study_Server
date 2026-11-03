@echo off
setlocal
cd /d "%~dp0"
start "" "%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -WindowStyle Hidden -NoLogo -NoProfile -STA -ExecutionPolicy Bypass -File "%~dp0launcher.ps1"
endlocal

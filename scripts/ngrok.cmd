@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0ngrok.ps1" %*
exit /b %ERRORLEVEL%

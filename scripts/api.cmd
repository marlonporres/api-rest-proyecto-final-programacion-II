@echo off
rem La politica se aplica solo al proceso hijo; no cambia la politica de Windows.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0api.ps1" %*
exit /b %ERRORLEVEL%
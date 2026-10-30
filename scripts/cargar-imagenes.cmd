@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0cargar-imagenes.ps1" %*
exit /b %ERRORLEVEL%
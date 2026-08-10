@echo off
setlocal
cd /d "%~dp0"
start "" "%~dp0tools\HtaTaskbarLauncher.exe" "%~dp0admin_data_menu.hta" "%~dp0assets\icons\admin_menu.ico"
exit /b

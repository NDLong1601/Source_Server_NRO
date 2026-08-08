@echo off
setlocal
cd /d "%~dp0"
start "" mshta.exe "%~dp0admin_data_menu.hta"
exit /b

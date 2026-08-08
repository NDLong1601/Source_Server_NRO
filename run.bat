@echo off
setlocal
cd /d "%~dp0"

if /i "%~1"=="start" goto control
if /i "%~1"=="stop" goto control
if /i "%~1"=="restart" goto control
if /i "%~1"=="status" goto control
if /i "%~1"=="event" goto control
if /i "%~1"=="eventrestart" goto control
if /i "%~1"=="exp" goto control
if /i "%~1"=="exprestart" goto control
if /i "%~1"=="build" goto control
if /i "%~1"=="openlogs" goto control

start "" mshta.exe "%~dp0server_menu.hta"
exit /b

:control
set "MENU_ACTION=%~1"
shift /1
set "MENU_VALUE="

:collect_value
if "%~1"=="" goto run_control
if defined MENU_VALUE (
    set "MENU_VALUE=%MENU_VALUE%,%~1"
) else (
    set "MENU_VALUE=%~1"
)
shift /1
goto collect_value

:run_control
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\server_control.ps1" -Action "%MENU_ACTION%" -Value "%MENU_VALUE%"
exit /b %errorlevel%

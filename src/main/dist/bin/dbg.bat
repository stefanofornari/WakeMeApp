@echo off
rem
rem Runs another script of bin\ with the JVM waiting for a debugger:
rem
rem   dbg.bat wakemeapp.bat [options of the application]
rem
rem The JVM listens on localhost:1044 (DEBUG_PORT changes the port) and does not start until a debugger is
rem attached (DEBUG_SUSPEND=n starts it at once).
rem
setlocal

if "%~1"=="" (
    echo SYNTAX: dbg.bat ^<script of bin\^> [arguments]
    echo   e.g.: dbg.bat wakemeapp.bat --settings
    exit /b 1
)

if not defined DEBUG_PORT set "DEBUG_PORT=1044"
if not defined DEBUG_SUSPEND set "DEBUG_SUSPEND=y"

set "JAVA_OPTS=%JAVA_OPTS% -agentlib:jdwp=transport=dt_socket,server=y,suspend=%DEBUG_SUSPEND%,address=%DEBUG_PORT%"

set "SCRIPT=%~1"
shift
set "ARGS="
:next
if "%~1"=="" goto run
set ARGS=%ARGS% %1
shift
goto next

:run
rem the working directory is not changed: a relative path in an option is relative to where the user is
call "%~dp0%SCRIPT%" %ARGS%
exit /b %ERRORLEVEL%

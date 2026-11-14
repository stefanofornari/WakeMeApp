@echo off
rem
rem Starts WakeMeApp. Any argument is passed on to the application:
rem
rem   wakemeapp.cmd                              starts in the tray (set the calendar in the settings dialog)
rem   wakemeapp.cmd --calendar <link or file>    reads that calendar for this run
rem   wakemeapp.cmd --help                       lists the options
rem
rem JAVA_HOME is used if it points to a JDK/JRE (Java 21 or later), else the java in the path.
rem JAVA_OPTS adds options for the JVM; conf\wakemeapp.cmd, if it exists, is run before starting (it may set
rem JAVA_OPTS or JAVA_HOME).
rem
setlocal

for %%I in ("%~dp0..") do set "WAKEMEAPP_HOME=%%~fI"

if exist "%WAKEMEAPP_HOME%\conf\wakemeapp.cmd" call "%WAKEMEAPP_HOME%\conf\wakemeapp.cmd"

set "JAVACMD=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVACMD=%JAVA_HOME%\bin\java.exe"

rem JavaFX is on the module path, everything else on the class path (lib\* is every jar of lib).
rem The working directory is not changed.
"%JAVACMD%" "-Djava.util.logging.config.file=%WAKEMEAPP_HOME%\conf\logging.properties" ^
    --enable-native-access=ALL-UNNAMED ^
    --module-path "%WAKEMEAPP_HOME%\lib\modules" ^
    --add-modules=javafx.controls,javafx.fxml,javafx.web,javafx.media ^
    %JAVA_OPTS% ^
    -cp "%WAKEMEAPP_HOME%\lib\*" ^
    ste.wakemeapp.WakeMeApp %*
exit /b %ERRORLEVEL%

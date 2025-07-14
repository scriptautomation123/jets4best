@echo off
REM Temporarily set JAVA_HOME and update PATH for this session
set "JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.6.7-hotspot"
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo JAVA_HOME is set to: %JAVA_HOME%
echo PATH is now: %PATH%
cmd 
@echo off
REM AIE Util Runner Script for Windows

setlocal

set "SCRIPT_DIR=%~dp0"
set "BUNDLE_DIR=%SCRIPT_DIR%"

REM Find JAR file
for %%f in ("%BUNDLE_DIR%\aieutil-*.jar") do (
    set "JAR_FILE=%%f"
    goto :found_jar
)
echo Error: No aieutil-*.jar found in bundle directory
exit /b 1
:found_jar

set "JRE_DIR=%BUNDLE_DIR%\jre"
if exist "%JRE_DIR%\bin\java.exe" (
    set "JAVA_CMD=%JRE_DIR%\bin\java.exe"
    set "JAVA_TYPE=bundled"
) else if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" (
    set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
    set "JAVA_TYPE=JAVA_HOME"
) else (
    set "JAVA_CMD=java"
    set "JAVA_TYPE=system"
)

REM Get Java version
for /f "tokens=3 delims= " %%v in ('"%JAVA_CMD%" -version 2>&1 ^| findstr /i "version"') do (
    set "JAVA_VER=%%v"
)
REM Remove quotes and get major version
set "JAVA_VER=%JAVA_VER:\"=%"
for /f "tokens=1,2 delims=." %%a in ("%JAVA_VER%") do (
    set "JAVA_MAJOR=%%a"
    set "JAVA_MINOR=%%b"
)
if "%JAVA_MAJOR%"=="1" (
    set "JAVA_MAJOR=%JAVA_MINOR%"
)

if "%JAVA_TYPE%"=="bundled" (
    echo ✅ Using bundled JRE: %JRE_DIR%
) else if "%JAVA_TYPE%"=="JAVA_HOME" (
    echo ✅ Using JAVA_HOME: %JAVA_HOME%
) else (
    echo ⚠️  Using system Java
)

if "%JAVA_MAJOR%"=="8" (
    echo Java version: %JAVA_VER%
) else if "%JAVA_MAJOR%"=="21" (
    echo Java version: %JAVA_VER%
) else (
    echo ⚠️  Warning: Detected Java version is not 8 or 21!
    "%JAVA_CMD%" -version
)

set "JAVA_OPTS=-Dfile.encoding=UTF-8"
if exist "%BUNDLE_DIR%\log4j2.xml" (
    set "JAVA_OPTS=%JAVA_OPTS% -Dlog4j.configurationFile=%BUNDLE_DIR%\log4j2.xml"
)

"%JAVA_CMD%" %JAVA_OPTS% -jar "%JAR_FILE%" %*
endlocal 
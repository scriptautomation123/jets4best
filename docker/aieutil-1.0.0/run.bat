@echo off
REM AIE Util Runner Script for Windows
REM This script runs the AIE Util application using the bundled JRE and resources
REM
REM USAGE EXAMPLES:
REM 1. From bundle directory (relative path):
REM    run.bat --help
REM    run.bat testdata
REM    run.bat sql -u sa -p "" -d testdb -c "jdbc:h2:mem:testdb" -q "SELECT 1"
REM
REM 2. From any directory (full path):
REM    C:\path\to\aieutil-0.1.0\run.bat --help
REM    C:\path\to\aieutil-0.1.0\run.bat testdata
REM
REM 3. From any directory with JAR path:
REM    run.bat C:\path\to\aieutil-0.1.0-with-dependencies.jar --help
REM    run.bat C:\path\to\aieutil-0.1.0-with-dependencies.jar testdata
REM
REM 4. Direct JAR execution (anywhere):
REM    java -jar C:\path\to\aieutil-0.1.0-with-dependencies.jar --help
REM
REM The script automatically detects the bundle structure and uses the included JRE.

REM Get the directory where this script is located
set "SCRIPT_DIR=%~dp0"
set "BUNDLE_DIR=%SCRIPT_DIR%.."

REM Function to find the bundle directory from any location
:find_bundle_dir
set "jar_path=%~1"
for %%i in ("%jar_path%") do set "current_dir=%%~dpi"
set "current_dir=%current_dir:~0,-1%"

REM Check if we're in the bundle directory (has jre\ and resources\ folders)
if exist "%current_dir%\jre" if exist "%current_dir%\resources" (
    set "BUNDLE_ROOT=%current_dir%"
    goto :found_bundle
)

REM Check if we're in a subdirectory of the bundle
for %%i in ("%current_dir%") do set "parent_dir=%%~dpi"
set "parent_dir=%parent_dir:~0,-1%"
if exist "%parent_dir%\jre" if exist "%parent_dir%\resources" (
    set "BUNDLE_ROOT=%parent_dir%"
    goto :found_bundle
)

REM If not found, assume the JAR is standalone (no bundle structure)
set "BUNDLE_ROOT=%current_dir%"
goto :found_bundle

:found_bundle

REM Function to find JAR file dynamically
:find_jar_file
set "search_dir=%~1"
for %%f in ("%search_dir%\aieutil-*-with-dependencies.jar") do (
    set "found_jar=%%f"
    goto :found_jar
)
set "found_jar="
goto :eof

:found_jar

REM Determine the actual JAR file path
set "JAR_FILE="
if not "%~1"=="" if exist "%~1" (
    REM JAR path provided as argument
    set "JAR_FILE=%~1"
    call :find_bundle_dir "%JAR_FILE%"
) else (
    REM Look for JAR in bundle directory
    call :find_jar_file "%BUNDLE_DIR%"
    if not "%found_jar%"=="" if exist "%found_jar%" (
        set "JAR_FILE=%found_jar%"
        set "BUNDLE_ROOT=%BUNDLE_DIR%"
    ) else (
        echo Error: No aieutil-*-with-dependencies.jar found in bundle directory
        echo Usage: %0 [path\to\aieutil-*-with-dependencies.jar] [options...]
        echo        %0 [options...] (when run from bundle directory)
        exit /b 1
    )
)

REM Set up paths relative to bundle root
set "JRE_DIR=%BUNDLE_ROOT%\jre"
set "RESOURCES_DIR=%BUNDLE_ROOT%\resources"
set "DRIVERS_DIR=%BUNDLE_ROOT%\drivers"

REM Check if JAR exists
if not exist "%JAR_FILE%" (
    echo Error: JAR file not found at %JAR_FILE%
    exit /b 1
)

REM Set up Java options
set "JAVA_OPTS=-Dfile.encoding=UTF-8"

REM Add log4j config if resources directory exists
if exist "%RESOURCES_DIR%" if exist "%RESOURCES_DIR%\log4j2.xml" (
    set "JAVA_OPTS=%JAVA_OPTS% -Dlog4j.configurationFile=%RESOURCES_DIR%\log4j2.xml"
)

REM Add drivers to classpath if they exist
if exist "%DRIVERS_DIR%" (
    for %%f in ("%DRIVERS_DIR%\*.jar") do (
        set "JAVA_OPTS=%JAVA_OPTS% -cp %%f"
    )
)

REM Use bundled JRE if available, otherwise use system Java
if exist "%JRE_DIR%\bin\java.exe" (
    set "JAVA_CMD=%JRE_DIR%\bin\java.exe"
    echo ✅ Using bundled JRE: %JRE_DIR%
    echo    This ensures compatibility and no external Java dependency
) else (
    set "JAVA_CMD=java"
    echo ⚠️  Using system Java (bundled JRE not found)
    echo    For best results, ensure the bundle includes the jre\ directory
)

REM Determine arguments to pass to the JAR
if not "%~1"=="" if exist "%~1" (
    REM Script was called with JAR path, shift off the first argument
    set "JAR_ARGS="
    shift
    set "JAR_ARGS=%*"
) else (
    REM Script is in bundle, pass all arguments
    set "JAR_ARGS=%*"
)

REM Run the application
echo.
echo 🚀 Starting AIE Util...
echo    JAR: %JAR_FILE%
echo    Bundle Root: %BUNDLE_ROOT%
echo    Resources: %RESOURCES_DIR%
echo    Java Runtime: %JAVA_CMD%
echo.

"%JAVA_CMD%" %JAVA_OPTS% -jar "%JAR_FILE%" %JAR_ARGS% 
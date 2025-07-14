#!/bin/bash

# Exit on any error
set -e

PROJECT="/home/swapanc/code/multiroot/app"

# Function to print error and exit
error_exit() {
    echo "ERROR: $1" >&2
    exit 1
}

# Function to check if command exists
check_command() {
    if ! command -v $1 &> /dev/null; then
        error_exit "$1 is not installed or not in PATH"
    fi
}

# Function to wait for user input
wait_for_enter() {
    echo "Press Enter to continue..."
    read -r
}

# Check required commands
echo "Checking prerequisites..."
check_command mvn
check_command java
check_command unzip

# Check if project directory exists
if [ ! -d "$PROJECT" ]; then
    error_exit "Project directory $PROJECT does not exist"
fi

# Move to app root dir
echo "Moving to project directory: $PROJECT"
cd "$PROJECT" || error_exit "Failed to change to project directory"

# Check if pom.xml exists
if [ ! -f "pom.xml" ]; then
    error_exit "pom.xml not found in project directory"
fi

# Build and package the application
echo "Building application..."
if ! mvn clean package; then
    error_exit "Maven build failed"
fi

# Check if target directory exists
if [ ! -d "target" ]; then
    error_exit "target directory not found after build"
fi

# Navigate to target directory
cd target || error_exit "Failed to change to target directory"

# Check if zip file exists
if [ ! -f "aieutil-1.0.0.zip" ]; then
    error_exit "aieutil-1.0.0.zip not found in target directory"
fi

# Clean up any existing directory
echo "Cleaning up existing directory..."
rm -rf aieutil-1.0.0

# Extract the zip file
echo "Extracting application..."
if ! unzip aieutil-1.0.0.zip; then
    error_exit "Failed to extract aieutil-1.0.0.zip"
fi

# Check if extraction was successful
if [ ! -d "aieutil-1.0.0" ]; then
    error_exit "Failed to create aieutil-1.0.0 directory after extraction"
fi

# Navigate to the extracted directory
cd aieutil-1.0.0 || error_exit "Failed to change to aieutil-1.0.0 directory"

# Check if JAR file exists
if [ ! -f "aieutil-1.0.0.jar" ]; then
    error_exit "aieutil-1.0.0.jar not found in extracted directory"
fi

# Check if bundled JRE exists
if [ ! -d "jre" ]; then
    error_exit "Bundled JRE directory not found"
fi

# Check if bundled Java executable exists
if [ ! -f "jre/bin/java" ]; then
    error_exit "Bundled Java executable not found"
fi

echo "=== Testing VaultDemoCli ==="

# Test 1: User and DB only (simulate mode) with bundled JRE
echo "Test 1: User and DB only - simulate mode (bundled JRE)"
wait_for_enter
if ! jre/bin/java -Dfile.encoding=UTF-8 -Dlog4j.configurationFile=log4j2.xml -jar aieutil-1.0.0.jar --user MAV_T2T_APP --db ECICMP01_SVC01 --simulate; then
    echo "WARNING: Test 1 failed (this might be expected if user not in vaults.yaml)"
fi

echo -e "\n"

# Test 2: Full vault parameters (simulate mode) with bundled JRE
echo "Test 2: Full vault parameters - simulate mode (bundled JRE)"
wait_for_enter
if ! jre/bin/java -Dfile.encoding=UTF-8 -Dlog4j.configurationFile=log4j2.xml -jar aieutil-1.0.0.jar --user MAV_T2T_APP --vault-url vault-lle --db ECICMP01_SVC01 --role-id KIJJAD --secret-id 9080xlkvjkluwert --ait 98873 --simulate; then
    echo "WARNING: Test 2 failed (this might be expected)"
fi

# echo -e "\n"

# # Test 3: User and DB only (real mode) with bundled JRE
# echo "Test 3: User and DB only - real mode (bundled JRE)"
# if ! jre/bin/java -Dfile.encoding=UTF-8 -Dlog4j.configurationFile=log4j2.xml -jar aieutil-1.0.0.jar vault-demo --user MAV_T2T_APP --db ECICMP01_SVC01 --real; then
#     echo "WARNING: Test 3 failed (this might be expected if user not in vaults.yaml or Vault is not running)"
# fi

echo -e "\n"

# # Test 4: All vault parameters (real mode) with bundled JRE
# echo "Test 4: All vault parameters - real mode (bundled JRE)"
# if ! jre/bin/java -Dfile.encoding=UTF-8 -Dlog4j.configurationFile=log4j2.xml -jar aieutil-1.0.0.jar vault-demo --user MAV_T2T_APP --vault-url vault-lle --db ECICMP01_SVC01 --role-id KIJJAD --secret-id 9080xlkvjkluwert --ait 98873 --real; then
#     echo "WARNING: Test 4 failed (this might be expected if Vault is not running)"
# fi

echo -e "\n=== Testing Complete ==="
echo "Note: Some tests may fail if Vault is not running or users are not configured in vaults.yaml"
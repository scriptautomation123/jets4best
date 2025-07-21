#!/bin/bash
set -e

# === CONFIGURATION: Set the path to your bundle zip file here ===
BUNDLE_ZIP="aieutil-1.0.0.zip"  # Just the filename, not the path
BUNDLE_DIR="aieutil-1.0.0"

error_exit() {
    echo "ERROR: $1" >&2
    exit 1
}

check_command() {
    if ! command -v "$1" &> /dev/null; then
        error_exit "$1 is not installed or not in PATH"
    fi
}

# Prerequisite checks
check_command unzip
check_command bash
check_command java
check_command mvn

# Detect Java version (same logic as run.sh)
JAVA_VERSION=$(java -version 2>&1 | awk -F[\".] '/version/ {if ($2 == "1") print $3; else print $2}')

# Only build if target or bundle zip does not exist
if [ ! -d "target" ] || [ ! -f "target/$BUNDLE_ZIP" ]; then
    if [ "$JAVA_VERSION" = "8" ]; then
        echo "Detected Java 8. Running: mvn clean package"
        mvn clean package
    elif [ "$JAVA_VERSION" = "21" ]; then
        echo "Detected Java 21. Running: mvn clean package -Pjava21"
        mvn clean package -Pjava21
    else
        error_exit "Java version $JAVA_VERSION is not supported. Please use Java 8 or 21."
    fi
fi

# Determine script directory and project root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Move to target directory in project root
cd "$PROJECT_ROOT" || error_exit "Failed to cd into project root"
if [ ! -d "target" ]; then
    error_exit "target directory not found in $PROJECT_ROOT. Please build the project."
fi
cd target || error_exit "Failed to cd into target directory"

if [ ! -f "$BUNDLE_ZIP" ]; then
    error_exit "$BUNDLE_ZIP not found in target/. Please build the project or check the path."
fi

if [ -d "$BUNDLE_DIR" ]; then
    echo "Removing existing $BUNDLE_DIR..."
    rm -rf "$BUNDLE_DIR"
fi

echo "Unzipping $BUNDLE_ZIP..."
if ! unzip -q "$BUNDLE_ZIP" -d .; then
    error_exit "Failed to unzip $BUNDLE_ZIP"
fi

cd "$BUNDLE_DIR" || error_exit "Failed to cd into $BUNDLE_DIR"

# Try to source jdk.sh from scripts/ or parent of scripts/
if [ -f "$PROJECT_ROOT/scripts/jdk.sh" ]; then
    source "$PROJECT_ROOT/scripts/jdk.sh"
elif [ -f "$PROJECT_ROOT/jdk.sh" ]; then
    source "$PROJECT_ROOT/jdk.sh"
else
    error_exit "jdk.sh not found in scripts/ or project root. Please provide the Java version selector script."
fi

if [ ! -f "./run.sh" ]; then
    error_exit "run.sh not found in $BUNDLE_DIR"
fi

chmod u+x ./run.sh

./run.sh \
-d ECICMD03_svc01 \
-u MAV_T2T_APP \
"MAV_OWNER.TempTable_Onehadoop_proc" \
--input "in_schema:VARCHAR2:MAV_OWNER,in_src_table_nm:VARCHAR2:CUST_INSGHT_DLY,in_typ_cd:INTEGER:136,in_prant_grp:VARCHAR2:HADOOP_DML_ROLE" \
--output "p_outmsg:STRING" 
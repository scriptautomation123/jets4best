#!/bin/bash

# AIE Util Runner Script
# This script runs the AIE Util application using the bundled JRE and resources

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUNDLE_DIR="$(dirname "$SCRIPT_DIR")"

# Function to find the bundle directory from any location
find_bundle_dir() {
    local jar_path="$1"
    local current_dir="$(dirname "$jar_path")"
    
    # Check if we're in the bundle directory (has jre/ and resources/ folders)
    if [ -d "$current_dir/jre" ] && [ -d "$current_dir/resources" ]; then
        echo "$current_dir"
        return 0
    fi
    
    # Check if we're in a subdirectory of the bundle
    local parent_dir="$(dirname "$current_dir")"
    if [ -d "$parent_dir/jre" ] && [ -d "$parent_dir/resources" ]; then
        echo "$parent_dir"
        return 0
    fi
    
    # If not found, assume the JAR is standalone (no bundle structure)
    echo "$current_dir"
    return 1
}

# Function to find the JAR file dynamically
find_jar_file() {
    local dir="$1"
    # Look for JAR files with pattern: aieutil-*-with-dependencies.jar
    find "$dir" -maxdepth 1 -name "aieutil-*-with-dependencies.jar" 2>/dev/null | head -1
}

# Determine the actual JAR file path
JAR_FILE=""
if [ -n "$1" ] && [ -f "$1" ]; then
    # JAR path provided as argument
    JAR_FILE="$1"
    BUNDLE_ROOT="$(find_bundle_dir "$JAR_FILE")"
else
    # Look for JAR in bundle directory
    JAR_FILE="$(find_jar_file "$BUNDLE_DIR")"
    if [ -n "$JAR_FILE" ] && [ -f "$JAR_FILE" ]; then
        BUNDLE_ROOT="$BUNDLE_DIR"
    else
        echo "Error: No aieutil-*-with-dependencies.jar found in bundle directory"
        echo "Usage: $0 [path/to/aieutil-*-with-dependencies.jar] [options...]"
        echo "       $0 [options...] (when run from bundle directory)"
        exit 1
    fi
fi

# Set up paths relative to bundle root
JRE_DIR="$BUNDLE_ROOT/jre"
RESOURCES_DIR="$BUNDLE_ROOT/resources"
DRIVERS_DIR="$BUNDLE_ROOT/drivers"

# Check if JAR exists
if [ ! -f "$JAR_FILE" ]; then
    echo "Error: JAR file not found at $JAR_FILE"
    exit 1
fi

# Set up Java options
JAVA_OPTS="-Dfile.encoding=UTF-8"

# Add log4j config if resources directory exists
if [ -d "$RESOURCES_DIR" ] && [ -f "$RESOURCES_DIR/log4j2.xml" ]; then
    JAVA_OPTS="$JAVA_OPTS -Dlog4j.configurationFile=$RESOURCES_DIR/log4j2.xml"
fi

# Add drivers to classpath if they exist
if [ -d "$DRIVERS_DIR" ]; then
    for driver in "$DRIVERS_DIR"/*.jar; do
        if [ -f "$driver" ]; then
            JAVA_OPTS="$JAVA_OPTS -cp $driver"
        fi
    done
fi

# Use bundled JRE if available, otherwise use system Java
if [ -d "$JRE_DIR" ] && [ -x "$JRE_DIR/bin/java" ]; then
    JAVA_CMD="$JRE_DIR/bin/java"
    echo "Using bundled JRE: $JRE_DIR"
else
    JAVA_CMD="java"
    echo "Using system Java"
fi

# Determine arguments to pass to the JAR
if [ -n "$1" ] && [ -f "$1" ]; then
    # Script was called with JAR path, shift off the first argument
    shift
    JAR_ARGS="$@"
else
    # Script is in bundle, pass all arguments
    JAR_ARGS="$@"
fi

# Run the application
echo "Starting AIE Util..."
echo "JAR: $JAR_FILE"
echo "Bundle Root: $BUNDLE_ROOT"
echo "Resources: $RESOURCES_DIR"
echo "Java: $JAVA_CMD"

exec "$JAVA_CMD" $JAVA_OPTS -jar "$JAR_FILE" $JAR_ARGS 
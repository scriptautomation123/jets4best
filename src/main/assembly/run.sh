#!/bin/bash

set -e

# ${BASH_SOURCE[0]} =                                                     path to current script could be /home/path/script.txt
# BASH_SOURCE[0] is the script's filename                                 would return script.txt
# dirname "${BASH_SOURCE[0]}" Gdirectory containing the script            woud return /home/path
# cd "$(dirname "${BASH_SOURCE[0]}")"                                     would cd to where the script is locaterd
# BUNDLE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"              gets the full path from where the script is located

BUNDLE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JRE_DIR="$BUNDLE_DIR/jre"

# Find the JAR file in the bundle directory
find_jar_file() {
    local dir="$1"
    find "$dir" -maxdepth 1 -name "aieutil-*.jar" 2>/dev/null | head -1
}

# Setup Java environment (JRE, PATH, JAVA_HOME)
setup_java_environment(
    if [ -d "$JRE_DIR" ] && [ -x "$JRE_DIR/bin/java" ]; then
        export JAVA_HOME="${BUNDLE_DIR}/jre"
        export PATH="${JAVA_HOME}/bin:$PATH"
        echo "✅ Using JRE ${BUNDLE_DIR}/jre"
    fi
    
    export JAVA_VERSION=$(java -version 2>&1 | awk -F[\".] '/version/ {if ($2 == "1") print $3; else print $2}')
}

# Setup Java options
setup_java_options() {
    export JAVA_OPTS="-Dfile.encoding=UTF-8"
    if [ -f "$BUNDLE_DIR/log4j2.xml" ]; then
        JAVA_OPTS="$JAVA_OPTS -Dlog4j.configurationFile=$BUNDLE_DIR/log4j2.xml"
    fi
}

# Validate JAR file exists
validate_jar_file() {
    JAR_FILE="$(find_jar_file "$BUNDLE_DIR")"
    if [ ! -f "$JAR_FILE" ]; then
        echo "Error: No -*.jar found in bundle directory ${BUNDLE_DIR}"
        exit 1
    fi
}

# Validate vault config parameter and shift arguments
validate_vault_config() {
    
    if [ "$1" != "--vault-config" ]; then
        echo "❌ Error: --vault-config /path/to/vault.yaml  parameter is required as first argument"
        exit 1
    fi

    local vault_config="$2"
    if [ ! -f "$vault_config" ]; then
        echo "❌ Error: Vault config file not found: $vault_config"
        exit 1
    fi
    
    # Add vault config to Java options
    JAVA_OPTS="$JAVA_OPTS -Dvault.config=$vault_config"
    
    # Remove --vault-config and path from arguments
    shift 2
}


# Run the JAR file with given arguments
run_jar() {
    # Default: run as CLI with exec-proc if no subcommand specified
    if [[ "$1" =~ ^(exec-proc|exec-sql|exec-vault)$ ]]; then
        # User explicitly specified a subcommand
        exec java $JAVA_OPTS -jar "$JAR_FILE" "$@"
    else
        # No subcommand specified, default to exec-proc
        exec java $JAVA_OPTS -jar "$JAR_FILE" exec-proc "$@"
    fi
}


# Main execution
main() {
    # Initialize environment
    validate_vault_config "$@"
    validate_jar_file
    setup_java_environment
    setup_java_options
    
    # Run the jar with remaining arguments
    run_jar "$@"
}

# Run main function
main "$@" 
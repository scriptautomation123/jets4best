#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUNDLE_DIR="$SCRIPT_DIR"

find_jar_file() {
    local dir="$1"
    find "$dir" -maxdepth 1 -name "aieutil-*.jar" 2>/dev/null | head -1
}

JAR_FILE="$(find_jar_file "$BUNDLE_DIR")"
if [ ! -f "$JAR_FILE" ]; then
    echo "Error: No aieutil-*.jar found in bundle directory"
    exit 1
fi

JRE_DIR="$BUNDLE_DIR/jre"
if [ -d "$JRE_DIR" ] && [ -x "$JRE_DIR/bin/java" ]; then
    JAVA_CMD="$JRE_DIR/bin/java"
    JAVA_TYPE="bundled"
else
    if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
        JAVA_CMD="$JAVA_HOME/bin/java"
        JAVA_TYPE="JAVA_HOME"
    else
        JAVA_CMD="java"
        JAVA_TYPE="system"
    fi
fi

JAVA_VERSION=$("$JAVA_CMD" -version 2>&1 | awk -F[\".] '/version/ {if ($2 == "1") print $3; else print $2}')
if [ "$JAVA_TYPE" = "bundled" ]; then
    echo "✅ Using bundled JRE: $JRE_DIR"
elif [ "$JAVA_TYPE" = "JAVA_HOME" ]; then
    echo "✅ Using JAVA_HOME: $JAVA_HOME"
else
    echo "⚠️  Using system Java"
fi

if [ "$JAVA_VERSION" = "8" ] || [ "$JAVA_VERSION" = "21" ]; then
    echo "Java version: $($JAVA_CMD -version 2>&1 | head -n 1)"
else
    echo "⚠️  Warning: Detected Java version is not 8 or 21! (JAVA_VERSION=$JAVA_VERSION)"
    $JAVA_CMD -version
fi
JAVA_OPTS="-Dfile.encoding=UTF-8 -Dsun.net.inetaddr.negative.ttl=1"
if [ -f "$BUNDLE_DIR/log4j2.xml" ]; then
    JAVA_OPTS="$JAVA_OPTS -Dlog4j.configurationFile=$BUNDLE_DIR/log4j2.xml"
fi

print_config() {
    echo "Bundle Root: $BUNDLE_DIR"
echo "JAR: $JAR_FILE"
    echo "JRE: $JRE_DIR"
echo "Java: $JAVA_CMD"
    echo "Log4j2: $BUNDLE_DIR/log4j2.xml"
    echo "Application config: $BUNDLE_DIR/application.yaml"
    echo "Vault config: $BUNDLE_DIR/vaults.yaml"
}

run_cli() {
    shift # Remove 'cli' from args
    exec "$JAVA_CMD" $JAVA_OPTS -jar "$JAR_FILE" "$@"
}

run_demo() {
    shift # Remove 'demo' from args
    case "$1" in
        vault-demo-urls)
            exec "$JAVA_CMD" $JAVA_OPTS -jar "$JAR_FILE" vault-demo --vault-url http://localhost:8200 --role-id myrole --secret-id mysecret --db ORCL --ait DEV --user scott
            ;;
        vault-demo-real)
            exec "$JAVA_CMD" $JAVA_OPTS -jar "$JAR_FILE" vault-demo --from-config --db ORCL --user scott --real
            ;;
        vault-demo-direct)
            exec "$JAVA_CMD" $JAVA_OPTS -jar "$JAR_FILE" vault-demo --vault-url http://localhost:8200 --role-id myrole --secret-id mysecret --db ORCL --ait DEV --user scott --real
            ;;
        cli-help)
            exec "$JAVA_CMD" $JAVA_OPTS -jar "$JAR_FILE" --help
            ;;
        cli-print-config)
            exec "$JAVA_CMD" $JAVA_OPTS -jar "$JAR_FILE" --print-config
            ;;
        cli-sample-connect)
            exec "$JAVA_CMD" $JAVA_OPTS -jar "$JAR_FILE" --sample-connect
            ;;
        *)
            echo "Unknown or missing demo: $1"
            echo "Available demos: vault-demo-urls, vault-demo-real, vault-demo-direct, cli-help, cli-print-config, cli-sample-connect"
            exit 1
            ;;
    esac
}

case "$1" in
    print-config)
        print_config
        ;;
    cli)
        run_cli "$@"
        ;;
    demo)
        run_demo "$@"
        ;;
    ""|help|--help|-h)
        echo "Usage: $0 [print-config|cli <args>|demo <demo-name>]"
        echo "  print-config         Print bundle and config info"
        echo "  cli <args>           Run the main CLI with arguments"
        echo "  demo <demo-name>     Run a named demo (vault-demo-urls, vault-demo-real, vault-demo-direct, cli-help, cli-print-config, cli-sample-connect)"
        ;;
    *)
        # Default: run as CLI
        exec "$JAVA_CMD" $JAVA_OPTS -jar "$JAR_FILE" "$@"
        ;;
esac 
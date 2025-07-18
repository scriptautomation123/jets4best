#!/bin/sh

# Set these to your actual JDK install locations
JAVA8_HOME="/home/swapanc/.sdkman/candidates/java/8.0.452-tem"
JAVA21_HOME="/home/swapanc/.sdkman/candidates/java/21.0.7-tem"

echo "Select Java version:"
echo "1) Java 8"
echo "2) Java 21"
echo -n "Enter choice [1-2]: "
read choice

case "$choice" in
    1)
        export JAVA_HOME="$JAVA8_HOME"
        export PATH="$JAVA_HOME/bin:$PATH"
        echo "JAVA_HOME set to $JAVA_HOME"
        ;;
    2)
        export JAVA_HOME="$JAVA21_HOME"
        export PATH="$JAVA_HOME/bin:$PATH"
        echo "JAVA_HOME set to $JAVA_HOME"
        ;;
    *)
        echo "Invalid choice."
        return 1 2>/dev/null || exit 1
        ;;
esac

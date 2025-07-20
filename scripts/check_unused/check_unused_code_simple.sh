#!/bin/bash

# Simple Java Unused Code Detector for Maven Projects
# Focuses on imports, classes, and Maven dependencies

set -euo pipefail

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

PROJECT_ROOT="${1:-.}"
TEMP_DIR="/tmp/unused_check_$$"

# Cleanup
cleanup() {
    rm -rf "${TEMP_DIR}"
}
trap cleanup EXIT

mkdir -p "${TEMP_DIR}"

echo -e "${BLUE}=== Simple Java Unused Code Detector ===${NC}"
echo -e "${BLUE}Project: ${PROJECT_ROOT}${NC}"
echo ""

# 1. Check Maven dependencies
check_maven_deps() {
    echo -e "${YELLOW}=== Checking Maven Dependencies ===${NC}"
    
    if [[ ! -f "${PROJECT_ROOT}/pom.xml" ]]; then
        echo -e "${YELLOW}No pom.xml found${NC}"
        return
    fi
    
    if ! command -v mvn >/dev/null 2>&1; then
        echo -e "${YELLOW}Maven not found in PATH${NC}"
        return
    fi
    
    echo "Running Maven dependency analysis..."
    cd "${PROJECT_ROOT}"
    
    # Use Maven's built-in dependency analysis
    mvn dependency:analyze > "${TEMP_DIR}/maven_analysis.txt" 2>&1 || true
    
    # Extract warnings about unused dependencies
    if grep -q "Unused declared dependencies" "${TEMP_DIR}/maven_analysis.txt"; then
        echo -e "${RED}Unused Dependencies:${NC}"
        grep -A 10 "Unused declared dependencies" "${TEMP_DIR}/maven_analysis.txt" | head -20
    else
        echo -e "${GREEN}✓ No unused dependencies found${NC}"
    fi
    
    # Extract warnings about missing dependencies
    if grep -q "Used undeclared dependencies" "${TEMP_DIR}/maven_analysis.txt"; then
        echo -e "${YELLOW}Missing Dependencies:${NC}"
        grep -A 10 "Used undeclared dependencies" "${TEMP_DIR}/maven_analysis.txt" | head -20
    fi
    
    echo ""
}

# 2. Check unused imports
check_unused_imports() {
    echo -e "${YELLOW}=== Checking Unused Imports ===${NC}"
    
    # Find all Java files
    find "${PROJECT_ROOT}" -name "*.java" -type f > "${TEMP_DIR}/java_files.txt"
    
    local java_count=$(wc -l < "${TEMP_DIR}/java_files.txt")
    echo "Found ${java_count} Java files"
    
    # Extract imports and check usage
    local unused_count=0
    
    while IFS= read -r java_file; do
        # Get imports
        local imports=$(grep "^import " "$java_file" | sed 's/import //' | sed 's/;//' | sort -u)
        
        if [[ -n "$imports" ]]; then
            # Read file content without imports
            local content=$(grep -v "^import " "$java_file" | grep -v "^package ")
            
            # Check each import
            while IFS= read -r import_line; do
                if [[ -n "$import_line" ]]; then
                    # Extract class name from import
                    local class_name=$(echo "$import_line" | sed 's/.*\.//')
                    
                    # Check if class is used in content
                    if ! echo "$content" | grep -q "$class_name"; then
                        echo -e "${RED}${java_file}: unused import ${import_line}${NC}"
                        ((unused_count++))
                    fi
                fi
            done <<< "$imports"
        fi
    done < "${TEMP_DIR}/java_files.txt"
    
    if [[ $unused_count -eq 0 ]]; then
        echo -e "${GREEN}✓ No unused imports found${NC}"
    else
        echo -e "${RED}Found ${unused_count} potentially unused imports${NC}"
    fi
    
    echo ""
}

# 3. Check for empty classes
check_empty_classes() {
    echo -e "${YELLOW}=== Checking Empty Classes ===${NC}"
    
    local empty_count=0
    
    while IFS= read -r java_file; do
        # Check if class has only basic structure
        local content=$(cat "$java_file")
        local class_count=$(echo "$content" | grep -c "class " || echo "0")
        local method_count=$(echo "$content" | grep -c "public.*(" || echo "0")
        local field_count=$(echo "$content" | grep -c "private.*;" || echo "0")
        
        # If class has no methods and no fields, it might be empty
        if [[ $class_count -gt 0 && $method_count -eq 0 && $field_count -eq 0 ]]; then
            echo -e "${YELLOW}${java_file}: potentially empty class${NC}"
            ((empty_count++))
        fi
    done < "${TEMP_DIR}/java_files.txt"
    
    if [[ $empty_count -eq 0 ]]; then
        echo -e "${GREEN}✓ No empty classes found${NC}"
    else
        echo -e "${YELLOW}Found ${empty_count} potentially empty classes${NC}"
    fi
    
    echo ""
}

# 4. Check for TODO/FIXME comments
check_todos() {
    echo -e "${YELLOW}=== Checking TODO/FIXME Comments ===${NC}"
    
    local todo_count=0
    
    while IFS= read -r java_file; do
        local todos=$(grep -n "TODO\|FIXME\|XXX" "$java_file" || true)
        if [[ -n "$todos" ]]; then
            echo -e "${YELLOW}${java_file}:${NC}"
            echo "$todos" | sed 's/^/  /'
            todo_count=$((todo_count + $(echo "$todos" | wc -l)))
        fi
    done < "${TEMP_DIR}/java_files.txt"
    
    if [[ $todo_count -eq 0 ]]; then
        echo -e "${GREEN}✓ No TODO/FIXME comments found${NC}"
    else
        echo -e "${YELLOW}Found ${todo_count} TODO/FIXME comments${NC}"
    fi
    
    echo ""
}

# 5. Check for dead code patterns
check_dead_code() {
    echo -e "${YELLOW}=== Checking Dead Code Patterns ===${NC}"
    
    local dead_count=0
    
    while IFS= read -r java_file; do
        # Check for unreachable code after return/throw
        local unreachable=$(grep -n -A 1 "return\|throw" "$java_file" | grep -v "return\|throw" | grep -v "^--" || true)
        if [[ -n "$unreachable" ]]; then
            echo -e "${YELLOW}${java_file}: potential unreachable code${NC}"
            ((dead_count++))
        fi
        
        # Check for empty catch blocks
        local empty_catch=$(grep -n "catch.*{" "$java_file" | while read line; do
            local line_num=$(echo "$line" | cut -d: -f1)
            local next_line=$((line_num + 1))
            local next_content=$(sed -n "${next_line}p" "$java_file")
            if [[ "$next_content" =~ ^[[:space:]]*}$ ]]; then
                echo "$line"
            fi
        done || true)
        
        if [[ -n "$empty_catch" ]]; then
            echo -e "${YELLOW}${java_file}: empty catch blocks${NC}"
            ((dead_count++))
        fi
    done < "${TEMP_DIR}/java_files.txt"
    
    if [[ $dead_count -eq 0 ]]; then
        echo -e "${GREEN}✓ No obvious dead code patterns found${NC}"
    else
        echo -e "${YELLOW}Found ${dead_count} potential dead code issues${NC}"
    fi
    
    echo ""
}

# Main execution
main() {
    check_maven_deps
    check_unused_imports
    check_empty_classes
    check_todos
    check_dead_code
    
    echo -e "${BLUE}=== Analysis Complete ===${NC}"
    echo -e "${YELLOW}Note: This is a simple analysis. Review findings manually before removing code.${NC}"
}

main "$@" 
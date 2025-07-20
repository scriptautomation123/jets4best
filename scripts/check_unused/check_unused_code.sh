#!/bin/bash

# Java Codebase Unused Code Detector
# Checks for unused classes, methods, imports, and Maven dependencies

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
PROJECT_ROOT="${1:-.}"
TEMP_DIR="/tmp/unused_code_check_$$"
REPORT_FILE="${TEMP_DIR}/unused_code_report.txt"
JAVA_FILES_DIR="${TEMP_DIR}/java_files"
CLASS_FILES_DIR="${TEMP_DIR}/class_files"

# Create temporary directories
mkdir -p "${TEMP_DIR}" "${JAVA_FILES_DIR}" "${CLASS_FILES_DIR}"

# Cleanup function
cleanup() {
    echo -e "${BLUE}Cleaning up temporary files...${NC}"
    rm -rf "${TEMP_DIR}"
}

# Set trap to cleanup on exit
trap cleanup EXIT

echo -e "${BLUE}=== Java Codebase Unused Code Detector ===${NC}"
echo -e "${BLUE}Project Root: ${PROJECT_ROOT}${NC}"
echo -e "${BLUE}Temporary Directory: ${TEMP_DIR}${NC}"
echo ""

# Function to print section headers
print_section() {
    echo -e "${YELLOW}=== $1 ===${NC}"
    echo ""
}

# Function to print results
print_result() {
    local status=$1
    local message=$2
    if [[ "$status" == "SUCCESS" ]]; then
        echo -e "${GREEN}✓ $message${NC}"
    elif [[ "$status" == "WARNING" ]]; then
        echo -e "${YELLOW}⚠ $message${NC}"
    else
        echo -e "${RED}✗ $message${NC}"
    fi
}

# 1. Check for unused Maven dependencies
check_unused_dependencies() {
    print_section "Checking Unused Maven Dependencies"
    
    if [[ ! -f "${PROJECT_ROOT}/pom.xml" ]]; then
        print_result "WARNING" "No pom.xml found in project root"
        return
    fi
    
    # Check if dependency:analyze plugin is available
    if command -v mvn >/dev/null 2>&1; then
        echo "Running Maven dependency analysis..."
        
        # Create a temporary pom.xml with the dependency:analyze plugin
        cat > "${TEMP_DIR}/analyze_pom.xml" << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>temp</groupId>
    <artifactId>dependency-analyzer</artifactId>
    <version>1.0.0</version>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-dependency-plugin</artifactId>
                <version>3.6.0</version>
                <executions>
                    <execution>
                        <id>analyze</id>
                        <goals>
                            <goal>analyze</goal>
                        </goals>
                        <configuration>
                            <failOnWarning>false</failOnWarning>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
EOF
        
        # Run dependency analysis
        cd "${PROJECT_ROOT}"
        mvn -f "${TEMP_DIR}/analyze_pom.xml" dependency:analyze > "${TEMP_DIR}/dependency_analysis.txt" 2>&1 || true
        
        # Extract unused dependencies
        grep -E "(WARNING|Used undeclared dependencies|Unused declared dependencies)" "${TEMP_DIR}/dependency_analysis.txt" > "${TEMP_DIR}/unused_deps.txt" || true
        
        if [[ -s "${TEMP_DIR}/unused_deps.txt" ]]; then
            echo -e "${RED}Unused Dependencies Found:${NC}"
            cat "${TEMP_DIR}/unused_deps.txt"
        else
            print_result "SUCCESS" "No unused dependencies detected"
        fi
    else
        print_result "WARNING" "Maven not found in PATH"
    fi
    echo ""
}

# 2. Find all Java files and extract class/method information
extract_java_info() {
    print_section "Extracting Java Class and Method Information"
    
    # Find all Java files
    find "${PROJECT_ROOT}" -name "*.java" -type f > "${TEMP_DIR}/java_files.txt"
    
    local java_count=$(wc -l < "${TEMP_DIR}/java_files.txt")
    echo "Found ${java_count} Java files"
    
    # Extract class names
    echo "Extracting class names..."
    while IFS= read -r java_file; do
        # Extract class names (simple regex - may need refinement)
        grep -E "^(public |private |protected )?class [A-Za-z0-9_]+" "$java_file" | \
        sed 's/.*class \([A-Za-z0-9_]*\).*/\1/' >> "${TEMP_DIR}/all_classes.txt" || true
    done < "${TEMP_DIR}/java_files.txt"
    
    # Extract method names
    echo "Extracting method names..."
    while IFS= read -r java_file; do
        # Extract method names (simple regex - may need refinement)
        grep -E "^(public |private |protected |static )?[A-Za-z0-9_<>\[\]]+ [a-z][A-Za-z0-9_]*\s*\(" "$java_file" | \
        sed 's/.*[A-Za-z0-9_<>\[\]]\+ \([a-z][A-Za-z0-9_]*\)\s*(.*/\1/' >> "${TEMP_DIR}/all_methods.txt" || true
    done < "${TEMP_DIR}/java_files.txt"
    
    # Extract imports
    echo "Extracting imports..."
    while IFS= read -r java_file; do
        grep "^import " "$java_file" | \
        sed 's/import //' | sed 's/;//' >> "${TEMP_DIR}/all_imports.txt" || true
    done < "${TEMP_DIR}/java_files.txt"
    
    print_result "SUCCESS" "Java information extraction completed"
    echo ""
}

# 3. Check for unused imports
check_unused_imports() {
    print_section "Checking Unused Imports"
    
    if [[ ! -f "${TEMP_DIR}/all_imports.txt" ]]; then
        print_result "WARNING" "No import information available"
        return
    fi
    
    # Create a simple import usage checker
    cat > "${TEMP_DIR}/check_imports.py" << 'EOF'
#!/usr/bin/env python3
import os
import re
import sys

def extract_class_from_import(import_statement):
    """Extract class name from import statement"""
    parts = import_statement.split('.')
    return parts[-1] if parts else ""

def check_import_usage():
    java_files = []
    imports = {}
    
    # Read Java files
    with open('/tmp/unused_code_check_$$/java_files.txt', 'r') as f:
        java_files = [line.strip() for line in f if line.strip()]
    
    # Read imports
    with open('/tmp/unused_code_check_$$/all_imports.txt', 'r') as f:
        for line in f:
            line = line.strip()
            if line:
                class_name = extract_class_from_import(line)
                imports[class_name] = line
    
    unused_imports = []
    
    for java_file in java_files:
        if not os.path.exists(java_file):
            continue
            
        with open(java_file, 'r', encoding='utf-8', errors='ignore') as f:
            content = f.read()
        
        # Remove import statements from content for checking
        content_without_imports = re.sub(r'^import .*$', '', content, flags=re.MULTILINE)
        
        # Check each import
        for class_name, import_statement in imports.items():
            if class_name and class_name not in content_without_imports:
                unused_imports.append(f"{java_file}: {import_statement}")
    
    return unused_imports

if __name__ == "__main__":
    unused = check_import_usage()
    for imp in unused:
        print(imp)
EOF
    
    chmod +x "${TEMP_DIR}/check_imports.py"
    
    # Run import checker
    python3 "${TEMP_DIR}/check_imports.py" > "${TEMP_DIR}/unused_imports.txt" 2>/dev/null || true
    
    if [[ -s "${TEMP_DIR}/unused_imports.txt" ]]; then
        echo -e "${RED}Unused Imports Found:${NC}"
        head -20 "${TEMP_DIR}/unused_imports.txt"
        local unused_count=$(wc -l < "${TEMP_DIR}/unused_imports.txt")
        if [[ $unused_count -gt 20 ]]; then
            echo "... and $((unused_count - 20)) more"
        fi
    else
        print_result "SUCCESS" "No unused imports detected"
    fi
    echo ""
}

# 4. Check for unused classes
check_unused_classes() {
    print_section "Checking Unused Classes"
    
    if [[ ! -f "${TEMP_DIR}/all_classes.txt" ]]; then
        print_result "WARNING" "No class information available"
        return
    fi
    
    # Create a simple class usage checker
    cat > "${TEMP_DIR}/check_classes.py" << 'EOF'
#!/usr/bin/env python3
import os
import re

def check_class_usage():
    java_files = []
    classes = set()
    
    # Read Java files
    with open('/tmp/unused_code_check_$$/java_files.txt', 'r') as f:
        java_files = [line.strip() for line in f if line.strip()]
    
    # Read classes
    with open('/tmp/unused_code_check_$$/all_classes.txt', 'r') as f:
        classes = {line.strip() for line in f if line.strip()}
    
    unused_classes = []
    
    for java_file in java_files:
        if not os.path.exists(java_file):
            continue
            
        with open(java_file, 'r', encoding='utf-8', errors='ignore') as f:
            content = f.read()
        
        # Check each class
        for class_name in classes:
            if class_name and class_name not in content:
                unused_classes.append(f"{java_file}: {class_name}")
    
    return unused_classes

if __name__ == "__main__":
    unused = check_class_usage()
    for cls in unused:
        print(cls)
EOF
    
    chmod +x "${TEMP_DIR}/check_classes.py"
    
    # Run class checker
    python3 "${TEMP_DIR}/check_classes.py" > "${TEMP_DIR}/unused_classes.txt" 2>/dev/null || true
    
    if [[ -s "${TEMP_DIR}/unused_classes.txt" ]]; then
        echo -e "${RED}Potentially Unused Classes Found:${NC}"
        head -20 "${TEMP_DIR}/unused_classes.txt"
        local unused_count=$(wc -l < "${TEMP_DIR}/unused_classes.txt")
        if [[ $unused_count -gt 20 ]]; then
            echo "... and $((unused_count - 20)) more"
        fi
    else
        print_result "SUCCESS" "No unused classes detected"
    fi
    echo ""
}

# 5. Check for unused methods
check_unused_methods() {
    print_section "Checking Unused Methods"
    
    if [[ ! -f "${TEMP_DIR}/all_methods.txt" ]]; then
        print_result "WARNING" "No method information available"
        return
    fi
    
    # Create a simple method usage checker
    cat > "${TEMP_DIR}/check_methods.py" << 'EOF'
#!/usr/bin/env python3
import os
import re

def check_method_usage():
    java_files = []
    methods = set()
    
    # Read Java files
    with open('/tmp/unused_code_check_$$/java_files.txt', 'r') as f:
        java_files = [line.strip() for line in f if line.strip()]
    
    # Read methods
    with open('/tmp/unused_code_check_$$/all_methods.txt', 'r') as f:
        methods = {line.strip() for line in f if line.strip()}
    
    unused_methods = []
    
    for java_file in java_files:
        if not os.path.exists(java_file):
            continue
            
        with open(java_file, 'r', encoding='utf-8', errors='ignore') as f:
            content = f.read()
        
        # Check each method
        for method_name in methods:
            if method_name and method_name not in content:
                unused_methods.append(f"{java_file}: {method_name}")
    
    return unused_methods

if __name__ == "__main__":
    unused = check_method_usage()
    for method in unused:
        print(method)
EOF
    
    chmod +x "${TEMP_DIR}/check_methods.py"
    
    # Run method checker
    python3 "${TEMP_DIR}/check_methods.py" > "${TEMP_DIR}/unused_methods.txt" 2>/dev/null || true
    
    if [[ -s "${TEMP_DIR}/unused_methods.txt" ]]; then
        echo -e "${RED}Potentially Unused Methods Found:${NC}"
        head -20 "${TEMP_DIR}/unused_methods.txt"
        local unused_count=$(wc -l < "${TEMP_DIR}/unused_methods.txt")
        if [[ $unused_count -gt 20 ]]; then
            echo "... and $((unused_count - 20)) more"
        fi
    else
        print_result "SUCCESS" "No unused methods detected"
    fi
    echo ""
}

# 6. Generate comprehensive report
generate_report() {
    print_section "Generating Comprehensive Report"
    
    {
        echo "Java Codebase Unused Code Analysis Report"
        echo "=========================================="
        echo "Generated: $(date)"
        echo "Project Root: ${PROJECT_ROOT}"
        echo ""
        
        echo "SUMMARY"
        echo "-------"
        
        # Count findings
        local unused_deps=$(wc -l < "${TEMP_DIR}/unused_deps.txt" 2>/dev/null || echo "0")
        local unused_imports=$(wc -l < "${TEMP_DIR}/unused_imports.txt" 2>/dev/null || echo "0")
        local unused_classes=$(wc -l < "${TEMP_DIR}/unused_classes.txt" 2>/dev/null || echo "0")
        local unused_methods=$(wc -l < "${TEMP_DIR}/unused_methods.txt" 2>/dev/null || echo "0")
        
        echo "Unused Dependencies: ${unused_deps}"
        echo "Unused Imports: ${unused_imports}"
        echo "Unused Classes: ${unused_classes}"
        echo "Unused Methods: ${unused_methods}"
        echo ""
        
        echo "DETAILED FINDINGS"
        echo "================="
        echo ""
        
        if [[ -s "${TEMP_DIR}/unused_deps.txt" ]]; then
            echo "UNUSED DEPENDENCIES:"
            echo "-------------------"
            cat "${TEMP_DIR}/unused_deps.txt"
            echo ""
        fi
        
        if [[ -s "${TEMP_DIR}/unused_imports.txt" ]]; then
            echo "UNUSED IMPORTS:"
            echo "---------------"
            cat "${TEMP_DIR}/unused_imports.txt"
            echo ""
        fi
        
        if [[ -s "${TEMP_DIR}/unused_classes.txt" ]]; then
            echo "UNUSED CLASSES:"
            echo "---------------"
            cat "${TEMP_DIR}/unused_classes.txt"
            echo ""
        fi
        
        if [[ -s "${TEMP_DIR}/unused_methods.txt" ]]; then
            echo "UNUSED METHODS:"
            echo "---------------"
            cat "${TEMP_DIR}/unused_methods.txt"
            echo ""
        fi
        
    } > "${REPORT_FILE}"
    
    echo -e "${GREEN}Report generated: ${REPORT_FILE}${NC}"
    echo ""
}

# 7. Main execution
main() {
    echo -e "${BLUE}Starting analysis...${NC}"
    echo ""
    
    check_unused_dependencies
    extract_java_info
    check_unused_imports
    check_unused_classes
    check_unused_methods
    generate_report
    
    print_section "Analysis Complete"
    echo -e "${GREEN}✓ Analysis completed successfully${NC}"
    echo -e "${BLUE}📄 Full report: ${REPORT_FILE}${NC}"
    echo ""
    echo -e "${YELLOW}Note: This analysis uses simple pattern matching and may have false positives.${NC}"
    echo -e "${YELLOW}Please review findings manually before removing any code.${NC}"
}

# Run main function
main "$@" 
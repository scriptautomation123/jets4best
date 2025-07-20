#!/bin/bash

# Modern Code Quality Plugin Adder
# Adds PMD and Google Code Style to pom.xml with modern rule names

set -euo pipefail

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Global variables (will be set in main function)
PROJECT_ROOT=""
POM_FILE=""
BACKUP_FILE=""

# Function to add plugins
add_plugins() {
    echo -e "${YELLOW}Adding modern code quality plugins...${NC}"
    
    # Find the closing </plugins> tag in the build section
    local plugins_end_line=$(grep -n "</plugins>" "${POM_FILE}" | head -1 | cut -d: -f1)
    
    if [[ -z "$plugins_end_line" ]]; then
        echo -e "${RED}Error: Could not find </plugins> tag${NC}"
        return 1
    fi
    
    # Create temporary file
    local temp_file=$(mktemp)
    
    # Copy content before plugins end
    head -n $((plugins_end_line - 1)) "${POM_FILE}" > "${temp_file}"
    
    # Add plugins with modern PMD configuration
    cat >> "${temp_file}" << 'EOF'
            <!-- PMD Plugin for Code Quality (Modern Rules) -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-pmd-plugin</artifactId>
                <version>3.21.2</version>
                <configuration>
                    <linkXRef>false</linkXRef>
                    <minimumTokens>100</minimumTokens>
                    <targetJdk>8</targetJdk>
                    <analysisCache>true</analysisCache>
                    <analysisCacheLocation>${project.build.directory}/pmd/pmd.cache</analysisCacheLocation>
                    <printFailingErrors>true</printFailingErrors>
                    <failOnViolation>false</failOnViolation>
                    <skipEmptyReport>false</skipEmptyReport>
                    <includeTests>true</includeTests>
                    <rulesets>
                        <ruleset>category/java/errorprone.xml</ruleset>
                        <ruleset>category/java/bestpractices.xml</ruleset>
                        <ruleset>category/java/codestyle.xml</ruleset>
                        <ruleset>category/java/design.xml</ruleset>
                        <ruleset>category/java/documentation.xml</ruleset>
                        <ruleset>category/java/multithreading.xml</ruleset>
                        <ruleset>category/java/performance.xml</ruleset>
                        <ruleset>category/java/security.xml</ruleset>
                    </rulesets>
                </configuration>
                <executions>
                    <execution>
                        <id>pmd-check</id>
                        <goals>
                            <goal>check</goal>
                        </goals>
                        <phase>verify</phase>
                    </execution>
                    <execution>
                        <id>pmd-cpd-check</id>
                        <goals>
                            <goal>cpd-check</goal>
                        </goals>
                        <phase>verify</phase>
                        <configuration>
                            <minimumTokenCount>100</minimumTokenCount>
                            <language>java</language>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
            <!-- Spotless Plugin for Google Code Style -->
            <plugin>
                <groupId>com.diffplug.spotless</groupId>
                <artifactId>spotless-maven-plugin</artifactId>
                <version>2.43.0</version>
                <configuration>
                    <java>
                        <googleJavaFormat>
                            <version>1.19.2</version>
                            <style>GOOGLE</style>
                        </googleJavaFormat>
                        <removeUnusedImports/>
                        <importOrder>
                            <order>java,javax,org,com,</order>
                        </importOrder>
                    </java>
                </configuration>
                <executions>
                    <execution>
                        <goals>
                            <goal>check</goal>
                        </goals>
                        <phase>verify</phase>
                    </execution>
                </executions>
            </plugin>
EOF
    
    # Copy content after plugins end
    tail -n +$((plugins_end_line)) "${POM_FILE}" >> "${temp_file}"
    
    # Replace original file
    mv "${temp_file}" "${POM_FILE}"
    
    echo -e "${GREEN}✓ Modern code quality plugins added${NC}"
}

# Function to restore backup
restore_backup() {
    echo -e "${YELLOW}Restoring backup...${NC}"
    if [[ -f "${BACKUP_FILE}" ]]; then
        cp "${BACKUP_FILE}" "${POM_FILE}"
        echo -e "${GREEN}✓ Backup restored from ${BACKUP_FILE}${NC}"
    else
        echo -e "${RED}Error: Backup file not found${NC}"
        echo -e "${YELLOW}Available backups:${NC}"
        ls -la pom.xml.backup.* 2>/dev/null || echo "No backup files found"
    fi
}

# Function to show usage
show_usage() {
    echo "Usage: $0 [OPTIONS] [PROJECT_DIR]"
    echo ""
    echo "Options:"
    echo "  -a, --add       Add PMD and Google Code Style plugins (modern rules)"
    echo "  -r, --restore   Restore original pom.xml from backup"
    echo "  -h, --help      Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0 -a .                    # Add plugins to current directory"
    echo "  $0 -a /path/to/project     # Add plugins to specific project"
    echo "  $0 -r                      # Restore original pom.xml"
}

# Main execution
main() {
    case "${1:-}" in
        -a|--add)
            PROJECT_ROOT="${2:-.}"
            POM_FILE="${PROJECT_ROOT}/pom.xml"
            BACKUP_FILE="${PROJECT_ROOT}/pom.xml.backup.$(date +%Y%m%d_%H%M%S)"
            
            if [[ ! -f "${POM_FILE}" ]]; then
                echo -e "${RED}Error: pom.xml not found in ${PROJECT_ROOT}${NC}"
                exit 1
            fi
            
            echo -e "${BLUE}=== Modern Code Quality Plugin Adder ===${NC}"
            echo -e "${BLUE}Project: ${PROJECT_ROOT}${NC}"
            echo ""
            
            echo -e "${YELLOW}Creating backup: ${BACKUP_FILE}${NC}"
            cp "${POM_FILE}" "${BACKUP_FILE}"
            
            add_plugins
            echo ""
            echo -e "${GREEN}=== Modern Code Quality Plugins Added ===${NC}"
            echo -e "${YELLOW}Backup saved to: ${BACKUP_FILE}${NC}"
            echo ""
            echo -e "${BLUE}Available Maven goals:${NC}"
            echo "  mvn pmd:check          # Run PMD analysis (modern rules)"
            echo "  mvn pmd:cpd-check      # Run copy-paste detection"
            echo "  mvn spotless:check     # Check code formatting"
            echo "  mvn spotless:apply     # Apply code formatting"
            echo ""
            echo -e "${YELLOW}To restore original pom.xml: $0 -r${NC}"
            ;;
        -r|--restore)
            # Find the most recent backup
            BACKUP_FILE=$(ls -t pom.xml.backup.* 2>/dev/null | head -1)
            if [[ -z "$BACKUP_FILE" ]]; then
                echo -e "${RED}Error: No backup files found${NC}"
                exit 1
            fi
            restore_backup
            ;;
        -h|--help|*)
            show_usage
            ;;
    esac
}

main "$@" 
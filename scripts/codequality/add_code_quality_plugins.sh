#!/bin/bash

# Code Quality Plugin Adder Script
# Temporarily adds PMD and Google Code Style to pom.xml

set -euo pipefail

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

PROJECT_ROOT="${1:-.}"
POM_FILE="${PROJECT_ROOT}/pom.xml"
BACKUP_FILE="${PROJECT_ROOT}/pom.xml.backup.$(date +%Y%m%d_%H%M%S)"

echo -e "${BLUE}=== Code Quality Plugin Adder ===${NC}"
echo -e "${BLUE}Project: ${PROJECT_ROOT}${NC}"
echo ""

# Check if pom.xml exists
if [[ ! -f "${POM_FILE}" ]]; then
    echo -e "${RED}Error: pom.xml not found in ${PROJECT_ROOT}${NC}"
    exit 1
fi

# Create backup
echo -e "${YELLOW}Creating backup: ${BACKUP_FILE}${NC}"
cp "${POM_FILE}" "${BACKUP_FILE}"

# Function to add PMD plugin
add_pmd_plugin() {
    echo -e "${YELLOW}Adding PMD plugin...${NC}"
    
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
    
    # Add PMD plugin
    cat >> "${temp_file}" << 'EOF'
            <!-- PMD Plugin for Code Quality -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-pmd-plugin</artifactId>
                <version>3.21.2</version>
                <configuration>
                    <linkXRef>false</linkXRef>
                    <sourceEncoding>UTF-8</sourceEncoding>
                    <minimumTokens>100</minimumTokens>
                    <targetJdk>8</targetJdk>
                    <analysisCache>true</analysisCache>
                    <analysisCacheLocation>${project.build.directory}/pmd/pmd.cache</analysisCacheLocation>
                    <printFailingErrors>true</printFailingErrors>
                    <failOnViolation>false</failOnViolation>
                    <skipEmptyReport>false</skipEmptyReport>
                    <includeTests>true</includeTests>
                    <analysisCacheLocation>${project.build.directory}/pmd/pmd.cache</analysisCacheLocation>
                    <rulesets>
                        <ruleset>rulesets/java/quickstart.xml</ruleset>
                        <ruleset>rulesets/java/basic.xml</ruleset>
                        <ruleset>rulesets/java/empty.xml</ruleset>
                        <ruleset>rulesets/java/imports.xml</ruleset>
                        <ruleset>rulesets/java/unnecessary.xml</ruleset>
                        <ruleset>rulesets/java/unusedcode.xml</ruleset>
                        <ruleset>rulesets/java/complexity.xml</ruleset>
                        <ruleset>rulesets/java/coupling.xml</ruleset>
                        <ruleset>rulesets/java/design.xml</ruleset>
                        <ruleset>rulesets/java/naming.xml</ruleset>
                        <ruleset>rulesets/java/optimizations.xml</ruleset>
                        <ruleset>rulesets/java/strings.xml</ruleset>
                        <ruleset>rulesets/java/braces.xml</ruleset>
                        <ruleset>rulesets/java/clone.xml</ruleset>
                        <ruleset>rulesets/java/codesize.xml</ruleset>
                        <ruleset>rulesets/java/comments.xml</ruleset>
                        <ruleset>rulesets/java/controversial.xml</ruleset>
                        <ruleset>rulesets/java/finalizers.xml</ruleset>
                        <ruleset>rulesets/java/typeresolution.xml</ruleset>
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
EOF
    
    # Copy content after plugins end
    tail -n +$((plugins_end_line)) "${POM_FILE}" >> "${temp_file}"
    
    # Replace original file
    mv "${temp_file}" "${POM_FILE}"
    
    echo -e "${GREEN}✓ PMD plugin added${NC}"
}

# Function to add Google Code Style plugin
add_google_code_style() {
    echo -e "${YELLOW}Adding Google Code Style plugin...${NC}"
    
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
    
    # Add Google Code Style plugin
    cat >> "${temp_file}" << 'EOF'
            <!-- Google Code Style Plugin -->
            <plugin>
                <groupId>com.coveo</groupId>
                <artifactId>fmt-maven-plugin</artifactId>
                <version>2.19</version>
                <configuration>
                    <style>google</style>
                    <options>
                        <indent>4</indent>
                        <maxLineLength>120</maxLineLength>
                    </options>
                </configuration>
                <executions>
                    <execution>
                        <goals>
                            <goal>format</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
            <!-- Spotless Plugin for Code Formatting -->
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
    
    echo -e "${GREEN}✓ Google Code Style plugins added${NC}"
}

# Function to add Checkstyle plugin
add_checkstyle_plugin() {
    echo -e "${YELLOW}Adding Checkstyle plugin...${NC}"
    
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
    
    # Add Checkstyle plugin
    cat >> "${temp_file}" << 'EOF'
            <!-- Checkstyle Plugin -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-checkstyle-plugin</artifactId>
                <version>3.3.1</version>
                <configuration>
                    <configLocation>google_checks.xml</configLocation>
                    <encoding>UTF-8</encoding>
                    <consoleOutput>true</consoleOutput>
                    <failsOnError>false</failsOnError>
                    <linkXRef>false</linkXRef>
                </configuration>
                <executions>
                    <execution>
                        <id>validate</id>
                        <phase>validate</phase>
                        <goals>
                            <goal>check</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
EOF
    
    # Copy content after plugins end
    tail -n +$((plugins_end_line)) "${POM_FILE}" >> "${temp_file}"
    
    # Replace original file
    mv "${temp_file}" "${POM_FILE}"
    
    echo -e "${GREEN}✓ Checkstyle plugin added${NC}"
}

# Function to create Google Checkstyle config
create_checkstyle_config() {
    echo -e "${YELLOW}Creating Google Checkstyle configuration...${NC}"
    
    cat > "${PROJECT_ROOT}/google_checks.xml" << 'EOF'
<?xml version="1.0"?>
<!DOCTYPE module PUBLIC
          "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
          "https://checkstyle.org/dtds/configuration_1_3.dtd">

<module name="Checker">
    <property name="charset" value="UTF-8"/>
    <property name="severity" value="warning"/>
    <property name="fileExtensions" value="java, properties, xml"/>

    <!-- Excludes all 'module-info.java' files -->
    <module name="BeforeExecutionExclusionFileFilter">
        <property name="fileNamePattern" value="module-info\.java$"/>
    </module>

    <!-- Checks for Size Violations. -->
    <module name="FileLength">
        <property name="max" value="2000"/>
    </module>

    <!-- Checks for Naming Conventions. -->
    <module name="ConstantName"/>
    <module name="LocalFinalVariableName"/>
    <module name="LocalVariableName"/>
    <module name="MemberName"/>
    <module name="MethodName"/>
    <module name="PackageName"/>
    <module name="ParameterName"/>
    <module name="StaticVariableName"/>
    <module name="TypeName"/>

    <!-- Checks for imports -->
    <module name="AvoidStarImport"/>
    <module name="IllegalImport"/>
    <module name="RedundantImport"/>
    <module name="UnusedImports">
        <property name="processJavadoc" value="false"/>
    </module>

    <!-- Checks for Size Violations. -->
    <module name="MethodLength">
        <property name="max" value="150"/>
    </module>
    <module name="ParameterNumber">
        <property name="max" value="7"/>
    </module>

    <!-- Checks for whitespace -->
    <module name="EmptyForIteratorPad"/>
    <module name="GenericWhitespace"/>
    <module name="MethodParamPad"/>
    <module name="NoWhitespaceAfter"/>
    <module name="NoWhitespaceBefore"/>
    <module name="OperatorWrap"/>
    <module name="ParenPad"/>
    <module name="TypecastParenPad"/>
    <module name="WhitespaceAfter"/>
    <module name="WhitespaceAround"/>

    <!-- Modifier Checks -->
    <module name="ModifierOrder"/>
    <module name="RedundantModifier"/>

    <!-- Checks for blocks -->
    <module name="AvoidNestedBlocks"/>
    <module name="EmptyBlock"/>
    <module name="LeftCurly"/>
    <module name="NeedBraces"/>
    <module name="RightCurly"/>

    <!-- Checks for common coding problems -->
    <module name="EmptyStatement"/>
    <module name="EqualsHashCode"/>
    <module name="HiddenField">
        <property name="ignoreSetter" value="true"/>
        <property name="ignoreConstructorParameter" value="true"/>
    </module>
    <module name="IllegalInstantiation"/>
    <module name="InnerAssignment"/>
    <module name="MagicNumber"/>
    <module name="MissingSwitchDefault"/>
    <module name="MultipleVariableDeclarations"/>
    <module name="SimplifyBooleanExpression"/>
    <module name="SimplifyBooleanReturn"/>

    <!-- Checks for class design -->
    <module name="DesignForExtension"/>
    <module name="FinalClass"/>
    <module name="HideUtilityClassConstructor"/>
    <module name="InterfaceIsType"/>
    <module name="VisibilityModifier"/>

    <!-- Miscellaneous other checks. -->
    <module name="ArrayTypeStyle"/>
    <module name="FinalParameters"/>
    <module name="TodoComment"/>
    <module name="UpperEll"/>

    <!-- Checks for indentation -->
    <module name="Indentation">
        <property name="basicOffset" value="4"/>
        <property name="braceAdjustment" value="0"/>
        <property name="caseIndent" value="4"/>
        <property name="throwsIndent" value="4"/>
        <property name="lineWrappingIndentation" value="4"/>
        <property name="arrayInitIndent" value="4"/>
    </module>

    <!-- Checks for line wrapping -->
    <module name="LineLength">
        <property name="max" value="120"/>
    </module>

    <!-- Checks for Javadoc comments. -->
    <module name="JavadocMethod">
        <property name="scope" value="public"/>
        <property name="allowMissingJavadoc" value="true"/>
    </module>
    <module name="JavadocType">
        <property name="scope" value="public"/>
        <property name="allowMissingJavadoc" value="true"/>
    </module>
    <module name="JavadocVariable">
        <property name="scope" value="public"/>
        <property name="allowMissingJavadoc" value="true"/>
    </module>
    <module name="JavadocStyle"/>

    <!-- Checks for Naming Conventions. -->
    <module name="ConstantName"/>
    <module name="LocalFinalVariableName"/>
    <module name="LocalVariableName"/>
    <module name="MemberName"/>
    <module name="MethodName"/>
    <module name="PackageName"/>
    <module name="ParameterName"/>
    <module name="StaticVariableName"/>
    <module name="TypeName"/>

    <!-- Checks for imports -->
    <module name="AvoidStarImport"/>
    <module name="IllegalImport"/>
    <module name="RedundantImport"/>
    <module name="UnusedImports">
        <property name="processJavadoc" value="false"/>
    </module>

    <!-- Checks for Size Violations. -->
    <module name="MethodLength">
        <property name="max" value="150"/>
    </module>
    <module name="ParameterNumber">
        <property name="max" value="7"/>
    </module>

    <!-- Checks for whitespace -->
    <module name="EmptyForIteratorPad"/>
    <module name="GenericWhitespace"/>
    <module name="MethodParamPad"/>
    <module name="NoWhitespaceAfter"/>
    <module name="NoWhitespaceBefore"/>
    <module name="OperatorWrap"/>
    <module name="ParenPad"/>
    <module name="TypecastParenPad"/>
    <module name="WhitespaceAfter"/>
    <module name="WhitespaceAround"/>

    <!-- Modifier Checks -->
    <module name="ModifierOrder"/>
    <module name="RedundantModifier"/>

    <!-- Checks for blocks -->
    <module name="AvoidNestedBlocks"/>
    <module name="EmptyBlock"/>
    <module name="LeftCurly"/>
    <module name="NeedBraces"/>
    <module name="RightCurly"/>

    <!-- Checks for common coding problems -->
    <module name="EmptyStatement"/>
    <module name="EqualsHashCode"/>
    <module name="HiddenField">
        <property name="ignoreSetter" value="true"/>
        <property name="ignoreConstructorParameter" value="true"/>
    </module>
    <module name="IllegalInstantiation"/>
    <module name="InnerAssignment"/>
    <module name="MagicNumber"/>
    <module name="MissingSwitchDefault"/>
    <module name="MultipleVariableDeclarations"/>
    <module name="SimplifyBooleanExpression"/>
    <module name="SimplifyBooleanReturn"/>

    <!-- Checks for class design -->
    <module name="DesignForExtension"/>
    <module name="FinalClass"/>
    <module name="HideUtilityClassConstructor"/>
    <module name="InterfaceIsType"/>
    <module name="VisibilityModifier"/>

    <!-- Miscellaneous other checks. -->
    <module name="ArrayTypeStyle"/>
    <module name="FinalParameters"/>
    <module name="TodoComment"/>
    <module name="UpperEll"/>

    <!-- Checks for indentation -->
    <module name="Indentation">
        <property name="basicOffset" value="4"/>
        <property name="braceAdjustment" value="0"/>
        <property name="caseIndent" value="4"/>
        <property name="throwsIndent" value="4"/>
        <property name="lineWrappingIndentation" value="4"/>
        <property name="arrayInitIndent" value="4"/>
    </module>

    <!-- Checks for line wrapping -->
    <module name="LineLength">
        <property name="max" value="120"/>
    </module>

    <!-- Checks for Javadoc comments. -->
    <module name="JavadocMethod">
        <property name="scope" value="public"/>
        <property name="allowMissingJavadoc" value="true"/>
    </module>
    <module name="JavadocType">
        <property name="scope" value="public"/>
        <property name="allowMissingJavadoc" value="true"/>
    </module>
    <module name="JavadocVariable">
        <property name="scope" value="public"/>
        <property name="allowMissingJavadoc" value="true"/>
    </module>
    <module name="JavadocStyle"/>

</module>
EOF

    echo -e "${GREEN}✓ Google Checkstyle configuration created${NC}"
}

# Function to restore backup
restore_backup() {
    echo -e "${YELLOW}Restoring backup...${NC}"
    if [[ -f "${BACKUP_FILE}" ]]; then
        cp "${BACKUP_FILE}" "${POM_FILE}"
        echo -e "${GREEN}✓ Backup restored from ${BACKUP_FILE}${NC}"
    else
        echo -e "${RED}Error: Backup file not found${NC}"
    fi
}

# Function to show usage
show_usage() {
    echo "Usage: $0 [OPTIONS] [PROJECT_DIR]"
    echo ""
    echo "Options:"
    echo "  -a, --add       Add all code quality plugins"
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
            add_pmd_plugin
            add_google_code_style
            add_checkstyle_plugin
            create_checkstyle_config
            echo ""
            echo -e "${GREEN}=== All Code Quality Plugins Added ===${NC}"
            echo -e "${YELLOW}Backup saved to: ${BACKUP_FILE}${NC}"
            echo ""
            echo -e "${BLUE}Available Maven goals:${NC}"
            echo "  mvn pmd:check          # Run PMD analysis"
            echo "  mvn pmd:cpd-check      # Run copy-paste detection"
            echo "  mvn fmt:format         # Format code with Google style"
            echo "  mvn spotless:check     # Check code formatting"
            echo "  mvn spotless:apply     # Apply code formatting"
            echo "  mvn checkstyle:check   # Run Checkstyle analysis"
            echo ""
            echo -e "${YELLOW}To restore original pom.xml: $0 -r${NC}"
            ;;
        -r|--restore)
            restore_backup
            ;;
        -h|--help|*)
            show_usage
            ;;
    esac
}

main "$@" 
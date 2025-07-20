# Java Unused Code Detector Scripts

A collection of shell scripts to detect unused code, imports, methods, and dependencies in Java projects.

## Scripts Overview

### 1. `check_unused_code_final.sh` (Recommended)
- **Purpose**: Simple, reliable analysis for Maven projects
- **Features**: 
  - Maven dependency analysis
  - Unused import detection
  - TODO/FIXME comment detection
  - Dead code pattern detection
  - Unused method detection
  - Summary report generation

### 2. `check_unused_code.sh` (Comprehensive)
- **Purpose**: Full-featured analysis with Python integration
- **Features**: 
  - All features from final script
  - Advanced class usage analysis
  - Detailed reporting
  - Python-based import checking

### 3. `check_unused_code_simple.sh` (Basic)
- **Purpose**: Quick analysis for basic projects
- **Features**: 
  - Maven dependency analysis
  - Basic import checking
  - Simple pattern detection

## Usage

### Basic Usage
```bash
# Analyze current directory
./check_unused_code_final.sh

# Analyze specific project directory
./check_unused_code_final.sh /path/to/your/project

# Make script executable (if needed)
chmod +x check_unused_code_final.sh
```

### Example Output
```
=== Java Unused Code Detector ===
Project: .

=== Checking Maven Dependencies ===
Running Maven dependency analysis...
Unused Dependencies:
[WARNING] Unused declared dependencies found:
[WARNING]    com.oracle.database.jdbc:ojdbc10:jar:19.27.0.0:compile
[WARNING]    com.h2database:h2:jar:2.2.224:compile
Missing Dependencies:
[WARNING] Used undeclared dependencies found:
[WARNING]    com.fasterxml.jackson.core:jackson-core:jar:2.15.3:compile
[WARNING]    org.apache.httpcomponents:httpcore:jar:4.4.16:compile

=== Checking Unused Imports ===
Found 23 Java files
✓ No unused imports found

=== Checking TODO/FIXME Comments ===
✓ No TODO/FIXME comments found

=== Checking Dead Code Patterns ===
./src/main/java/com/baml/mav/aieutil/validate/VaultClientTest.java: potential unreachable code

=== Summary Report ===
Maven Dependencies:
  - Unused: 2
  - Missing: 2

Code Quality:
  - Java files analyzed: 23
  - TODO/FIXME comments: 0

Recommendations:
  - Consider removing unused Maven dependencies
  - Add missing Maven dependencies to pom.xml
  - Review all findings manually before making changes
```

## What Each Script Checks

### 1. Maven Dependencies
- **Unused Dependencies**: Dependencies declared in `pom.xml` but not used in code
- **Missing Dependencies**: Dependencies used in code but not declared in `pom.xml`

### 2. Unused Imports
- **Detection**: Finds import statements for classes that are not used in the file
- **Method**: Simple text-based analysis (may have false positives)

### 3. TODO/FIXME Comments
- **Detection**: Finds TODO, FIXME, and XXX comments in Java files
- **Purpose**: Identify incomplete or problematic code

### 4. Dead Code Patterns
- **Empty Catch Blocks**: Catch blocks with no implementation
- **Unreachable Code**: Code after return/throw statements

### 5. Unused Methods
- **Detection**: Methods that are defined but never called
- **Limitation**: Simple analysis, may miss reflection-based calls

## Prerequisites

### Required Tools
- **Bash**: Available on most Unix-like systems
- **Maven**: For dependency analysis (`mvn` command)
- **Python 3**: For advanced analysis (optional)

### System Requirements
- Unix-like system (Linux, macOS, WSL)
- Bash shell
- Read access to Java source files

## Installation

1. **Download Scripts**:
   ```bash
   # Copy scripts to your project or a tools directory
   cp check_unused_code_final.sh /path/to/your/project/
   ```

2. **Make Executable**:
   ```bash
   chmod +x check_unused_code_final.sh
   ```

3. **Verify Maven**:
   ```bash
   mvn --version
   ```

## Configuration

### Environment Variables
- `PROJECT_ROOT`: Default project directory (defaults to current directory)

### Customization
You can modify the scripts to:
- Add new detection patterns
- Change output format
- Adjust sensitivity levels
- Add project-specific rules

## Limitations and False Positives

### Known Limitations
1. **Reflection**: Methods called via reflection may appear unused
2. **Configuration Files**: Classes used in XML/YAML configs may appear unused
3. **Runtime Dependencies**: Dependencies used only at runtime may appear unused
4. **Simple Analysis**: Uses basic text matching, not full AST parsing

### False Positives
- Methods called via reflection
- Classes used in configuration files
- Dependencies used at runtime only
- Overloaded methods
- Interface implementations

## Best Practices

### Before Running
1. **Commit Changes**: Ensure your code is committed before analysis
2. **Clean Build**: Run `mvn clean compile` to ensure clean state
3. **Review Context**: Understand your project's architecture

### After Analysis
1. **Manual Review**: Always review findings manually
2. **Test Thoroughly**: Test after removing any code
3. **Incremental Changes**: Make changes in small increments
4. **Documentation**: Update documentation when removing code

### Recommended Workflow
1. Run analysis: `./check_unused_code_final.sh .`
2. Review findings manually
3. Create backup branch: `git checkout -b cleanup-unused-code`
4. Make changes incrementally
5. Test each change thoroughly
6. Commit changes with descriptive messages

## Troubleshooting

### Common Issues

#### Script Not Executable
```bash
chmod +x check_unused_code_final.sh
```

#### Maven Not Found
```bash
# Install Maven or add to PATH
export PATH=$PATH:/path/to/maven/bin
```

#### Permission Denied
```bash
# Check file permissions
ls -la check_unused_code_final.sh
# Fix if needed
chmod +x check_unused_code_final.sh
```

#### No Java Files Found
```bash
# Verify Java files exist
find . -name "*.java" -type f
```

### Error Messages

#### "No pom.xml found"
- Ensure you're running from a Maven project root
- Check if `pom.xml` exists in the specified directory

#### "Maven not found in PATH"
- Install Maven or add to your PATH
- Verify with `mvn --version`

#### "Temporary directory creation failed"
- Check disk space
- Verify write permissions in `/tmp`

## Advanced Usage

### Custom Analysis
```bash
# Analyze specific directories only
find src/main/java -name "*.java" | xargs grep "TODO"

# Check specific file types
find . -name "*.java" -exec grep -l "unused" {} \;

# Generate detailed report
./check_unused_code_final.sh . > analysis_report.txt
```

### Integration with CI/CD
```bash
#!/bin/bash
# Example CI script
set -e

echo "Running unused code analysis..."
./check_unused_code_final.sh .

# Fail if too many issues found
UNUSED_DEPS=$(grep -c "Unused declared dependencies" /tmp/unused_check_*/maven_analysis.txt || echo "0")
if [ "$UNUSED_DEPS" -gt 5 ]; then
    echo "Too many unused dependencies found: $UNUSED_DEPS"
    exit 1
fi
```

## Contributing

### Adding New Checks
1. Add new function to script
2. Update main() function
3. Test with sample projects
4. Update documentation

### Reporting Issues
- Include script version and output
- Provide sample project structure
- Describe expected vs actual behavior

## License

These scripts are provided as-is for educational and development purposes. Use at your own risk and always review findings manually before making changes to your codebase.

## Support

For issues or questions:
1. Check the troubleshooting section
2. Review the limitations section
3. Test with a simple project first
4. Consider using professional static analysis tools for production use 
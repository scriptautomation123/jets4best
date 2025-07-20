# Code Quality Plugin Scripts

Scripts to temporarily add code quality plugins to Maven projects.

## Scripts Overview

### 1. `add_code_quality_simple.sh` (Recommended)
- **Purpose**: Adds PMD and Google Code Style plugins
- **Features**: 
  - PMD code analysis with all rulesets
  - Copy-paste detection (CPD)
  - Google Java Format
  - Automatic backup and restore

### 2. `add_code_quality_plugins.sh` (Comprehensive)
- **Purpose**: Adds PMD, Google Code Style, and Checkstyle plugins
- **Features**: 
  - All features from simple script
  - Checkstyle with Google rules
  - More comprehensive analysis

## Quick Start

### Add Code Quality Plugins
```bash
# Add plugins to current project
./add_code_quality_simple.sh -a .

# Add plugins to specific project
./add_code_quality_simple.sh -a /path/to/project
```

### Restore Original pom.xml
```bash
# Restore from backup
./add_code_quality_simple.sh -r
```

## What Gets Added

### PMD Plugin
- **Code Analysis**: Detects potential problems in Java code
- **Rulesets**: All major PMD rulesets (quickstart, basic, design, etc.)
- **Copy-Paste Detection**: Finds duplicate code blocks
- **Configuration**: Optimized for Java 8 projects

### Spotless Plugin (Google Code Style)
- **Code Formatting**: Applies Google Java Style Guide
- **Import Organization**: Sorts imports (java, javax, org, com)
- **Unused Import Removal**: Automatically removes unused imports
- **Line Length**: 120 characters max

## Available Maven Goals

### PMD Analysis
```bash
# Run PMD code analysis
mvn pmd:check

# Run copy-paste detection
mvn pmd:cpd-check

# Generate PMD report
mvn pmd:pmd
```

### Code Formatting
```bash
# Check code formatting (without changing files)
mvn spotless:check

# Apply code formatting (modifies files)
mvn spotless:apply

# Format specific files
mvn spotless:apply -DspotlessFiles="src/main/java/**/*.java"
```

### Combined Analysis
```bash
# Run all quality checks
mvn verify

# Run specific phase
mvn compile pmd:check spotless:check
```

## Example Output

### PMD Analysis
```
[INFO] --- maven-pmd-plugin:3.21.2:check (pmd-check) @ aieutil ---
[INFO] PMD version: 6.55.0
[INFO] PMD found 3 violations in 18 files
[WARNING] src/main/java/com/baml/mav/aieutil/util/VaultClient.java:45: 
    Avoid unused private fields such as 'CLIENT_TOKEN_PATH'.
[WARNING] src/main/java/com/baml/mav/aieutil/util/VaultClient.java:67: 
    Avoid unused private methods such as 'buildVaultBaseUrl'.
```

### Spotless Check
```
[INFO] --- spotless-maven-plugin:2.43.0:check (default) @ aieutil ---
[INFO] Spotless check completed successfully.
```

## Configuration Options

### PMD Configuration
The script adds comprehensive PMD configuration:
- **Target JDK**: 8
- **Minimum Tokens**: 100 (for CPD)
- **Analysis Cache**: Enabled for performance
- **Fail on Violation**: Disabled (warnings only)
- **Include Tests**: Enabled

### Spotless Configuration
- **Style**: Google Java Format
- **Line Length**: 120 characters
- **Import Order**: java, javax, org, com
- **Unused Imports**: Automatically removed

## Backup and Restore

### Automatic Backup
- Creates timestamped backup: `pom.xml.backup.YYYYMMDD_HHMMSS`
- Backup is created before any changes

### Manual Restore
```bash
# Restore using script
./add_code_quality_simple.sh -r

# Or manually copy backup
cp pom.xml.backup.20250720_143022 pom.xml
```

## Integration with CI/CD

### GitHub Actions Example
```yaml
name: Code Quality
on: [push, pull_request]
jobs:
  quality:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '8'
      - name: Add Code Quality Plugins
        run: ./add_code_quality_simple.sh -a .
      - name: Run PMD Analysis
        run: mvn pmd:check
      - name: Check Code Formatting
        run: mvn spotless:check
      - name: Restore pom.xml
        run: ./add_code_quality_simple.sh -r
```

### Jenkins Pipeline Example
```groovy
pipeline {
    agent any
    stages {
        stage('Code Quality') {
            steps {
                sh './add_code_quality_simple.sh -a .'
                sh 'mvn pmd:check'
                sh 'mvn spotless:check'
                sh './add_code_quality_simple.sh -r'
            }
        }
    }
}
```

## Troubleshooting

### Common Issues

#### "Could not find </plugins> tag"
- Ensure pom.xml has a proper `<build><plugins>` section
- Check if pom.xml is well-formed XML

#### PMD Plugin Not Found
- Maven will download the plugin automatically
- Check internet connection for plugin download

#### Spotless Formatting Issues
- Some code may need manual formatting
- Run `mvn spotless:apply` to auto-format

### Error Messages

#### "PMD found violations"
- Review violations in the report
- Fix code issues or adjust PMD rules
- Set `failOnViolation` to `false` for warnings only

#### "Spotless found violations"
- Run `mvn spotless:apply` to auto-fix
- Or manually format code according to Google style

## Best Practices

### Before Running
1. **Commit Changes**: Ensure code is committed before analysis
2. **Clean Build**: Run `mvn clean compile` first
3. **Review Context**: Understand your project's architecture

### After Analysis
1. **Review Violations**: Don't fix everything blindly
2. **Prioritize Issues**: Focus on high-priority violations
3. **Test Changes**: Ensure fixes don't break functionality
4. **Document Decisions**: Note why certain violations are ignored

### Recommended Workflow
1. Add plugins: `./add_code_quality_simple.sh -a .`
2. Run analysis: `mvn verify`
3. Review violations and fix important ones
4. Apply formatting: `mvn spotless:apply`
5. Test: `mvn clean compile test`
6. Restore: `./add_code_quality_simple.sh -r`

## Customization

### Modify PMD Rules
Edit the rulesets in the generated pom.xml:
```xml
<rulesets>
    <ruleset>rulesets/java/quickstart.xml</ruleset>
    <!-- Add or remove rulesets as needed -->
</rulesets>
```

### Adjust Spotless Configuration
Modify the Spotless configuration:
```xml
<configuration>
    <java>
        <googleJavaFormat>
            <style>GOOGLE</style>
        </googleJavaFormat>
        <lineLength>120</lineLength>
    </java>
</configuration>
```

## Support

For issues or questions:
1. Check the troubleshooting section
2. Review PMD and Spotless documentation
3. Test with a simple project first
4. Consider using IDE plugins for real-time feedback

## License

These scripts are provided as-is for educational and development purposes. Use at your own risk and always review findings manually before making changes to your codebase. 
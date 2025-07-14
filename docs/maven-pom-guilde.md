c# Maven POM Structure Guide for AIE Util Project

## Overview

This document explains the structure of the `pom.xml` file in the AIE Util project. Maven is a build tool for Java projects that manages dependencies, compiles code, runs tests, and packages applications. The `pom.xml` (Project Object Model) is the configuration file that tells Maven how to build your project.

## 1. Project Metadata Section

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.baml.mav</groupId>
  <artifactId>aieutil</artifactId>
  <version>0.1.0</version>
  <packaging>jar</packaging>
  <name>AIE Util</name>
  <description>All-in-one utility library for AIE (flattened, non-modular)</description>
```

### What This Section Does:

- **`groupId`**: The organization's unique identifier (like a company domain in reverse)
- **`artifactId`**: The project name
- **`version`**: The current version of your project
- **`packaging`**: How the project should be packaged (jar = Java Archive)
- **`name` & `description`**: Human-readable project information

### Why It Matters:

This is like the "header" of your project - it identifies what you're building and who owns it. Maven uses this information to organize dependencies and artifacts in repositories.

## 2. Properties Section

```xml
<properties>
  <java.version>21</java.version>
  <maven.compiler.release>21</maven.compiler.release>
  <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
</properties>
```

### What This Section Does:

- **`java.version`**: Specifies which Java version to use (Java 21 in this case)
- **`maven.compiler.release`**: Tells the compiler to target Java 21
- **`project.build.sourceEncoding`**: Sets the character encoding for source files

### Why It Matters:

Think of properties as "variables" that can be reused throughout the POM. Instead of hardcoding "21" everywhere, we define it once here and reference it elsewhere. This makes it easy to upgrade Java versions later.

## 3. Dependency Management Section

```xml
<dependencyManagement>
  <dependencies>
    <!-- JDBI BOM-->
    <dependency>
      <groupId>org.jdbi</groupId>
      <artifactId>jdbi3-bom</artifactId>
      <type>pom</type>
      <version>3.49.5</version>
      <scope>import</scope>
    </dependency>
    <!-- ... more dependencies ... -->
  </dependencies>
</dependencyManagement>
```

### What This Section Does:

- **Centralized version control**: All dependency versions are defined here
- **BOM (Bill of Materials)**: Imports predefined dependency sets from other projects
- **Version consistency**: Ensures all modules use the same versions

### Why It Matters:

This is like a "shopping list" where you specify exactly which versions of libraries you want. The `scope="import"` means "use the versions defined in this other project's dependency list."

### Key Dependencies in This Project:

- **JDBI**: Database access library
- **Jackson**: JSON processing
- **Log4j**: Logging framework
- **Oracle JDBC**: Database driver
- **OkHttp**: HTTP client
- **PicoCLI**: Command-line interface framework

## 4. Dependencies Section

```xml
<dependencies>
  <dependency>
    <groupId>org.apache.logging.log4j</groupId>
    <artifactId>log4j-api</artifactId>
  </dependency>
  <!-- ... more dependencies ... -->
</dependencies>
```

### What This Section Does:

- **Actual dependencies**: These are the libraries your project actually uses
- **No versions needed**: Versions come from the `dependencyManagement` section above
- **Compile-time and runtime**: These dependencies are included when building and running your project

### Why It Matters:

This is where you declare "I need these libraries to build and run my project." Maven will automatically download these dependencies and make them available to your code.

## 5. Build Section - Plugin Management

```xml
<build>
  <pluginManagement>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <version>3.14.0</version>
        <configuration>
          <release>${maven.compiler.release}</release>
          <compilerArgs>
            <arg>--enable-preview</arg>
          </compilerArgs>
        </configuration>
      </plugin>
      <!-- ... more plugins ... -->
    </plugins>
  </pluginManagement>
```

### What This Section Does:

- **Plugin versions**: Defines which versions of build tools to use
- **Plugin configuration**: Sets up how each tool should behave
- **Centralized management**: All plugin settings in one place

### Why It Matters:

Plugins are like "tools" that Maven uses to build your project. This section ensures everyone uses the same versions and settings, preventing "works on my machine" problems.

### Key Plugins:

- **maven-compiler-plugin**: Compiles Java code (with Java 21 preview features enabled)
- **maven-surefire-plugin**: Runs tests
- **maven-shade-plugin**: Creates "fat JARs" with all dependencies included
- **exec-maven-plugin**: Runs external commands (like jlink for custom runtimes)
- **maven-assembly-plugin**: Creates distribution packages

## 6. Build Section - Active Plugins

```xml
<plugins>
  <plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
  </plugin>
  <!-- ... more plugins ... -->
</plugins>
```

### What This Section Does:

- **Active plugins**: These are the plugins that actually run during the build
- **Inherits configuration**: Uses the settings defined in `pluginManagement` above
- **Build lifecycle**: Defines what happens when you run `mvn compile`, `mvn test`, `mvn package`, etc.

### Why It Matters:

This is where you say "actually use these tools during the build process." The plugins here will run in sequence to compile, test, and package your application.

## 7. Profiles Section

```xml
<profiles>
  <profile>
    <id>format-code</id>
    <build>
      <plugins>
        <plugin>
          <groupId>com.diffplug.spotless</groupId>
          <artifactId>spotless-maven-plugin</artifactId>
          <!-- ... configuration ... -->
        </plugin>
      </plugins>
    </build>
  </profile>
  <!-- ... more profiles ... -->
</profiles>
```

### What This Section Does:

- **Conditional builds**: Different build configurations for different scenarios
- **Optional features**: Tools that only run when you specifically request them
- **Environment-specific settings**: Different behavior for development, testing, production

### Why It Matters:

Profiles let you have different build configurations without changing the main POM. You can activate them with `-P profile-name` or they can run automatically based on conditions.

### Available Profiles:

- **`format-code`**: Formats your code using Google Java Format
- **`check-and-validate-versions`**: Checks if dependencies are up to date
- **`update-versions-pre-check`**: Updates dependency versions (minor updates only)
- **`update-versions-post-check`**: Shows what versions were updated

## How to Use This POM

### Basic Commands:

```bash
# Compile the project
mvn compile

# Run tests
mvn test

# Package the application
mvn package

# Install to local repository
mvn install

# Clean and rebuild everything
mvn clean install
```

### Profile Commands:

```bash
# Format your code
mvn -P format-code compile

# Check for dependency updates
mvn -P check-and-validate-versions

# Update dependency versions
mvn -P update-versions-pre-check
```

### What Happens During Build:

1. **Compile**: Java code is compiled to bytecode
2. **Test**: Unit tests are run
3. **Package**: Code is packaged into a JAR file
4. **Shade**: Dependencies are included in the JAR (creates "fat JAR")
5. **Assembly**: Final distribution package is created

## Key Benefits of This Structure

1. **Consistency**: Everyone uses the same versions and settings
2. **Reproducibility**: Builds work the same way on any machine
3. **Maintainability**: Easy to update versions and add new dependencies
4. **Automation**: Build process is fully automated
5. **Quality**: Code formatting and testing are built into the process

## For Developers New to Java/Maven

- **Think of Maven as a "build manager"** that handles all the complex parts of building Java applications
- **The POM is like a "recipe"** that tells Maven exactly how to build your project
- **Dependencies are like "ingredients"** that your project needs to work
- **Plugins are like "tools"** that Maven uses to compile, test, and package your code
- **Profiles are like "variations"** of the recipe for different situations

This structure ensures that your AIE Util project is built consistently, efficiently, and with all the modern Java 21 features enabled.

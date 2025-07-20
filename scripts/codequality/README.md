# Code Quality Scripts

This directory contains scripts to add code quality tools to Maven projects.

## Available Scripts

### 1. `add_code_quality_fixed.sh` ✅ **RECOMMENDED**

**Features:**
- Uses modern PMD rule categories (`category/java/errorprone.xml`, `category/java/bestpractices.xml`, etc.)
- These are the current PMD rule names that match what you're seeing in your PMD violations
- Includes both PMD and Spotless (Google Code Style)
- Has proper backup/restore functionality
- Clean, focused configuration

**Usage:**
```bash
./scripts/add_code_quality_fixed.sh -a .
```

### 2. `add_code_quality_modern.sh`

**Features:**
- Similar to `fixed` but with slightly different rule organization
- Also uses modern rule categories
- Good alternative if you want to try different rule sets

### 3. `add_code_quality_simple.sh`

**Features:**
- Uses legacy PMD rule names (`rulesets/java/quickstart.xml`, etc.)
- These are **deprecated** and may not work with newer PMD versions
- **Avoid this one**

### 4. `add_code_quality_plugins.sh`

**Features:**
- **Overkill** - includes PMD, Spotless, Checkstyle, and creates a complex Checkstyle config
- Too many tools for your current needs
- The Checkstyle config is duplicated in the file

## Recommendation

Use **`add_code_quality_fixed.sh`** for most projects as it provides:
- Modern PMD rules that match current violation reports
- Google Code Style formatting
- Proper backup/restore functionality
- Clean, focused configuration without unnecessary complexity
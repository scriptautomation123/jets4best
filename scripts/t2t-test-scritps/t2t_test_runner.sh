#!/bin/bash

# T2T Process Test Runner
# Comprehensive test automation for Transform to Target processes

set -euo pipefail

# Source environment files immediately at startup
if [[ -f "/efs/dist/efs/environ/prod/common/etc/init.functions" ]]; then
    source "/efs/dist/efs/environ/prod/common/etc/init.functions"
fi

if [[ -f "/efs/dist/efs/environ/prod/common/etc/init.environ" ]]; then
    source "/efs/dist/efs/environ/prod/common/etc/init.environ"
fi

# =============================================================================
# CONFIGURATION VARIABLES
# =============================================================================

# Colors for console output
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly PURPLE='\033[0;35m'
readonly CYAN='\033[0;36m'
readonly NC='\033[0m' # No Color

# Environment setup
readonly JAVA_8_HOME="${JAVA_8_HOME:?JAVA_8_HOME is required}"
readonly JAVA_21_HOME="${JAVA_21_HOME:?JAVA_21_HOME is required}"
readonly MAVEN_HOME="${MAVEN_HOME:?MAVEN_HOME is required}"
readonly EFS_INIT_FUNCTIONS="${EFS_INIT_FUNCTIONS:?EFS_INIT_FUNCTIONS is required}"
readonly EFS_INIT_ENVIRON="${EFS_INIT_ENVIRON:?EFS_INIT_ENVIRON is required}"

# Database configuration
readonly DB_NAME="${DB_NAME:?DB_NAME is required}"
readonly DB_USER="${DB_USER:?DB_USER is required}"

# Vault configuration
readonly VAULT_CONFIG_PATH="${VAULT_CONFIG_PATH:?VAULT_CONFIG_PATH is required}"
readonly VAULT_SECRET_ID="${VAULT_SECRET_ID:?VAULT_SECRET_ID is required}"
readonly VAULT_URL="${VAULT_URL:?VAULT_URL is required}"
readonly VAULT_ROLE_ID="${VAULT_ROLE_ID:?VAULT_ROLE_ID is required}"
readonly VAULT_AIT="${VAULT_AIT:?VAULT_AIT is required}"

# Procedure names
readonly TEMP_TABLE_PROC="${TEMP_TABLE_PROC:-MAV_OWNER.TempTable_Onehadoop_proc}"
readonly ENABLE_CONS_PROC="${ENABLE_CONS_PROC:-MAV_OWNER.ENABLE_CONS_TEMPTABLE_PROC}"
readonly PARTITION_SWAP_PROC="${PARTITION_SWAP_PROC:-MAV_OWNER.PARTITION_SWAP_PROC}"

# Input parameters
readonly TEMP_TABLE_INPUT="${TEMP_TABLE_INPUT:-in_schema:VARCHAR2:MAV_OWNER,in_src_table_nm:VARCHAR2:CUST_INSGHT_DLY,in_typ_cd:INTEGER:136,in_prant_grp:VARCHAR2:HADOOP_DML_ROLE}"
readonly ENABLE_CONS_INPUT="${ENABLE_CONS_INPUT:-in_schema:VARCHAR2:MAV_OWNER}"
readonly PARTITION_SWAP_INPUT="${PARTITION_SWAP_INPUT:-in_schema:VARCHAR2:MAV_OWNER}"

# Output parameters
readonly OUTPUT_PARAM="${OUTPUT_PARAM:-p_outmsg:STRING}"

# Script configuration
readonly RUN_SCRIPT="${RUN_SCRIPT:-./run.sh}"
readonly LOG_DIR="${LOG_DIR:-./logs}"
readonly LOG_FILE="${LOG_FILE:-${LOG_DIR}/t2t_test_$(date +%Y%m%d_%H%M%S).log}"
readonly RESULTS_FILE="${RESULTS_FILE:-${LOG_DIR}/t2t_results_$(date +%Y%m%d_%H%M%S).txt}"

# Default Java version
readonly DEFAULT_JAVA_VERSION="${DEFAULT_JAVA_VERSION:-21}"

# Timeout and performance constants
readonly TEST_TIMEOUT_SECONDS="${TEST_TIMEOUT_SECONDS:-300}"
readonly JAVA_8_MODULE="1.8.0u451"
readonly JAVA_21_MODULE="21.0.7"

# =============================================================================
# UTILITY FUNCTIONS
# =============================================================================

# Log messages with color coding and timestamp
# Usage: log <level> <message>
# Levels: INFO, SUCCESS, WARNING, ERROR, DEBUG
log() {
    local level="$1"
    shift
    local message="$*"
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    
    case "$level" in
        "INFO")  echo -e "${BLUE}[INFO]${NC} $message" | tee -a "$LOG_FILE" ;;
        "SUCCESS") echo -e "${GREEN}[SUCCESS]${NC} $message" | tee -a "$LOG_FILE" ;;
        "WARNING") echo -e "${YELLOW}[WARNING]${NC} $message" | tee -a "$LOG_FILE" ;;
        "ERROR")   echo -e "${RED}[ERROR]${NC} $message" | tee -a "$LOG_FILE" ;;
        "DEBUG")   echo -e "${PURPLE}[DEBUG]${NC} $message" >> "$LOG_FILE" ;;
    esac
}

error_exit() {
    log "ERROR" "$1"
    exit 1
}

check_command() {
    if ! command -v "$1" &> /dev/null; then
        error_exit "$1 is not installed or not in PATH"
    fi
}

check_file() {
    if [[ ! -f "$1" ]]; then
        error_exit "Required file not found: $1"
    fi
}

check_directory() {
    if [[ ! -d "$1" ]]; then
        error_exit "Required directory not found: $1"
    fi
}

# =============================================================================
# VALIDATION FUNCTIONS
# =============================================================================

validate_branch_name() {
    local branch="$1"
    [[ "$branch" =~ ^[a-zA-Z0-9._/-]+$ ]] || error_exit "Invalid branch name: $branch"
}

validate_java_version() {
    local version="$1"
    [[ "$version" =~ ^(8|21)$ ]] || error_exit "Invalid Java version: $version. Supported: 8, 21"
}

# =============================================================================
# CLEANUP FUNCTIONS
# =============================================================================

cleanup_distribution() {
    local distribution_dir="$1"
    if [[ -n "$distribution_dir" ]] && [[ -d "$distribution_dir" ]]; then
        rm -rf "$distribution_dir"
        log "INFO" "Cleaned up distribution directory: $distribution_dir"
    fi
}

cleanup_branch() {
    local branch="$1"
    if [[ -n "$branch" ]]; then
        if git branch -D "$branch" 2>/dev/null; then
            log "INFO" "Cleaned up temporary branch: $branch"
        fi
    fi
}

cleanup_all() {
    local distribution_dir="$1"
    local branch="$2"
    
    cleanup_distribution "$distribution_dir"
    cleanup_branch "$branch"
    
    log "SUCCESS" "Cleanup completed"
}

# Set Java version and environment
# Usage: set_java_version <version>
# Args: version - Java version (8 or 21)
# Returns: 0 on success, 1 on failure
set_java_version() {
    local jdk="$1"
    
    # Validate input
    validate_java_version "$jdk"
    
    # can be set to 8 or 21
    # if its set to 8 then $jdk=1.8.0u451
    # if its set to 21 then $jdk=21.0.7
    
    case "$jdk" in
        "8")
            jdk="$JAVA_8_MODULE"
            ;;
        "21")
            jdk="$JAVA_21_MODULE"
            ;;
        *)
            error_exit "Unsupported Java version: $jdk. Supported: 8, 21"
            ;;
    esac
    
    . "$EFS_INIT_FUNCTIONS"
    . "$EFS_INIT_ENVIRON"
    module load oracle/jdk/${jdk}
    # below sets the maven home
    export PATH="$MAVEN_HOME/bin:$PATH"
    
    # check if java version matches what was requested
    local java_output=$(java -version 2>&1)
    if [[ "$jdk" == "$JAVA_8_MODULE" ]] && [[ "$java_output" == *"1.8"* ]]; then
        return 0
    elif [[ "$jdk" == "$JAVA_21_MODULE" ]] && [[ "$java_output" == *"21"* ]]; then
        return 0
    else
        return 1
    fi
}

# =============================================================================
# GIT AND MAVEN FUNCTIONS
# =============================================================================

# Clone a branch with timestamp suffix
# Usage: clone_branch <branch_name>
# Args: branch_name - Name of the branch to clone
# Returns: 0 on success, 1 on failure
# Output: Name of the new branch on stdout
clone_branch() {
    local branch="$1"
    local timestamp=$(date +%Y%m%d_%H%M%S)
    local target_branch="${branch}.${timestamp}"
    
    # Validate input
    validate_branch_name "$branch"
    
    log "INFO" "Cloning branch: $branch to $target_branch"
    
    if git checkout -b "$target_branch" "$branch" 2>/dev/null; then
        log "SUCCESS" "Successfully cloned $branch to $target_branch"
        echo "$target_branch"
        return 0
    else
        log "ERROR" "Failed to clone branch $branch to $target_branch"
        return 1
    fi
}

maven_clean() {
    log "INFO" "Running Maven clean..."
    if mvn clean; then
        log "SUCCESS" "Maven clean completed successfully"
        return 0
    else
        log "ERROR" "Maven clean failed"
        return 1
    fi
}

maven_clean_package() {
    log "INFO" "Running Maven clean package..."
    if mvn clean package; then
        log "SUCCESS" "Maven clean package completed successfully"
        return 0
    else
        log "ERROR" "Maven clean package failed"
        return 1
    fi
}

maven_clean_install() {
    log "INFO" "Running Maven clean install..."
    if mvn clean install; then
        log "SUCCESS" "Maven clean install completed successfully"
        return 0
    else
        log "ERROR" "Maven clean install failed"
        return 1
    fi
}

maven_assembly() {
    log "INFO" "Running Maven assembly..."
    if mvn clean package assembly:single; then
        log "SUCCESS" "Maven assembly completed successfully"
        return 0
    else
        log "ERROR" "Maven assembly failed"
        return 1
    fi
}

extract_distribution() {
    local timestamp=$(date +%Y%m%d_%H%M%S)
    local distribution_dir="distribution.${timestamp}"
    local zip_file=""
    
    # Find the distribution zip file
    if [[ -f "target/aieutil-bundle.zip" ]]; then
        zip_file="target/aieutil-bundle.zip"
    else
        # Use proper glob expansion
        local bundle_files=(target/*-bundle.zip)
        if [[ ${#bundle_files[@]} -gt 0 ]] && [[ -f "${bundle_files[0]}" ]]; then
            zip_file="${bundle_files[0]}"
        else
            error_exit "No distribution zip file found in target directory"
        fi
    fi
    
    log "INFO" "Extracting distribution from $zip_file to $distribution_dir"
    
    # Create distribution directory
    mkdir -p "$distribution_dir"
    
    # Extract the zip file
    if unzip -q "$zip_file" -d "$distribution_dir"; then
        log "SUCCESS" "Distribution extracted to $distribution_dir"
        echo "$distribution_dir"
        return 0
    else
        log "ERROR" "Failed to extract distribution from $zip_file"
        return 1
    fi
}

# Build and prepare distribution for a branch
# Usage: build_and_prepare <branch> [<java_version>]
# Args: branch - Branch name to build
#       java_version - Java version to use (default: 21)
# Returns: 0 on success, exits on failure
# Output: Distribution directory path on stdout
build_and_prepare() {
    local branch="$1"
    local java_version="${2:-21}"
    
    # Validate inputs
    validate_branch_name "$branch"
    validate_java_version "$java_version"
    
    log "INFO" "Building and preparing environment for branch: $branch, Java: $java_version"
    
    # Clone the branch
    local target_branch
    if ! target_branch=$(clone_branch "$branch"); then
        error_exit "Failed to clone branch $branch"
    fi
    
    # Set Java version
    if ! set_java_version "$java_version"; then
        error_exit "Failed to set Java version $java_version"
    fi
    
    # Run Maven clean package
    if ! maven_clean_package; then
        error_exit "Maven build failed"
    fi
    
    # Extract distribution
    local distribution_dir
    if ! distribution_dir=$(extract_distribution); then
        error_exit "Failed to extract distribution"
    fi
    
    log "SUCCESS" "Build and preparation completed:"
    log "INFO" "  - Branch: $target_branch"
    log "INFO" "  - Java: $java_version"
    log "INFO" "  - Distribution: $distribution_dir"
    
    echo "$distribution_dir"
}

run_tests_with_build() {
    local branch="$1"
    local java_version="${2:-21}"
    
    # Validate inputs
    validate_branch_name "$branch"
    validate_java_version "$java_version"
    
    log "INFO" "Starting complete test run with build for branch: $branch, Java: $java_version"
    
    # Build and prepare
    local distribution_dir
    if ! distribution_dir=$(build_and_prepare "$branch" "$java_version"); then
        error_exit "Failed to build and prepare environment"
    fi
    
    # Change to distribution directory
    cd "$distribution_dir" || error_exit "Failed to change to distribution directory: $distribution_dir"
    
    # Run the tests
    log "INFO" "Running tests from distribution directory: $distribution_dir"
    ./run.sh --help  # Test that the script is executable
    
    # Run your test commands here
    # Example:
    # ./run.sh exec-proc --vault-config /path/to/vaults.yaml -d DB_NAME -u DB_USER "PROCEDURE_NAME" --input "params" --output "output"
    
    log "SUCCESS" "Test run completed from distribution: $distribution_dir"
}

setup_environment() {
    log "INFO" "Setting up environment..."
    
    # Check required commands
    check_command java
    check_command mvn
    
    # Create log directory
    mkdir -p "$LOG_DIR"
    
    # Setup Java version based on environment variable
    set_java_version "${JAVA_VERSION:-21}"
    
    # Set Maven
    export PATH="$MAVEN_HOME/bin:$PATH"
    
    log "SUCCESS" "Environment setup complete - Java: $(java -version 2>&1 | head -1), Maven: $(mvn -version 2>&1 | head -1)"
}

run_t2t_command() {
    local test_name="$1"
    local command_args="$2"
    local output_file="${LOG_DIR}/${test_name}_output.log"
    
    log "INFO" "Running: $test_name"
    log "DEBUG" "Command: $RUN_SCRIPT $command_args"
    
    local start_time=$(date +%s)
    
    if timeout "$TEST_TIMEOUT_SECONDS" "$RUN_SCRIPT" $command_args > "$output_file" 2>&1; then
        local end_time=$(date +%s)
        local duration=$((end_time - start_time))
        log "SUCCESS" "$test_name completed in ${duration}s"
        echo "✅ $test_name - SUCCESS (${duration}s)" >> "$RESULTS_FILE"
        return 0
    else
        local exit_code=$?
        local end_time=$(date +%s)
        local duration=$((end_time - start_time))
        log "ERROR" "$test_name failed after ${duration}s (exit code: $exit_code)"
        echo "❌ $test_name - FAILED (${duration}s, exit: $exit_code)" >> "$RESULTS_FILE"
        log "DEBUG" "Command output saved to: $output_file"
        return 1
    fi
}

# =============================================================================
# TEST FUNCTIONS
# =============================================================================

test_temp_table_creation_vault_id() {
    local command_args="--vault-config $VAULT_CONFIG_PATH -d $DB_NAME -u $DB_USER \"$TEMP_TABLE_PROC\" --input \"$TEMP_TABLE_INPUT\" --output \"$OUTPUT_PARAM\""
    run_t2t_command "Temp Table Creation (Vault ID)" "$command_args"
}

test_enable_constraints_vault_id() {
    local command_args="--vault-config $VAULT_CONFIG_PATH -d $DB_NAME -u $DB_USER \"$ENABLE_CONS_PROC\" --input \"$ENABLE_CONS_INPUT\" --output \"$OUTPUT_PARAM\""
    run_t2t_command "Enable Constraints (Vault ID)" "$command_args"
}

test_partition_swap_vault_id() {
    local command_args="--vault-config $VAULT_CONFIG_PATH -d $DB_NAME -u $DB_USER \"$PARTITION_SWAP_PROC\" --input \"$PARTITION_SWAP_INPUT\" --output \"$OUTPUT_PARAM\""
    run_t2t_command "Partition Swap (Vault ID)" "$command_args"
}

test_temp_table_creation_full_vault() {
    local command_args="--vault-config $VAULT_CONFIG_PATH -d $DB_NAME -u $DB_USER --secret $VAULT_SECRET_ID --vault-url $VAULT_URL --vault-role-id $VAULT_ROLE_ID --vault-ait $VAULT_AIT \"$TEMP_TABLE_PROC\" --input \"$TEMP_TABLE_INPUT\" --output \"$OUTPUT_PARAM\""
    run_t2t_command "Temp Table Creation (Full Vault)" "$command_args"
}

test_enable_constraints_full_vault() {
    local command_args="--vault-config $VAULT_CONFIG_PATH -d $DB_NAME -u $DB_USER --secret $VAULT_SECRET_ID --vault-url $VAULT_URL --vault-role-id $VAULT_ROLE_ID --vault-ait $VAULT_AIT \"$ENABLE_CONS_PROC\" --input \"$ENABLE_CONS_INPUT\" --output \"$OUTPUT_PARAM\""
    run_t2t_command "Enable Constraints (Full Vault)" "$command_args"
}

# =============================================================================
# MAIN EXECUTION
# =============================================================================

main() {
    local total_tests=0
    local passed_tests=0
    local failed_tests=0
    
    echo "🚀 T2T Process Test Runner" | tee "$RESULTS_FILE"
    echo "Started: $(date)" | tee -a "$RESULTS_FILE"
    echo "========================================" | tee -a "$RESULTS_FILE"
    
    # Setup environment
    setup_environment
    
    # Check required files
    check_file "$RUN_SCRIPT"
    check_file "$VAULT_CONFIG_PATH"
    
    log "INFO" "Starting T2T process tests..."
    
    # Run tests with Vault ID only
    log "INFO" "=== Testing with Vault ID configuration ==="
    
    ((total_tests++))
    if test_temp_table_creation_vault_id; then
        ((passed_tests++))
    else
        ((failed_tests++))
    fi
    
    ((total_tests++))
    if test_enable_constraints_vault_id; then
        ((passed_tests++))
    else
        ((failed_tests++))
    fi
    
    ((total_tests++))
    if test_partition_swap_vault_id; then
        ((passed_tests++))
    else
        ((failed_tests++))
    fi
    
    # Run tests with full Vault parameters
    log "INFO" "=== Testing with full Vault parameters ==="
    
    ((total_tests++))
    if test_temp_table_creation_full_vault; then
        ((passed_tests++))
    else
        ((failed_tests++))
    fi
    
    ((total_tests++))
    if test_enable_constraints_full_vault; then
        ((passed_tests++))
    else
        ((failed_tests++))
    fi
    
    # Summary
    echo "========================================" | tee -a "$RESULTS_FILE"
    echo "Test Summary:" | tee -a "$RESULTS_FILE"
    echo "Total: $total_tests" | tee -a "$RESULTS_FILE"
    echo "Passed: $passed_tests" | tee -a "$RESULTS_FILE"
    echo "Failed: $failed_tests" | tee -a "$RESULTS_FILE"
    echo "Success Rate: $((passed_tests * 100 / total_tests))%" | tee -a "$RESULTS_FILE"
    echo "Completed: $(date)" | tee -a "$RESULTS_FILE"
    
    if [[ $failed_tests -eq 0 ]]; then
        log "SUCCESS" "All tests passed! 🎉"
        exit 0
    else
        log "WARNING" "$failed_tests test(s) failed. Check logs for details."
        exit 1
    fi
}

# =============================================================================
# SCRIPT EXECUTION
# =============================================================================

# Handle script arguments
case "${1:-}" in
    --help|-h)
        echo "Usage: $0 [--help|--setup-only|--test <test_name>|--build <branch>|--full-test <branch>|--cleanup <distribution_dir> [<branch>]]"
        echo ""
        echo "Options:"
        echo "  --help, -h        Show this help message"
        echo "  --setup-only      Only setup environment, don't run tests"
        echo "  --test <name>     Run specific test (temp_table, enable_constraints, partition_swap)"
        echo "  --build <branch>  Build and prepare distribution for branch"
        echo "  --full-test <branch> Build, prepare, and run tests for branch"
        echo "  --cleanup <dir> [<branch>] Clean up distribution directory and optional branch"
        echo ""
        echo "Environment variables:"
        echo "  JAVA_VERSION      Java version to use (8, 21)"
        echo "  JAVA_8_HOME       Path to Java 8 installation"
        echo "  JAVA_21_HOME      Path to Java 21 installation"
        echo "  MAVEN_HOME        Path to Maven installation"
        echo "  VAULT_CONFIG_PATH Path to vault configuration file"
        echo "  DB_NAME           Database name"
        echo "  DB_USER           Database user"
        echo "  LOG_DIR           Directory for log files"
        exit 0
        ;;
    --cleanup)
        if [[ -z "${2:-}" ]]; then
            error_exit "Distribution directory required with --cleanup option"
        fi
        cleanup_all "$2" "${3:-}"
        exit 0
        ;;
    --build)
        if [[ -z "${2:-}" ]]; then
            error_exit "Branch name required with --build option"
        fi
        build_and_prepare "$2" "${JAVA_VERSION:-21}"
        exit 0
        ;;
    --full-test)
        if [[ -z "${2:-}" ]]; then
            error_exit "Branch name required with --full-test option"
        fi
        run_tests_with_build "$2" "${JAVA_VERSION:-21}"
        exit 0
        ;;
    --setup-only)
        setup_environment
        log "SUCCESS" "Environment setup completed"
        exit 0
        ;;
    --test)
        if [[ -z "${2:-}" ]]; then
            error_exit "Test name required with --test option"
        fi
        setup_environment
        case "$2" in
            temp_table) test_temp_table_creation_vault_id ;;
            enable_constraints) test_enable_constraints_vault_id ;;
            partition_swap) test_partition_swap_vault_id ;;
            *) error_exit "Unknown test: $2" ;;
        esac
        exit $?
        ;;
    "")
        main
        ;;
    *)
        error_exit "Unknown option: $1"
        ;;
esac 
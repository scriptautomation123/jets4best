#!/bin/bash

set -euo pipefail

# =============================================================================
# Usage:
#   ./t2t_process.sh [options]
#
# Options:
#   -i                    Run in interactive mode (prompts for JDK, mode, code)
#   --build-and-t2t       Build the app and run the T2T process (default)
#   --t2t-only            Only run the T2T process, skip build
#   --branch <url>        Clone the specified git branch before running
#   --mode <mode>         T2T mode: t2t_regular or t2t_full (default: t2t_regular)
#   --jdk <8|21>          JDK version to use (default: 8)
#   --proj_dir <DIR>      Project directory (default: current dir)
#   --app <APP>           App name (default: inferred from project)
#   --insght_typ_code <C> Insight type code (required)
#   --logging <0|1>       Enable extra logging (default: 0)
#   -h, --help            Show this help message
#
# Environment Variables:
#   JDK, PROJ_DIR, APP, INSGHT_TYP_CODE (can be set or prompted)
#
# Example:
#   ./t2t_process.sh --jdk 21 --mode t2t_full --insght_typ_code 136
#   ./t2t_process.sh -i
# =============================================================================

# Add default values at the beginning of the script, after the color definitions
# Default values
JDK="${JDK:-8}"
MODE="${MODE:-t2t_regular}"
PROJ_DIR="${PROJ_DIR:-$(pwd)}"
APP="${APP:-}"
INSGHT_TYP_CODE="${INSGHT_TYP_CODE:-}"
BRANCH_URL="${BRANCH_URL:-}"
BUILD_AND_T2T="${BUILD_AND_T2T:-1}"
T2T_ONLY="${T2T_ONLY:-0}"
INTERACTIVE="${INTERACTIVE:-0}"
LOGGING="${LOGGING:-0}"

# Colors for console output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Enhanced logging
log() {
    local level="$1"; shift
    local message="$*"
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    case "$level" in
        INFO)    echo -e "${BLUE}[INFO]${NC} $message" ;;
        SUCCESS) echo -e "${GREEN}[SUCCESS]${NC} $message" ;;
        WARNING) echo -e "${YELLOW}[WARNING]${NC} $message" ;;
        ERROR)   echo -e "${RED}[ERROR]${NC} $message" ;;
        DEBUG)   echo -e "${PURPLE}[DEBUG]${NC} $message" ;;
        *)       echo -e "[LOG] $message" ;;
    esac
}

error_exit() {
    log ERROR "$1"
    exit 1
}

# Utility checks
check_command() { command -v "$1" &>/dev/null || error_exit "$1 is not installed or not in PATH"; }
check_file()    { [[ -f "$1" ]] || error_exit "Required file not found: $1"; }
check_directory() { [[ -d "$1" ]] || error_exit "Required directory not found: $1"; }

# Validate required env vars (add more as needed)
validate_env() {
    local missing=0
    for var in JDK PROJ_DIR APP INSGHT_TYP_CODE; do
        if [[ -z "${!var:-}" ]]; then
            log ERROR "Required env var missing: $var"
            missing=1
        fi
    done
    if (( missing )); then exit 1; fi
}

usage() {
    echo "Usage: $0 [-i] [--build-and-t2t|--t2t-only] [--branch <branch_url>] [--mode t2t_regular|t2t_full] [--jdk 8|21] [--proj_dir DIR] [--app APP] [--insght_typ_code CODE] [--logging 1]"
    echo "Defaults: --build-and-t2t, jdk=8, mode=t2t_regular, logging=0"
    exit 1
}


prompt_interactive() {
    # Prompt for JDK
    while true; do
        read -p "JDK version [8/21] (current: $JDK): " input
        input="${input:-$JDK}"
        if [[ "$input" == "8" || "$input" == "21" ]]; then
            JDK="$input"
            break
        else
            echo "Please enter 8 or 21."
        fi
    done
    # Prompt for MODE
    while true; do
        read -p "T2T mode [t2t_regular/t2t_full] (current: $MODE): " input
        input="${input:-$MODE}"
        if [[ "$input" == "t2t_regular" || "$input" == "t2t_full" ]]; then
            MODE="$input"
            break
        else
            echo "Please enter t2t_regular or t2t_full."
        fi
    done
    # Prompt for INSGHT_TYP_CODE
    read -p "Insight Type Code (current: $INSGHT_TYP_CODE): " input
    if [[ -n "$input" ]]; then
        INSGHT_TYP_CODE="$input"
    fi
}

clone_branch() {
    local url="$1"
    local branch_name=$(basename "$url" .git)
    local timestamp=$(date +%Y%m%d_%H%M%S)
    local folder="${branch_name}.${timestamp}"
    git clone "$url" "$folder" || error_exit "Failed to clone $url"
    PROJ_DIR="$(cd "$folder" && pwd)"
    log "Cloned $url to $PROJ_DIR"
}

load_jdk() {
    if [[ "$JDK" == "8" ]]; then
        module load 1.8.0.u351 || error_exit "Failed to load JDK 8"
    elif [[ "$JDK" == "21" ]]; then
        module load jdk21 || error_exit "Failed to load JDK 21"
    else
        error_exit "Invalid JDK version: $JDK"
    fi
    log "Loaded JDK $JDK"
}

build_app() {
    cd "$PROJ_DIR" || error_exit "Cannot cd to $PROJ_DIR"
    source /efs/env/prod/common/etc/init.functions || error_exit "init.functions missing"
    source /efs/env/prod/common/etc/init.environ || error_exit "init.environ missing"
    export PATH=/opt/oracle/jdk/21.0.7/bin:$PATH
    log "Building app with Maven"
    mvn clean package -Pjdk21 -q || error_exit "Maven build failed"
    cd "$PROJ_DIR/target" || error_exit "Cannot cd to target"
    unzip -q "${APP}.zip" || error_exit "Unzip failed"
    log "Build and unzip complete"
}

run_temp_table_create() {
    local code="$1"
    log "Running temp table create"
    local cmd=(./run.sh --vault-config "$PROJ_DIR/src/main/resources/vaults.yaml" -d ECICMD03_SVC01 -u MAV_T2T_APP "MAV_OWNER.TempTable_Onehadoop_proc" --input "in_schema:VARCHAR2:MAV_OWNER,in_src_table_nm:VARCHAR2:CUST_INSGHT_DLY,in_typ_cd:INTEGER:${code},in_prant_grp:VARCHAR2:HADOOP_DML_ROLE" --output "p_outmsg:STRING")
    if [[ "$MODE" == "t2t_full" ]]; then
        cmd+=(--vault-id "$VAULT_ID" --vault-url "$VAULT_URL" --role-id "$ROLE_ID" --ait "$AIT")
    fi
    "${cmd[@]}" || error_exit "Temp table create failed"
}

run_enable_constraints() {
    local code="$1"
    log "Running enable constraints"
    local cmd=(./run.sh --vault-config "$PROJ_DIR/src/main/resources/vaults.yaml" -d ECICMD03_SVC01 -u MAV_T2T_APP "MAV_OWNER.ENABLE_CONS_TEMPTABLE_PROC" --input "in_schema:VARCHAR2:MAV_OWNER,in_src_table_nm:VARCHAR2:CUST_INSGHT_DLY,in_typ_cd:INTEGER:${code}" --output "p_outmsg:STRING")
    if [[ "$MODE" == "t2t_full" ]]; then
        cmd+=(--vault-id "$VAULT_ID" --vault-url "$VAULT_URL" --role-id "$ROLE_ID" --ait "$AIT")
    fi
    "${cmd[@]}" || error_exit "Enable constraints failed"
}

run_partition_swap() {
    local code="$1"
    log "Running partition swap"
    local cmd=(./run.sh --vault-config "$PROJ_DIR/src/main/resources/vaults.yaml" -d ECICMD03_SVC01 -u MAV_T2T_APP "MAV_OWNER.PARTITION_SWAP_PROC" --input "in_schema:VARCHAR2:MAV_OWNER,in_src_table_nm:VARCHAR2:CUST_INSGHT_DLY,in_typ_cd:INTEGER:${code}" --output "p_outmsg:STRING")
    if [[ "$MODE" == "t2t_full" ]]; then
        cmd+=(--vault-id "$VAULT_ID" --vault-url "$VAULT_URL" --role-id "$ROLE_ID" --ait "$AIT")
    fi
    "${cmd[@]}" || error_exit "Partition swap failed"
}

t2t_process() {
    run_temp_table_create "$1" || return 1
    sleep 2
    run_enable_constraints "$1" || return 1
    sleep 2
    run_partition_swap "$1" || return 1
}

main() {
    # Parse args (optional, for CLI usage)
    while [[ $# -gt 0 ]]; do
        case $1 in
            -i) INTERACTIVE=1; shift;;
            --build-and-t2t) BUILD_AND_T2T=1; T2T_ONLY=0; shift;;
            --t2t-only) BUILD_AND_T2T=0; T2T_ONLY=1; shift;;
            --branch) BRANCH_URL="$2"; shift 2;;
            --mode) MODE="$2"; shift 2;;
            --jdk) JDK="$2"; shift 2;;
            --proj_dir) PROJ_DIR="$2"; shift 2;;
            --app) APP="$2"; shift 2;;
            --insght_typ_code) INSGHT_TYP_CODE="$2"; shift 2;;
            --logging) LOGGING="$2"; shift 2;;
            -h|--help) usage;;
            *) usage;;
        esac
    done
    if [[ "$INTERACTIVE" == "1" ]]; then
        prompt_interactive
    fi
    if [[ -n "$BRANCH_URL" ]]; then
        clone_branch "$BRANCH_URL"
    fi
    load_jdk
    if [[ "$BUILD_AND_T2T" == "1" ]]; then
        build_app
    fi
    cd "$PROJ_DIR/target/$APP" || error_exit "Cannot cd to app dir"
    t2t_process "$INSGHT_TYP_CODE"
}

main "$@" 
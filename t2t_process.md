# T2T Process Automation Script Usage

## Overview
This script automates the T2T (Table-to-Table) process, including building the app, running Oracle stored procedures, and managing environment setup. It is suitable for both developers and CI environments.

## Usage
```sh
./t2t_process.sh [options]
```

## Options
- `-i`                    Run in interactive mode (prompts for JDK, mode, code)
- `--build-and-t2t`       Build the app and run the T2T process (default)
- `--t2t-only`            Only run the T2T process, skip build
- `--branch <url>`        Clone the specified git branch before running
- `--mode <mode>`         T2T mode: `t2t_regular` or `t2t_full` (default: `t2t_regular`)
- `--jdk <8|21>`          JDK version to use (default: 8)
- `--proj_dir <DIR>`      Project directory (default: current dir)
- `--app <APP>`           App name (default: inferred from project)
- `--insght_typ_code <C>` Insight type code (required)
- `--logging <0|1>`       Enable extra logging (default: 0)
- `-h, --help`            Show this help message

## Environment Variables
- `JDK`, `PROJ_DIR`, `APP`, `INSGHT_TYP_CODE` (can be set or prompted)

## Examples
```sh
# Run in interactive mode
./t2t_process.sh -i

# Build and run T2T process with JDK 21 and full mode
./t2t_process.sh --jdk 21 --mode t2t_full --insght_typ_code 136

# Only run T2T process, skip build
./t2t_process.sh --t2t-only --insght_typ_code 136

# Clone a branch and run
./t2t_process.sh --branch https://github.com/org/repo.git --insght_typ_code 136
```

## Notes
- The script requires certain environment modules and files to be present (see error messages for details).
- For production use, ensure all required environment variables and files are set up before running. 
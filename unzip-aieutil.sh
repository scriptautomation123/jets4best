#!/bin/bash
set -euo pipefail

APP=aieutil-1.0.0
ZIP_FILE="/home/swapanc/code/multiroot/app/target/${APP}.zip"
DEST_DIR="/home/swapanc/code/multiroot/app/target/${APP}-unzipped"
APP_DEST_DIR="${DEST_DIR}/${APP}"


# If the destination directory exists, delete it
if [ -d "$DEST_DIR" ]; then
  echo "Removing existing directory: $DEST_DIR"
  rm -rf "$DEST_DIR"
fi

echo "creating ${DEST_DIR}"
mkdir -p "$DEST_DIR"

echo "unzipping  ${ZIP_FILE} to  ${DEST_DIR}"
unzip -q -o "$ZIP_FILE" -d "$DEST_DIR"



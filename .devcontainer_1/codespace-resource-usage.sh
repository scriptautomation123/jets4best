#!/usr/bin/env bash
set -euo pipefail

# codespace-resource-usage.sh
# Script to track and identify resource utilization in a Codespace
# Shows top processes, container stats, and disk usage

# Print timestamp and host info
echo "=== Codespace Resource Utilization Report ==="
date
echo "Host: $(hostname)"
echo

# 1. Show overall system resource usage
echo "--- System Resource Usage (top 10 processes) ---"
ps -eo pid,ppid,cmd,%mem,%cpu --sort=-%mem | head -n 11
echo

# 2. Show Docker container stats (if Docker is running)
if command -v docker &>/dev/null && docker info &>/dev/null; then
  echo "--- Docker Container Resource Usage ---"
  docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.NetIO}}\t{{.BlockIO}}"
else
  echo "Docker is not running or not available."
fi
echo

# 3. Show disk usage by directory (top 10)
echo "--- Disk Usage (Top 10 Directories) ---"
du -h --max-depth=2 /workspaces 2>/dev/null | sort -hr | head -n 10
echo

# 4. Show top users by memory and CPU usage
echo "--- Top Users by Memory and CPU ---"
ps -eo user,%mem,%cpu --sort=-%mem | awk 'NR==1 || !seen[$1]++' | head -n 10
echo

# 5. Show open files by process (top 5 by open files)
echo "--- Top Processes by Open Files ---"
lsof -n | awk '{print $2}' | sort | uniq -c | sort -nr | head -n 5 | awk '{print "PID: "$2" Open files: "$1}'
echo

# 6. Show network connections by process (top 5)
echo "--- Top Processes by Network Connections ---"
lsof -i -n | awk '{print $1}' | sort | uniq -c | sort -nr | head -n 5

echo "=== End of Report ==="

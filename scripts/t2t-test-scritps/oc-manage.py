#!/usr/bin/env python3
"""
oc_report.py - OpenShift Namespace Report (CLI & Ansible Module)
Author: Openshift SME / Principal Engineer
"""

import os
import sys
import json
import subprocess
import argparse
from datetime import datetime, timezone
from collections import defaultdict

BOOTSTRAP_CDN = "https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css"

def run_oc(cmd):
    result = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True)
    return result.stdout.decode()

def get_namespaces(target_namespaces=None):
    data = json.loads(run_oc(["oc", "get", "ns", "-o", "json"]))
    all_ns = [item["metadata"]["name"] for item in data["items"]]
    if target_namespaces:
        return [ns for ns in all_ns if ns in target_namespaces]
    return all_ns

def get_deploymentconfigs(ns):
    try:
        data = json.loads(run_oc(["oc", "get", "dc", "-n", ns, "-o", "json"]))
        return [{
            "name": item["metadata"]["name"],
            "replicas": item.get("status", {}).get("replicas", 0),
            "age": get_age(item["metadata"]["creationTimestamp"])
        } for item in data.get("items", [])]
    except Exception:
        return []

def get_pods(ns):
    try:
        data = json.loads(run_oc(["oc", "get", "pods", "-n", ns, "-o", "json"]))
        return [{
            "name": item["metadata"]["name"],
            "status": item["status"]["phase"],
            "age": get_age(item["metadata"]["creationTimestamp"])
        } for item in data.get("items", [])]
    except Exception:
        return []

def get_age(creation_ts):
    created = datetime.strptime(creation_ts, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)
    now = datetime.now(timezone.utc)
    delta = now - created
    days = delta.days
    hours = delta.seconds // 3600
    minutes = (delta.seconds % 3600) // 60
    if days > 0:
        return f"{days}d {hours}h"
    elif hours > 0:
        return f"{hours}h {minutes}m"
    else:
        return f"{minutes}m"

def build_report(data):
    html = f"""<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <title>OpenShift Namespace Report</title>
  <link rel="stylesheet" href="{BOOTSTRAP_CDN}">
</head>
<body>
<div class="container my-4">
  <h1 class="mb-4">OpenShift Namespace Report</h1>
  <p>Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}</p>
"""
    for ns, nsdata in data.items():
        html += f'<h2 class="mt-4">{ns}</h2>'
        # DeploymentConfigs
        html += '<h4>DeploymentConfigs</h4>'
        html += '<table class="table table-striped table-bordered"><thead><tr><th>Name</th><th>Replicas</th><th>Age</th></tr></thead><tbody>'
        for dc in nsdata["deploymentconfigs"]:
            html += f'<tr><td>{dc["name"]}</td><td>{dc["replicas"]}</td><td>{dc["age"]}</td></tr>'
        html += '</tbody></table>'
        # Pods
        html += '<h4>Pods</h4>'
        html += '<table class="table table-striped table-bordered"><thead><tr><th>Name</th><th>Status</th><th>Age</th></tr></thead><tbody>'
        for pod in nsdata["pods"]:
            html += f'<tr><td>{pod["name"]}</td><td>{pod["status"]}</td><td>{pod["age"]}</td></tr>'
        html += '</tbody></table>'
    html += "</div></body></html>"
    return html

def main_cli():
    parser = argparse.ArgumentParser(description="OpenShift Namespace Report")
    parser.add_argument("--namespaces", help="Comma-separated list of namespaces (default: all)")
    parser.add_argument("--output", help="Output HTML file (default: stdout)")
    args = parser.parse_args()

    namespaces = args.namespaces.split(",") if args.namespaces else None
    ns_list = get_namespaces(namespaces)
    report_data = defaultdict(dict)
    for ns in ns_list:
        report_data[ns]["deploymentconfigs"] = get_deploymentconfigs(ns)
        report_data[ns]["pods"] = get_pods(ns)
    html = build_report(report_data)
    if args.output:
        with open(args.output, "w") as f:
            f.write(html)
        print(f"Report written to {args.output}")
    else:
        print(html)

def main_ansible():
    # Read args from env
    args = json.loads(os.environ["ANSIBLE_MODULE_ARGS"])
    namespaces = args.get("namespaces")
    output = args.get("output")
    ns_list = get_namespaces(namespaces)
    report_data = defaultdict(dict)
    for ns in ns_list:
        report_data[ns]["deploymentconfigs"] = get_deploymentconfigs(ns)
        report_data[ns]["pods"] = get_pods(ns)
    html = build_report(report_data)
    if output:
        with open(output, "w") as f:
            f.write(html)
        result = {"changed": True, "msg": f"Report written to {output}"}
    else:
        result = {"changed": False, "report": html}
    print(json.dumps(result))

if __name__ == "__main__":
    if "ANSIBLE_MODULE_ARGS" in os.environ:
        main_ansible()
    else:
        main_cli()
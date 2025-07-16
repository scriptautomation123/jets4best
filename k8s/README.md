# ExecProcCmd on OpenShift

Containerized vault-authenticated stored procedure execution for OpenShift.

## 🚀 Quick Start

### Prerequisites
- OpenShift cluster access
- `oc` CLI tool
- Docker/Podman
- Container registry access

### Deploy

1. **Set environment variables:**
```bash
export NAMESPACE="my-project"
export REGISTRY="quay.io/myorg"
export VERSION="v1.0.0"
```

2. **Update credentials in `k8s/secret.yaml`:**
```bash
# Encode your vault credentials
echo -n "your-role-id" | base64
echo -n "your-secret-id" | base64
```

3. **Deploy everything:**
```bash
chmod +x deploy.sh
./deploy.sh
```

## 📋 Usage

### Ad Hoc Execution
Run a procedure immediately:
```bash
oc apply -f k8s/job.yaml
```

### Scheduled Execution
The CronJob runs daily at 2 AM. Modify `k8s/cronjob.yaml` to change schedule:
```yaml
schedule: "0 2 * * *"  # Daily at 2 AM
schedule: "*/30 * * * *"  # Every 30 minutes
schedule: "0 9 * * 1-5"  # Weekdays at 9 AM
```

### Manual Trigger
Trigger the CronJob manually:
```bash
oc create job manual-run --from=cronjob/exec-proc-scheduled -n $NAMESPACE
```

## 🔧 Configuration

### Procedure Parameters
Edit the `args` section in job manifests:
```yaml
args:
  - "-t"
  - "oracle"
  - "-d" 
  - "YOUR_DATABASE"
  - "-u"
  - "YOUR_USER"
  - "YOUR_PROCEDURE_NAME"
  - "--input"
  - "param1:VARCHAR2:value1,param2:INTEGER:123"
  - "--output"
  - "result:VARCHAR2"
```

### Database Connections
Update `k8s/configmap.yaml` for your environment:
```yaml
data:
  application.yaml: |
    databases:
      oracle:
        connection-string:
          ldap:
            servers: "your-ldap1.com,your-ldap2.com"
            context: "cn=OracleContext,dc=yourcompany,dc=com"
```

### Vault Configuration
Update `k8s/configmap.yaml` for your Vault setup:
```yaml
data:
  vaults.yaml: |
    vaults:
      - id: "YOUR_USER"
        base-url: "https://your-vault.com"
        ait: "YOUR_AIT"
        db: "YOUR_DB"
```

## 📊 Monitoring

### Check Job Status
```bash
# List all jobs
oc get jobs -n $NAMESPACE

# Check specific job
oc describe job exec-proc-adhoc -n $NAMESPACE

# View job logs
oc logs -f job/exec-proc-adhoc -n $NAMESPACE
```

### Check CronJob Status
```bash
# List CronJobs
oc get cronjobs -n $NAMESPACE

# Check last execution
oc describe cronjob exec-proc-scheduled -n $NAMESPACE

# View recent job history
oc get jobs -l app=exec-proc -n $NAMESPACE
```

### Suspend/Resume CronJob
```bash
# Suspend (stop scheduling)
oc patch cronjob exec-proc-scheduled -p '{"spec":{"suspend":true}}' -n $NAMESPACE

# Resume
oc patch cronjob exec-proc-scheduled -p '{"spec":{"suspend":false}}' -n $NAMESPACE
```

## 🔒 Security

### Secrets Management
- Vault credentials stored in OpenShift Secrets
- Non-root container execution (UID 1001)
- Minimal RBAC permissions
- Security Context Constraints applied

### Network Access
Ensure network policies allow:
- Outbound HTTPS to Vault (port 443)
- Outbound LDAP to Oracle directory (port 389)
- Outbound Oracle connections (port 1521)

## 🐛 Troubleshooting

### Common Issues

**Job fails with "permission denied":**
```bash
# Check SCC assignment
oc describe pod <pod-name> -n $NAMESPACE
oc get scc exec-proc-scc
```

**Vault authentication fails:**
```bash
# Check secret values
oc get secret exec-proc-vault-creds -o yaml -n $NAMESPACE

# Verify vault connectivity
oc run debug --image=curlimages/curl -n $NAMESPACE -- \
  curl -k https://your-vault.com/v1/sys/health
```

**Oracle connection fails:**
```bash
# Check LDAP connectivity
oc run debug --image=nicolaka/netshoot -n $NAMESPACE -- \
  nslookup your-ldap-server.com

# Verify application.yaml
oc get configmap exec-proc-config -o yaml -n $NAMESPACE
```

### Debug Mode
Run with debug logging:
```yaml
env:
- name: JAVA_OPTS
  value: "-Dlogging.level.com.baml.mav.aieutil=DEBUG"
```

## 📈 Scaling

### Resource Tuning
Adjust resources based on procedure complexity:
```yaml
resources:
  requests:
    memory: "512Mi"    # For complex procedures
    cpu: "200m"
  limits:
    memory: "1Gi"
    cpu: "1000m"
```

### Parallel Execution
For multiple procedures, use Job parallelism:
```yaml
spec:
  parallelism: 3
  completions: 3
```

### Multiple Environments
Use different namespaces:
```bash
# Development
NAMESPACE=dev-procedures ./deploy.sh

# Production  
NAMESPACE=prod-procedures ./deploy.sh
```
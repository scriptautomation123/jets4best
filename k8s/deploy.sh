#!/bin/bash

set -e

# Configuration
NAMESPACE=${NAMESPACE:-"your-namespace"}
REGISTRY=${REGISTRY:-"your-registry"}
IMAGE_NAME="exec-proc"
VERSION=${VERSION:-"latest"}
FULL_IMAGE="$REGISTRY/$IMAGE_NAME:$VERSION"

echo "🚀 Deploying ExecProcCmd to OpenShift"
echo "Namespace: $NAMESPACE"
echo "Image: $FULL_IMAGE"

# Step 1: Build the application
echo "📦 Building application..."
mvn clean package -DskipTests

# Step 2: Build container image
echo "🐳 Building container image..."
docker build -t $FULL_IMAGE .

# Step 3: Push to registry
echo "📤 Pushing to registry..."
docker push $FULL_IMAGE

# Step 4: Create namespace if it doesn't exist
echo "🏗️  Creating namespace..."
oc create namespace $NAMESPACE --dry-run=client -o yaml | oc apply -f -

# Step 5: Deploy RBAC resources
echo "🔐 Deploying RBAC resources..."
sed "s/your-namespace/$NAMESPACE/g" k8s/rbac.yaml | oc apply -f -

# Step 6: Deploy ConfigMap and Secret
echo "⚙️  Deploying configuration..."
sed "s/your-namespace/$NAMESPACE/g" k8s/configmap.yaml | oc apply -f -

echo "🔑 Creating secret (update with real credentials)..."
sed "s/your-namespace/$NAMESPACE/g" k8s/secret.yaml | oc apply -f -

# Step 7: Update image references in manifests
echo "🔄 Updating image references..."
sed -i.bak "s|your-registry/exec-proc:v1.0.0|$FULL_IMAGE|g" k8s/job.yaml
sed -i.bak "s|your-registry/exec-proc:v1.0.0|$FULL_IMAGE|g" k8s/cronjob.yaml
sed -i.bak "s/your-namespace/$NAMESPACE/g" k8s/job.yaml
sed -i.bak "s/your-namespace/$NAMESPACE/g" k8s/cronjob.yaml

# Step 8: Deploy CronJob for scheduled execution
echo "⏰ Deploying scheduled CronJob..."
oc apply -f k8s/cronjob.yaml

echo "✅ Deployment complete!"
echo ""
echo "📋 Available commands:"
echo ""
echo "# Run ad hoc job:"
echo "oc apply -f k8s/job.yaml"
echo ""
echo "# Check job status:"
echo "oc get jobs -n $NAMESPACE"
echo ""
echo "# Check CronJob status:"
echo "oc get cronjobs -n $NAMESPACE"
echo ""
echo "# View logs:"
echo "oc logs -f job/exec-proc-adhoc -n $NAMESPACE"
echo ""
echo "# Manual trigger CronJob:"
echo "oc create job exec-proc-manual --from=cronjob/exec-proc-scheduled -n $NAMESPACE"
echo ""
echo "# Scale CronJob (suspend/resume):"
echo "oc patch cronjob exec-proc-scheduled -p '{\"spec\":{\"suspend\":true}}' -n $NAMESPACE"
echo "oc patch cronjob exec-proc-scheduled -p '{\"spec\":{\"suspend\":false}}' -n $NAMESPACE"

# Cleanup backup files
rm -f k8s/*.bak 
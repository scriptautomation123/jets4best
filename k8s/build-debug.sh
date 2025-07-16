#!/bin/bash

# Build Debug Image Script
# Creates containerized debugging environment for ExecProcCmd

set -e

# Configuration
REGISTRY=${REGISTRY:-"your-registry"}
IMAGE_NAME="exec-proc"
VERSION=${VERSION:-"v1.0.0"}
DEBUG_TAG="$VERSION-debug"
FULL_IMAGE="$REGISTRY/$IMAGE_NAME:$DEBUG_TAG"

echo "🔨 Building ExecProcCmd Debug Image"
echo "===================================="
echo "Registry: $REGISTRY"
echo "Image: $IMAGE_NAME:$DEBUG_TAG"
echo "Full Image: $FULL_IMAGE"
echo ""

# Step 1: Build the application
echo "📦 Building application..."
mvn clean package -DskipTests

# Step 2: Create debug directory if it doesn't exist
echo "📁 Preparing debug directory..."
mkdir -p debug

# Make debug scripts executable
chmod +x debug/*.sh 2>/dev/null || echo "No debug scripts found yet"

# Step 3: Build debug image
echo "🐳 Building debug container image..."
docker build -f Dockerfile.debug -t $FULL_IMAGE .

echo "✅ Debug image built successfully!"
echo ""

# Step 4: Optional - push to registry
read -p "🚀 Push to registry? (y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "📤 Pushing to registry..."
    docker push $FULL_IMAGE
    echo "✅ Image pushed successfully!"
fi

echo ""
echo "🔧 Debug Image Usage:"
echo "====================="
echo ""
echo "# Local debugging:"
echo "docker run -it $FULL_IMAGE /bin/bash"
echo ""
echo "# Quick health check:"
echo "docker run --rm $FULL_IMAGE ./debug/quick-fix.sh"
echo ""
echo "# Deploy to Kubernetes:"
echo "sed -i 's|your-registry/exec-proc:v1.0.0-debug|$FULL_IMAGE|g' k8s/debug-job.yaml"
echo "oc apply -f k8s/debug-job.yaml"
echo ""
echo "# Monitor debug execution:"
echo "oc logs -f job/exec-proc-debug"
echo ""
echo "# Connect for manual inspection:"
echo "oc exec -it exec-proc-debug -- /bin/bash"
echo ""
echo "📚 See debug/README.md for comprehensive debugging guide" 
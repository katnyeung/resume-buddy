#!/bin/bash

# Complete deployment script for Resume Buddy on EKS
# This script builds all Docker images, pushes to Docker Hub, and deploys to EKS

set -e

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
echo_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
echo_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# Check required variables
if [ -z "$DOCKERHUB_USERNAME" ]; then
  echo_error "DOCKERHUB_USERNAME not set. Export it first:"
  echo "  export DOCKERHUB_USERNAME=your-username"
  exit 1
fi

# Configuration
PROJECT_ROOT="/mnt/d/project/resume-buddy"
DOCKERHUB_USER="$DOCKERHUB_USERNAME"

echo_info "Starting Resume Buddy EKS deployment..."
echo_info "Docker Hub user: $DOCKERHUB_USER"
echo_info "Project root: $PROJECT_ROOT"

# Phase 1: Build and push Docker images
echo ""
echo_info "═══════════════════════════════════════════════"
echo_info "Phase 1: Building and pushing Docker images"
echo_info "═══════════════════════════════════════════════"

# 1. Resume API
echo_info "[1/4] Building resume-api..."
cd "$PROJECT_ROOT/backend"
docker build -t ${DOCKERHUB_USER}/resume-api:latest .
echo_info "Pushing resume-api..."
docker push ${DOCKERHUB_USER}/resume-api:latest

# 2. Job Search Service
echo_info "[2/4] Building job-search..."
cd "$PROJECT_ROOT/job-search-service"
docker build -t ${DOCKERHUB_USER}/job-search:latest .
echo_info "Pushing job-search..."
docker push ${DOCKERHUB_USER}/job-search:latest

# 3. Interview Practice
echo_info "[3/4] Building interview-practice..."
cd "$PROJECT_ROOT/interview-practice-service"
docker build -t ${DOCKERHUB_USER}/interview-practice:latest .
echo_info "Pushing interview-practice..."
docker push ${DOCKERHUB_USER}/interview-practice:latest

# 4. Frontend
echo_info "[4/4] Building frontend..."
cd "$PROJECT_ROOT/frontend"
docker build -t ${DOCKERHUB_USER}/frontend:latest .
echo_info "Pushing frontend..."
docker push ${DOCKERHUB_USER}/frontend:latest

echo_info "✅ All images built and pushed successfully"

# Phase 2: Update Kubernetes manifests with Docker Hub username
echo ""
echo_info "═══════════════════════════════════════════════"
echo_info "Phase 2: Updating Kubernetes manifests"
echo_info "═══════════════════════════════════════════════"

K8S_DIR="$PROJECT_ROOT/interview-practice-service/aws_poc/k8s"

sed -i "s|YOUR_DOCKERHUB_USERNAME|${DOCKERHUB_USER}|g" "$K8S_DIR/resume-api.yaml"
sed -i "s|YOUR_DOCKERHUB_USERNAME|${DOCKERHUB_USER}|g" "$K8S_DIR/job-search.yaml"
sed -i "s|YOUR_DOCKERHUB_USERNAME|${DOCKERHUB_USER}|g" "$K8S_DIR/interview-practice.yaml"
sed -i "s|YOUR_DOCKERHUB_USERNAME|${DOCKERHUB_USER}|g" "$K8S_DIR/frontend.yaml"

echo_info "✅ Manifests updated"

# Phase 3: Deploy to EKS
echo ""
echo_info "═══════════════════════════════════════════════"
echo_info "Phase 3: Deploying to EKS"
echo_info "═══════════════════════════════════════════════"

# Create namespace and secrets (if not already exist)
echo_info "Setting up namespace and secrets..."
cd "$PROJECT_ROOT/interview-practice-service/aws_poc"

# Load environment variables from parent .env if exists
if [ -f "$PROJECT_ROOT/.env" ]; then
  echo_info "Loading environment variables from .env..."
  export $(cat "$PROJECT_ROOT/.env" | grep -v '^#' | xargs)
fi

./scripts/setup-app-namespace.sh || echo_warn "Namespace may already exist, continuing..."

echo_info "Applying ConfigMap..."
kubectl apply -f k8s/app-namespace.yaml

# Deploy services in order
echo_info "Deploying Redis..."
kubectl apply -f k8s/redis.yaml

echo_info "Waiting for Redis to be ready..."
kubectl wait --for=condition=ready pod -l app=redis -n app-services --timeout=120s || echo_warn "Redis may still be starting..."

echo_info "Deploying backend services..."
kubectl apply -f k8s/resume-api.yaml
kubectl apply -f k8s/job-search.yaml
kubectl apply -f k8s/interview-practice.yaml

echo_info "Deploying frontend..."
kubectl apply -f k8s/frontend.yaml

echo_info "✅ All services deployed"

# Phase 4: Monitor deployment
echo ""
echo_info "═══════════════════════════════════════════════"
echo_info "Phase 4: Monitoring deployment status"
echo_info "═══════════════════════════════════════════════"

echo_info "Waiting for pods to start (this may take 2-5 minutes)..."
sleep 10

kubectl get pods -n app-services

echo ""
echo_info "═══════════════════════════════════════════════"
echo_info "Deployment initiated successfully!"
echo_info "═══════════════════════════════════════════════"
echo ""
echo_info "Next steps:"
echo_info "1. Monitor pods:    kubectl get pods -n app-services -w"
echo_info "2. Check logs:      kubectl logs -n app-services -l app=<service-name> -f"
echo_info "3. Get endpoints:   kubectl get svc -n app-services"
echo_info "4. Check resources: kubectl top nodes"
echo ""
echo_warn "If pods are stuck in Pending, you may need to add a CPU node:"
echo "  eksctl create nodegroup --cluster=interview-practice-nim --region=us-east-1 --name=cpu-nodes --instance-types=t3.medium --nodes=1"

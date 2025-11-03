#!/bin/bash
# NVIDIA NIM POC - EKS Cluster Creation
# Creates GPU-enabled EKS cluster in us-east-1 with 2x g5.xlarge nodes

set -e  # Exit on error

echo "========================================="
echo "EKS Cluster Creation for NVIDIA NIM POC"
echo "========================================="
echo ""

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

# Configuration
CLUSTER_NAME="interview-practice-nim"
REGION="us-east-1"
NODE_TYPE="g5.xlarge"
MIN_NODES=1
MAX_NODES=2
INITIAL_NODES=2

echo -e "${YELLOW}Cluster Configuration:${NC}"
echo "  - Name: ${CLUSTER_NAME}"
echo "  - Region: ${REGION}"
echo "  - Node Type: ${NODE_TYPE} (A10G GPU, 4 vCPU, 16GB RAM)"
echo "  - Initial Nodes: ${INITIAL_NODES}"
echo "  - Min/Max Nodes: ${MIN_NODES}/${MAX_NODES}"
echo "  - Cost: ~\$0.53/hr per node × 2 + \$0.10/hr control plane = ~\$1.16/hr"
echo ""
echo -e "${RED}WARNING: This will start consuming your AWS budget!${NC}"
echo ""
read -p "Continue? (yes/no): " CONFIRM

if [[ "$CONFIRM" != "yes" ]]; then
    echo "Cluster creation cancelled"
    exit 0
fi

echo ""
echo -e "${BLUE}Step 1/4: Creating EKS cluster${NC}"
echo "--------------------------------------"
echo "This will take 15-20 minutes..."
echo ""

# Create cluster with eksctl
eksctl create cluster \
  --name ${CLUSTER_NAME} \
  --region ${REGION} \
  --node-type ${NODE_TYPE} \
  --nodes ${INITIAL_NODES} \
  --nodes-min ${MIN_NODES} \
  --nodes-max ${MAX_NODES} \
  --with-oidc \
  --managed \
  --version 1.28

echo -e "${GREEN}✓ EKS cluster created successfully${NC}"

echo ""
echo -e "${BLUE}Step 2/4: Verifying cluster access${NC}"
echo "--------------------------------------"

# Update kubeconfig
aws eks update-kubeconfig --region ${REGION} --name ${CLUSTER_NAME}

# Verify cluster access
echo "Cluster info:"
kubectl cluster-info

echo ""
echo "Nodes:"
kubectl get nodes

echo -e "${GREEN}✓ Cluster access verified${NC}"

echo ""
echo -e "${BLUE}Step 3/4: Installing NVIDIA GPU Operator${NC}"
echo "--------------------------------------"

# Create namespace
kubectl create ns gpu-operator

# Add NVIDIA Helm repo
helm repo add nvidia https://helm.ngc.nvidia.com/nvidia
helm repo update

# Install GPU operator
echo "Installing GPU operator (this may take 5-10 minutes)..."
helm install --wait --generate-name \
  -n gpu-operator \
  nvidia/gpu-operator

echo -e "${GREEN}✓ GPU operator installed${NC}"

echo ""
echo -e "${BLUE}Step 4/4: Verifying GPU availability${NC}"
echo "--------------------------------------"

# Wait for GPU operator pods to be ready
echo "Waiting for GPU operator pods to be ready..."
kubectl wait --for=condition=ready pod -l app=nvidia-operator-validator -n gpu-operator --timeout=600s

# Check GPU allocation
echo ""
echo "GPU allocation on nodes:"
kubectl get nodes "-o=custom-columns=NAME:.metadata.name,GPU:.status.allocatable.nvidia\.com/gpu"

echo -e "${GREEN}✓ GPU availability confirmed${NC}"

echo ""
echo -e "${GREEN}=========================================${NC}"
echo -e "${GREEN}EKS Cluster Setup Complete!${NC}"
echo -e "${GREEN}=========================================${NC}"
echo ""
echo "Cluster Details:"
echo "  - Name: ${CLUSTER_NAME}"
echo "  - Region: ${REGION}"
echo "  - Endpoint: $(kubectl config view --minify -o jsonpath='{.clusters[0].cluster.server}')"
echo "  - Nodes with GPUs: ${INITIAL_NODES}"
echo ""
echo -e "${YELLOW}Important:${NC}"
echo "  - Cluster is now running and consuming budget (~\$1.16/hr with 2 nodes)"
echo "  - Monitor costs at: https://console.aws.amazon.com/cost-management/home"
echo "  - To delete cluster later: ${BLUE}eksctl delete cluster --name ${CLUSTER_NAME} --region ${REGION}${NC}"
echo ""
echo -e "${BLUE}Useful commands:${NC}"
echo "  - Get cluster info: kubectl cluster-info"
echo "  - Get nodes: kubectl get nodes"
echo "  - Get all pods: kubectl get pods -A"
echo "  - Check GPU operator: kubectl get pods -n gpu-operator"
echo ""
echo -e "${YELLOW}Next steps:${NC}"
echo "1. Create NGC secret: ${BLUE}kubectl create secret generic ngc-secret -n nim-services --from-literal=api-key=YOUR_NGC_KEY${NC}"
echo "2. Deploy Nemotron: ${BLUE}kubectl apply -f aws_poc/k8s/nemotron-deployment.yaml${NC}"
echo "3. Deploy NV-Embed: ${BLUE}kubectl apply -f aws_poc/k8s/embedding-deployment.yaml${NC}"
echo ""

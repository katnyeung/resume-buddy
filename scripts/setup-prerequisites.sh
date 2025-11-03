#!/bin/bash
# NVIDIA NIM POC - Prerequisites Installation Script
# Installs AWS CLI, eksctl, kubectl on Ubuntu/WSL2

set -e  # Exit on error

echo "========================================="
echo "NVIDIA NIM POC - Prerequisites Setup"
echo "========================================="
echo ""

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if running on Linux
if [[ "$(uname)" != "Linux" ]]; then
    echo -e "${YELLOW}Warning: This script is designed for Linux/WSL2${NC}"
    echo "You may need to install tools manually on macOS/Windows"
    exit 1
fi

echo -e "${BLUE}Step 1/4: Installing AWS CLI v2${NC}"
echo "--------------------------------------"

# Check if AWS CLI already installed
if command -v aws &> /dev/null; then
    echo -e "${GREEN}✓ AWS CLI already installed: $(aws --version)${NC}"
else
    echo "Installing AWS CLI v2..."
    cd /tmp
    curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
    unzip -q awscliv2.zip
    sudo ./aws/install
    rm -rf aws awscliv2.zip
    echo -e "${GREEN}✓ AWS CLI installed successfully${NC}"
fi

echo ""
echo -e "${BLUE}Step 2/4: Installing eksctl${NC}"
echo "--------------------------------------"

if command -v eksctl &> /dev/null; then
    echo -e "${GREEN}✓ eksctl already installed: $(eksctl version)${NC}"
else
    echo "Installing eksctl..."
    ARCH=amd64
    PLATFORM=$(uname -s)_$ARCH

    curl -sLO "https://github.com/eksctl-io/eksctl/releases/latest/download/eksctl_$PLATFORM.tar.gz"

    tar -xzf eksctl_$PLATFORM.tar.gz -C /tmp && rm eksctl_$PLATFORM.tar.gz
    sudo mv /tmp/eksctl /usr/local/bin
    echo -e "${GREEN}✓ eksctl installed successfully${NC}"
fi

echo ""
echo -e "${BLUE}Step 3/4: Installing kubectl${NC}"
echo "--------------------------------------"

if command -v kubectl &> /dev/null; then
    echo -e "${GREEN}✓ kubectl already installed: $(kubectl version --client --short 2>/dev/null || kubectl version --client)${NC}"
else
    echo "Installing kubectl..."

    # Get latest stable version
    KUBECTL_VERSION=$(curl -L -s https://dl.k8s.io/release/stable.txt)

    curl -LO "https://dl.k8s.io/release/$KUBECTL_VERSION/bin/linux/amd64/kubectl"
    curl -LO "https://dl.k8s.io/release/$KUBECTL_VERSION/bin/linux/amd64/kubectl.sha256"

    # Verify checksum
    echo "$(cat kubectl.sha256)  kubectl" | sha256sum --check

    sudo install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl
    rm kubectl kubectl.sha256
    echo -e "${GREEN}✓ kubectl installed successfully${NC}"
fi

echo ""
echo -e "${BLUE}Step 4/4: Installing Helm${NC}"
echo "--------------------------------------"

if command -v helm &> /dev/null; then
    echo -e "${GREEN}✓ Helm already installed: $(helm version --short)${NC}"
else
    echo "Installing Helm..."
    curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
    echo -e "${GREEN}✓ Helm installed successfully${NC}"
fi

echo ""
echo -e "${GREEN}=========================================${NC}"
echo -e "${GREEN}Prerequisites Installation Complete!${NC}"
echo -e "${GREEN}=========================================${NC}"
echo ""
echo "Installed tools:"
echo "  - AWS CLI: $(aws --version)"
echo "  - eksctl:  $(eksctl version)"
echo "  - kubectl: $(kubectl version --client --short 2>/dev/null || kubectl version --client | head -n1)"
echo "  - helm:    $(helm version --short)"
echo ""
echo -e "${YELLOW}Next steps:${NC}"
echo "1. Configure AWS credentials: ${BLUE}aws configure${NC}"
echo "2. Run billing alarm script: ${BLUE}./aws_poc/scripts/create-billing-alarm.sh${NC}"
echo "3. Create EKS cluster: ${BLUE}./aws_poc/scripts/create-eks-cluster.sh${NC}"
echo ""

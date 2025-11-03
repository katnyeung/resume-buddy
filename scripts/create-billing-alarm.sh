#!/bin/bash
# NVIDIA NIM POC - AWS Billing Alarm Setup
# Creates CloudWatch alarm at $80 (90% of $100 budget)

set -e  # Exit on error

echo "========================================="
echo "AWS Billing Alarm Setup"
echo "========================================="
echo ""

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

# Configuration
THRESHOLD=80
BUDGET=100
ALARM_NAME="NVIDIA-NIM-POC-Budget-Alarm"
ALARM_DESCRIPTION="Alert when AWS spending reaches $80 (90% of $100 hackathon budget)"

# Prompt for email
echo -e "${BLUE}Enter your email address for billing alerts:${NC}"
read -p "Email: " EMAIL_ADDRESS

if [[ ! "$EMAIL_ADDRESS" =~ ^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$ ]]; then
    echo -e "${RED}Error: Invalid email address${NC}"
    exit 1
fi

echo ""
echo -e "${BLUE}Step 1/3: Creating SNS topic for notifications${NC}"
echo "--------------------------------------"

# Create SNS topic
SNS_TOPIC_ARN=$(aws sns create-topic \
    --name ${ALARM_NAME}-Topic \
    --region us-east-1 \
    --query 'TopicArn' \
    --output text)

echo -e "${GREEN}✓ SNS Topic created: ${SNS_TOPIC_ARN}${NC}"

echo ""
echo -e "${BLUE}Step 2/3: Subscribing email to SNS topic${NC}"
echo "--------------------------------------"

# Subscribe email to topic
aws sns subscribe \
    --topic-arn ${SNS_TOPIC_ARN} \
    --protocol email \
    --notification-endpoint ${EMAIL_ADDRESS} \
    --region us-east-1

echo -e "${GREEN}✓ Email subscription created${NC}"
echo -e "${YELLOW}⚠ Check your email and confirm the subscription!${NC}"
echo ""
read -p "Press Enter after confirming the subscription email..."

echo ""
echo -e "${BLUE}Step 3/3: Creating CloudWatch billing alarm${NC}"
echo "--------------------------------------"

# Create billing alarm (must be in us-east-1)
aws cloudwatch put-metric-alarm \
    --alarm-name "${ALARM_NAME}" \
    --alarm-description "${ALARM_DESCRIPTION}" \
    --alarm-actions ${SNS_TOPIC_ARN} \
    --metric-name EstimatedCharges \
    --namespace AWS/Billing \
    --statistic Maximum \
    --period 21600 \
    --evaluation-periods 1 \
    --threshold ${THRESHOLD} \
    --comparison-operator GreaterThanThreshold \
    --dimensions Name=Currency,Value=USD \
    --region us-east-1

echo -e "${GREEN}✓ Billing alarm created successfully${NC}"

echo ""
echo -e "${GREEN}=========================================${NC}"
echo -e "${GREEN}Billing Alarm Setup Complete!${NC}"
echo -e "${GREEN}=========================================${NC}"
echo ""
echo "Configuration:"
echo "  - Alarm Name: ${ALARM_NAME}"
echo "  - Threshold: \$${THRESHOLD} (${THRESHOLD}% of \$${BUDGET} budget)"
echo "  - Email: ${EMAIL_ADDRESS}"
echo "  - Region: us-east-1 (billing metrics only available here)"
echo ""
echo -e "${YELLOW}Important:${NC}"
echo "  - Billing data updates every 6 hours"
echo "  - You'll receive email when spending exceeds \$${THRESHOLD}"
echo "  - Monitor AWS Cost Explorer for real-time costs"
echo ""
echo -e "${BLUE}To check alarm status:${NC}"
echo "  aws cloudwatch describe-alarms --alarm-names ${ALARM_NAME} --region us-east-1"
echo ""
echo -e "${YELLOW}Next step:${NC}"
echo "  Run EKS cluster creation: ${BLUE}./aws_poc/scripts/create-eks-cluster.sh${NC}"
echo ""

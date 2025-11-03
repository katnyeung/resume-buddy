#!/bin/bash

# Setup app-services namespace with secrets from environment variables
# Run this script after setting environment variables or sourcing .env file

set -e

echo "Creating app-services namespace..."
kubectl create namespace app-services --dry-run=client -o yaml | kubectl apply -f -

echo "Creating secrets from environment variables..."
kubectl create secret generic app-secrets \
  --from-literal=SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-}" \
  --from-literal=POSTGRES_PASSWORD_INTERVIEW="${POSTGRES_PASSWORD_INTERVIEW:-}" \
  --from-literal=OPENAI_API_KEY="${OPENAI_API_KEY:-}" \
  --from-literal=XAI_API_KEY="${XAI_API_KEY:-}" \
  --from-literal=ONET_PASSWORD="${ONET_PASSWORD:-}" \
  --from-literal=NGC_API_KEY="${NGC_API_KEY:-}" \
  --from-literal=ADZUNA_APP_ID="${ADZUNA_APP_ID:-}" \
  --from-literal=ADZUNA_APP_KEY="${ADZUNA_APP_KEY:-}" \
  --from-literal=REED_API_KEY="${REED_API_KEY:-}" \
  --from-literal=JSEARCH_API_KEY="${JSEARCH_API_KEY:-}" \
  --from-literal=STRIPE_SECRET_KEY="${STRIPE_SECRET_KEY:-}" \
  --from-literal=STRIPE_WEBHOOK_SECRET="${STRIPE_WEBHOOK_SECRET:-}" \
  --from-literal=STRIPE_PRICE_ID_200="${STRIPE_PRICE_ID_200:-}" \
  --from-literal=STRIPE_PRICE_ID_500="${STRIPE_PRICE_ID_500:-}" \
  --from-literal=STRIPE_PRICE_ID_1000="${STRIPE_PRICE_ID_1000:-}" \
  --from-literal=AWS_SES_USERNAME="${AWS_SES_USERNAME:-}" \
  --from-literal=AWS_SES_PASSWORD="${AWS_SES_PASSWORD:-}" \
  --from-literal=RUNPOD_ENDPOINT_ID="${RUNPOD_ENDPOINT_ID:-}" \
  --from-literal=RUNPOD_API_KEY="${RUNPOD_API_KEY:-}" \
  --namespace app-services \
  --dry-run=client -o yaml | kubectl apply -f -

echo "✅ Namespace and secrets created successfully"
echo ""
echo "Next steps:"
echo "1. Update ConfigMap values in k8s/app-namespace.yaml with your Neon database URLs"
echo "2. Apply: kubectl apply -f k8s/app-namespace.yaml"

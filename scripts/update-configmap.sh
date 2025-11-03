#!/bin/bash

# Script to update ConfigMap with actual Neon database URLs
# Run this after editing the values below

set -e

echo "Updating ConfigMap with Neon database URLs..."

# EDIT THESE VALUES:
NEON_HOST="your-neon-host.neon.tech"
NEON_RESUME_DB="resumebuddy"
NEON_JOBSEARCH_DB="jobsearch"
NEON_INTERVIEW_DB="interview_practice"
ONET_USERNAME="your-onet-username"

# Update the ConfigMap file
cat > k8s/app-namespace.yaml << EOF
---
apiVersion: v1
kind: Namespace
metadata:
  name: app-services
  labels:
    name: app-services

---
apiVersion: v1
kind: ConfigMap
metadata:
  name: app-config
  namespace: app-services
data:
  # Database URLs (Neon PostgreSQL)
  SPRING_DATASOURCE_URL_RESUME: "jdbc:postgresql://${NEON_HOST}/${NEON_RESUME_DB}?sslmode=require"
  SPRING_DATASOURCE_URL_JOBSEARCH: "jdbc:postgresql://${NEON_HOST}/${NEON_JOBSEARCH_DB}?sslmode=require"
  POSTGRES_HOST_INTERVIEW: "${NEON_HOST}"
  POSTGRES_DB_INTERVIEW: "${NEON_INTERVIEW_DB}"

  # Redis (internal to cluster)
  REDIS_HOST: "redis-service.app-services.svc.cluster.local"
  REDIS_PORT: "6379"

  # NIM Services (cross-namespace)
  NEMOTRON_URL: "http://nemotron-service.nim-services.svc.cluster.local:8000"
  EMBED_URL: "http://embedding-service.nim-services.svc.cluster.local:8001"

  # Service URLs (internal)
  RESUME_API_URL: "http://resume-api-service.app-services.svc.cluster.local:8080"
  JOBSEARCH_API_URL: "http://job-search-service.app-services.svc.cluster.local:8085"
  INTERVIEW_API_URL: "http://interview-practice-service.app-services.svc.cluster.local:8086"

  # Frontend
  NEXT_PUBLIC_API_URL: "http://resume-api-service.app-services.svc.cluster.local:8080/api"

  # O*NET
  ONET_USERNAME: "${ONET_USERNAME}"

  # File upload (for hackathon, disable Docling)
  DOCLING_SERVICE_URL: "http://localhost:8081"
  DOCLING_USE_RUNPOD: "false"
EOF

echo "✅ ConfigMap updated in k8s/app-namespace.yaml"
echo ""
echo "To apply changes:"
echo "  kubectl apply -f k8s/app-namespace.yaml"
echo "  kubectl rollout restart deployment -n app-services"

# Resume Buddy - AI-Powered Resume Enhancement Platform

## 🚀 Overview

AI-powered platform for resume analysis, job matching, and interview practice with NVIDIA NIM on AWS EKS.

## 🎯 AWS NVIDIA Hackathon - EKS Deployment

**Quick Deploy**:
```bash
./scripts/create-eks-cluster.sh      # Create EKS cluster with GPU nodes
./scripts/setup-prerequisites.sh     # Install Helm, GPU operator, NGC secret
./scripts/build-and-deploy-all.sh    # Deploy all services
```

**Key Folders**:
- `/k8s/` - Kubernetes manifests (deployments, services, config)
- `/scripts/` - EKS deployment automation scripts

## 🏗️ Architecture

```
EKS Cluster → NVIDIA NIM (Nemotron LLM + NV-Embed-v2) on GPU
           → Backend Services (Spring Boot)
           → Frontend (Next.js)
           → Redis (Vectors)
External   → PostgreSQL (Neon) + Neo4j (Aura) + RunPod (Docling)
```

## 📁 Project Structure

```
resume-buddy/
├── backend/                    # Spring Boot Resume API
├── job-search-service/         # Spring Boot Job Search API
├── interview-practice-service/ # Python FastAPI Interview Practice
├── frontend/                   # Next.js Application
│
├── k8s/                        # ⭐ Kubernetes Manifests (EKS Deployment)
│   ├── app-namespace.yaml      # ConfigMap + Secrets
│   ├── *-deployment.yaml       # Service deployments
│   ├── nemotron-deployment.yaml   # NVIDIA NIM LLM (GPU)
│   └── embedding-deployment.yaml  # NVIDIA NIM Embeddings (GPU)
│
└── scripts/                    # ⭐ EKS Deployment Scripts
    ├── create-eks-cluster.sh
    ├── setup-prerequisites.sh
    └── build-and-deploy-all.sh
```

## 🛠️ Tech Stack

- **Backend**: Spring Boot + PostgreSQL + Neo4j + Redis
- **Frontend**: Next.js 14 + TypeScript + Lexical Editor
- **AI/ML**: NVIDIA NIM (Nemotron LLM + NV-Embed-v2) on EKS
- **Infrastructure**: Amazon EKS + g5.xlarge GPU nodes (A10G)

## 🚀 Quick Start - EKS Deployment

```bash
./scripts/create-eks-cluster.sh      # 15-20 min
./scripts/setup-prerequisites.sh
./scripts/build-and-deploy-all.sh
```

**Cost**: ~$1.16/hr (2x g5.xlarge GPU nodes)

## ✨ Features

- Resume analysis with O*NET job mapping
- Vector-based job matching with NVIDIA embeddings
- AI interview practice with real-time voice
- Stripe payments & user credits
- Multi-source job crawling (Reed, Adzuna, JSearch)

---

**For detailed setup and configuration, see [CLAUDE.md](./CLAUDE.md)**
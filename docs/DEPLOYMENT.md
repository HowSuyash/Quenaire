# Quenaire Deployment Guide

## Overview

This guide covers deploying Quenaire in production environments using Docker Compose or Kubernetes.

## Prerequisites

- Docker 24+ and Docker Compose 2.20+
- Domain name with SSL certificate
- Stripe account for billing
- Keycloak server (or use included)
- AWS/GCP/Azure account for S3-compatible storage

## Quick Start (Docker Compose)

### 1. Configure Environment

```bash
cp .env.example .env
# Edit .env with your values
```

Required environment variables:
```env
# Database
DB_PASSWORD=secure-random-password

# Redis
REDIS_PASSWORD=secure-random-password

# Keycloak
KEYCLOAK_ADMIN_SECRET=secure-random-secret

# Stripe
STRIPE_SECRET_KEY=sk_live_...
STRIPE_PUBLISHABLE_KEY=pk_live_...
STRIPE_WEBHOOK_SECRET=whsec_...

# Storage
STORAGE_TYPE=s3
STORAGE_S3_BUCKET=your-bucket
STORAGE_S3_REGION=us-east-1
STORAGE_S3_ACCESS_KEY=...
STORAGE_S3_SECRET_KEY=...

# Monitoring
GRAFANA_PASSWORD=secure-password
BACKUP_S3_BUCKET=your-backup-bucket
```

### 2. Start Services

```bash
docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

### 3. Initialize Keycloak

1. Access Keycloak at `https://auth.yourdomain.com`
2. Login with admin/admin
3. Import realm from `keycloak/realm-export.json`
4. Create client `quenaire-frontend` with redirect URIs
5. Copy client secret to `KEYCLOAK_ADMIN_CLIENT_SECRET`

### 4. Pull LLM Models

```bash
docker exec -it quenaire-ollama-prod ollama pull llama3.1:8b
docker exec -it quenaire-ollama-prod ollama pull nomic-embed-text
```

### 5. Verify Deployment

```bash
# Check health
curl https://api.yourdomain.com/api/actuator/health

# Check frontend
curl https://app.yourdomain.com
```

## Production Checklist

- [ ] SSL certificates configured
- [ ] Database backups scheduled
- [ ] Monitoring alerts configured
- [ ] Log retention policies set
- [ ] Stripe webhooks registered
- [ ] DNS records configured
- [ ] Firewall rules applied
- [ ] Secret rotation scheduled

## Kubernetes Deployment

### Prerequisites

- Kubernetes 1.28+
- Helm 3.12+
- cert-manager for SSL
- External DNS for DNS management
- CSI driver for persistent volumes

### Install with Helm

```bash
helm repo add quenaire https://charts.quenaire.io
helm install quenaire quenaire/quenaire \
  --namespace quenaire \
  --create-namespace \
  --values values-prod.yaml
```

### Required Helm Values

```yaml
global:
  domain: yourdomain.com
  environment: production

postgresql:
  enabled: true
  auth:
    password: ${DB_PASSWORD}

redis:
  enabled: true
  auth:
    password: ${REDIS_PASSWORD}

keycloak:
  enabled: true
  auth:
    adminPassword: ${KEYCLOAK_ADMIN_PASSWORD}

ollama:
  enabled: true
  models:
    - llama3.1:8b
    - nomic-embed-text

monitoring:
  prometheus:
    enabled: true
    retention: 30d
  grafana:
    enabled: true
    adminPassword: ${GRAFANA_PASSWORD}
  loki:
    enabled: true

backup:
  enabled: true
  schedule: "0 3 * * *"
  s3Bucket: ${BACKUP_S3_BUCKET}

ingress:
  enabled: true
  className: nginx
  annotations:
    cert-manager.io/cluster-issuer: letsencrypt-prod
  tls:
    - secretName: quenaire-tls
      hosts:
        - api.yourdomain.com
        - app.yourdomain.com
        - auth.yourdomain.com
```

## Scaling Considerations

### Application
- Horizontal pod autoscaler based on CPU/memory
- Minimum 3 replicas for HA
- Connection pooling via HikariCP (max 20 connections)

### Database
- Read replicas for query scaling
- PgBouncer for connection pooling
- Automated failover with Patroni

### Redis
- Cluster mode for HA
- Persistence enabled (AOF + RDB)

### Kafka
- Minimum 3 brokers
- ISR replication factor 3
- Topic partitions ≥ consumer count

## Security Hardening

### Network
- Private subnets for all services
- Network policies restricting inter-service communication
- WAF for ingress traffic

### Secrets
- External secrets operator (HashiCorp Vault/AWS Secrets Manager)
- Automatic rotation every 90 days

### Compliance
- SOC2 Type II ready
- Audit logging enabled
- Data encryption at rest and in transit

## Troubleshooting

### Common Issues

| Issue | Solution |
|-------|----------|
| App won't start | Check DB connectivity, Flyway migrations |
| Keycloak auth fails | Verify realm config, client secrets |
| Ollama OOM | Increase memory limit, use smaller model |
| High latency | Check DB indexes, connection pool |
| Backup fails | Verify S3 permissions, disk space |

### Useful Commands

```bash
# View logs
docker-compose -f docker-compose.prod.yml logs -f app

# Database shell
docker exec -it quenaire-postgres-prod psql -U quenaire -d quenaire

# Run migrations manually
docker exec quenaire-app java -jar app.jar flyway migrate

# Check metrics
curl https://api.yourdomain.com/api/actuator/prometheus
```

## Maintenance

### Weekly
- Review backup success
- Check disk space
- Review error logs

### Monthly
- Rotate secrets
- Update base images
- Review capacity
- Test restore procedure

### Quarterly
- Security scan
- Dependency updates
- Disaster recovery drill
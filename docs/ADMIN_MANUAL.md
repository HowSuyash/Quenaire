# Quenaire Administrator Manual

## Table of Contents

1. [Getting Started](#getting-started)
2. [User Management](#user-management)
3. [Tenant Configuration](#tenant-configuration)
4. [Billing & Subscriptions](#billing--subscriptions)
5. [Integrations](#integrations)
5. [Document Management](#document-management)
6. [Questionnaire Workflow](#questionnaire-workflow)
7. [Monitoring & Maintenance](#monitoring--maintenance)
8. [Troubleshooting](#troubleshooting)

---

## Getting Started

### Initial Setup

1. **Access the Admin Panel**
   - Navigate to `https://app.yourdomain.com`
   - Sign in with your owner account

2. **Configure Organization**
   - Go to Settings → General
   - Set organization name, logo, and branding
   - Configure default language and timezone

3. **Set Up Keycloak SSO**
   - Navigate to Settings → Integrations → Keycloak
   - Enter Keycloak server URL, realm, and client credentials
   - Test connection and save

---

## User Management

### Roles & Permissions

| Role | Description | Permissions |
|------|-------------|-------------|
| **Owner** | Organization owner | Full access, billing, user management |
| **Admin** | Administrator | Manage users, questionnaires, documents |
| **Member** | Team member | Create/edit questionnaires, upload documents |
| **Viewer** | Read-only access | View questionnaires and documents |
| **API User** | Programmatic access | API access only |

### Adding Users

1. Go to Settings → Team
2. Click "Invite User"
3. Enter email and select role
4. User receives invitation email
5. User accepts and sets password

### Managing Users

- **Edit Role**: Click user → Edit → Change role
- **Deactivate**: Click user → Deactivate (preserves data)
- **Remove**: Click user → Remove (deletes user data)

---

## Tenant Configuration

### Plans

| Plan | Monthly | Annual | Questionnaires | Documents | Questions | API | Integrations | SSO |
|------|---------|--------|----------------|-----------|-----------|-----|--------------|-----|
| Free | $0 | $0 | 5/month | 100 | 1,000 | ❌ | ❌ | ❌ |
| Starter | $49 | $490 | 20/month | 500 | 5,000 | ✅ | ❌ | ❌ |
| Professional | $199 | $1,990 | 100/month | 2,000 | 25,000 | ✅ | ✅ | ❌ |
| Enterprise | $599 | $5,990 | 500/month | 10,000 | 100,000 | ✅ | ✅ | ✅ |
| Custom | Contact | Contact | Unlimited | Unlimited | Unlimited | ✅ | ✅ | ✅ |

### Upgrading Plan

1. Go to Settings → Billing
2. Click "Upgrade Plan"
3. Select new plan
4. Enter payment method
5. Confirm upgrade

### Usage Limits

Monitor usage at Settings → Billing → Usage:
- Questionnaires this month
- Documents stored
- Questions processed
- API calls

---

## Billing & Subscriptions

### Payment Methods

- Credit/Debit cards (via Stripe)
- ACH bank transfer (Enterprise+)
- Invoice billing (Enterprise+)

### Invoices

- Generated monthly on billing date
- Available at Settings → Billing → Invoices
- Download PDF for records
- Auto-pay enabled by default

### Managing Subscription

- **Upgrade**: Immediate, prorated charge
- **Downgrade**: Takes effect at period end
- **Cancel**: Access continues until period end
- **Pause**: Available for Enterprise plans

### Webhooks

Configure Stripe webhook endpoint:
```
URL: https://api.yourdomain.com/api/billing/webhook
Events:
  - invoice.payment_succeeded
  - invoice.payment_failed
  - customer.subscription.updated
  - customer.subscription.deleted
  - customer.subscription.trial_will_end
```

---

## Integrations

### Jira

**Prerequisites:**
- Jira Cloud or Server
- API token (Profile → Security → API tokens)
- Project with issue creation permissions

**Configuration:**
1. Settings → Integrations → Jira
2. Base URL: `https://your-domain.atlassian.net`
3. Username: Your email
4. API Token: Generated token
5. Project Key: e.g., `SEC`
6. Click "Test Connection" → "Save"

**Usage:**
- Questionnaire → Actions → Create Jira Issue
- Creates parent issue + sub-tasks per question
- Syncs status changes back to Quenaire

### Confluence

**Prerequisites:**
- Confluence Cloud or Server
- API token
- Space with create permissions

**Configuration:**
1. Settings → Integrations → Confluence
2. Base URL: `https://your-domain.atlassian.net/wiki`
3. Username: Your email
4. API Token: Generated token
5. Space Key: e.g., `SEC`
6. Click "Test Connection" → "Save"

**Usage:**
- Questionnaire → Actions → Publish to Confluence
- Creates formatted page with all Q&A
- Documents can be published as reference pages

### GitHub

**Prerequisites:**
- GitHub Personal Access Token (classic)
- Repo with Issues enabled
- Token scopes: `repo`, `workflow`

**Configuration:**
1. Settings → Integrations → GitHub
2. Token: `ghp_...`
3. Owner: Organization or username
4. Repository: e.g., `security-questionnaires`
5. Click "Test Connection" → "Save"

**Usage:**
- Questionnaire → Actions → Create GitHub Issue
- Creates issue with checklist of questions
- Sub-issues for individual questions

### Slack

**Prerequisites:**
- Slack App with Bot Token
- Scopes: `chat:write`, `channels:read`, `groups:read`, `commands`
- Install to workspace

**Configuration:**
1. Settings → Integrations → Slack
2. Bot Token: `xoxb-...`
3. Signing Secret: From app config
4. Default Channel: `#security-reviews`
5. Click "Test Connection" → "Save"

**Usage:**
- Real-time notifications for:
  - New questionnaires
  - Questions needing review
  - Review completion
  - Document indexing
- Interactive approval buttons in Slack
- `/quenaire` slash command for quick actions

---

## Document Management

### Uploading Documents

1. Navigate to Documents
2. Click "Upload Document"
3. Drag & drop or browse files
4. Supported formats: PDF, DOCX, XLSX, TXT, MD
5. Max file size: 50MB
6. Add optional description
6. Click "Upload"

### Document Processing

- **Status**: Uploading → Processing → Indexed
- **Processing**: Text extraction, chunking, embedding
- **Failed**: Check error message, re-upload if needed

### Managing Documents

- **View**: Click document → View details and chunks
- **Search**: Full-text search across all documents
- **Delete**: Click document → Delete (soft delete)
- **Export**: Download original file

### Knowledge Base Best Practices

1. Organize by category (policies, architecture, compliance)
2. Use descriptive titles
3. Keep documents updated
4. Tag with relevant keywords
5. Regular cleanup of outdated docs

---

## Questionnaire Workflow

### Creating Questionnaires

1. Navigate to Questionnaires
2. Click "New Questionnaire"
3. Choose source:
   - **Upload Excel/CSV**: Structured questionnaire
   - **Upload PDF/Word**: Auto-parsed
   - **Portal Link**: Import from vendor portal
   - **API Import**: Programmatic creation
4. Fill in metadata (title, due date, assignee)
5. Click "Create"

### Processing

1. Questionnaire status: Draft → Processing
2. Click "Process" to start AI parsing
3. Parser extracts questions and sections
4. Status: Processing → Ready for Review

### Review Process

#### Side-by-Side Review

For each question:
- **Question**: Original text, type, required flag
- **AI Answer**: Generated with citations
- **Citations**: Click to view source document chunks

#### Actions

| Action | Description |
|--------|-------------|
| **Approve** | Accept AI answer as-is |
| **Edit** | Modify answer, mark as human-edited |
| **Reject** | Reject with reason, requires new answer |
| **Skip** | Mark as not applicable |

#### Progress Tracking

- Dashboard shows: Approved / Total
- Progress bar at top of review page
- Filter by status: All, Pending, Needs Review, Approved, Rejected

### Exporting

When all questions approved:
1. Click "Export"
2. Choose format: Excel, PDF, Word
3. Download formatted response
4. Submit to requester

### Integrations in Workflow

- **Jira**: Auto-create issues for tracking
- **Confluence**: Publish final responses
- **GitHub**: Track as issues in repo
- **Slack**: Get notified of review requests

---

## Monitoring & Maintenance

### Health Checks

Run health check:
```bash
./scripts/healthcheck.sh --verbose
```

### Key Metrics

Monitor in Grafana:
- **Application**: Request rate, latency, errors
- **JVM**: Memory, GC, threads
- **Database**: Connections, query latency
- **Kafka**: Consumer lag, throughput
- **Business**: Questionnaires processed, questions answered

### Alerts

Default alerts configured:
- App down > 1 minute
- Error rate > 5%
- Latency p95 > 5s
- Disk > 85%
- Memory > 85%
- DB connections > 80%
- Kafka lag > 1000

### Backup

Automated daily at 3 AM:
- PostgreSQL dump (compressed)
- Uploaded to S3 (STANDARD_IA)
- Retention: 7 daily, 4 weekly, 12 monthly

Manual backup:
```bash
./scripts/backup.sh daily
./scripts/backup.sh weekly
./scripts/backup.sh monthly
```

### Restore

```bash
./scripts/restore.sh /backups/2024/01/15/quenaire_daily_20240115_030000.sql.gz quenaire_restore
```

### Logs

Centralized in Loki/Grafana:
- Application logs: JSON format
- Structured fields: level, logger, traceId
- Retention: 30 days
- Query: `{job="docker-containers"} |= "error"`

---

## Troubleshooting

### Common Issues

#### Application Won't Start

| Symptom | Cause | Solution |
|---------|-------|----------|
| DB connection failed | Wrong credentials/host | Check `DATABASE_URL`, network |
| Flyway migration failed | Schema conflict | Check migration logs, repair if needed |
| Port already in use | Another process on 8080 | Stop conflicting service |

#### Keycloak Authentication Fails

| Symptom | Cause | Solution |
|---------|-------|----------|
| Invalid token | Clock skew | Sync NTP on all servers |
| Realm not found | Wrong realm name | Check `KEYCLOAK_ISSUER_URI` |
| Client secret mismatch | Rotated secret | Update secret in `.env` |

#### Ollama Issues

| Symptom | Cause | Solution |
|---------|-------|----------|
| Model not found | Not pulled | `ollama pull llama3.1:8b` |
| OOM killed | Insufficient memory | Increase limit, use smaller model |
| Slow responses | No GPU | Enable GPU, or accept CPU speed |

#### High Latency

| Check | Action |
|-------|--------|
| DB query time | Add indexes, analyze slow queries |
| Connection pool | Increase HikariCP max connections |
| Kafka consumer lag | Scale consumers, check processing time |
| External API calls | Add caching, async processing |

#### Backup Failures

| Error | Solution |
|-------|----------|
| S3 access denied | Check IAM permissions |
| Disk full | Clean old backups, increase volume |
| pg_dump timeout | Increase timeout, check DB locks |

### Debug Commands

```bash
# Application logs
docker logs quenaire-app -f --tail 100

# Database queries
docker exec quenaire-postgres-prod psql -U quenaire -d quenaire -c "SELECT * FROM pg_stat_activity;"

# Kafka topics
docker exec quenaire-kafka-prod kafka-topics --bootstrap-server localhost:9092 --list

# Redis info
docker exec quenaire-redis-prod redis-cli -a $REDIS_PASSWORD INFO

# Check disk space
df -h /

# Check memory
free -h
```

### Getting Help

- **Documentation**: https://docs.quenaire.io
- **Support Email**: support@quenaire.io
- **Slack Community**: #quenaire-support
- **Status Page**: status.quenaire.io

---

## Appendix: API Reference

### Base URL
```
https://api.yourdomain.com/api
```

### Authentication
```
Authorization: Bearer <access_token>
```

### Key Endpoints

| Resource | Methods | Description |
|----------|---------|-------------|
| `/questionnaires` | GET, POST | List/create questionnaires |
| `/questionnaires/{id}` | GET, PATCH, DELETE | Manage questionnaire |
| `/questionnaires/{id}/process` | POST | Start AI processing |
| `/questionnaires/{id}/export` | GET | Export (excel, pdf, word) |
| `/questions` | GET | List questions |
| `/questions/{id}/answer` | GET, POST, PUT | Manage answers |
| `/questions/{id}/answer/generate` | POST | Generate AI answer |
| `/questions/{id}/answer/approve` | POST | Approve answer |
| `/questions/{id}/answer/reject` | POST | Reject answer |
| `/documents` | GET, POST | List/upload documents |
| `/documents/{id}` | GET, DELETE | Manage document |
| `/integrations/jira` | GET, POST | Jira integration |
| `/integrations/confluence` | GET, POST | Confluence integration |
| `/integrations/github` | GET, POST | GitHub integration |
| `/integrations/slack` | GET, POST | Slack integration |
| `/billing` | GET | Billing status |
| `/billing/subscribe` | POST | Subscribe to plan |
| `/billing/cancel` | POST | Cancel subscription |
| `/billing/invoices` | GET | List invoices |

---

*Last updated: August 2024 | Version 1.0*
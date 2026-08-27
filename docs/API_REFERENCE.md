# ComplyAI API Reference

## Overview

The ComplyAI API provides programmatic access to all platform features for automating security questionnaire workflows.

## Base URL

```
Production: https://api.yourdomain.com/api
Staging: https://api-staging.yourdomain.com/api
Local: http://localhost:8080/api
```

## Authentication

All API requests require authentication via JWT Bearer token:

```
Authorization: Bearer <access_token>
```

### Obtaining Tokens

1. **Keycloak OIDC Flow** (Recommended)
   - Redirect user to Keycloak authorization endpoint
   - Exchange authorization code for tokens
   - Refresh tokens automatically

2. **API Key** (For service-to-service)
   - Generate in Settings → API Access
   - Include in header: `X-API-Key: <api_key>`

### Token Format

```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refresh_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expires_in": 300,
  "token_type": "Bearer"
}
```

## Rate Limits

| Tier | Requests/minute | Burst |
|------|----------------|-------|
| Free | 60 | 10 |
| Starter | 120 | 20 |
| Professional | 300 | 50 |
| Enterprise | 1000 | 100 |

Rate limit headers:
```
X-RateLimit-Limit: 120
X-RateLimit-Remaining: 115
X-RateLimit-Reset: 1699999999
```

## Error Responses

All errors follow RFC 7807 Problem Details:

```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Questionnaire not found",
  "path": "/api/questionnaires/123"
}
```

Common status codes:
- `400` - Bad Request (validation error)
- `401` - Unauthorized (invalid/expired token)
- `403` - Forbidden (insufficient permissions)
- `404` - Not Found
- `409` - Conflict (duplicate resource)
- `422` - Unprocessable Entity (business rule violation)
- `429` - Too Many Requests
- `500` - Internal Server Error
- `503` - Service Unavailable

## Pagination

List endpoints support cursor-based pagination:

```
GET /api/questionnaires?page=0&size=20&sort=createdAt,desc
```

Response:
```json
{
  "content": [...],
  "page": 0,
  "size": 20,
  "totalElements": 150,
  "totalPages": 8,
  "first": true,
  "last": false
}
```

## Filtering & Search

Common query parameters:
- `search` - Full-text search
- `status` - Filter by status
- `assignedTo` - Filter by assignee
- `createdAfter` - ISO date filter
- `createdBefore` - ISO date filter

---

## Questionnaires

### List Questionnaires

```http
GET /api/questionnaires?page=0&size=20&status=READY_FOR_REVIEW
```

Response: `PaginatedResponse<Questionnaire>`

### Get Questionnaire

```http
GET /api/questionnaires/{id}
```

Response: `QuestionnaireWithDetails`

### Create Questionnaire

```http
POST /api/questionnaires
Content-Type: multipart/form-data

file: <file>
title: "Security Questionnaire 2024"
description: "Annual security review"
sourceType: UPLOAD_EXCEL
dueDate: 2024-03-01T00:00:00Z
assignedToId: <user-id>
```

Response: `Questionnaire`

### Update Questionnaire

```http
PATCH /api/questionnaires/{id}
Content-Type: application/json

{
  "title": "Updated Title",
  "description": "Updated description",
  "dueDate": "2024-04-01T00:00:00Z"
}
```

### Process Questionnaire

```http
POST /api/questionnaires/{id}/process
```

Triggers AI parsing of questions. Returns updated questionnaire with status `PROCESSING`.

### Export Questionnaire

```http
GET /api/questionnaires/{id}/export?format=excel
```

Formats: `excel`, `pdf`, `word`

Returns file download.

### Delete Questionnaire

```http
DELETE /api/questionnaires/{id}
```

---

## Questions

### List Questions

```http
GET /api/questionnaires/{questionnaireId}/questions?page=0&size=50&status=NEEDS_REVIEW
```

Response: `PaginatedResponse<Question>`

### Get Question

```http
GET /api/questions/{id}
```

Response: `QuestionWithAnswer`

### Update Question

```http
PATCH /api/questions/{id}
Content-Type: application/json

{
  "text": "Updated question text",
  "section": "Security",
  "isRequired": true
}
```

---

## Answers

### Get Answer

```http
GET /api/questions/{questionId}/answer
```

Response: `Answer`

### Generate AI Answer

```http
POST /api/questions/{questionId}/answer/generate
```

Response: `GeneratedAnswer`
```json
{
  "answer": "Our data is encrypted at rest using AES-256...",
  "citations": [
    {
      "documentId": "doc-123",
      "documentTitle": "Data Encryption Policy",
      "chunkId": "chunk-456",
      "chunkContent": "All data at rest is encrypted using AES-256...",
      "relevanceScore": 0.92
    }
  ],
  "confidenceScore": 0.87
}
```

### Update Answer

```http
PUT /api/questions/{questionId}/answer
Content-Type: application/json

{
  "content": "Custom answer text",
  "status": "HUMAN_EDITED"
}
```

### Approve Answer

```http
POST /api/questions/{questionId}/answer/approve
```

### Reject Answer

```http
POST /api/questions/{questionId}/answer/reject
Content-Type: application/json

{
  "reviewNotes": "Answer needs more detail on key rotation"
}
```

---

## Documents

### List Documents

```http
GET /api/documents?page=0&size=20&status=INDEXED&search=policy
```

### Get Document

```http
GET /api/documents/{id}
```

### Upload Document

```http
POST /api/documents
Content-Type: multipart/form-data

file: <file>
title: "Data Encryption Policy"
description: "Company-wide encryption standards"
sourceType: UPLOAD_PDF
```

### Upload Text Document

```http
POST /api/documents/text
Content-Type: application/json

{
  "title": "Quick Reference",
  "content": "API keys must be rotated every 90 days...",
  "description": "Internal reference document"
}
```

### Delete Document

```http
DELETE /api/documents/{id}
```

---

## Tenant

### Get Current Tenant

```http
GET /api/tenants/current
```

Response: `Tenant`

### Update Tenant

```http
PUT /api/tenants/current
Content-Type: application/json

{
  "name": "Acme Corp",
  "customDomain": "security.acme.com",
  "settingsJson": {
    "defaultLanguage": "en",
    "timezone": "America/New_York"
  },
  "brandingJson": {
    "primaryColor": "#4F46E5",
    "logoUrl": "https://cdn.acme.com/logo.png"
  }
}
```

---

## Users

### List Users

```http
GET /api/users?page=0&size=50
```

### Invite User

```http
POST /api/users/invite
Content-Type: application/json

{
  "email": "newuser@company.com",
  "role": "MEMBER"
}
```

### Update User Role

```http
PATCH /api/users/{userId}/role
Content-Type: application/json

{
  "role": "ADMIN"
}
```

---

## Integrations

### Jira

#### Get Status
```http
GET /api/integrations/jira/status
```

#### Create Issue
```http
POST /api/integrations/jira/questionnaires/{questionnaireId}/create-issue
```

#### Get Issue
```http
GET /api/integrations/jira/issues/{issueKey}
```

#### Add Comment
```http
POST /api/integrations/jira/issues/{issueKey}/comments
Content-Type: application/json
{"comment": "Review completed"}
```

#### Transition Issue
```http
POST /api/integrations/jira/issues/{issueKey}/transitions
Content-Type: application/json
{"transitionName": "Done"}
```

### Confluence

#### Get Status
```http
GET /api/integrations/confluence/status
```

#### Create Questionnaire Page
```http
POST /api/integrations/confluence/questionnaires/{questionnaireId}/create-page
```

#### Create Document Page
```http
POST /api/integrations/confluence/documents/{documentId}/create-page
```

### GitHub

#### Get Status
```http
GET /api/integrations/github/status
```

#### Create Issue
```http
POST /api/integrations/github/questionnaires/{questionnaireId}/create-issue
```

#### Create Sub-Issue
```http
POST /api/integrations/github/issues/{issueNumber}/sub-issues
Content-Type: application/json
{
  "question": {...},
  "answer": "Answer text"
}
```

### Slack

#### Get Status
```http
GET /api/integrations/slack/status
```

#### Send Notification
```http
POST /api/integrations/slack/questionnaires/{questionnaireId}/notify?channel=#reviews
```

#### Request Approval
```http
POST /api/integrations/slack/questions/{questionId}/approval-request?answer=Answer text&channel=#reviews
```

---

## Billing

### Get Billing Status
```http
GET /api/billing/status
```

### Get Plans
```http
GET /api/billing/plans
```

### Subscribe
```http
POST /api/billing/subscribe
Content-Type: application/json
{
  "plan": "PROFESSIONAL",
  "paymentMethodId": "pm_123..."
}
```

### Cancel Subscription
```http
POST /api/billing/cancel
Content-Type: application/json
{
  "atPeriodEnd": true
}
```

### Resume Subscription
```http
POST /api/billing/resume
```

### List Invoices
```http
GET /api/billing/invoices?page=0&size=10
```

---

## Webhooks

### Register Webhook

```http
POST /api/webhooks
Content-Type: application/json
{
  "url": "https://your-app.com/webhooks/complyai",
  "events": [
    "questionnaire.created",
    "questionnaire.processed",
    "questionnaire.completed",
    "answer.generated",
    "answer.approved",
    "answer.rejected",
    "document.indexed",
    "subscription.created",
    "subscription.updated",
    "subscription.cancelled"
  ],
  "secret": "webhook-secret-key"
}
```

### Webhook Payload

```json
{
  "event": "questionnaire.completed",
  "timestamp": "2024-01-15T10:30:00Z",
  "data": {
    "questionnaireId": "q-123",
    "title": "Security Questionnaire 2024",
    "approvedQuestions": 45,
    "totalQuestions": 50
  }
}
```

### Verify Signature

```python
import hmac
import hashlib

def verify(payload, signature, secret):
    expected = hmac.new(
        secret.encode(),
        payload.encode(),
        hashlib.sha256
    ).hexdigest()
    return hmac.compare_digest(expected, signature)
```

---

## OpenAPI/Swagger

Interactive API documentation available at:
- Production: `https://api.yourdomain.com/api/swagger-ui.html`
- Local: `http://localhost:8080/api/swagger-ui.html`

Download OpenAPI spec:
```http
GET /api/v3/api-docs
```

---

## SDKs & Libraries

### JavaScript/TypeScript
```bash
npm install @complyai/api-client
```

```typescript
import { ComplyAIClient } from '@complyai/api-client';

const client = new ComplyAIClient({
  baseUrl: 'https://api.yourdomain.com/api',
  apiKey: 'your-api-key'
});

const questionnaires = await client.questionnaires.list();
```

### Python
```bash
pip install complyai-client
```

```python
from complyai import ComplyAIClient

client = ComplyAIClient(
    base_url='https://api.yourdomain.com/api',
    api_key='your-api-key'
)

questionnaires = client.questionnaires.list()
```

---

## Versioning

- API version in URL: `/api/v1/...`
- Current version: `v1`
- Breaking changes only in new versions
- Deprecation notice: 6 months

---

## Support

- API Status: https://status.complyai.io
- Documentation: https://docs.complyai.io
- Support: api-support@complyai.io
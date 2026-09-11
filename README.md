# Quenaire - AI-Powered Security Questionnaire Automation

Enterprise-grade SaaS for automating security questionnaire responses using RAG and LLMs. Built for self-hosted deployment with full data sovereignty.

## 🎯 Value Proposition

- **Reduce questionnaire turnaround** from 2 weeks → 4 hours
- **Self-hosted / VPC deployment** — data never leaves your infrastructure
- **Technical questionnaire specialization** — handles API specs, architecture diagrams, crypto implementations
- **Enterprise-ready** — SAML/OIDC, RBAC, audit logs, multi-tenancy
- **Integrations** — Jira, Confluence, GitHub, Slack, Notion

## 🏗 Architecture

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│   Frontend      │────▶│   Spring Boot    │────▶│   PostgreSQL    │
│   (React/Next)  │     │   API Gateway    │     │   + pgvector    │
└─────────────────┘     └────────┬─────────┘     └─────────────────┘
                                 │
                    ┌────────────┼────────────┐
                    ▼            ▼            ▼
              ┌──────────┐ ┌──────────┐ ┌──────────┐
              │  Kafka   │ │  Redis   │ │  Ollama  │
              │  (Async) │ │ (Cache/  │ │ (Local   │
              │          │ │  Sessions)│ │  LLMs)   │
              └──────────┘ └──────────┘ └──────────┘
```

## 🚀 Quick Start

### Prerequisites
- Java 21+
- Docker & Docker Compose
- Gradle 8.5+ (or use wrapper)

### 1. Start Infrastructure
```bash
docker-compose up -d
```

### 2. Pull LLM Models (in another terminal)
```bash
docker exec -it quenaire-ollama ollama pull llama3.1:8b
docker exec -it quenaire-ollama ollama pull nomic-embed-text
```

### 3. Configure Keycloak
1. Open http://localhost:8081/admin (admin/admin)
2. Realm `quenaire` is pre-imported
3. Create client `quenaire-frontend` → copy client secret
4. Create user with `OWNER` role

### 4. Run Application
```bash
./gradlew bootRun
```

### 5. Verify
- API: http://localhost:8080/api/actuator/health
- Swagger: http://localhost:8080/api/swagger-ui.html
- Keycloak: http://localhost:8081/admin

## 📁 Project Structure

```
src/main/java/com/quenaire/
├── QuenaireApplication.java
├── config/                 # Configuration classes
├── security/               # Security, JWT, Multi-tenancy
├── domain/
│   ├── common/             # BaseEntity, AuditorAware
│   ├── tenant/             # Tenant, TenantRepository
│   ├── user/               # User, UserRepository
│   ├── document/           # Document, DocumentChunk, Repositories
│   ├── questionnaire/      # Questionnaire, Question, Repositories
│   ├── answer/             # Answer, AnswerRepository
│   └── audit/              # AuditLog, AuditLogRepository
├── ai/
│   ├── ingestion/          # Document processing pipeline
│   ├── rag/                # Retrieval-Augmented Generation
│   └── generation/         # Answer generation
├── api/
│   ├── rest/               # REST controllers
│   └── webhook/            # Webhook handlers
├── integration/
│   ├── jira/               # Jira integration
│   ├── confluence/         # Confluence integration
│   ├── slack/              # Slack integration
│   └── github/             # GitHub integration
└── web/                    # Web MVC (if using Thymeleaf/HTMX)
```

## 🔧 Configuration

Key environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `DATABASE_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/quenaire` |
| `KEYCLOAK_ISSUER_URI` | Keycloak realm URL | `http://localhost:8081/realms/quenaire` |
| `OLLAMA_BASE_URL` | Ollama API endpoint | `http://localhost:11434` |
| `OLLAMA_MODEL` | Generation model | `llama3.1:8b` |
| `OLLAMA_EMBEDDING_MODEL` | Embedding model | `nomic-embed-text` |
| `STORAGE_TYPE` | `local`, `s3`, `minio` | `local` |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka brokers | `localhost:9092` |

## 🧪 Testing

```bash
# Unit tests
./gradlew test

# Integration tests (requires Docker)
./gradlew integrationTest

# All tests with coverage
./gradlew jacocoTestReport
```

## 📦 Building

```bash
# Build JAR
./gradlew bootJar

# Build Docker image
docker build -t quenaire:latest .

# Run in production mode
docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

## 🎯 Roadmap (12 Weeks)

| Week | Focus |
|------|-------|
| 1-2 | Core: Auth, Multi-tenancy, Document Upload, pgvector |
| 3-4 | Ingestion: PDF/Excel/Word parsing → Chunking → Embedding |
| 5-6 | Questionnaire Parser: Excel/PDF → Structured Questions |
| 7-8 | RAG Engine: Retrieval + Generation with Citations |
| 9-10 | Review UI: Side-by-side, Approve/Edit, Export |
| 11-12 | Integrations, Billing, Landing Page, Deploy |

## 🏷 License

Proprietary — All rights reserved.

## 🤝 Contributing

Internal project — not accepting external contributions at this time.
-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Enable uuid-ossp for UUID generation
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create custom types
CREATE TYPE tenant_status AS ENUM ('ACTIVE', 'SUSPENDED', 'TRIAL', 'EXPIRED', 'DELETED');
CREATE TYPE tenant_plan AS ENUM ('FREE', 'STARTER', 'PROFESSIONAL', 'ENTERPRISE', 'CUSTOM');
CREATE TYPE user_role AS ENUM ('OWNER', 'ADMIN', 'MEMBER', 'VIEWER', 'API_USER');
CREATE TYPE user_status AS ENUM ('INVITED', 'ACTIVE', 'SUSPENDED', 'DEACTIVATED');
CREATE TYPE document_status AS ENUM ('UPLOADING', 'PROCESSING', 'INDEXED', 'FAILED', 'ARCHIVED');
CREATE TYPE questionnaire_source_type AS ENUM ('UPLOAD_EXCEL', 'UPLOAD_PDF', 'UPLOAD_WORD', 'PORTAL_LINK', 'API_IMPORT', 'EMAIL_FORWARD');
CREATE TYPE questionnaire_status AS ENUM ('DRAFT', 'PROCESSING', 'READY_FOR_REVIEW', 'IN_REVIEW', 'APPROVED', 'SUBMITTED', 'ARCHIVED');
CREATE TYPE question_type AS ENUM ('FREE_TEXT', 'SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'YES_NO', 'NUMERIC', 'DATE', 'FILE_UPLOAD', 'TABLE');
CREATE TYPE question_status AS ENUM ('PENDING', 'AI_DRAFTED', 'NEEDS_REVIEW', 'APPROVED', 'REJECTED', 'SKIPPED');
CREATE TYPE answer_status AS ENUM ('DRAFT', 'AI_GENERATED', 'HUMAN_EDITED', 'APPROVED', 'REJECTED');
CREATE TYPE audit_severity AS ENUM ('DEBUG', 'INFO', 'WARNING', 'ERROR', 'CRITICAL');

-- Tenants table
CREATE TABLE tenants (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(50) NOT NULL UNIQUE,
    custom_domain VARCHAR(255) UNIQUE,
    status tenant_status NOT NULL DEFAULT 'ACTIVE',
    plan tenant_plan NOT NULL DEFAULT 'FREE',
    plan_expires_at TIMESTAMP WITH TIME ZONE,
    settings_json JSONB NOT NULL DEFAULT '{}',
    branding_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_tenants_slug ON tenants(slug) WHERE deleted_at IS NULL;
CREATE INDEX idx_tenants_custom_domain ON tenants(custom_domain) WHERE deleted_at IS NULL;
CREATE INDEX idx_tenants_status ON tenants(status) WHERE deleted_at IS NULL;

-- Users table
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    keycloak_id VARCHAR(36) UNIQUE,
    role user_role NOT NULL DEFAULT 'MEMBER',
    status user_status NOT NULL DEFAULT 'INVITED',
    last_login_at TIMESTAMP WITH TIME ZONE,
    preferences_json JSONB NOT NULL DEFAULT '{}',
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_users_tenant_email ON users(tenant_id, email) WHERE deleted_at IS NULL;
CREATE INDEX idx_users_tenant_id ON users(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_users_keycloak_id ON users(keycloak_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_users_status ON users(status) WHERE deleted_at IS NULL;

-- Documents table
CREATE TABLE documents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    title VARCHAR(500) NOT NULL,
    description TEXT,
    source_type VARCHAR(100) NOT NULL,
    source_url VARCHAR(500),
    source_id VARCHAR(100),
    file_name VARCHAR(500),
    mime_type VARCHAR(100),
    file_size BIGINT,
    storage_path VARCHAR(1000),
    status document_status NOT NULL DEFAULT 'UPLOADING',
    content_hash VARCHAR(64),
    page_count INTEGER,
    word_count INTEGER,
    language VARCHAR(10),
    embedding vector(1536),
    metadata_json JSONB NOT NULL DEFAULT '{}',
    processing_error TEXT,
    processed_at TIMESTAMP WITH TIME ZONE,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    created_by_id UUID REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_documents_tenant_id ON documents(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_documents_status ON documents(status) WHERE deleted_at IS NULL;
CREATE INDEX idx_documents_created_by ON documents(created_by_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_documents_content_hash ON documents(tenant_id, content_hash) WHERE deleted_at IS NULL;

-- Document chunks table
CREATE TABLE document_chunks (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    chunk_index INTEGER NOT NULL,
    token_count INTEGER,
    start_char INTEGER,
    end_char INTEGER,
    embedding vector(1536),
    metadata_json JSONB NOT NULL DEFAULT '{}',
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_chunks_document_id ON document_chunks(document_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_chunks_tenant_id ON document_chunks(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_chunks_document_chunk_index ON document_chunks(document_id, chunk_index) WHERE deleted_at IS NULL;

-- Questionnaires table
CREATE TABLE questionnaires (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    title VARCHAR(500) NOT NULL,
    description TEXT,
    source_type questionnaire_source_type NOT NULL,
    source_url VARCHAR(500),
    source_id VARCHAR(100),
    file_name VARCHAR(500),
    status questionnaire_status NOT NULL DEFAULT 'DRAFT',
    total_questions INTEGER NOT NULL DEFAULT 0,
    answered_questions INTEGER NOT NULL DEFAULT 0,
    approved_questions INTEGER NOT NULL DEFAULT 0,
    due_date TIMESTAMP WITH TIME ZONE,
    submitted_at TIMESTAMP WITH TIME ZONE,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    assigned_to_id UUID REFERENCES users(id),
    created_by_id UUID REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_questionnaires_tenant_id ON questionnaires(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_questionnaires_status ON questionnaires(status) WHERE deleted_at IS NULL;
CREATE INDEX idx_questionnaires_assigned_to ON questionnaires(assigned_to_id) WHERE deleted_at IS NULL;

-- Questions table
CREATE TABLE questions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    questionnaire_id UUID NOT NULL REFERENCES questionnaires(id) ON DELETE CASCADE,
    text TEXT NOT NULL,
    original_text TEXT,
    question_type question_type NOT NULL DEFAULT 'FREE_TEXT',
    options_json JSONB NOT NULL DEFAULT '[]',
    section VARCHAR(255),
    subsection VARCHAR(255),
    question_number VARCHAR(100),
    row_index INTEGER,
    status question_status NOT NULL DEFAULT 'PENDING',
    confidence_score DOUBLE PRECISION,
    is_required BOOLEAN NOT NULL DEFAULT FALSE,
    tags_json JSONB NOT NULL DEFAULT '[]',
    metadata_json JSONB NOT NULL DEFAULT '{}',
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_questions_questionnaire_id ON questions(questionnaire_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_questions_tenant_id ON questions(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_questions_status ON questions(status) WHERE deleted_at IS NULL;

-- Answers table
CREATE TABLE answers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    question_id UUID NOT NULL UNIQUE REFERENCES questions(id) ON DELETE CASCADE,
    content TEXT,
    ai_generated_content TEXT,
    citations_json JSONB NOT NULL DEFAULT '[]',
    confidence_score DOUBLE PRECISION,
    status answer_status NOT NULL DEFAULT 'DRAFT',
    review_notes TEXT,
    reviewed_at TIMESTAMP WITH TIME ZONE,
    reviewed_by_id UUID REFERENCES users(id),
    -- The answer's own revision number, distinct from the optimistic-lock
    -- `version` every table carries. Both were called "version", which made this
    -- CREATE TABLE invalid -- the entity already maps this one as answer_version.
    answer_version INTEGER NOT NULL DEFAULT 1,
    previous_version_id UUID,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_answers_question_id ON answers(question_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_answers_tenant_id ON answers(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_answers_reviewed_by ON answers(reviewed_by_id) WHERE deleted_at IS NULL;

-- Audit logs table
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    entity_type VARCHAR(100) NOT NULL,
    entity_id UUID,
    action VARCHAR(50) NOT NULL,
    severity audit_severity NOT NULL DEFAULT 'INFO',
    description TEXT,
    old_values_json JSONB,
    new_values_json JSONB,
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    request_id VARCHAR(36),
    session_id VARCHAR(36),
    metadata_json JSONB NOT NULL DEFAULT '{}',
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    user_id UUID REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_audit_logs_tenant_id ON audit_logs(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_audit_logs_user_id ON audit_logs(user_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_type, entity_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_audit_logs_action ON audit_logs(action) WHERE deleted_at IS NULL;
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at) WHERE deleted_at IS NULL;

-- Updated at trigger function
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply updated_at triggers
CREATE TRIGGER update_tenants_updated_at BEFORE UPDATE ON tenants FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_documents_updated_at BEFORE UPDATE ON documents FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_document_chunks_updated_at BEFORE UPDATE ON document_chunks FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_questionnaires_updated_at BEFORE UPDATE ON questionnaires FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_questions_updated_at BEFORE UPDATE ON questions FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_answers_updated_at BEFORE UPDATE ON answers FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_audit_logs_updated_at BEFORE UPDATE ON audit_logs FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Row Level Security (RLS) policies for multi-tenancy
ALTER TABLE tenants ENABLE ROW LEVEL SECURITY;
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE document_chunks ENABLE ROW LEVEL SECURITY;
ALTER TABLE questionnaires ENABLE ROW LEVEL SECURITY;
ALTER TABLE questions ENABLE ROW LEVEL SECURITY;
ALTER TABLE answers ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_logs ENABLE ROW LEVEL SECURITY;

-- RLS policies will be enforced at application level via TenantContext
-- These are placeholder policies; actual enforcement is in the application layer
CREATE POLICY tenant_isolation ON tenants FOR ALL USING (true);
CREATE POLICY tenant_isolation ON users FOR ALL USING (true);
CREATE POLICY tenant_isolation ON documents FOR ALL USING (true);
CREATE POLICY tenant_isolation ON document_chunks FOR ALL USING (true);
CREATE POLICY tenant_isolation ON questionnaires FOR ALL USING (true);
CREATE POLICY tenant_isolation ON questions FOR ALL USING (true);
CREATE POLICY tenant_isolation ON answers FOR ALL USING (true);
CREATE POLICY tenant_isolation ON audit_logs FOR ALL USING (true);
-- Vanta-Grade Questionnaire Automation Tables
-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS vector;

-- Custom types for Vanta questionnaire system
CREATE TYPE vanta_question_type AS ENUM (
    'FREE_TEXT', 'SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'YES_NO', 
    'YES_NO_NA', 'YES_NO_PARTIAL', 'NUMERIC', 'DATE', 
    'FILE_UPLOAD', 'TABLE', 'ATTACHMENT'
);

CREATE TYPE vanta_question_status AS ENUM (
    'PENDING', 'AI_DRAFTED', 'NEEDS_REVIEW', 'APPROVED', 
    'REJECTED', 'SKIPPED', 'DELEGATED'
);

CREATE TYPE vanta_questionnaire_status AS ENUM (
    'DRAFT', 'PARSING', 'PROCESSING', 'READY_FOR_REVIEW', 
    'IN_REVIEW', 'APPROVED', 'EXPORTED', 'SUBMITTED', 'ARCHIVED'
);

CREATE TYPE vanta_questionnaire_source AS ENUM (
    'UPLOAD_EXCEL', 'UPLOAD_PDF', 'UPLOAD_WORD', 'PORTAL_LINK', 
    'API_IMPORT', 'EMAIL_FORWARD', 'ONETRUST', 'WHISTIC', 
    'LOOPIO', 'CYBERGRX', 'GOOGLE_FORMS', 'TYPEFORM'
);

CREATE TYPE vanta_answer_library_status AS ENUM (
    'ACTIVE', 'STALE', 'DEPRECATED', 'PENDING_REVIEW'
);

CREATE TYPE vanta_citation_type AS ENUM (
    'DIRECT_MATCH', 'DOCUMENT_RAG', 'INFERRED', 'HUMAN_VERIFIED'
);

-- Answer Library (Tier 1 Knowledge Base)
CREATE TABLE vanta_answer_library (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    question_text TEXT NOT NULL,
    question_hash VARCHAR(64) NOT NULL,
    normalized_question TEXT,
    question_type vanta_question_type NOT NULL DEFAULT 'FREE_TEXT',
    options_json JSONB,
    approved_answer TEXT NOT NULL,
    answer_explanation TEXT,
    citation_ids UUID[] DEFAULT '{}',
    status vanta_answer_library_status NOT NULL DEFAULT 'ACTIVE',
    confidence_score INTEGER DEFAULT 100,
    usage_count INTEGER DEFAULT 0,
    last_used_at TIMESTAMP WITH TIME ZONE,
    approved_by_id UUID REFERENCES users(id),
    approved_at TIMESTAMP WITH TIME ZONE,
    -- Foreign keys added at the end of this migration: both target tables are
    -- created below, and an inline REFERENCES here fails on a forward reference.
    source_questionnaire_id UUID,
    source_question_id UUID,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_answer_library_tenant ON vanta_answer_library(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_answer_library_question_hash ON vanta_answer_library(question_hash) WHERE deleted_at IS NULL;
CREATE INDEX idx_answer_library_status ON vanta_answer_library(status) WHERE deleted_at IS NULL;
CREATE INDEX idx_answer_library_updated ON vanta_answer_library(updated_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_answer_library_question_type ON vanta_answer_library(question_type) WHERE deleted_at IS NULL;

-- Vanta Questionnaires
CREATE TABLE vanta_questionnaires (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    title VARCHAR(500) NOT NULL,
    description TEXT,
    source_type vanta_questionnaire_source NOT NULL DEFAULT 'UPLOAD_EXCEL',
    source_url VARCHAR(500),
    file_name VARCHAR(500),
    original_file_bytes BYTEA,
    status vanta_questionnaire_status NOT NULL DEFAULT 'DRAFT',
    total_questions INTEGER NOT NULL DEFAULT 0,
    answered_questions INTEGER NOT NULL DEFAULT 0,
    approved_questions INTEGER NOT NULL DEFAULT 0,
    high_confidence_questions INTEGER NOT NULL DEFAULT 0,
    needs_review_questions INTEGER NOT NULL DEFAULT 0,
    delegated_questions INTEGER NOT NULL DEFAULT 0,
    due_date TIMESTAMP WITH TIME ZONE,
    submitted_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    template_id UUID,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_vanta_questionnaire_tenant ON vanta_questionnaires(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_vanta_questionnaire_status ON vanta_questionnaires(status) WHERE deleted_at IS NULL;
CREATE INDEX idx_vanta_questionnaire_source ON vanta_questionnaires(source_type) WHERE deleted_at IS NULL;
CREATE INDEX idx_vanta_questionnaire_due ON vanta_questionnaires(due_date) WHERE deleted_at IS NULL;
CREATE INDEX idx_vanta_questionnaire_template ON vanta_questionnaires(template_id) WHERE deleted_at IS NULL;

-- Vanta Questions
CREATE TABLE vanta_questions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    questionnaire_id UUID NOT NULL REFERENCES vanta_questionnaires(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    question_number VARCHAR(50) NOT NULL,
    original_text TEXT,
    text TEXT NOT NULL,
    category VARCHAR(255),
    sub_category VARCHAR(255),
    question_type vanta_question_type NOT NULL DEFAULT 'FREE_TEXT',
    options_json JSONB,
    is_required BOOLEAN NOT NULL DEFAULT FALSE,
    is_attachment_request BOOLEAN NOT NULL DEFAULT FALSE,
    status vanta_question_status NOT NULL DEFAULT 'PENDING',
    confidence_score INTEGER,
    ai_answer TEXT,
    selected_option VARCHAR(500),
    human_answer TEXT,
    final_answer TEXT,
    review_notes TEXT,
    staleness_detected BOOLEAN NOT NULL DEFAULT FALSE,
    source_library_entry_id UUID REFERENCES vanta_answer_library(id),
    citation_ids UUID[] DEFAULT '{}',
    assigned_to_id UUID REFERENCES users(id),
    reviewed_by_id UUID REFERENCES users(id),
    reviewed_at TIMESTAMP WITH TIME ZONE,
    approved_by_id UUID REFERENCES users(id),
    approved_at TIMESTAMP WITH TIME ZONE,
    row_index INTEGER,
    col_index INTEGER,
    sheet_name VARCHAR(255),
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_vanta_question_questionnaire ON vanta_questions(questionnaire_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_vanta_question_tenant ON vanta_questions(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_vanta_question_status ON vanta_questions(status) WHERE deleted_at IS NULL;
CREATE INDEX idx_vanta_question_type ON vanta_questions(question_type) WHERE deleted_at IS NULL;
CREATE INDEX idx_vanta_question_assignee ON vanta_questions(assigned_to_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_vanta_question_library ON vanta_questions(source_library_entry_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_vanta_question_coords ON vanta_questions(questionnaire_id, row_index, col_index) WHERE deleted_at IS NULL;

-- Citations
CREATE TABLE vanta_citations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    document_id UUID NOT NULL,
    document_title VARCHAR(500) NOT NULL,
    document_version VARCHAR(100),
    chunk_id UUID NOT NULL,
    page_number INTEGER,
    section_title VARCHAR(255),
    paragraph_index INTEGER,
    start_char INTEGER,
    end_char INTEGER,
    exact_quote TEXT NOT NULL,
    relevance_score DOUBLE PRECISION NOT NULL,
    citation_type vanta_citation_type NOT NULL DEFAULT 'DOCUMENT_RAG',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_citation_document ON vanta_citations(document_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_citation_chunk ON vanta_citations(chunk_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_citation_type ON vanta_citations(citation_type) WHERE deleted_at IS NULL;
CREATE INDEX idx_citation_relevance ON vanta_citations(relevance_score) WHERE deleted_at IS NULL;

-- Updated at triggers
-- Deferred foreign keys for vanta_answer_library (targets are created above).
ALTER TABLE vanta_answer_library
    ADD CONSTRAINT fk_answer_library_source_questionnaire
    FOREIGN KEY (source_questionnaire_id) REFERENCES vanta_questionnaires(id);

ALTER TABLE vanta_answer_library
    ADD CONSTRAINT fk_answer_library_source_question
    FOREIGN KEY (source_question_id) REFERENCES vanta_questions(id);

CREATE TRIGGER update_vanta_answer_library_updated_at BEFORE UPDATE ON vanta_answer_library FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_vanta_questionnaires_updated_at BEFORE UPDATE ON vanta_questionnaires FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_vanta_questions_updated_at BEFORE UPDATE ON vanta_questions FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_vanta_citations_updated_at BEFORE UPDATE ON vanta_citations FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- RLS policies
ALTER TABLE vanta_answer_library ENABLE ROW LEVEL SECURITY;
ALTER TABLE vanta_questionnaires ENABLE ROW LEVEL SECURITY;
ALTER TABLE vanta_questions ENABLE ROW LEVEL SECURITY;
ALTER TABLE vanta_citations ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON vanta_answer_library FOR ALL USING (true);
CREATE POLICY tenant_isolation ON vanta_questionnaires FOR ALL USING (true);
CREATE POLICY tenant_isolation ON vanta_questions FOR ALL USING (true);
CREATE POLICY tenant_isolation ON vanta_citations FOR ALL USING (true);
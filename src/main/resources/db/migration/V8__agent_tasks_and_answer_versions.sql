-- Tables for entities that were written without a matching migration.
--
-- AgentTask (the orchestration queue) and AnswerVersion (answer history) are
-- both mapped JPA entities that no migration ever created, along with two
-- @ElementCollection join tables. Hibernate's schema validation rejects the
-- whole context over any one of them, so the application could never start
-- against Postgres.

-- ---------------------------------------------------------------------------
-- Agent task queue
-- ---------------------------------------------------------------------------
CREATE TABLE agent_tasks (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id        UUID NOT NULL REFERENCES tenants(id),

    task_type        VARCHAR(64) NOT NULL,
    status           VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    -- Higher runs first; the queue orders by priority then age.
    priority         INTEGER NOT NULL DEFAULT 0,

    questionnaire_id UUID,
    question_id      UUID,

    payload_json     JSONB,
    result_json      JSONB,
    error_message    TEXT,

    retry_count      INTEGER DEFAULT 0,
    max_retries      INTEGER DEFAULT 3,
    next_retry_at    TIMESTAMPTZ,

    started_at       TIMESTAMPTZ,
    completed_at     TIMESTAMPTZ,

    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(255),
    updated_by       VARCHAR(255),
    deleted_at       TIMESTAMPTZ,
    version          BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT chk_agent_task_type CHECK (task_type IN (
        'PROCESS_QUESTIONNAIRE', 'GENERATE_ANSWER', 'ROUTE_TO_SME',
        'SEND_NOTIFICATION', 'EXPORT_QUESTIONNAIRE', 'SYNC_KNOWLEDGE_BASE',
        'INGEST_DOCUMENT')),
    CONSTRAINT chk_agent_task_status CHECK (status IN (
        'PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'RETRYING', 'CANCELLED')),
    CONSTRAINT chk_agent_task_retries CHECK (retry_count >= 0 AND max_retries >= 0)
);

CREATE INDEX idx_agent_task_tenant        ON agent_tasks (tenant_id);
CREATE INDEX idx_agent_task_status        ON agent_tasks (status);
CREATE INDEX idx_agent_task_questionnaire ON agent_tasks (questionnaire_id);
CREATE INDEX idx_agent_task_priority      ON agent_tasks (priority);

-- The queue's hot path: next runnable task, highest priority, oldest first.
CREATE INDEX idx_agent_task_runnable
    ON agent_tasks (tenant_id, priority DESC, created_at)
    WHERE deleted_at IS NULL AND status IN ('PENDING', 'RETRYING');

-- Finding tasks whose retry backoff has elapsed.
CREATE INDEX idx_agent_task_retry_due
    ON agent_tasks (next_retry_at)
    WHERE deleted_at IS NULL AND status = 'RETRYING';

-- ---------------------------------------------------------------------------
-- Answer version history
-- ---------------------------------------------------------------------------
CREATE TABLE answer_versions (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id        UUID NOT NULL REFERENCES tenants(id),
    answer_id        UUID NOT NULL REFERENCES answers(id) ON DELETE CASCADE,

    version_number   INTEGER NOT NULL,
    content          TEXT,
    citations_json   JSONB NOT NULL DEFAULT '[]',
    confidence_score DOUBLE PRECISION,
    status           answer_status NOT NULL,
    review_notes     TEXT,
    created_by_id    UUID,
    change_summary   TEXT,

    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(255),
    updated_by       VARCHAR(255),
    deleted_at       TIMESTAMPTZ,
    version          BIGINT NOT NULL DEFAULT 0,

    -- An answer cannot have two revisions numbered the same.
    CONSTRAINT uq_answer_version UNIQUE (answer_id, version_number),
    CONSTRAINT chk_answer_version_number CHECK (version_number > 0)
);

CREATE INDEX idx_answer_version_answer ON answer_versions (answer_id);
CREATE INDEX idx_answer_version_tenant ON answer_versions (tenant_id);

-- History is read newest-first for one answer.
CREATE INDEX idx_answer_version_history
    ON answer_versions (answer_id, version_number DESC)
    WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------------
-- @ElementCollection join tables
-- ---------------------------------------------------------------------------
CREATE TABLE vanta_question_citations (
    question_id UUID NOT NULL REFERENCES vanta_questions(id) ON DELETE CASCADE,
    citation_id UUID NOT NULL
);

CREATE INDEX idx_vanta_question_citations_question ON vanta_question_citations (question_id);

CREATE TABLE vanta_answer_library_citations (
    entry_id    UUID NOT NULL REFERENCES vanta_answer_library(id) ON DELETE CASCADE,
    citation_id UUID NOT NULL
);

CREATE INDEX idx_vanta_library_citations_entry ON vanta_answer_library_citations (entry_id);

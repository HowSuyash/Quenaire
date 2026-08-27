-- SME Collaboration Hub: durable review queue + SME roster.
--
-- Replaces the in-memory maps that previously held SME registrations and review
-- assignments, so routing decisions and SLA clocks survive a restart and are
-- scoped per tenant.

-- ---------------------------------------------------------------------------
-- SME roster
-- ---------------------------------------------------------------------------
CREATE TABLE sme_profiles (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id               UUID NOT NULL REFERENCES tenants(id),
    user_id                 UUID NOT NULL,
    email                   VARCHAR(320) NOT NULL,
    full_name               VARCHAR(255),
    slack_id                VARCHAR(64),
    max_concurrent_reviews  INTEGER NOT NULL DEFAULT 10,
    is_active               BOOLEAN NOT NULL DEFAULT TRUE,
    last_active_at          TIMESTAMPTZ,

    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(255),
    updated_by              VARCHAR(255),
    deleted_at              TIMESTAMPTZ,
    version                 BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uq_sme_profile_tenant_user UNIQUE (tenant_id, user_id),
    CONSTRAINT chk_sme_max_concurrent CHECK (max_concurrent_reviews > 0)
);

CREATE INDEX idx_sme_profile_tenant ON sme_profiles (tenant_id);
CREATE INDEX idx_sme_profile_user   ON sme_profiles (user_id);
CREATE INDEX idx_sme_profile_active ON sme_profiles (is_active);

CREATE TABLE sme_profile_expertise (
    sme_profile_id UUID NOT NULL REFERENCES sme_profiles(id) ON DELETE CASCADE,
    tag            VARCHAR(100) NOT NULL
);

CREATE INDEX idx_sme_expertise_profile ON sme_profile_expertise (sme_profile_id);
CREATE INDEX idx_sme_expertise_tag     ON sme_profile_expertise (LOWER(tag));

-- ---------------------------------------------------------------------------
-- Review queue
-- ---------------------------------------------------------------------------
CREATE TYPE sme_assignment_status AS ENUM (
    'PENDING', 'IN_PROGRESS', 'COMPLETED', 'ESCALATED', 'CANCELLED'
);

CREATE TABLE sme_assignments (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id        UUID NOT NULL REFERENCES tenants(id),
    question_id      UUID NOT NULL,
    questionnaire_id UUID NOT NULL,
    sme_user_id      UUID NOT NULL,
    assigned_by_id   UUID,
    category         VARCHAR(255),
    -- Stored as VARCHAR to match the JPA @Enumerated(EnumType.STRING) mapping
    -- used across this codebase; the enum type above documents the domain.
    status           VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    assigned_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    due_at           TIMESTAMPTZ,
    started_at       TIMESTAMPTZ,
    completed_at     TIMESTAMPTZ,
    notes            TEXT,

    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(255),
    updated_by       VARCHAR(255),
    deleted_at       TIMESTAMPTZ,
    version          BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT chk_sme_assignment_status
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'ESCALATED', 'CANCELLED'))
);

CREATE INDEX idx_sme_assignment_tenant        ON sme_assignments (tenant_id);
CREATE INDEX idx_sme_assignment_question      ON sme_assignments (question_id);
CREATE INDEX idx_sme_assignment_questionnaire ON sme_assignments (questionnaire_id);
CREATE INDEX idx_sme_assignment_sme           ON sme_assignments (sme_user_id);
CREATE INDEX idx_sme_assignment_status        ON sme_assignments (status);
CREATE INDEX idx_sme_assignment_due           ON sme_assignments (due_at);

-- At most one live assignment per question: re-routing must close the old row
-- first, otherwise two SMEs could both believe they own the answer.
CREATE UNIQUE INDEX uq_sme_assignment_open_question
    ON sme_assignments (tenant_id, question_id)
    WHERE deleted_at IS NULL AND status IN ('PENDING', 'IN_PROGRESS');

-- The queue is read overwhelmingly as "open work, soonest due first".
CREATE INDEX idx_sme_assignment_open_due
    ON sme_assignments (tenant_id, due_at)
    WHERE deleted_at IS NULL AND status IN ('PENDING', 'IN_PROGRESS');

-- ---------------------------------------------------------------------------
-- Question status values added since V3
-- ---------------------------------------------------------------------------
-- VantaQuestion.QuestionStatus gained HUMAN_EDITED, which the V3 enum lacks.
-- Without this, saving an inline edit fails on Postgres.
ALTER TYPE vanta_question_status ADD VALUE IF NOT EXISTS 'HUMAN_EDITED';

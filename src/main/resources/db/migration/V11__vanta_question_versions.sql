-- An audit trail for changes to a questionnaire answer.
--
-- This was being written into vanta_questions.metadata_json as a JSON array,
-- which replaced whatever metadata the row already held, and it was never read
-- back: getVersionHistory returned an empty list unconditionally. So the review
-- screen showed no history while history was silently overwriting other data.
--
-- answer_versions (V8) could not serve this: it has a foreign key to answers,
-- and Vanta questions live in vanta_questions.
--
-- Who changed a compliance answer, when, and what it said before is not
-- housekeeping -- it is the evidence behind the answer, and vendor
-- questionnaires ask for it directly.

CREATE TABLE vanta_question_versions (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id         UUID NOT NULL REFERENCES tenants(id),
    question_id       UUID NOT NULL REFERENCES vanta_questions(id) ON DELETE CASCADE,

    -- What happened: ANSWERED, APPROVED, REJECTED, DELEGATED, EDITED.
    action            VARCHAR(50) NOT NULL,
    -- Null when the change came from the system rather than a person.
    actor_id          UUID,

    -- The state being replaced, so the row records what was lost.
    previous_status   vanta_question_status,
    previous_answer   TEXT,
    new_answer        TEXT,
    note              TEXT,

    occurred_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        VARCHAR(255),
    updated_by        VARCHAR(255),
    deleted_at        TIMESTAMPTZ,
    version           BIGINT NOT NULL DEFAULT 0
);

-- The read is always "history for this question, newest first".
CREATE INDEX idx_vanta_question_version_question
    ON vanta_question_versions (question_id, occurred_at DESC);
CREATE INDEX idx_vanta_question_version_tenant
    ON vanta_question_versions (tenant_id);

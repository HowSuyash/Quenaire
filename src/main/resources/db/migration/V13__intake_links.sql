-- Intake links: a customer sends one to whoever is asking them to fill in a
-- security questionnaire, and that person uploads it without an account.
--
-- These were never stored. Creating a link minted a token, returned it and
-- forgot it. The public endpoints then accepted any token at all, filed the
-- upload under a freshly generated tenant id, did not save it, and told the
-- sender it was being processed. The customer never saw the questionnaire.
--
-- Only a SHA-256 of the token is kept. A link is a bearer credential that lets
-- a stranger put a file into a customer's workspace; reading this table should
-- not be enough to use one. The full link is shown once, when it is created.

CREATE TABLE intake_links (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id         UUID NOT NULL REFERENCES tenants(id),
    token_hash        VARCHAR(64) NOT NULL UNIQUE,

    -- Who the link was sent to, as the customer entered it.
    vendor_name       VARCHAR(255) NOT NULL,
    vendor_email      VARCHAR(320) NOT NULL,
    expires_at        TIMESTAMPTZ NOT NULL,

    -- Filled by the first successful submission. A link is single-use.
    questionnaire_id  UUID REFERENCES vanta_questionnaires(id),
    submitted_at      TIMESTAMPTZ,
    -- What the person uploading said about themselves. Untrusted input.
    submitter_name    VARCHAR(255),
    submitter_email   VARCHAR(320),
    portal_url        VARCHAR(1000),

    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        VARCHAR(255),
    updated_by        VARCHAR(255),
    deleted_at        TIMESTAMPTZ,
    version           BIGINT NOT NULL DEFAULT 0
);

-- The tenant-side read is always "my links, newest first".
CREATE INDEX idx_intake_link_tenant ON intake_links (tenant_id, created_at DESC);

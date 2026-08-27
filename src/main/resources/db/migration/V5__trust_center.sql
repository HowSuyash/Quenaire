-- Trust Center: the public, prospect-facing view of a tenant's security posture,
-- plus the leads captured by its call to action.

CREATE TABLE trust_center_profiles (
    id                        UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id                 UUID NOT NULL REFERENCES tenants(id),
    slug                      VARCHAR(120) NOT NULL,
    published                 BOOLEAN NOT NULL DEFAULT FALSE,
    company_name              VARCHAR(255) NOT NULL,
    headline                  VARCHAR(500),
    summary                   TEXT,
    logo_url                  VARCHAR(1000),
    primary_color             VARCHAR(16),
    contact_email             VARCHAR(320),
    security_page_url         VARCHAR(1000),
    status_page_url           VARCHAR(1000),

    cta_enabled               BOOLEAN NOT NULL DEFAULT TRUE,
    cta_heading               VARCHAR(255),
    cta_body                  TEXT,
    cta_button_label          VARCHAR(120),
    lead_notify_slack_channel VARCHAR(120),

    published_at              TIMESTAMPTZ,

    created_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by                VARCHAR(255),
    updated_by                VARCHAR(255),
    deleted_at                TIMESTAMPTZ,
    version                   BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uq_trust_profile_slug   UNIQUE (slug),
    CONSTRAINT uq_trust_profile_tenant UNIQUE (tenant_id),
    -- The slug is a public URL segment; keep it URL-safe at the storage layer too.
    CONSTRAINT chk_trust_profile_slug  CHECK (slug ~ '^[a-z0-9-]+$')
);

CREATE INDEX idx_trust_profile_tenant ON trust_center_profiles (tenant_id);
CREATE INDEX idx_trust_profile_slug   ON trust_center_profiles (slug);

-- The public page looks up exactly one thing: a published profile by slug.
CREATE INDEX idx_trust_profile_published
    ON trust_center_profiles (slug)
    WHERE published = TRUE AND deleted_at IS NULL;

CREATE TABLE trust_center_items (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    profile_id    UUID NOT NULL REFERENCES trust_center_profiles(id) ON DELETE CASCADE,
    kind          VARCHAR(32) NOT NULL DEFAULT 'CONTROL',
    title         VARCHAR(500) NOT NULL,
    body          TEXT,
    icon          VARCHAR(64),
    issuer        VARCHAR(255),
    valid_from    DATE,
    valid_until   DATE,
    access_level  VARCHAR(32) NOT NULL DEFAULT 'PUBLIC',
    document_id   UUID,
    external_url  VARCHAR(1000),
    display_order INTEGER NOT NULL DEFAULT 0,

    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by    VARCHAR(255),
    updated_by    VARCHAR(255),
    deleted_at    TIMESTAMPTZ,
    version       BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT chk_trust_item_kind
        CHECK (kind IN ('CERTIFICATION', 'DOCUMENT', 'CONTROL', 'FAQ')),
    CONSTRAINT chk_trust_item_access
        CHECK (access_level IN ('PUBLIC', 'REQUEST_ONLY')),
    CONSTRAINT chk_trust_item_validity
        CHECK (valid_until IS NULL OR valid_from IS NULL OR valid_until >= valid_from)
);

CREATE INDEX idx_trust_item_profile ON trust_center_items (profile_id);
CREATE INDEX idx_trust_item_kind    ON trust_center_items (kind);
CREATE INDEX idx_trust_item_order   ON trust_center_items (profile_id, display_order);

CREATE TABLE trust_center_leads (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id         UUID NOT NULL REFERENCES tenants(id),
    profile_id        UUID NOT NULL REFERENCES trust_center_profiles(id) ON DELETE CASCADE,
    name              VARCHAR(255) NOT NULL,
    email             VARCHAR(320) NOT NULL,
    company           VARCHAR(255),
    role_title        VARCHAR(255),
    message           TEXT,
    requested_item_id UUID,
    status            VARCHAR(32) NOT NULL DEFAULT 'NEW',
    source_url        VARCHAR(1000),
    -- SHA-256 of the submitting address: enough for rate limiting, not a stored IP.
    ip_hash           VARCHAR(64),

    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        VARCHAR(255),
    updated_by        VARCHAR(255),
    deleted_at        TIMESTAMPTZ,
    version           BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT chk_trust_lead_status
        CHECK (status IN ('NEW', 'CONTACTED', 'GRANTED', 'DECLINED', 'SPAM'))
);

CREATE INDEX idx_trust_lead_tenant  ON trust_center_leads (tenant_id);
CREATE INDEX idx_trust_lead_profile ON trust_center_leads (profile_id);
CREATE INDEX idx_trust_lead_status  ON trust_center_leads (status);
CREATE INDEX idx_trust_lead_email   ON trust_center_leads (email);

-- Backs the per-source rate limit on the public lead form.
CREATE INDEX idx_trust_lead_rate_limit ON trust_center_leads (profile_id, ip_hash, created_at);

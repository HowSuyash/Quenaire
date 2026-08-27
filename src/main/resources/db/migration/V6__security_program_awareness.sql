-- Security Program Awareness: observed evidence from connected systems, and
-- the mapping of that evidence onto compliance framework controls.

CREATE TABLE program_signals (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id     UUID NOT NULL REFERENCES tenants(id),
    source        VARCHAR(32) NOT NULL,
    -- Stable identifier inside the source system (Config rule ARN, repo name…).
    external_id   VARCHAR(512) NOT NULL,
    title         VARCHAR(500) NOT NULL,
    detail        TEXT,
    status        VARCHAR(32) NOT NULL DEFAULT 'INFORMATIONAL',
    category      VARCHAR(255),
    resource_type VARCHAR(255),
    source_url    VARCHAR(1000),
    -- When the SOURCE last reported this state, not when we polled. Freshness
    -- checks depend on this distinction.
    observed_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    raw_json      TEXT,

    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by    VARCHAR(255),
    updated_by    VARCHAR(255),
    deleted_at    TIMESTAMPTZ,
    version       BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uq_program_signal_identity UNIQUE (tenant_id, source, external_id),
    CONSTRAINT chk_program_signal_source
        CHECK (source IN ('AWS', 'GITHUB', 'JIRA', 'CONFLUENCE', 'MANUAL')),
    CONSTRAINT chk_program_signal_status
        CHECK (status IN ('PASSING', 'WARNING', 'FAILING', 'INFORMATIONAL'))
);

CREATE INDEX idx_program_signal_tenant   ON program_signals (tenant_id);
CREATE INDEX idx_program_signal_source   ON program_signals (source);
CREATE INDEX idx_program_signal_status   ON program_signals (status);
CREATE INDEX idx_program_signal_observed ON program_signals (observed_at);

-- Grounding an answer searches signal text; these keep that from a seq scan.
CREATE INDEX idx_program_signal_title_lower  ON program_signals (LOWER(title));
CREATE INDEX idx_program_signal_category_low ON program_signals (LOWER(category));

CREATE TABLE control_mappings (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    signal_id       UUID NOT NULL REFERENCES program_signals(id) ON DELETE CASCADE,
    framework       VARCHAR(32) NOT NULL,
    control_id      VARCHAR(64) NOT NULL,
    control_title   VARCHAR(500),
    -- 0-100. Keyword-inferred mappings score lower than operator-confirmed ones.
    strength        INTEGER NOT NULL DEFAULT 50,
    confirmed_by_id UUID,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255),
    deleted_at      TIMESTAMPTZ,
    version         BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uq_control_mapping UNIQUE (tenant_id, signal_id, framework, control_id),
    CONSTRAINT chk_control_mapping_framework
        CHECK (framework IN ('SOC2', 'ISO_27001', 'NIST_CSF', 'PCI_DSS', 'HIPAA', 'GDPR', 'CIS')),
    CONSTRAINT chk_control_mapping_strength
        CHECK (strength BETWEEN 0 AND 100)
);

CREATE INDEX idx_control_mapping_tenant    ON control_mappings (tenant_id);
CREATE INDEX idx_control_mapping_signal    ON control_mappings (signal_id);
CREATE INDEX idx_control_mapping_framework ON control_mappings (framework);
CREATE INDEX idx_control_mapping_control   ON control_mappings (control_id);

-- Coverage reports read "all mappings for one framework in one tenant".
CREATE INDEX idx_control_mapping_coverage
    ON control_mappings (tenant_id, framework, control_id)
    WHERE deleted_at IS NULL;

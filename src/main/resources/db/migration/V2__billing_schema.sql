-- Enable uuid-ossp for UUID generation (if not already enabled)
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create custom types for billing
CREATE TYPE subscription_status AS ENUM ('ACTIVE', 'PAST_DUE', 'CANCELED', 'INCOMPLETE', 'TRIALING', 'PAUSED');
CREATE TYPE billing_plan AS ENUM ('FREE', 'STARTER', 'PROFESSIONAL', 'ENTERPRISE', 'CUSTOM');
CREATE TYPE invoice_status AS ENUM ('DRAFT', 'OPEN', 'PAID', 'VOID', 'UNCOLLECTIBLE');

-- Subscriptions table
CREATE TABLE subscriptions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    plan billing_plan NOT NULL DEFAULT 'FREE',
    status subscription_status NOT NULL DEFAULT 'INCOMPLETE',
    stripe_customer_id VARCHAR(255),
    stripe_subscription_id VARCHAR(255) UNIQUE,
    stripe_price_id VARCHAR(255),
    current_period_start TIMESTAMP WITH TIME ZONE,
    current_period_end TIMESTAMP WITH TIME ZONE,
    cancel_at_period_end BOOLEAN NOT NULL DEFAULT FALSE,
    canceled_at TIMESTAMP WITH TIME ZONE,
    trial_start TIMESTAMP WITH TIME ZONE,
    trial_end TIMESTAMP WITH TIME ZONE,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_subscriptions_tenant_id ON subscriptions(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_subscriptions_stripe_customer_id ON subscriptions(stripe_customer_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_subscriptions_stripe_subscription_id ON subscriptions(stripe_subscription_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_subscriptions_status ON subscriptions(status) WHERE deleted_at IS NULL;

-- Invoices table
CREATE TABLE invoices (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    stripe_invoice_id VARCHAR(255) UNIQUE,
    stripe_subscription_id VARCHAR(255),
    invoice_number VARCHAR(100) NOT NULL UNIQUE,
    status invoice_status NOT NULL DEFAULT 'DRAFT',
    amount_due BIGINT NOT NULL,
    amount_paid BIGINT,
    amount_remaining BIGINT,
    currency VARCHAR(3) NOT NULL DEFAULT 'usd',
    due_date TIMESTAMP WITH TIME ZONE,
    paid_at TIMESTAMP WITH TIME ZONE,
    period_start TIMESTAMP WITH TIME ZONE,
    period_end TIMESTAMP WITH TIME ZONE,
    invoice_pdf_url VARCHAR(500),
    hosted_invoice_url VARCHAR(500),
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_invoices_tenant_id ON invoices(tenant_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_invoices_stripe_invoice_id ON invoices(stripe_invoice_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_invoices_status ON invoices(status) WHERE deleted_at IS NULL;
CREATE INDEX idx_invoices_due_date ON invoices(due_date) WHERE deleted_at IS NULL;

-- Usage records table
CREATE TABLE usage_records (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    metric VARCHAR(100) NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 0,
    period_start TIMESTAMP WITH TIME ZONE NOT NULL,
    period_end TIMESTAMP WITH TIME ZONE NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_usage_records_tenant_period ON usage_records(tenant_id, period_start, period_end) WHERE deleted_at IS NULL;
CREATE INDEX idx_usage_records_metric ON usage_records(metric) WHERE deleted_at IS NULL;

-- Updated at triggers for billing tables
CREATE TRIGGER update_subscriptions_updated_at BEFORE UPDATE ON subscriptions FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_invoices_updated_at BEFORE UPDATE ON invoices FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_usage_records_updated_at BEFORE UPDATE ON usage_records FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- RLS policies for billing tables
ALTER TABLE subscriptions ENABLE ROW LEVEL SECURITY;
ALTER TABLE invoices ENABLE ROW LEVEL SECURITY;
ALTER TABLE usage_records ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON subscriptions FOR ALL USING (true);
CREATE POLICY tenant_isolation ON invoices FOR ALL USING (true);
CREATE POLICY tenant_isolation ON usage_records FOR ALL USING (true);
-- payments_db：支付服务自有 schema

CREATE TABLE payments (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    customer_id BIGINT NOT NULL,
    -- 稳定的支付操作标识：UNIQUE(order_id) 保证一单至多一次有效支付尝试，
    -- 重试/重复事件只能查到既有记录，不可能发起第二次扣款
    amount NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL,
    -- PENDING -> COMPLETED / FAILED / UNKNOWN；UNKNOWN 经对账进入 COMPLETED/FAILED
    status VARCHAR(20) NOT NULL,
    provider_charge_id VARCHAR(100),
    failure_reason VARCHAR(200),
    refund_amount NUMERIC(12, 2),
    refund_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_payment_order UNIQUE (order_id)
);
CREATE INDEX idx_payments_status_updated ON payments (status, updated_at);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    customer_id BIGINT,
    event_type VARCHAR(100) NOT NULL,
    sequence BIGINT NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ
);
CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;
CREATE INDEX idx_outbox_aggregate_seq ON outbox_events (aggregate_id, sequence);

CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

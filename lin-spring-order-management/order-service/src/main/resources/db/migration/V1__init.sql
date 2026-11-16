-- orders_db：订单服务自有 schema（其他服务禁止直连本库）
CREATE TABLE orders (
    id UUID PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    status VARCHAR(40) NOT NULL,
    total_amount NUMERIC(12, 2) NOT NULL,
    currency CHAR(3) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    -- 幂等键关联的请求指纹（SHA-256）：同一键不同负载应拒绝，而非静默返回无关结果
    request_fingerprint VARCHAR(64) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- 幂等性由数据库唯一约束兜底，而非"先查后插"（并发下存在竞态窗口）
    CONSTRAINT uq_order_idempotency UNIQUE (customer_id, idempotency_key)
);

-- 下单时快照约定单价：产品日后调价不影响已有订单
CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    product_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(12, 2) NOT NULL
);
CREATE INDEX idx_order_items_order ON order_items (order_id);

-- 事务性 Outbox：订单与事件同事务提交，发布器异步投递到 Kafka
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
-- 只扫描未发布事件（部分索引，避免全表扫描）
CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;
CREATE INDEX idx_outbox_aggregate_seq ON outbox_events (aggregate_id, sequence);

-- 消费者幂等：已处理事件登记表（eventId 唯一）
CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

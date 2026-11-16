-- notifications_db：通知服务仅登记"哪些事件已通知"（幂等），不持有业务状态

CREATE TABLE sent_notifications (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    customer_id BIGINT,
    order_id UUID,
    sent_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_sent_notifications_order ON sent_notifications (order_id);

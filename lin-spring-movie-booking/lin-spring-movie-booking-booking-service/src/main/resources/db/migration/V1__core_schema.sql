-- =====================================================================
-- BookMyShow-style booking schema (MySQL 8 semantics).
-- Correctness invariants live HERE, in the database:
--   * uk_confirmed_seat_ownership(show_id, seat_id) on booking_seats is
--     the structural backstop: no application bug can double-issue a seat.
--   * show_seats clustered PK (show_id, seat_id) makes seat-map reads a
--     single contiguous range scan.
-- All timestamps are DATETIME(3) UTC.
-- =====================================================================

-- One row per (show, seat). Created in bulk when a show is published.
CREATE TABLE show_seats (
    show_id         BIGINT        NOT NULL,
    seat_id         BIGINT        NOT NULL,
    status          VARCHAR(16)   NOT NULL DEFAULT 'AVAILABLE',
    price           DECIMAL(10,2) NOT NULL,
    hold_id         VARCHAR(64)   NULL,
    hold_expires_at DATETIME(3)   NULL,
    booking_id      BIGINT        NULL,
    version         BIGINT        NOT NULL DEFAULT 0,
    updated_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (show_id, seat_id),
    KEY idx_show_seats_expiry (hold_expires_at),
    KEY idx_show_seats_hold (hold_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE bookings (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    reference       VARCHAR(32)   NOT NULL,
    user_id         BIGINT        NOT NULL,
    show_id         BIGINT        NOT NULL,
    hold_id         VARCHAR(64)   NOT NULL,
    seat_count      SMALLINT      NOT NULL,
    total_amount    DECIMAL(10,2) NOT NULL,
    status          VARCHAR(24)   NOT NULL,
    hold_extended   TINYINT(1)    NOT NULL DEFAULT 0,
    idempotency_key VARCHAR(64)   NOT NULL,
    created_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    hold_expires_at DATETIME(3)   NULL,
    confirmed_at    DATETIME(3)   NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_bookings_reference (reference),
    UNIQUE KEY uk_bookings_idempotency (user_id, idempotency_key),
    UNIQUE KEY uk_bookings_hold (hold_id),
    KEY idx_bookings_user_created (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Append-only ledger of CONFIRMED seat ownership.
-- Rows are inserted only at confirmation and deleted only on refund/cancel.
-- <<<< THE INVARIANT: a seat of a show can appear here at most once. >>>>
CREATE TABLE booking_seats (
    booking_id BIGINT      NOT NULL,
    show_id    BIGINT      NOT NULL,
    seat_id    BIGINT      NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (booking_id, seat_id),
    UNIQUE KEY uk_confirmed_seat_ownership (show_id, seat_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE payments (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    booking_id      BIGINT        NOT NULL,
    gateway         VARCHAR(16)   NOT NULL DEFAULT 'SIMULATED',
    gateway_ref     VARCHAR(64)   NULL,
    status          VARCHAR(16)   NOT NULL,
    amount          DECIMAL(10,2) NOT NULL,
    idempotency_key VARCHAR(64)   NOT NULL,
    failure_code    VARCHAR(64)   NULL,
    created_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    -- Collapses duplicate webhook deliveries: five callbacks, one row affected.
    UNIQUE KEY uk_payment_gateway_ref (gateway, gateway_ref),
    KEY idx_payment_status_updated (status, updated_at),
    KEY idx_payment_booking (booking_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Response cache for double-tapped buttons / retried requests.
CREATE TABLE idempotency_keys (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    idem_key         VARCHAR(64)  NOT NULL,
    endpoint         VARCHAR(64)  NOT NULL,
    response_payload TEXT         NOT NULL,
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_idem (user_id, idem_key, endpoint),
    KEY idx_idem_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Transactional outbox: written in the same transaction as the state change,
-- relayed to Kafka after commit by the poller.
CREATE TABLE outbox_events (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    event_id     VARCHAR(64)   NOT NULL,
    topic        VARCHAR(128)  NOT NULL,
    aggregate_id VARCHAR(64)   NOT NULL,
    payload      TEXT          NOT NULL,
    status       VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    attempts     INT           NOT NULL DEFAULT 0,
    available_at DATETIME(3)   NULL,
    published_at DATETIME(3)   NULL,
    last_error   VARCHAR(1024) NULL,
    lock_owner   VARCHAR(64)   NULL,
    locked_at    DATETIME(3)   NULL,
    created_at   DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_outbox_event_id (event_id),
    KEY idx_outbox_pending (status, available_at, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Inbox: consumer-side idempotency backed by a unique key.
CREATE TABLE processed_events (
    event_id     VARCHAR(64)   NOT NULL,
    event_type   VARCHAR(128)  NOT NULL,
    processed_at DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (event_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

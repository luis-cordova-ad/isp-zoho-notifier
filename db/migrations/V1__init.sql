-- V1__init.sql — Table d'idempotence pour isp-zoho-notifier
-- Base : freeradius_db (via ProxySQL svc-data-persistence:6033)

CREATE TABLE IF NOT EXISTS isp_zoho_notifier_sent (
    request_id       VARCHAR(36)  NOT NULL,
    zoho_account_id  VARCHAR(255) NOT NULL,
    action           VARCHAR(50),
    zoho_note_id     VARCHAR(255),
    status           ENUM('SENT', 'FAILED') NOT NULL,
    sent_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    error_message    TEXT,
    PRIMARY KEY (request_id),
    INDEX idx_zoho_account_id (zoho_account_id),
    INDEX idx_sent_at (sent_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

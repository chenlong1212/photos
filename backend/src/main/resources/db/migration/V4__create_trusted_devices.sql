CREATE TABLE trusted_devices (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    token_hash CHAR(64) NOT NULL UNIQUE,
    user_agent VARCHAR(512) NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    INDEX idx_trusted_devices_expires_at (expires_at)
);

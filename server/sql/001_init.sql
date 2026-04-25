CREATE TABLE IF NOT EXISTS users (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    firebase_uid VARCHAR(128) NOT NULL,
    email VARCHAR(320) NOT NULL,
    name VARCHAR(120) NULL,
    avatar_url TEXT NULL,
    provider VARCHAR(32) NOT NULL DEFAULT 'google',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY users_firebase_uid_unique (firebase_uid),
    UNIQUE KEY users_email_unique (email)
);

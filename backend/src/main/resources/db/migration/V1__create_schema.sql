CREATE TABLE albums (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    album_key VARCHAR(32) NOT NULL UNIQUE,
    label VARCHAR(64) NOT NULL,
    folder_name VARCHAR(128) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE album_days (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    album_id BIGINT NOT NULL,
    photo_date INT NOT NULL,
    info TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_days_album FOREIGN KEY (album_id) REFERENCES albums(id),
    CONSTRAINT uk_days_album_date UNIQUE (album_id, photo_date)
);

CREATE TABLE images (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    album_id BIGINT NOT NULL,
    photo_date INT NOT NULL,
    raw_path VARCHAR(1024) NOT NULL,
    preview_path VARCHAR(1024) NOT NULL,
    original_filename VARCHAR(512) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    photo_time VARCHAR(32) NOT NULL DEFAULT '',
    is_cover BOOLEAN NOT NULL DEFAULT FALSE,
    file_size BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_images_album FOREIGN KEY (album_id) REFERENCES albums(id),
    INDEX idx_images_album_date_order (album_id, photo_date, sort_order, id)
);

CREATE TABLE recycled_images (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    origin_album_key VARCHAR(32) NOT NULL,
    origin_date INT NOT NULL,
    filename VARCHAR(512) NOT NULL,
    raw_path VARCHAR(1024) NOT NULL,
    preview_path VARCHAR(1024) NOT NULL,
    deleted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_recycle_deleted_at (deleted_at)
);

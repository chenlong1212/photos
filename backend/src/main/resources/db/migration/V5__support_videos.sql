ALTER TABLE images
    ADD COLUMN media_type VARCHAR(16) NOT NULL DEFAULT 'photo' AFTER original_filename,
    ADD COLUMN mime_type VARCHAR(128) NOT NULL DEFAULT '' AFTER media_type,
    ADD COLUMN duration_ms BIGINT NOT NULL DEFAULT 0 AFTER file_size,
    ADD COLUMN width INT NOT NULL DEFAULT 0 AFTER duration_ms,
    ADD COLUMN height INT NOT NULL DEFAULT 0 AFTER width;

ALTER TABLE recycled_images
    ADD COLUMN media_type VARCHAR(16) NOT NULL DEFAULT 'photo' AFTER filename,
    ADD COLUMN mime_type VARCHAR(128) NOT NULL DEFAULT '' AFTER media_type,
    ADD COLUMN duration_ms BIGINT NOT NULL DEFAULT 0 AFTER photo_time,
    ADD COLUMN width INT NOT NULL DEFAULT 0 AFTER duration_ms,
    ADD COLUMN height INT NOT NULL DEFAULT 0 AFTER width;

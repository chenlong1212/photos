ALTER TABLE recycled_images
    ADD COLUMN photo_time VARCHAR(32) NOT NULL DEFAULT '' AFTER preview_path;

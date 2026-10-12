ALTER TABLE chapters
    ADD COLUMN parent_chapter_id BIGINT REFERENCES chapters (id);
ALTER TABLE sources
    ADD COLUMN subject_id BIGINT NOT NULL REFERENCES subjects (id);
ALTER TABLE sources
    ADD COLUMN source_type VARCHAR(30) NOT NULL DEFAULT 'FLAT_POOL';

ALTER TABLE chapters
    ADD COLUMN source_id BIGINT NOT NULL REFERENCES sources (id);
ALTER TABLE chapters
    DROP COLUMN subject_id;
ALTER TABLE chapters
    DROP COLUMN parent_chapter_id;

ALTER TABLE cards
    ALTER COLUMN chapter_id DROP NOT NULL;
ALTER TABLE cards
    ALTER COLUMN source_id SET NOT NULL;
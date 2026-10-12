CREATE TABLE categories
(
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(255)        NOT NULL,
    slug VARCHAR(255) UNIQUE NOT NULL
);

ALTER TABLE subjects
    ADD COLUMN category_id BIGINT REFERENCES categories (id);

INSERT INTO categories (name, slug)
VALUES ('Student', 'student');

UPDATE subjects
SET category_id = (SELECT id FROM categories WHERE slug = 'student');

ALTER TABLE subjects
    ALTER COLUMN category_id SET NOT NULL;
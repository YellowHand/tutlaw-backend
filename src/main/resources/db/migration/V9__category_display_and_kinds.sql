-- Etap 3: Testy w układzie „rodzaj → przedmiot/akt prawny → źródło".
-- Kategorie (rodzaje) dostają etykietę elementów, które zawierają, oraz kolejność wyświetlania,
-- a do bazy trafia lista działów prawa (wzorowana na układzie „Nowy test" u konkurencji).
-- Same ustawy i przedmioty nie są tu dodawane: Twoje istniejące przedmioty zostają tam, gdzie są
-- (po migracji V4 wszystkie leżą w „Student"). Przenoszenie: docs/assign-subjects-to-categories.sql.

ALTER TABLE categories
    ADD COLUMN items_label VARCHAR(50) NOT NULL DEFAULT 'Przedmioty';
ALTER TABLE categories
    ADD COLUMN sort_order INT NOT NULL DEFAULT 100;

-- „Student" zostaje pierwszy: tam leży dziś cała istniejąca zawartość.
UPDATE categories
SET sort_order  = 5,
    items_label = 'Przedmioty'
WHERE slug = 'student';

-- Działy prawa. ON CONFLICT: migracja jest bezpieczna, jeśli któryś dział już dodałeś ręcznie.
INSERT INTO categories (name, slug, items_label, sort_order)
VALUES ('Prawo konstytucyjne i UE', 'prawo-konstytucyjne-i-ue', 'Akty prawne', 10),
       ('Prawo cywilne i postępowanie cywilne', 'prawo-cywilne-i-postepowanie-cywilne', 'Akty prawne', 20),
       ('Prawo karne i postępowanie karne', 'prawo-karne-i-postepowanie-karne', 'Akty prawne', 30),
       ('Prawo administracyjne', 'prawo-administracyjne', 'Akty prawne', 40),
       ('Prawo gospodarcze i handlowe', 'prawo-gospodarcze-i-handlowe', 'Akty prawne', 50),
       ('Prawo pracy i ubezpieczeń społecznych', 'prawo-pracy-i-ubezpieczen-spolecznych', 'Akty prawne', 60),
       ('Prawo korporacyjne', 'prawo-korporacyjne', 'Akty prawne', 70),
       ('Inne', 'inne', 'Akty prawne', 90)
ON CONFLICT (slug) DO NOTHING;

ALTER TABLE categories
    ADD CONSTRAINT chk_categories_sort_order CHECK (sort_order >= 0);

-- Przenoszenie przedmiotów / aktów prawnych do właściwych rodzajów (kategorii) w zakładce Testy.
--
-- Po migracji V4 wszystkie Twoje dotychczasowe przedmioty leżą w kategorii „Student".
-- Migracja V9 dodała działy prawa (Prawo karne i postępowanie karne itd.), ale celowo nie przenosi
-- niczego sama, bo nie wiem, jak nazwałeś przedmioty. Zrób to ręcznie w 3 krokach.
--
-- Uruchomienie:  psql -d tutlaw -f docs/assign-subjects-to-categories.sql   (po odkomentowaniu UPDATE-ów)

-- KROK 1. Zobacz, co masz i gdzie leży.
SELECT s.id, s.name, s.slug, c.name AS kategoria
FROM subjects s
JOIN categories c ON c.id = s.category_id
ORDER BY c.sort_order, s.name;

-- KROK 2. Zobacz dostępne kategorie (slug jest potrzebny w kroku 3).
SELECT id, name, slug, items_label, sort_order FROM categories ORDER BY sort_order, name;

-- KROK 3. Przenieś przedmioty, które mają być pod danym działem. Przykłady (dopasuj slugi przedmiotów):
--
-- UPDATE subjects
--    SET category_id = (SELECT id FROM categories WHERE slug = 'prawo-karne-i-postepowanie-karne')
--  WHERE slug IN ('kodeks-karny', 'kodeks-karny-skarbowy', 'kodeks-postepowania-karnego');
--
-- UPDATE subjects
--    SET category_id = (SELECT id FROM categories WHERE slug = 'prawo-cywilne-i-postepowanie-cywilne')
--  WHERE slug IN ('kodeks-cywilny', 'kodeks-postepowania-cywilnego');
--
-- UPDATE subjects
--    SET category_id = (SELECT id FROM categories WHERE slug = 'prawo-konstytucyjne-i-ue')
--  WHERE slug IN ('konstytucja-rp');
--
-- Zmiana kolejności lub nazwy etykiety kategorii (np. żeby „Student" był na końcu):
-- UPDATE categories SET sort_order = 100 WHERE slug = 'student';
-- UPDATE categories SET items_label = 'Ustawy' WHERE slug = 'prawo-karne-i-postepowanie-karne';

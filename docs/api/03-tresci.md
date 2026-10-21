# API: ścieżki, przedmioty, pula pytań, katalog Testów

[← Przegląd API](README.md) · [Spis treści](../README.md)

Struktura materiału to dwa drzewa, które spotykają się w **akcie prawnym**:

- **dziedzina → akt prawny (albo zagadnienie) → pytanie**: podział treści (np. „Prawo karne i proces karny” → „Kodeks
  karny”),
- **ścieżka → akty prawne**: kto czego się uczy. Pięć aplikacji (adwokacka i radcowska, sędziowska i prokuratorska,
  uzupełniająca sędziowska, notarialna, komornicza) ma listy aktów, a ścieżka **Student** składa się z przedmiotów
  (kursów), które są podziałem aktów.

Pytania należą do aktu i do **źródła** (arkusz egzaminu z danego roku albo „Pytania autorskie”), a nie do ścieżki: ten
sam akt (i jego pytania) może być w puli kilku ścieżek. Opis tabel: [02. Baza danych](../02-baza-danych.md). Zasady
liczenia puli: [04. Nauka](../04-nauka-testy-fiszki-fsrs.md#pula-pytań-użytkownika).

Wszystkie endpointy z tego pliku są **tylko do odczytu albo zapisują ustawienia zalogowanego** (wybór aktów i filtr
źródeł). **Aplikacja nie ma endpointów do dodawania ani edycji treści** (aktów, ścieżek, arkuszy, pytań): treść wczytuje
się skryptami SQL z katalogu `baza/` (patrz [05. Konfiguracja i uruchomienie](../05-konfiguracja-i-uruchomienie.md)).

## Ścieżki

### `GET /api/tracks`

Sześć ścieżek w kolejności wyświetlania (kolejność z bazy). Służy do listy wyboru („Do czego się uczysz?”), etykiet i
chipów na stronie głównej.

```json
[
  { "id": 1, "slug": "adwokacka-radcowska", "name": "Aplikacja adwokacka i radcowska",
    "shortName": "Adwokacka i radcowska", "kind": "EXAM" },
  { "id": 6, "slug": "student", "name": "Student", "shortName": "Student", "kind": "STUDENT" }
]
```

| Pole | Znaczenie |
|------|-----------|
| `slug` | Stały identyfikator tekstowy (w eksporcie danych, w adresach FE) |
| `name` / `shortName` | Pełna nazwa i skrót (skrót jest też w nazwach arkuszy: „Notarialna 2024”) |
| `kind` | `EXAM`: aplikacja z egzaminem i arkuszami; `STUDENT`: ścieżka bez egzaminu, z przedmiotami |

Wywołanie jest dostępne po zalogowaniu (jak reszta aplikacji). Wybór ścieżki: [`PUT /api/me/track`](09-ustawienia.md#put-apimetrack--wybór-ścieżki).

## Przedmioty studenta

### `GET /api/courses`

Przedmioty studenta (kursy) w kolejności z bazy. Przedmiot to **tylko podział aktów prawnych**: wskazuje akty (i
zagadnienia), z których składa się jego materiał. Pytania i tak należą do aktów, więc nie ma osobnych „pytań
przedmiotu”. Administrator tworzy przedmioty i przypisuje do nich akty skryptem SQL (`course_acts`); student wybiera
przedmioty, z których się uczy ([`PUT /api/me/pool/courses`](#put-apimepoolcourses--wybór-przedmiotów-studenta)), i dostaje
wszystkie pytania aktów tych przedmiotów. Ten sam akt w kilku przedmiotach nie dubluje pytań.

```json
[
  { "id": 3, "name": "Prawo karne", "slug": "prawo-karne", "questionCount": 212, "actIds": [12, 13, 15] },
  { "id": 9, "name": "Logika prawnicza", "slug": "logika-prawnicza", "questionCount": 40, "actIds": [41] }
]
```

| Pole | Znaczenie |
|------|-----------|
| `questionCount` | Pytania przedmiotu **ze wszystkich źródeł** (suma pytań jego aktów) |
| `actIds` | Numery aktów prawnych przedmiotu. Ten sam akt może należeć do kilku przedmiotów |

Zagadnienia spoza prawa (np. paremie łacińskie, logika) to akty rodzaju `TOPIC`; ich pytania są zwykłymi pytaniami.

## Pula pytań użytkownika

Pula to zbiór pytań, które użytkownik może rozwiązywać: **pytania z jego własnych arkuszy ALBO z aktów prawnych jego
ścieżki** (dla Studenta: z aktów **wybranych przedmiotów**, a bez wyboru wszystkich przedmiotów), opcjonalnie **zawężone**
do aktów wybranych przez użytkownika (**zakres nauki**; Student zawęża przedmiotami, nie aktami). Użytkownik, który nie wybrał ścieżki, ma pulę obejmującą wszystko.

**Źródło pytań nie jest częścią puli** (od Etapu 34). Trwałego filtra źródeł nie ma: źródło (`SourceMode`: `ALL`, `MINISTRY`
albo `AUTHORED`) podaje się osobno przy każdym teście, w parametrze `source` katalogu Testów, liczników i losowania
(zob. [05. Nauka](05-nauka.md)). `MINISTRY` to arkusze egzaminacyjne (arkusze ścieżki i arkusze innych ścieżek razem),
`AUTHORED` to pytania autorskie, a `ALL` (domyślnie, także gdy parametru brak) to wszystkie źródła.

### `GET /api/me/pool`

Opis puli do karty i okna „Zakres nauki”: wybrana ścieżka, dziedziny z aktami (Student: przedmioty) do zaznaczenia.

```json
{
  "track": { "id": 4, "slug": "notarialna", "name": "Aplikacja notarialna", "shortName": "Notarialna", "kind": "EXAM" },
  "narrowed": true,
  "questionCount": 318,
  "fields": [
    {
      "id": 1, "name": "Prawo cywilne i postępowanie cywilne", "slug": "prawo-cywilne",
      "acts": [
        { "id": 5, "name": "Kodeks cywilny", "shortName": "k.c.", "kind": "ACT", "questionCount": 410, "selected": true }
      ]
    },
    { "id": 8, "name": "Prawo Unii Europejskiej i międzynarodowe", "slug": "prawo-ue-i-miedzynarodowe", "acts": [] }
  ],
  "courses": []
}
```

| Pole | Znaczenie |
|------|-----------|
| `track` | Wybrana ścieżka; `null`, gdy użytkownik jeszcze jej nie wybrał |
| `narrowed` | Czy użytkownik zawęził pulę do wybranych aktów (Student: do wybranych przedmiotów, czyli nie do wszystkich). Bez zawężenia obowiązują wszystkie akty ścieżki (Student: wszystkie przedmioty) |
| `questionCount` | Pytania w puli po zawężeniu do aktów (ze wszystkich źródeł) |
| `fields` | Dziedziny z aktami ścieżki użytkownika; dziedzina **bez** takich aktów jest pomijana (np. „Student” dla użytkownika aplikacji). Na ścieżce Student pusta lista (Student wybiera przedmioty) |
| `courses` | Tylko na ścieżce Student: przedmioty do wyboru (`id`, `name`, `slug`, `questionCount` = pytania aktów przedmiotu ze wszystkich źródeł, `selected`). Przedmiot bez pytań jest pomijany. Bez własnego wyboru wszystkie mają `selected: true`. Na pozostałych ścieżkach pusta lista |
| `fields[].acts[].selected` | Czy akt jest w puli po zawężeniu; `questionCount` to pytania aktu ze wszystkich źródeł |
| `fields[].acts[].kind` | `ACT` (akt prawny) albo `TOPIC` (zagadnienie, np. paremie) |

### `PUT /api/me/pool/acts` — zawężenie puli do aktów

Zawęża pulę do wybranych aktów prawnych (trwale, na koncie). **Tylko dla aplikacji**: Student wybiera przedmioty
([`PUT /api/me/pool/courses`](#put-apimepoolcourses--wybór-przedmiotów-studenta)), a wpisy o aktach są u niego ignorowane.
Zawężenie obowiązuje w Testach, w fiszkach, w testach z kreatora,
w „Moich błędach” i w statystykach (poza statystykami źródeł), ale **nie dotyczy egzaminu próbnego**.

```json
{ "actIds": [5, 6, 12] }
```

| Pole | Reguła |
|------|--------|
| `actIds` | Wymagana lista (może być pusta), najwyżej 1000 numerów |

- **Pusta lista** albo zaznaczenie **wszystkich** aktów ścieżki zdejmuje zawężenie (`narrowed: false`): dzięki temu akty
  dodane później przez administratora pojawiają się użytkownikowi same.
- Gdy po zmianie list aktów przez administratora z zapisanego zawężenia nic nie zostało w aktach ścieżki, pula wraca do
  całej ścieżki (zawężenie nie może wyciąć całej puli).
- Zmiana ścieżki ([`PUT /api/me/track`](09-ustawienia.md#put-apimetrack--wybór-ścieżki)) czyści zawężenie.

**Odpowiedź `200`:** ten sam kształt co `GET /api/me/pool`.

**Błędy:** `400` — „Wybrany akt prawny nie należy do Twojej puli pytań.” (numer spoza aktów ścieżki użytkownika),
„lista aktów jest wymagana”, „za dużo wybranych aktów prawnych”.

### `PUT /api/me/pool/courses` — wybór przedmiotów Studenta

Wybiera przedmioty, z których uczy się Student (trwale, na koncie). Pulą są wszystkie pytania aktów wybranych przedmiotów;
akty wspólne dla kilku przedmiotów liczą się raz. Wybór obowiązuje tak samo jak zawężenie do aktów: w Testach, fiszkach,
własnych testach, „Moich błędach” i statystykach, ale **nie dotyczy egzaminu próbnego**.

```json
{ "courseIds": [3, 9] }
```

| Pole | Reguła |
|------|--------|
| `courseIds` | Wymagana lista (może być pusta), najwyżej 200 numerów |

- **Pusta lista** albo zaznaczenie **wszystkich** przedmiotów zdejmuje wybór (`narrowed: false`): przedmioty dodane później
  przez administratora pojawiają się wtedy same.
- Wybrany przedmiot, którego administrator potem usunął albo opróżnił z aktów, jest ignorowany (wybór nie może wyciąć
  całej puli).
- Wybór **zostaje przy zmianie ścieżki** ([`PUT /api/me/track`](09-ustawienia.md#put-apimetrack--wybór-ścieżki)) i przy
  zerowaniu postępów.

**Odpowiedź `200`:** ten sam kształt co `GET /api/me/pool`.

**Błędy:** `400` — „Przedmioty wybierasz na ścieżce Student.” (inna ścieżka), „Wybrany przedmiot nie istnieje albo nie ma
jeszcze pytań.” (nic się wtedy nie zmienia), „lista przedmiotów jest wymagana”, „za dużo wybranych przedmiotów”.

## Katalog Testów

### `GET /api/tests/catalog`

Drzewo kreatora Testów **jednym wywołaniem**: dziedzina → akt prawny → rozdział, z licznikami pytań dla zalogowanego
użytkownika. Liczą się tylko pytania z **puli** użytkownika (akty jego ścieżki i zawężenie) i z wybranego **źródła**.

| Parametr | Znaczenie |
|----------|-----------|
| `source` | Opcjonalny: `ALL` (domyślnie), `MINISTRY` albo `AUTHORED`. Akty i rozdziały bez pytań z tego źródła są pomijane |

```json
{
  "narrowed": false,
  "fields": [
    {
      "id": 2, "name": "Prawo karne i proces karny", "slug": "prawo-karne",
      "questionCount": 420, "solvedCount": 130,
      "acts": [
        { "id": 12, "name": "Kodeks karny", "shortName": "k.k.", "slug": "kodeks-karny", "kind": "ACT",
          "questionCount": 300, "solvedCount": 100,
          "sections": [
            { "id": 51, "name": "XIX. Przestępstwa przeciwko życiu i zdrowiu", "questionCount": 40 },
            { "id": 52, "name": "XXXV. Przestępstwa przeciwko mieniu", "questionCount": 25 }
          ] }
      ]
    }
  ],
  "courses": []
}
```

| Pole | Znaczenie |
|------|-----------|
| `narrowed` | Czy pula jest zawężona przez użytkownika do wybranych aktów |
| `questionCount` | Pytania, które użytkownik **może teraz rozwiązywać** (z wybranego źródła) |
| `solvedCount` | Ile z nich rozwiązał co najmniej raz w teście |
| `acts[].sections` | Rozdziały aktu, w których jest co rozwiązywać (`id`, `name`, `questionCount`), według `sortOrder`. Pusta lista, gdy akt nie ma rozdziałów. Pytania **bez rozdziału** są tylko w akcie jako całości, więc suma rozdziałów może być mniejsza niż `questionCount` aktu |
| `courses` | Tylko dla Studenta: wybrane (albo wszystkie) przedmioty z licznikami (`id`, `name`, `slug`, `questionCount`, `solvedCount`, `actIds`). Przedmioty się nakładają (ten sam akt w dwóch przedmiotach liczy się w obu), więc ich sumy nie dają puli. Przedmiot bez pytań jest pomijany. Dla aplikacji pusta lista |

Rozdziały (`act_sections`) wczytuje administrator skryptem SQL (katalog `baza/`, skrypty 07 i 08); pytanie ma najwyżej jeden
rozdział (`questions.section_id`), a zwykłe pytania wczytane bez rozdziału po prostu go nie mają.

Kolejność: **dziedziny** według `sortOrder` (potem nazwa po polsku), **akty** według `sortOrder` (potem nazwa),
**rozdziały** według `sortOrder`. Liczniki
dziedziny są sumą liczników jej aktów. **Dziedziny bez aktów w puli użytkownika są pomijane** (np. „Student” u użytkownika aplikacji),
tak samo rozdziały bez pytań w wybranym źródle.

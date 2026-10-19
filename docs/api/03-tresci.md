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
przedmiotu”. Wybór przedmiotu w aplikacji to zawężenie puli do jego aktów
([`PUT /api/me/pool/acts`](#put-apimepoolacts--zawężenie-puli-do-aktów)).

```json
[
  { "id": 3, "name": "Prawo karne", "slug": "prawo-karne", "questionCount": 212, "actIds": [12, 13, 15] },
  { "id": 9, "name": "Logika prawnicza", "slug": "logika-prawnicza", "questionCount": 40, "actIds": [41] }
]
```

| Pole | Znaczenie |
|------|-----------|
| `questionCount` | Pytania przedmiotu **ze wszystkich źródeł** (suma pytań jego aktów, bez filtra źródeł) |
| `actIds` | Numery aktów prawnych przedmiotu. Ten sam akt może należeć do kilku przedmiotów |

Zagadnienia spoza prawa (np. paremie łacińskie, logika) to akty rodzaju `TOPIC`; ich pytania są zwykłymi pytaniami.

## Pula pytań użytkownika

Pula to zbiór pytań, które użytkownik może rozwiązywać: **pytania z jego własnych arkuszy ALBO z aktów prawnych jego
ścieżki** (dla Studenta: z aktów wszystkich przedmiotów), opcjonalnie **zawężone** do aktów wybranych przez
użytkownika i **przefiltrowane** po źródłach. Użytkownik, który nie wybrał ścieżki, ma pulę obejmującą wszystko.

### `GET /api/me/pool`

Opis puli do okna „Pula pytań”: wybrana ścieżka, dziedziny z aktami do zaznaczenia i trzy grupy źródeł.

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
  "sourceGroups": [
    { "code": "OWN_SHEETS",   "selected": true,  "questionCount": 600 },
    { "code": "OTHER_SHEETS", "selected": false, "questionCount": 1400 },
    { "code": "AUTHORED",     "selected": true,  "questionCount": 90 }
  ]
}
```

| Pole | Znaczenie |
|------|-----------|
| `track` | Wybrana ścieżka; `null`, gdy użytkownik jeszcze jej nie wybrał |
| `narrowed` | Czy użytkownik zawęził pulę do wybranych aktów. Bez zawężenia obowiązują wszystkie akty ścieżki |
| `questionCount` | Pytania w puli po zawężeniu **i** po filtrze źródeł |
| `fields` | **Wszystkie** dziedziny (każdy widzi wszystkie), ale `acts` zawiera tylko akty ścieżki użytkownika; dziedzina bez jego aktów ma pustą listę |
| `fields[].acts[].selected` | Czy akt jest w puli po zawężeniu; `questionCount` to pytania aktu ze wszystkich źródeł |
| `fields[].acts[].kind` | `ACT` (akt prawny) albo `TOPIC` (zagadnienie, np. paremie) |
| `sourceGroups` | Trzy grupy źródeł: `OWN_SHEETS` (arkusze wybranej ścieżki), `OTHER_SHEETS` (arkusze innych ścieżek), `AUTHORED` (pytania autorskie). `questionCount` to pytania grupy w aktach puli **przed** filtrem; 0 oznacza, że włączenie grupy nic nie da |

### `PUT /api/me/pool/acts` — zawężenie puli do aktów

Zawęża pulę do wybranych aktów prawnych (trwale, na koncie). Zawężenie obowiązuje w Testach, w fiszkach, we własnych
testach, w „Moich błędach” i w statystykach (poza statystykami źródeł), ale **nie dotyczy egzaminu próbnego**.

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

### `PUT /api/me/pool/source-filter` — filtr źródeł

Wybiera, które z trzech grup źródeł mają dawać pytania w Testach.

```json
{ "ownSheets": true, "otherSheets": false, "authored": true }
```

Wszystkie trzy pola są wymagane. Filtr dotyczy **pytań testowych**: katalogu Testów, kolejek testów, własnych testów,
„Moich błędów”, planu nowych pytań, statystyk testów i **egzaminu próbnego** (zawężenie do aktów egzaminu nie dotyczy,
filtr źródeł tak). **Fiszek nie dotyczy**: fiszka wynika z błędu w teście, więc zostaje w nauce niezależnie od tego, z
którego źródła pochodzi pytanie.

**Odpowiedź `200`:** ten sam kształt co `GET /api/me/pool`.

**Błędy:** `400` — „Zaznacz co najmniej jedną grupę pytań.” (filtr nie może ukryć całego materiału).

## Katalog Testów

### `GET /api/tests/catalog`

Całe drzewo ekranu Testów **jednym wywołaniem**: dziedzina → akt prawny oraz osobno lista źródeł, z licznikami pytań
dla zalogowanego użytkownika. Liczą się tylko pytania z **puli** użytkownika (akty jego ścieżki i zawężenie) i ze
**źródeł wybranych w filtrze**.

```json
{
  "narrowed": false,
  "fields": [
    {
      "id": 2, "name": "Prawo karne i proces karny", "slug": "prawo-karne",
      "questionCount": 420, "solvedCount": 130,
      "acts": [
        { "id": 12, "name": "Kodeks karny", "shortName": "k.k.", "slug": "kodeks-karny", "kind": "ACT",
          "questionCount": 300, "solvedCount": 100 }
      ]
    }
  ],
  "sources": [
    { "id": 31, "name": "Notarialna 2024", "kind": "EXAM_SHEET", "group": "OWN_SHEETS", "examYear": 2024,
      "questionCount": 150, "solvedCount": 70 },
    { "id": 1, "name": "Pytania autorskie", "kind": "AUTHORED", "group": "AUTHORED", "examYear": null,
      "questionCount": 40, "solvedCount": 5 }
  ]
}
```

| Pole | Znaczenie |
|------|-----------|
| `narrowed` | Czy pula jest zawężona przez użytkownika do wybranych aktów |
| `questionCount` | Pytania, które użytkownik **może teraz rozwiązywać** |
| `solvedCount` | Ile z nich rozwiązał co najmniej raz w teście |
| `sources[].group` | `OWN_SHEETS`, `OTHER_SHEETS` albo `AUTHORED` (jak w filtrze źródeł) |
| `sources[].kind` | `EXAM_SHEET` (arkusz egzaminu, `examYear` wypełniony) albo `AUTHORED` |

Kolejność: **dziedziny** według `sortOrder` (potem nazwa po polsku), **akty** według `sortOrder` (potem nazwa),
**źródła**: własne arkusze, arkusze innych ścieżek, na końcu pytania autorskie; w grupie od najnowszego roku. Liczniki
dziedziny są sumą liczników jej aktów. **Dziedziny są zawsze wszystkie** (także puste, z zerami i pustą listą aktów),
a źródła bez pytań w puli są pomijane.

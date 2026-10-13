# API: kategorie, przedmioty, źródła, rozdziały, tagi, katalog Testów

[← Przegląd API](README.md) · [Spis treści](../README.md)

Struktura materiału to drzewo: **kategoria → przedmiot / akt prawny → źródło → (rozdział) → karta**. Opis tabel:
[02. Baza danych](../02-baza-danych.md). Odczyt jest dla każdego zalogowanego, dodawanie tylko dla **ADMIN**.

Aplikacja nie ma endpointów do edycji ani usuwania tych elementów, tylko do dodawania. Poprawki robi się w bazie.

## Kategorie

### `GET /api/categories`

Kategorie w kolejności wyświetlania (`sortOrder`, potem nazwa), każda z liczbą przedmiotów.

```json
[
  { "id": 1, "name": "Student", "slug": "student", "itemsLabel": "Przedmioty", "sortOrder": 5, "subjectCount": 4 },
  { "id": 4, "name": "Prawo karne i postępowanie karne", "slug": "prawo-karne-i-postepowanie-karne",
    "itemsLabel": "Akty prawne", "sortOrder": 30, "subjectCount": 3 }
]
```

`itemsLabel` mówi, jak nazwać zawartość kategorii w interfejsie („Przedmioty” albo „Akty prawne”).

### `GET /api/categories/{categoryId}/subjects`

Przedmioty w kategorii, posortowane po polsku według nazwy. Format elementu jak w
[`GET /api/subjects`](#get-apisubjects).

### `POST /api/categories` — ADMIN

```json
{ "name": "Prawo rodzinne", "slug": "prawo-rodzinne", "itemsLabel": "Akty prawne", "sortOrder": 80 }
```

| Pole | Wymagane | Uwagi |
|------|----------|-------|
| `name`, `slug` | tak | Niepuste. `slug` musi być unikalny |
| `itemsLabel` | nie | Domyślnie „Przedmioty” |
| `sortOrder` | nie | Domyślnie 100; nie może być ujemne |

Odpowiedź `200`: utworzona kategoria (`subjectCount` = 0). Błędy: `409` „Kategoria o takim slugu już istnieje”,
`400` (ujemna kolejność, puste pola), `403`.

## Przedmioty i akty prawne

### `GET /api/subjects`

Wszystkie przedmioty (bez filtrowania i sortowania).

```json
[ { "id": 12, "categoryId": 4, "name": "Kodeks karny", "slug": "kodeks-karny" } ]
```

### `POST /api/subjects` — ADMIN

```json
{ "categoryId": 4, "name": "Kodeks karny skarbowy", "slug": "kodeks-karny-skarbowy" }
```

Odpowiedź `200`: utworzony przedmiot. Błędy: `404` (nie ma takiej kategorii), `400` (taki `slug` już istnieje), `403`.

## Źródła

### `GET /api/subjects/{subjectId}/sources`

```json
[
  { "id": 31, "subjectId": 12, "name": "Egzamin notarialny 2024", "author": null, "edition": null, "sourceType": "FLAT_POOL" },
  { "id": 32, "subjectId": 12, "name": "Prawo karne. Część ogólna", "author": "J. Kowalski", "edition": "5", "sourceType": "TEXTBOOK" }
]
```

`sourceType`: `FLAT_POOL` (pula płaska: wszystkie karty od razu dostępne) albo `TEXTBOOK` (podręcznik: karty
dostępne po przeczytaniu rozdziału).

### `POST /api/sources` — ADMIN

```json
{ "subjectId": 12, "name": "Egzamin notarialny 2025", "author": null, "edition": null, "sourceType": "FLAT_POOL" }
```

Wymagane: `subjectId`, `name` (niepuste), `sourceType`. Błędy: `404` (nie ma przedmiotu), `400`, `403`.

## Rozdziały

### `GET /api/sources/{sourceId}/chapters`

Rozdziały źródła w kolejności (`orderIndex`), każdy z informacją, czy **zalogowany użytkownik** go przeczytał.

```json
[
  { "id": 70, "sourceId": 32, "parentChapterId": null, "title": "Zasady ogólne", "orderIndex": 1, "isRead": true },
  { "id": 71, "sourceId": 32, "parentChapterId": 70,   "title": "Zasada legalizmu", "orderIndex": 2, "isRead": false }
]
```

Uwaga na nazwę pola: w JSON jest **`isRead`** (nie `read`).

### `POST /api/chapters` — ADMIN

```json
{ "sourceId": 32, "parentChapterId": 70, "title": "Zasada legalizmu", "orderIndex": 2 }
```

Wymagane: `sourceId`, `title`, `orderIndex`. `parentChapterId` opcjonalne. Błędy: `404` (nie ma źródła albo
rozdziału nadrzędnego), `400`, `403`.

### `PUT /api/chapters/{chapterId}/progress` — oznaczenie przeczytania

```json
{ "isRead": true }
```

Oznacza rozdział jako przeczytany (`true`) albo nieprzeczytany (`false`). Odpowiedź `200`, bez treści. Od tej chwili
karty tego rozdziału **wchodzą do nauki** (albo z niej wypadają). Błąd: `404`, gdy nie ma rozdziału.

## Tagi

### `GET /api/tags`

```json
[ { "id": 1, "name": "przedawnienie" } ]
```

### `POST /api/tags` — ADMIN

```json
{ "name": "przedawnienie" }
```

Błąd `400`, gdy tag o tej nazwie już istnieje (treść komunikatu jest dziś nieprecyzyjna, patrz
[Znane ograniczenia](../07-znane-ograniczenia.md)).

## Katalog Testów

### `GET /api/tests/catalog`

Całe drzewo ekranu Testów **jednym wywołaniem**: kategoria → przedmiot / akt prawny → źródło, z licznikami pytań dla
zalogowanego użytkownika. Liczą się tylko pytania z **pochodzeń wybranych w filtrze** użytkownika
([ustawienia](09-ustawienia.md#filtr-pochodzenia-pytań)).

```json
{
  "categories": [
    {
      "id": 4, "name": "Prawo karne i postępowanie karne", "slug": "prawo-karne-i-postepowanie-karne",
      "itemsLabel": "Akty prawne", "sortOrder": 30,
      "questionCount": 420, "solvedCount": 130, "lockedCount": 60,
      "subjects": [
        {
          "id": 12, "name": "Kodeks karny", "slug": "kodeks-karny",
          "questionCount": 300, "solvedCount": 100, "lockedCount": 60,
          "sources": [
            { "id": 31, "name": "Egzamin notarialny 2024", "sourceType": "FLAT_POOL",
              "questionCount": 150, "solvedCount": 70, "lockedCount": 0 }
          ]
        }
      ]
    }
  ]
}
```

| Pole | Znaczenie |
|------|-----------|
| `questionCount` | Pytania, które użytkownik **może teraz rozwiązywać** (pule płaskie i przeczytane rozdziały) |
| `solvedCount` | Ile z nich rozwiązał co najmniej raz w teście |
| `lockedCount` | Pytania z **nieprzeczytanych** rozdziałów podręczników; odblokują się po przeczytaniu |

Liczniki kategorii i przedmiotów są sumami liczników niższego poziomu. Kolejność: kategorie według `sortOrder` (potem
nazwa), przedmioty alfabetycznie po polsku, źródła według numeru `id`. Kategorie i przedmioty bez źródeł też są w
drzewie (z zerami).

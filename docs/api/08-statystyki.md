# API: statystyki (`/api/statistics`)

[← Przegląd API](README.md) · [Spis treści](../README.md)

Wszystkie endpointy tylko do odczytu, dla zalogowanego użytkownika. Definicje wskaźników („Opanowane”, wskaźnik
zapamiętywania, pewne błędy) są w [04](../04-nauka-testy-fiszki-fsrs.md#jak-liczone-są-statystyki).

## Parametr `mode`

Część endpointów przyjmuje opcjonalny `mode`:

| `mode` | Co jest liczone |
|--------|------------------|
| `FLASHCARD` | Tylko fiszki (bez pytań testowych i bez zablokowanych fiszek powiązanych z pytaniem) |
| `TEST` | Tylko pytania testowe |
| (brak) | Cały materiał: fiszki i pytania razem |

## `GET /api/statistics/overview`

Przegląd nauki. Parametry: `subjectId` (opcjonalny, zawęża do przedmiotu), `mode`.

```json
{
  "totalCards": 820,
  "cardsStarted": 310,
  "matureCards": 95,
  "retentionRate": 88.4,
  "upcomingReviews": [
    { "date": "2026-10-12", "count": 42 },
    { "date": "2026-10-13", "count": 17 },
    { "date": "2026-10-14", "count": 0 }
  ]
}
```

| Pole | Znaczenie |
|------|-----------|
| `totalCards` | Karty, które użytkownik **może teraz ćwiczyć** (pule płaskie i przeczytane rozdziały) |
| `cardsStarted` | Z nich: karty, na które odpowiedział co najmniej raz |
| `matureCards` | Karty „Opanowane”: stabilność powyżej 21 dni |
| `retentionRate` | Procent udanych powtórek z 30 dni (po co najmniej dobie od poprzedniej odpowiedzi); **`null`**, gdy nie było jeszcze prawdziwej powtórki |
| `upcomingReviews` | Prognoza na **7 dni** (dziś i 6 następnych). Karty z minionym terminem liczą się do „dziś” |

## `GET /api/statistics/weak-cards`

Karty, które najłatwiej zapomnisz: najniższa szansa przypomnienia **w tej chwili**.

| Parametr | Opis |
|----------|------|
| `subjectId` | Opcjonalny: zawęża do przedmiotu |
| `limit` | Ile kart, domyślnie 10, od 1 do 100 |

```json
[ { "cardId": 140, "front": "Termin przedawnienia…", "lapses": 4, "currentRetrievability": 0.41,
    "lastReviewAt": "2026-09-20T18:00:00" } ]
```

`currentRetrievability` to liczba od 0 do 1 (szansa przypomnienia sobie karty teraz).

## `GET /api/statistics/chapters`

Opanowanie rozdziałów jednego źródła. Parametry: `sourceId` (**wymagany**), `mode`.

```json
[ { "chapterId": 70, "chapterTitle": "Zasady ogólne", "totalCards": 60, "cardsStarted": 40,
    "matureCards": 22, "masteryPercentage": 36.7 } ]
```

`masteryPercentage` = opanowane / wszystkie karty rozdziału · 100. Liczone dla **wszystkich rozdziałów** źródła w
kolejności rozdziałów.

## `GET /api/statistics/sources`

Postęp w źródłach przedmiotu. Parametry: `subjectId` (**wymagany**), `mode`.

```json
[ { "sourceId": 32, "name": "Prawo karne. Część ogólna", "sourceType": "TEXTBOOK",
    "totalCards": 410, "cardsStarted": 120, "progressPercentage": 29.3 } ]
```

Tu mianownikiem jest **cała zawartość źródła** (także nieprzeczytane rozdziały podręcznika), bo pytanie brzmi „ile z
całej książki już umiem”. `progressPercentage` = rozpoczęte / wszystkie · 100.

## `GET /api/statistics/tests`

Statystyki Testów (zakładka „Testy” na pulpicie). Bez parametrów.

```json
{
  "totalQuestions": 600,
  "solvedQuestions": 210,
  "answersLast30Days": 340,
  "correctLast30Days": 265,
  "accuracyRate": 77.9,
  "confidentMistakesLast30Days": 41,
  "luckyGuessesLast30Days": 22,
  "activity": [ { "date": "2026-09-15", "reviewCount": 12 } ]
}
```

| Pole | Znaczenie |
|------|-----------|
| `totalQuestions` | Pytania testowe, które użytkownik może teraz rozwiązywać (liczone tak jak w katalogu Testów) |
| `solvedQuestions` | Z nich rozwiązane co najmniej raz |
| `answersLast30Days`, `correctLast30Days` | Odpowiedzi w testach z 30 dni i ile z nich poprawnych (niezależnie od „Nie jestem pewien”) |
| `accuracyRate` | Procent poprawnych z 30 dni; `null` bez odpowiedzi |
| `confidentMistakesLast30Days` | Błędne odpowiedzi **bez** „Nie jestem pewien” (utrwalone błędne przekonania) |
| `luckyGuessesLast30Days` | Poprawne odpowiedzi **z** „Nie jestem pewien” (wiedza krucha) |
| `activity` | Liczba odpowiedzi w testach na każdy z ostatnich **28 dni** (dni bez odpowiedzi mają 0) |

## `GET /api/statistics/origins`

Statystyki Testów według **pochodzenia pytań** i **roku egzaminu**. Bez parametrów. Nie podlega filtrowi pochodzenia z
Testów: zawsze pokazuje wszystkie pochodzenia.

```json
{
  "origins": [
    {
      "origin": "NOTARY",
      "stats": {
        "totalQuestions": 300, "solvedQuestions": 120, "matureQuestions": 45, "dueQuestions": 18,
        "answers": 410, "correctAnswers": 320, "accuracyRate": 78.0,
        "answersLast30Days": 90, "accuracyLast30Days": 81.1, "confidentMistakesLast30Days": 9
      },
      "years": [ { "year": 2024, "stats": { "totalQuestions": 150, "solvedQuestions": 70, "…": "…" } } ]
    }
  ]
}
```

- Zawsze **wszystkie sześć pochodzeń** w kolejności: `AUTHORED`, `ADVOCATE_LEGAL_ADVISER`, `JUDGE_PROSECUTOR`, `NOTARY`,
  `SUPPLEMENTARY_JUDGE`, `BAILIFF`; pochodzenia bez pytań mają same zera.
- `years`: rozbicie na lata egzaminu od najnowszego; dla pytań autorskich puste (nie mają roku).
- Liczby dotyczą pytań **dostępnych teraz** dla użytkownika (nieukrytych, z puli płaskiej albo z przeczytanego rozdziału).
  `matureQuestions` to stabilność powyżej 21 dni, `dueQuestions` to pytania z minionym terminem (bez zawieszonych).
  `accuracyRate` i `accuracyLast30Days` są `null` bez odpowiedzi.

## `GET /api/statistics/activity`

Liczba odpowiedzi na dzień. Parametry: `days` (domyślnie 30, od 1 do 365), `mode`.

```json
[ { "date": "2026-10-11", "reviewCount": 25 }, { "date": "2026-10-12", "reviewCount": 31 } ]
```

Lista od najstarszego dnia do dzisiaj; dni bez odpowiedzi mają 0, więc wykres nie ma dziur. Bez `mode` liczone są
fiszki i testy razem.

# API: statystyki (`/api/statistics`)

[← Przegląd API](README.md) · [Spis treści](../README.md)

Wszystkie endpointy tylko do odczytu, dla zalogowanego użytkownika. Definicje wskaźników („Opanowane”, wskaźnik
zapamiętywania, pewne błędy) są w [04](../04-nauka-testy-fiszki-fsrs.md#jak-liczone-są-statystyki). Liczą się pytania z
**puli** użytkownika (akty jego ścieżki po zawężeniu); pytania ukryte przez administratora są pomijane.

## Parametr `mode`

Część endpointów przyjmuje opcjonalny `mode`. Fiszka to tryb nauki tego samego pytania, więc każdy tryb ma własny stan:

| `mode` | Co jest liczone |
|--------|------------------|
| `FLASHCARD` | Stan nauki w trybie fiszek; pytania liczą się tylko, gdy ich fiszka jest **odblokowana** (pewny błąd albo ręczne dodanie) i widoczna. Bez filtra źródeł |
| `TEST` | Stan nauki w testach, z filtrem źródeł |
| (brak) | Oba tryby razem: to samo pytanie, które ma stan w obu trybach, liczy się dwa razy (raz jako test, raz jako fiszka) |

## `GET /api/statistics/overview`

Przegląd nauki. Parametry: `actId` (opcjonalny, zawęża do aktu prawnego), `mode`.

```json
{
  "totalQuestions": 820,
  "questionsStarted": 310,
  "matureQuestions": 95,
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
| `totalQuestions` | Pytania, które użytkownik **może teraz ćwiczyć** w danym trybie (w fiszkach: odblokowane fiszki) |
| `questionsStarted` | Z nich: pytania, na które odpowiedział co najmniej raz w tym trybie |
| `matureQuestions` | Pytania „Opanowane”: stabilność powyżej 21 dni |
| `retentionRate` | Procent udanych powtórek z 30 dni (po co najmniej dobie od poprzedniej odpowiedzi); **`null`**, gdy nie było jeszcze prawdziwej powtórki |
| `upcomingReviews` | Prognoza na **7 dni** (dziś i 6 następnych). Pytania z minionym terminem liczą się do „dziś” |

## `GET /api/statistics/weak-questions`

Pytania, które najłatwiej zapomnisz: najniższa szansa przypomnienia **w tej chwili**.

| Parametr | Opis |
|----------|------|
| `actId` | Opcjonalny: zawęża do aktu prawnego |
| `mode` | Opcjonalny; bez niego oba tryby, a pytanie obecne w obu pojawia się raz (z gorszym wynikiem) |
| `limit` | Ile pytań, domyślnie 10, od 1 do 100 |

```json
[ { "questionId": 140, "body": "Termin przedawnienia…", "mode": "TEST", "lapses": 4,
    "currentRetrievability": 0.41, "lastReviewAt": "2026-09-20T18:00:00" } ]
```

`currentRetrievability` to liczba od 0 do 1 (szansa przypomnienia sobie pytania teraz).

## `GET /api/statistics/acts`

Opanowanie aktów prawnych z puli użytkownika, w kolejności dziedzin i aktów. Parametr: `mode`.

```json
[ { "actId": 12, "name": "Kodeks karny", "shortName": "k.k.", "fieldId": 2, "fieldName": "Prawo karne i proces karny",
    "totalQuestions": 300, "questionsStarted": 180, "matureQuestions": 66, "masteryPercentage": 22.0 } ]
```

`masteryPercentage` = opanowane / wszystkie pytania aktu · 100 (0, gdy akt nie ma pytań). Są wszystkie akty puli, także
te, w których użytkownik jeszcze nic nie zaczął.

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
| `totalQuestions` | Pytania, które użytkownik może teraz rozwiązywać w testach (liczone tak jak w katalogu Testów: pula, zawężenie, filtr źródeł) |
| `solvedQuestions` | Z nich rozwiązane co najmniej raz |
| `answersLast30Days`, `correctLast30Days` | Odpowiedzi w testach z 30 dni i ile z nich poprawnych (niezależnie od „Nie jestem pewien”) |
| `accuracyRate` | Procent poprawnych z 30 dni; `null` bez odpowiedzi |
| `confidentMistakesLast30Days` | Błędne odpowiedzi **bez** „Nie jestem pewien” (utrwalone błędne przekonania) |
| `luckyGuessesLast30Days` | Poprawne odpowiedzi **z** „Nie jestem pewien” (wiedza krucha) |
| `activity` | Liczba odpowiedzi w testach na każdy z ostatnich **28 dni** (dni bez odpowiedzi mają 0) |

## `GET /api/statistics/sources`

Statystyki Testów według **źródła pytań**: arkuszy egzaminów (z podziałem na lata) i pytań autorskich. Bez parametrów.
Nie podlega filtrowi źródeł z Testów ani zawężeniu do aktów: zawsze pokazuje całą pulę ścieżki.

```json
{
  "groups": [
    {
      "group": "OWN_SHEETS",
      "stats": {
        "totalQuestions": 300, "solvedQuestions": 120, "matureQuestions": 45, "dueQuestions": 18,
        "answers": 410, "correctAnswers": 320, "accuracyRate": 78.0,
        "answersLast30Days": 90, "accuracyLast30Days": 81.1, "confidentMistakesLast30Days": 9
      },
      "sources": [
        { "id": 31, "name": "Notarialna 2024", "kind": "EXAM_SHEET", "examYear": 2024,
          "stats": { "totalQuestions": 150, "solvedQuestions": 70, "…": "…" } }
      ]
    }
  ]
}
```

- Zawsze **trzy grupy** w stałej kolejności: `OWN_SHEETS` (arkusze wybranej ścieżki), `OTHER_SHEETS` (arkusze innych
  ścieżek), `AUTHORED` (pytania autorskie); grupy bez pytań mają same zera i pustą listę `sources`.
- `sources`: źródła grupy, arkusze od najnowszego roku; źródła bez pytań w puli są pomijane. `kind` to `EXAM_SHEET`
  albo `AUTHORED` (pytania autorskie nie mają `examYear`).
- Liczby dotyczą pytań **dostępnych teraz** dla użytkownika (nieukrytych, z aktów jego ścieżki).
  `matureQuestions` to stabilność powyżej 21 dni, `dueQuestions` to pytania z minionym terminem (bez zawieszonych).
  `accuracyRate` i `accuracyLast30Days` są `null` bez odpowiedzi.

## `GET /api/statistics/learning-summary`

„Podsumowanie nauki” na ekranie Statystyk: luźne liczby o całej dotychczasowej nauce. Nie przyjmuje `mode`.

```json
{
  "totalAnswers": 1234,
  "testAnswers": 1000,
  "flashcardReviews": 234,
  "examsDone": 3,
  "customTestsDone": 5,
  "studySecondsToday": 1500,
  "studySecondsLast7Days": 9000,
  "studySecondsTotal": 100000,
  "averageSecondsPerQuestion": 18.4,
  "averageSecondsPerFlashcard": 9.2
}
```

| Pole | Znaczenie |
|------|-----------|
| `totalAnswers`, `testAnswers`, `flashcardReviews` | Wszystkie odpowiedzi: w testach (także powtórki, błędy i własne testy) i oceny fiszek. Egzaminy próbne liczone są osobno |
| `examsDone` | Ukończone egzaminy próbne |
| `customTestsDone` | Ukończone własne testy („Moje testy”); nieukończone się nie liczą |
| `studySecondsToday`, `studySecondsLast7Days`, `studySecondsTotal` | **Czas nauki**: czas spędzony na ekranach nauki przy widocznej karcie przeglądarki, razem z czytaniem wyjaśnień (dziś, dziś i sześć poprzednich dni, łącznie). Zgłasza go przeglądarka przez `POST /api/me/study-time` |
| `averageSecondsPerQuestion`, `averageSecondsPerFlashcard` | Średni **czas odpowiedzi** na pytanie testowe i na fiszkę w sekundach (pojedyncza odpowiedź liczy się najwyżej 120 s); `null`, gdy nie ma pomiarów |

Czas nauki i czas odpowiedzi to dwie osobne miary: pierwsza obejmuje cały pobyt na ekranie nauki, druga tylko czas do
udzielenia odpowiedzi na jedno pytanie.

## `GET /api/statistics/activity`

Liczba odpowiedzi na dzień. Parametry: `days` (domyślnie 30, od 1 do 365), `mode`.

```json
[ { "date": "2026-10-11", "reviewCount": 25 }, { "date": "2026-10-12", "reviewCount": 31 } ]
```

Lista od najstarszego dnia do dzisiaj; dni bez odpowiedzi mają 0, więc wykres nie ma dziur. Bez `mode` liczone są
fiszki i testy razem.

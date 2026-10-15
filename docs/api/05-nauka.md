# API: nauka (`/api/review`)

[← Przegląd API](README.md) · [Spis treści](../README.md)

Kolejki, odpowiedzi i liczniki. Wszystkie endpointy wymagają tokenu. **Jak działa algorytm, limity dzienne i
dostępność kart** wyjaśnia [04. Nauka: testy, fiszki, FSRS](../04-nauka-testy-fiszki-fsrs.md); tutaj są tylko
kształty żądań i odpowiedzi.

## Wspólne parametry zakresu

Kolejki i liczniki przyjmują opcjonalny **zakres**, od najwęższego:

| Parametr | Znaczenie |
|----------|-----------|
| `sourceId` | Tylko jedno źródło |
| `subjectId` | Cały przedmiot / akt prawny (gdy brak `sourceId`) |
| `categoryId` | Cała kategoria (gdy brak obu powyższych) |
| (żaden) | Cały dostępny materiał użytkownika |

Brany jest pierwszy podany, w kolejności: źródło, przedmiot, kategoria. Zwracane są tylko karty **dostępne** dla
użytkownika (pula płaska albo przeczytany rozdział, nieukryte).

## Kolejki

### `GET /api/review/queue` — kolejka fiszek

Parametry: zakres oraz opcjonalne `extraNewCards`, `extraDueReviews` (zob. niżej).

**Odpowiedź `200`**: lista fiszek w kolejności sesji (najpierw zaległe, potem nowe).

```json
[
  {
    "cardId": 205,
    "front": "Czym jest kontratyp?",
    "back": "Okoliczność wyłączająca bezprawność czynu zabronionego…",
    "pageNumber": 41,
    "verbatimQuote": false,
    "sourceName": "Prawo karne. Część ogólna",
    "legalBasis": "art. 25–26 k.k.",
    "validAsOf": "2026-01-01",
    "lapses": 2,
    "leech": false,
    "origin": "AUTHORED",
    "examYear": null
  }
]
```

`lapses` i `leech` opisują stan **sprzed tej sesji** (dla nowej karty 0 i `false`). Na odwrocie fiszki pozwalają
zaproponować zawieszenie pytania, z którym użytkownik od dawna sobie nie radzi.

W kolejce są tylko **fiszki** (karty bez opcji ABC) i tylko odblokowane (fiszka powiązana z pytaniem pojawia się po
pewnym błędzie w tym pytaniu albo po ręcznym dodaniu).

### `GET /api/review/test-queue` — kolejka pytań testowych

Parametry jak wyżej. Zaległe i nowe pytania są losowo mieszane (zaległe na początku). Pytania są ograniczone do
**pochodzeń z filtra** użytkownika.

**Odpowiedź `200`**: lista pytań **bez poprawnej odpowiedzi**.

```json
[
  {
    "cardId": 101,
    "front": "Czy czyn zabroniony popełniony w stanie wyższej konieczności jest przestępstwem?",
    "optionA": "Tak, zawsze",
    "optionB": "Nie, jeżeli dobro poświęcone nie przedstawia wartości oczywiście większej od dobra ratowanego",
    "optionC": "Tak, ale sąd odstępuje od wymierzenia kary",
    "sourceName": "Egzamin notarialny 2024",
    "origin": "NOTARY",
    "examYear": 2024
  }
]
```

### Zwiększanie limitu dziennego: `extraNewCards`, `extraDueReviews`

| Parametr | Zachowanie |
|----------|------------|
| (brak obu) | Zaległe do dziennego limitu, potem nowe w ramach dziennego limitu nowych |
| `extraNewCards=N` | Dokładnie N nowych kart (niezależnie od dziennego limitu), nie więcej niż dostępnych. **`0` = sesja bez nowych kart** (same zaległe) |
| `extraDueReviews=N` | Dokładnie N zaległych kart (niezależnie od dziennego limitu); N jest przycinane do 0–200 |

### `GET /api/review/practice-queue?cardIds=1,2,3` — sesja z podanych pytań

Sesja testowa z konkretnych pytań (np. „Przerób błędy teraz” po egzaminie próbnym), **w podanej kolejności**.

| Parametr | Opis |
|----------|------|
| `cardIds` | Lista numerów (`cardIds=1&cardIds=2` albo `cardIds=1,2`), niepusta, bez powtórzeń, najwyżej 200 |

Odpowiedź jak w `test-queue`. Pytania niedostępne, ukryte, zawieszone i niebędące pytaniami testowymi są po cichu
pomijane. Błędy: `400` („Nie podano pytań do przerobienia.”, „Za dużo pytań naraz (maksymalnie 200).”).

### `GET /api/review/mistakes-queue` i `GET /api/review/mistakes-count` — „Powtórz moje błędy”

`mistakes-queue` zwraca do **50** pytań (losowo), w których **ostatnia** odpowiedź w teście z ostatnich 30 dni była
błędna. Format jak `test-queue`.

`mistakes-count`:

```json
{ "count": 12, "lookbackDays": 30, "sessionSize": 50 }
```

`count` to liczba pytań nadal dostępnych do powtórzenia (niezawieszonych, z wybranych pochodzeń).

### `GET /api/review/custom-test/counts` — własny test: ile pytań w zakresie

Pierwszy krok „Własnego testu”: podgląd „Dostępnych: N” przy układaniu testu. Zakres to **suma** wskazanych rodzajów
i aktów prawnych; bez żadnego z nich cały dostępny materiał.

| Parametr | Opis |
|----------|------|
| `categoryIds` | Rodzaje (kategorie): `categoryIds=1&categoryIds=2` albo `categoryIds=1,2`. Opcjonalne |
| `subjectIds` | Akty prawne (przedmioty), jak wyżej. Opcjonalne. Pytanie z rodzaju i z jego aktu liczy się raz |

```json
{ "all": 1240, "newQuestions": 800, "mistakes": 12, "due": 45 }
```

Liczby dotyczą pytań dostępnych dla użytkownika, **niezawieszonych** i z **wybranych pochodzeń** (filtr pochodzenia).
Rodzaje nakładają się: to samo pytanie może być zaległe i błędne, więc suma nie musi dawać `all`. `mistakes` to te
same pytania co w „Moich błędach” (ostatnia odpowiedź błędna, ostatnie 30 dni), ale w wybranym zakresie i bez limitu
50 na sesję. Wyboru lat egzaminu już nie ma: wcześniejsze parametry `yearFrom` i `yearTo` są ignorowane.

### `POST /api/review/custom-test` — własny test: losowanie pytań

Drugi krok: serwer losuje numery pytań i **zapisuje test** jako sesję („Moje testy”, tabela `custom_test_sessions`):
konfigurację, filtr pochodzenia z tej chwili i wylosowane pytania w kolejności sesji. Test powstaje już teraz, więc
porzucony w połowie zostaje na liście jako nieukończony. Sesję otwiera się potem przez
`GET /api/review/practice-queue?cardIds=...`, a odpowiedzi idą normalną drogą testu (ocena pamięci, błędy trafiają do
„Moich błędów”) **z numerem testu** w polu `sessionId` (patrz niżej).

```json
{
  "categoryIds": [2, 3],
  "subjectIds": [41],
  "kind": "NEW",
  "questionCount": 50
}
```

| Pole | Opis |
|------|------|
| `categoryIds`, `subjectIds` | Zakres jak wyżej (najwyżej po 200 numerów); oba puste = cały materiał |
| `kind` | `ALL` (domyślnie), `NEW` (jeszcze nierozwiązywane), `MISTAKES` (ostatnia odpowiedź błędna), `DUE` (po terminie powtórki) |
| `questionCount` | Ile pytań (wymagane): **10, 20, 50, 100 albo 150**. Własnej liczby nie ma. Wyjątek: gdy pytań w zakresie jest **mniej niż 10**, jedyny wybór to wszystkie z nich (liczba równa liczbie dostępnych) |

```json
{ "cardIds": [812, 77, 1203], "available": 3, "requested": 10, "sessionId": 42 }
```

`cardIds` jest w kolejności sesji (zawsze wymieszanej). Gdy jest mniej pytań, niż użytkownik chciał, dostaje wszystkie
(`available` < `requested`); frontend dla „Wszystkie (N)” wysyła najbliższą liczbę z listy, nie mniejszą niż N.
Przy ograniczonej liczbie `MISTAKES` bierze najświeższe błędy, a `DUE` te, które użytkownik najpewniej już zapomniał;
`ALL` i `NEW` są losowe. Własny test **nie jest ograniczony dziennym limitem nowych pytań**. Błędy: `400` (brak pytań
spełniających warunki, z komunikatem dopasowanym do rodzaju; liczba spoza listy: „Wybierz liczbę pytań: 10, 20, 50, 100
albo 150.”).

### `GET /api/custom-tests/sessions` — „Moje testy”: lista

Ostatnie 50 zapisanych własnych testów użytkownika, od najnowszego. Powtórki, „Popraw błędy” i egzamin próbny tu nie
trafiają (egzaminy mają własną historię).

```json
[
  {
    "id": 42,
    "startedAt": "2026-10-10T18:30:00",
    "finishedAt": "2026-10-10T18:52:00",
    "finished": true,
    "scopeLabel": "Prawo karne i postępowanie karne",
    "kind": "NEW",
    "categoryIds": [1],
    "subjectIds": [],
    "origins": ["NOTARY", "AUTHORED"],
    "requestedCount": 20,
    "questionCount": 20,
    "answeredCount": 20,
    "correctCount": 15,
    "guessedCount": 2,
    "timeSeconds": 311.5
  }
]
```

| Pole | Znaczenie |
|------|-----------|
| `finished`, `finishedAt` | Test jest ukończony, gdy odpowiedziano na ostatnie pytanie; inaczej `finished = false`, `finishedAt = null` („Nieukończony”) |
| `scopeLabel` | Nazwa zakresu zapisana w chwili układania (np. „Kodeks karny i Kodeks cywilny”), żeby lista była czytelna po zmianie nazw |
| `categoryIds`, `subjectIds` | Wybrany zakres; oba puste = cały materiał. Z nich frontend układa test ponownie („Ułóż ponownie z tymi ustawieniami”) |
| `origins` | Filtr pochodzenia pytań obowiązujący, gdy układano test; `null` = wszystkie |
| `requestedCount`, `questionCount` | O ile pytań prosił użytkownik i ile wylosowano |
| `answeredCount`, `correctCount`, `guessedCount` | Odpowiedzi, poprawne i z zaznaczeniem „Nie jestem pewien” |
| `timeSeconds` | Suma czasu odpowiadania (pojedyncza odpowiedź najwyżej 120 s) |

### `GET /api/custom-tests/sessions/{id}` — „Moje testy”: szczegóły

Podsumowanie (`summary`, jak wyżej) i wszystkie pytania w kolejności, w jakiej je pokazano, z odpowiedziami użytkownika.

```json
{
  "summary": { "id": 42, "finished": true, "questionCount": 20 },
  "questions": [
    {
      "position": 1, "cardId": 812, "front": "Treść pytania?",
      "optionA": "…", "optionB": "…", "optionC": "…",
      "correctOption": "B", "selectedOption": "A", "correct": false, "guessed": false, "timeSeconds": 12.5,
      "explanation": "…", "legalBasis": "art. 26 § 1 k.k.", "validAsOf": "2026-10-01",
      "sourceName": "Egzamin 2024", "origin": "NOTARY", "examYear": 2024
    }
  ]
}
```

`selectedOption`, `correct` i `timeSeconds` są `null` przy pytaniu, na które użytkownik nie zdążył odpowiedzieć.
`selectedOption` jest pod oryginalnym kluczem z bazy (odpowiedzi w teście są mieszane na ekranie). Cudzy lub
nieistniejący test: `404` „Nie znaleziono takiego testu.”

## Liczniki

### `GET /api/review/due-count?mode=FLASHCARD|TEST`

```json
{ "dueCount": 37 }
```

Ile kart danego trybu ma już **minięty termin** powtórki (bez nowych kart). `mode` domyślnie `FLASHCARD`. Dla `TEST`
liczone są tylko pytania z wybranych pochodzeń. To liczba na pasku bocznym.

### `GET /api/review/due-tomorrow?mode=FLASHCARD|TEST`

```json
{ "dueCount": 12 }
```

Ile kart danego trybu wróci na powtórkę **do końca jutra**, razem z tymi, które czekają już teraz (bez nowych kart). To
liczba z podsumowania sesji („Jutro wróci 12 powtórek”). `mode` domyślnie `FLASHCARD`.

### `GET /api/review/queue/new-cards-available`

Parametry: zakres i `mode` (`FLASHCARD` domyślnie albo `TEST`).

```json
{ "availableCount": 85 }
```

Ile **nowych** (jeszcze nietkniętych) kart danego trybu czeka w zakresie.

### `GET /api/review/queue/backlog`

Parametry: zakres i `mode` (`FLASHCARD` domyślnie albo `TEST`).

```json
{ "dueNow": 140, "deferred": 40, "dailyLimit": 100, "doneToday": 0 }
```

| Pole | Znaczenie |
|------|-----------|
| `dueNow` | Ile kart ma teraz minięty termin |
| `deferred` | Ile z nich **nie zmieści się dziś** w sesji (limit dzienny) i poczeka na kolejne dni |
| `dailyLimit` | Dzienny limit zaległych powtórek użytkownika |
| `doneToday` | Ile zaległych powtórek zrobiono już dziś |

## Odpowiedzi

### `POST /api/review` — ocena fiszki

```json
{ "cardId": 205, "rating": 3, "timeSpentSeconds": 6.4 }
```

| Pole | Wymagane | Opis |
|------|----------|------|
| `cardId` | tak | Numer fiszki |
| `rating` | tak | 1 = Nie pamiętam, 2 = Trudne, 3 = Znam, 4 = Łatwe |
| `timeSpentSeconds` | nie | Czas odpowiedzi w sekundach (do średniej czasu) |

**Odpowiedź `200`:** `{ "newCard": true }`. `newCard` to `true`, gdy to była **pierwsza ocena** tej fiszki (zaczęła się uczyć
właśnie teraz); frontend liczy z tego nowe fiszki w podsumowaniu sesji. Błędy: `404` (karta nie istnieje albo jest niedostępna), `400` („To pytanie jest
dostępne tylko w Testach” — próba oceny pytania testowego jako fiszki; albo `rating` spoza 1–4), `409` (ta sama karta
zmieniona równolegle w innym oknie).

### `POST /api/review/test-answer` — odpowiedź na pytanie testowe

```json
{ "cardId": 101, "selectedOption": "A", "timeSpentSeconds": 14.2, "guessed": false, "sessionId": 42 }
```

| Pole | Wymagane | Opis |
|------|----------|------|
| `cardId` | tak | Numer pytania |
| `selectedOption` | tak | `"A"`, `"B"` albo `"C"` |
| `timeSpentSeconds` | nie | Czas odpowiedzi |
| `guessed` | nie | `true`, gdy użytkownik zaznaczył „Nie jestem pewien”. Brak pola = `false` |
| `sessionId` | nie | Numer zapisanego własnego testu (z odpowiedzi na `POST /api/review/custom-test`). Odpowiedź trafia wtedy także do „Moich testów”; po odpowiedzi na ostatnie pytanie test jest ukończony. Zły, cudzy numer albo pytanie spoza testu są po cichu pomijane (odpowiedź w nauce i tak się zapisuje) |

**Odpowiedź `200`**

```json
{
  "correct": false,
  "correctOption": "B",
  "explanation": "Stan wyższej konieczności jest kontratypem (art. 26 k.k.).",
  "legalBasis": "art. 26 § 1 k.k.",
  "validAsOf": "2026-01-01",
  "lapses": 3,
  "leech": false,
  "flashcardUnlocked": true,
  "flashcard": "ADDED_BY_MISTAKE",
  "newQuestion": false
}
```

| Pole | Znaczenie |
|------|-----------|
| `correct` | Czy wybrana opcja była poprawna |
| `correctOption` | Poprawna opcja (dopiero tu serwer ją ujawnia) |
| `explanation`, `legalBasis`, `validAsOf` | Wyjaśnienie, podstawa prawna i stan prawny (każde może być `null`) |
| `lapses` | Ile razy użytkownik pomylił to pytanie, łącznie z tą odpowiedzią |
| `leech` | Czy pytanie jest już „trudne” (liczba błędów osiągnęła próg 8) — warto zaproponować zawieszenie |
| `flashcardUnlocked` | `true` tylko wtedy, gdy **ta** odpowiedź właśnie dodała fiszkę pytania do Fiszek (pierwszy pewny błąd w tym pytaniu) |
| `flashcard` | Stan fiszki pytania po tej odpowiedzi: `UNAVAILABLE`, `AVAILABLE`, `ADDED_MANUALLY`, `ADDED_BY_MISTAKE` |
| `newQuestion` | `true`, gdy to była pierwsza odpowiedź na to pytanie (nowe pytanie); do podsumowania sesji |

Jak serwer zamienia odpowiedź na ocenę pamięci (poprawna i pewna = „Znam”, w pozostałych przypadkach „Nie pamiętam”):
[04](../04-nauka-testy-fiszki-fsrs.md#odpowiedź-w-teście-krok-po-kroku).

**Błędy:** `404` (pytanie nie istnieje albo jest niedostępne), `400` („To pytanie nie ma formatu testowego”, albo
`selectedOption` spoza A/B/C), `409` (równoległa zmiana tej samej karty).

**Ochrona przed podwójnym kliknięciem:** druga odpowiedź na tę samą kartę w ciągu 1 sekundy nie jest liczona jako
kolejna powtórka (odpowiedź zwraca aktualny stan).

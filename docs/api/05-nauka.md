# API: nauka (`/api/review`)

[← Przegląd API](README.md) · [Spis treści](../README.md)

Kolejki, odpowiedzi i liczniki. Wszystkie endpointy wymagają tokenu. **Jak działa algorytm, limity dzienne i pula
pytań** wyjaśnia [04. Nauka: testy, fiszki, FSRS](../04-nauka-testy-fiszki-fsrs.md); tutaj są tylko kształty żądań i
odpowiedzi.

Fiszka nie jest osobnym rekordem: to **tryb nauki pytania**. To samo pytanie ma osobny stan nauki (FSRS) w trybie
`TEST` i w trybie `FLASHCARD`, więc `mode` w licznikach i `questionId` w odpowiedziach dotyczą jednego i tego samego
pytania z dwoma niezależnymi harmonogramami.

## Wspólne parametry zakresu

Kolejki i liczniki przyjmują opcjonalny **zakres**, od najwęższego:

| Parametr | Znaczenie |
|----------|-----------|
| `sourceId` | Tylko jedno źródło (arkusz egzaminu albo pytania autorskie) |
| `actId` | Jeden akt prawny albo zagadnienie (gdy brak `sourceId`) |
| `fieldId` | Cała dziedzina (gdy brak obu powyższych) |
| (żaden) | Cała pula użytkownika |

Brany jest pierwszy podany, w kolejności: źródło, akt, dziedzina (zakresów się nie łączy). Zakres zawsze obcina się do
**puli** użytkownika: pytania z aktów jego ścieżki (po zawężeniu) i, w testach, ze źródeł z filtra; pytania ukryte
przez administratora są pomijane.

## Kolejki

### `GET /api/review/flashcard-queue` — kolejka fiszek

Parametry: zakres oraz opcjonalne `extraNewCards`, `extraDueReviews` (zob. niżej).

**Odpowiedź `200`**: lista fiszek w kolejności sesji (najpierw zaległe, potem nowe).

```json
[
  {
    "questionId": 205,
    "front": "Czym jest kontratyp?",
    "back": "Okoliczność wyłączająca bezprawność czynu zabronionego…",
    "explanation": "Kontratypy: obrona konieczna, stan wyższej konieczności…",
    "legalBasis": "art. 25–26 k.k.",
    "validAsOf": "2026-01-01",
    "actName": "Kodeks karny",
    "actShortName": "k.k.",
    "sourceName": "Notarialna 2024",
    "sourceKind": "EXAM_SHEET",
    "examYear": 2024,
    "lapses": 2,
    "leech": false,
    "lawChange": { "changedAt": "2026-10-01", "note": "nowelizacja art. 25 k.k." }
  }
]
```

Fiszka ma tę samą treść co pytanie testowe: **przód** (`front`) to treść pytania, **tył** (`back`) to tekst poprawnej
opcji, a pod nim `explanation` i `legalBasis` (każde z nich bywa `null`). `lapses` i `leech` opisują stan **sprzed tej
sesji** (dla nowej fiszki 0 i `false`); na odwrocie pozwalają zaproponować zawieszenie pytania, z którym użytkownik od
dawna sobie nie radzi. `lawChange` (też w kolejce testów, w odpowiedzi na pytanie, w szczegółach własnego testu i w
wyniku egzaminu) to znacznik zmiany w prawie: `{ "changedAt": data wejścia zmiany w życie, "note": krótki opis albo null }`
albo `null`, gdy pytania zmiana nie dotyczy. Frontend pokazuje z niego plakietkę „Zmiana w prawie: data”.

W kolejce są tylko pytania z **odblokowaną fiszką**: użytkownik pomylił je w teście bez zaznaczenia „Nie jestem pewien”
albo dodał je ręcznie ([`POST /api/questions/{questionId}/flashcard`](06-zawieszanie-fiszki-zgloszenia.md#post-apiquestionsquestionidflashcard--dodaj)).
Pytania z ukrytą fiszką (`flashcardHidden`) i zawieszone w trybie fiszek są pomijane. Fiszki nie mają wyboru
źródła (wynikają z błędów w testach), ale podlegają zawężeniu do aktów.

### `GET /api/review/test-queue` — kolejka pytań testowych

Parametry jak wyżej. Zaległe i nowe pytania są losowo mieszane (zaległe na początku). Pytania pochodzą ze
**wszystkich źródeł** (wybór źródła jest tylko przy teście z kreatora, zob. niżej).

**Odpowiedź `200`**: lista pytań **bez poprawnej odpowiedzi**.

```json
[
  {
    "questionId": 101,
    "body": "Czy czyn zabroniony popełniony w stanie wyższej konieczności jest przestępstwem?",
    "optionA": "Tak, zawsze",
    "optionB": "Nie, jeżeli dobro poświęcone nie przedstawia wartości oczywiście większej od dobra ratowanego",
    "optionC": "Tak, ale sąd odstępuje od wymierzenia kary",
    "actName": "Kodeks karny",
    "actShortName": "k.k.",
    "sourceName": "Notarialna 2024",
    "sourceKind": "EXAM_SHEET",
    "examYear": 2024,
    "lawChange": null
  }
]
```

Każde pytanie ma dokładnie jedną poprawną odpowiedź (A, B albo C). `sourceKind` to `EXAM_SHEET` (arkusz egzaminu z roku
`examYear`) albo `AUTHORED` (pytanie autorskie, `examYear` = `null`).

### Zwiększanie limitu dziennego: `extraNewCards`, `extraDueReviews`

| Parametr | Zachowanie |
|----------|------------|
| (brak obu) | Zaległe do dziennego limitu, potem nowe w ramach dziennego limitu nowych (osobne limity dla fiszek i dla testów) |
| `extraNewCards=N` | Dokładnie N nowych pytań (niezależnie od dziennego limitu), nie więcej niż dostępnych. **`0` = sesja bez nowych** (same zaległe) |
| `extraDueReviews=N` | Dokładnie N zaległych pytań (niezależnie od dziennego limitu); N jest przycinane do 0–200 |

### `GET /api/review/practice-queue?questionIds=1,2,3` — sesja z podanych pytań

Sesja testowa z konkretnych pytań (np. „Przerób błędy teraz” po egzaminie próbnym), **w podanej kolejności**.

| Parametr | Opis |
|----------|------|
| `questionIds` | Lista numerów (`questionIds=1&questionIds=2` albo `questionIds=1,2`), niepusta, bez powtórzeń, najwyżej 200 |

Odpowiedź jak w `test-queue`. Pytania spoza aktów ścieżki użytkownika, ukryte i zawieszone w testach są po cichu
pomijane. Zawężenie puli do aktów tu nie działa: sesja składa się z pytań wybranych wcześniej i zmiana
ustawień w międzyczasie jej nie podmienia. Błędy: `400` („Nie podano pytań do przerobienia.”, „Za dużo pytań naraz (maksymalnie 200).”).

### `GET /api/review/mistakes-queue` i `GET /api/review/mistakes-count` — „Powtórz moje błędy”

`mistakes-queue` zwraca do **50** pytań (losowo), w których **ostatnia** odpowiedź w teście z ostatnich 30 dni była
błędna. Format jak `test-queue`.

`mistakes-count`:

```json
{ "count": 12, "lookbackDays": 30, "sessionSize": 50 }
```

`count` to liczba pytań nadal dostępnych do powtórzenia (w puli użytkownika, niezawieszonych, ze wszystkich źródeł).

### `GET /api/review/custom-test/counts` — test z kreatora: ile pytań w zakresie

Pierwszy krok układania testu: liczby pytań w kreatorze, w „Losuj z całej puli” i na kafelkach szybkich zestawów. Zakres to
**suma** wskazanych dziedzin, aktów prawnych, rozdziałów i przedmiotów; bez żadnego z nich cała pula.

| Parametr | Opis |
|----------|------|
| `fieldIds` | Dziedziny: `fieldIds=1&fieldIds=2` albo `fieldIds=1,2`. Opcjonalne |
| `actIds` | Akty prawne, jak wyżej. Opcjonalne. Pytanie z dziedziny i z jej aktu liczy się raz |
| `courseIds` | Przedmioty Studenta, jak wyżej. Opcjonalne. Przedmiot zastępuje jego akty; przedmiot bez aktów **nie** poszerza zakresu do całej puli (zakres jest wtedy pusty) |
| `sectionIds` | Rozdziały aktów, jak wyżej. Opcjonalne. Rozdział obejmuje tylko pytania przypisane do niego; pytania aktu bez rozdziału są tylko w zakresie „cały akt” (`actIds` albo dziedzina / przedmiot) |
| `source` | Źródło pytań: `ALL` (domyślnie), `MINISTRY` (arkusze egzaminacyjne) albo `AUTHORED` (pytania autorskie) |

```json
{ "all": 1240, "newQuestions": 800, "mistakes": 12, "due": 45, "lawChanges": 7, "timeLimits": 31, "traps": 52 }
```

Liczby dotyczą pytań z puli użytkownika, **niezawieszonych** i z wybranego **źródła**. Rodzaje nakładają się: to samo
pytanie może być zaległe i błędne, więc suma nie musi dawać `all`. `mistakes` to te same pytania co w „Moich błędach”
(ostatnia odpowiedź błędna, ostatnie 30 dni), ale w wybranym zakresie i bez limitu 50 na sesję. `lawChanges` to pytania,
w których zmiana w prawie weszła w życie w ostatnich 365 dniach (`app.questions.recent-change-days`) i już obowiązuje;
pytanie ze zmianą z przyszłości albo starszą niż rok nie jest liczone.

### `POST /api/review/custom-test` — test z kreatora: losowanie pytań

Drugi krok: serwer losuje numery pytań i **zapisuje test** jako sesję („Moje testy”, tabela `custom_test_sessions`):
konfigurację (zakres, źródło, rodzaj, liczbę) i wylosowane pytania w kolejności sesji. Test powstaje już teraz, więc
porzucony w połowie zostaje na liście jako nieukończony. Sesję otwiera się potem przez
`GET /api/review/practice-queue?questionIds=...`, a odpowiedzi idą normalną drogą testu (ocena pamięci, błędy trafiają do
„Moich błędów”) **z numerem testu** w polu `sessionId` (patrz niżej).

```json
{
  "fieldIds": [2, 3],
  "actIds": [41],
  "courseIds": [],
  "sectionIds": [51, 52],
  "source": "MINISTRY",
  "kind": "NEW",
  "questionCount": 50
}
```

| Pole | Opis |
|------|------|
| `fieldIds`, `actIds` | Zakres jak wyżej (najwyżej po 200 numerów); wszystkie listy puste = cała pula |
| `courseIds` | Przedmioty Studenta (najwyżej 50 numerów). Zakres to **suma** dziedzin, aktów, rozdziałów i aktów wskazanych przedmiotów |
| `sectionIds` | Rozdziały aktów (najwyżej 500 numerów) |
| `source` | `ALL` (domyślnie, także gdy brak pola), `MINISTRY` albo `AUTHORED` |
| `kind` | `ALL` (domyślnie), `NEW` (jeszcze nierozwiązywane), `MISTAKES` (ostatnia odpowiedź błędna), `DUE` (po terminie powtórki), `LAW_CHANGE` (zmiana w prawie z ostatniego roku), `TIME_LIMIT` (pytania o terminy) albo `TRAPS` (pułapki). Dwa ostatnie to pytania z **zatwierdzonym** oznaczeniem administratora (zob. [oznaczenia pytań](04-pytania-admin.md#oznaczenia-pytań-apiadminquestion-marks)) |
| `questionCount` | Ile pytań (wymagane): **10, 20, 50, 100 albo 150**. Własnej liczby nie ma. Wyjątek: gdy pytań w zakresie jest **mniej niż 10**, jedyny wybór to wszystkie z nich (liczba równa liczbie dostępnych) |

```json
{ "questionIds": [812, 77, 1203], "available": 3, "requested": 10, "sessionId": 42 }
```

`questionIds` jest w kolejności sesji (zawsze wymieszanej). Gdy jest mniej pytań, niż użytkownik chciał, dostaje wszystkie
(`available` < `requested`); frontend dla „Wszystkie (N)” wysyła najbliższą liczbę z listy, nie mniejszą niż N.
Przy ograniczonej liczbie `MISTAKES` bierze najświeższe błędy, a `DUE` te, które użytkownik najpewniej już zapomniał;
`ALL` i `NEW` są losowe. Test z kreatora **nie jest ograniczony dziennym limitem nowych pytań**. Błędy: `400` (brak pytań
spełniających warunki, z komunikatem dopasowanym do rodzaju, np. „W wybranym zakresie nie ma pytań z wybranego źródła.
Wybierz inne źródło.”; liczba spoza listy: „Wybierz liczbę pytań: 10, 20, 50, 100 albo 150.”).

### `GET /api/custom-tests/sessions` — „Moje testy”: lista

Zapisane testy użytkownika (z kreatora, z „Losuj z całej puli” i z szybkich zestawów), od najnowszego, **stronami**: parametry
`page` (od 0) i `size` (domyślnie 20, najwyżej 100; złe wartości są przycinane). Odpowiedź to `PageResponse`: `items`, `page`,
`size`, `totalElements`, `totalPages`. Sesja z paska zaległych (`/app/test/due`), „Popraw błędy” i egzamin próbny tu nie
trafiają (egzaminy mają własną listę: `GET /api/exams/history`). Elementy `items`:

```json
[
  {
    "id": 42,
    "startedAt": "2026-10-10T18:30:00",
    "finishedAt": "2026-10-10T18:52:00",
    "finished": true,
    "scopeLabel": "Prawo karne i proces karny",
    "kind": "NEW",
    "fieldIds": [1],
    "actIds": [],
    "courseIds": [],
    "sectionIds": [],
    "source": "ALL",
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
| `scopeLabel` | Nazwa zakresu zapisana w chwili układania (np. „Kodeks karny i Kodeks cywilny”, „Cała pula”), żeby lista była czytelna po zmianie nazw |
| `fieldIds`, `actIds`, `courseIds`, `sectionIds` | Wybrany zakres; wszystkie puste = cała pula. `actIds` zawiera też akty wybranych przedmiotów. Z nich frontend układa test ponownie („Ułóż ponownie z tymi ustawieniami”) |
| `source` | Źródło pytań wybrane przy układaniu testu (`ALL`, `MINISTRY`, `AUTHORED`). Testy zapisane przed Etapem 34 mają je wyprowadzone z dawnego filtra (same arkusze to `MINISTRY`, same pytania autorskie to `AUTHORED`, reszta `ALL`) |
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
      "position": 1, "questionId": 812, "body": "Treść pytania?",
      "optionA": "…", "optionB": "…", "optionC": "…",
      "correctOption": "B", "selectedOption": "A", "correct": false, "guessed": false, "timeSeconds": 12.5,
      "explanation": "…", "legalBasis": "art. 26 § 1 k.k.", "validAsOf": "2026-10-01",
      "actName": "Kodeks karny", "sourceName": "Notarialna 2024", "sourceKind": "EXAM_SHEET", "examYear": 2024,
      "lawChange": null
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

Ile pytań danego trybu ma już **minięty termin** powtórki (bez nowych). `mode` domyślnie `FLASHCARD`. To liczba na pasku
bocznym i na pasku zaległych w Testach.

### `GET /api/review/due-tomorrow?mode=FLASHCARD|TEST`

```json
{ "dueCount": 12 }
```

Ile pytań danego trybu wróci na powtórkę **do końca jutra**, razem z tymi, które czekają już teraz (bez nowych). To
liczba z podsumowania sesji („Jutro wróci 12 powtórek”). `mode` domyślnie `FLASHCARD`.

### `GET /api/review/queue/new-available`

Parametry: zakres i `mode` (`FLASHCARD` domyślnie albo `TEST`).

```json
{ "availableCount": 85 }
```

Ile **nowych** (jeszcze nietkniętych w danym trybie) pytań czeka w zakresie.

### `GET /api/review/queue/backlog`

Parametry: zakres i `mode` (`FLASHCARD` domyślnie albo `TEST`).

```json
{ "dueNow": 140, "deferred": 40, "dailyLimit": 100, "doneToday": 0 }
```

| Pole | Znaczenie |
|------|-----------|
| `dueNow` | Ile pytań ma teraz minięty termin |
| `deferred` | Ile z nich **nie zmieści się dziś** w sesji (limit dzienny) i poczeka na kolejne dni |
| `dailyLimit` | Dzienny limit zaległych powtórek użytkownika |
| `doneToday` | Ile zaległych powtórek zrobiono już dziś |

## Odpowiedzi

### `POST /api/review` — ocena fiszki

```json
{ "questionId": 205, "rating": 3, "timeSpentSeconds": 6.4 }
```

| Pole | Wymagane | Opis |
|------|----------|------|
| `questionId` | tak | Numer pytania (jego fiszki) |
| `rating` | tak | 1 = Nie pamiętam, 2 = Trudne, 3 = Znam, 4 = Łatwe |
| `timeSpentSeconds` | nie | Czas odpowiedzi w sekundach (do średniej czasu) |

**Odpowiedź `200`:** `{ "firstReview": true }`. `firstReview` to `true`, gdy to była **pierwsza ocena** fiszki tego pytania
(zaczęła się uczyć właśnie teraz); frontend liczy z tego nowe fiszki w podsumowaniu sesji. Ocena zmienia stan nauki
**tylko w trybie fiszek**: stan tego samego pytania w testach jest osobny.

**Błędy:** `404` (pytanie nie istnieje), `400` („Z tego pytania nie ma fiszki” — administrator ukrył jego fiszkę; albo
`rating` spoza 1–4), `409` (ten sam stan zmieniony równolegle w innym oknie). Ocena nie wymaga odblokowania: kto ma
fiszkę otwartą w sesji, kończy ją normalnie.

### `POST /api/review/test-answer` — odpowiedź na pytanie testowe

```json
{ "questionId": 101, "selectedOption": "A", "timeSpentSeconds": 14.2, "guessed": false, "sessionId": 42 }
```

| Pole | Wymagane | Opis |
|------|----------|------|
| `questionId` | tak | Numer pytania |
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
  "newQuestion": false,
  "lawChange": { "changedAt": "2026-10-01", "note": "nowelizacja art. 26 k.k." }
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
| `flashcard` | Stan fiszki pytania po tej odpowiedzi: `UNAVAILABLE` (administrator ukrył fiszkę), `AVAILABLE` (można ją dodać przyciskiem „Dodaj do fiszek”), `ADDED_MANUALLY`, `ADDED_BY_MISTAKE` |
| `newQuestion` | `true`, gdy to była pierwsza odpowiedź na to pytanie w teście (nowe pytanie); do podsumowania sesji |
| `lawChange` | Zmiana w prawie przy pytaniu (`changedAt`, `note`) albo `null` |

Jak serwer zamienia odpowiedź na ocenę pamięci (poprawna i pewna = „Znam”, w pozostałych przypadkach „Nie pamiętam”):
[04](../04-nauka-testy-fiszki-fsrs.md#odpowiedź-w-teście-krok-po-kroku).

**Błędy:** `404` (pytanie nie istnieje), `400` (`selectedOption` spoza A/B/C), `409` (równoległa zmiana stanu tego pytania).
Ukrycie pytania przez administratora nie blokuje odpowiedzi: kto ma je już otwarte w sesji, kończy ją normalnie (nowe
sesje go nie zawierają).

**Ochrona przed podwójnym kliknięciem:** druga odpowiedź na to samo pytanie w tym samym trybie w ciągu 1 sekundy nie jest
liczona jako kolejna powtórka (odpowiedź zwraca aktualny stan).

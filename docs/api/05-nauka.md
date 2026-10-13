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

## Liczniki

### `GET /api/review/due-count?mode=FLASHCARD|TEST`

```json
{ "dueCount": 37 }
```

Ile kart danego trybu ma już **minięty termin** powtórki (bez nowych kart). `mode` domyślnie `FLASHCARD`. Dla `TEST`
liczone są tylko pytania z wybranych pochodzeń. To liczba na pasku bocznym.

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

**Odpowiedź:** `200`, bez treści. Błędy: `404` (karta nie istnieje albo jest niedostępna), `400` („To pytanie jest
dostępne tylko w Testach” — próba oceny pytania testowego jako fiszki; albo `rating` spoza 1–4), `409` (ta sama karta
zmieniona równolegle w innym oknie).

### `POST /api/review/test-answer` — odpowiedź na pytanie testowe

```json
{ "cardId": 101, "selectedOption": "A", "timeSpentSeconds": 14.2, "guessed": false }
```

| Pole | Wymagane | Opis |
|------|----------|------|
| `cardId` | tak | Numer pytania |
| `selectedOption` | tak | `"A"`, `"B"` albo `"C"` |
| `timeSpentSeconds` | nie | Czas odpowiedzi |
| `guessed` | nie | `true`, gdy użytkownik zaznaczył „Nie jestem pewien”. Brak pola = `false` |

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
  "flashcard": "ADDED_BY_MISTAKE"
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

Jak serwer zamienia odpowiedź na ocenę pamięci (poprawna i pewna = „Znam”, w pozostałych przypadkach „Nie pamiętam”):
[04](../04-nauka-testy-fiszki-fsrs.md#odpowiedź-w-teście-krok-po-kroku).

**Błędy:** `404` (pytanie nie istnieje albo jest niedostępne), `400` („To pytanie nie ma formatu testowego”, albo
`selectedOption` spoza A/B/C), `409` (równoległa zmiana tej samej karty).

**Ochrona przed podwójnym kliknięciem:** druga odpowiedź na tę samą kartę w ciągu 1 sekundy nie jest liczona jako
kolejna powtórka (odpowiedź zwraca aktualny stan).

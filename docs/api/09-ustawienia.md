# API: ustawienia (`/api/me/settings`, `/api/me/origin-filter`)

[← Przegląd API](README.md) · [Spis treści](../README.md)

Dwa zestawy ustawień zapisanych **na koncie** (działają tak samo na każdym urządzeniu). Wymagają tokenu.

## Ustawienia nauki

Wpływają na limity dzienne, terminy powtórek i tryb egzaminacyjny. Co dokładnie robi każde ustawienie:
[04](../04-nauka-testy-fiszki-fsrs.md#kolejka-na-dziś-i-limity-dzienne).

### `GET /api/me/settings`

Zwraca **obowiązujące** wartości (nigdy `null`: tam, gdzie użytkownik niczego nie ustawił, wstawiana jest wartość
domyślna z konfiguracji serwera), wartości domyślne do podpowiedzi i, gdy ustawiono datę egzaminu, plan nowych pytań.

```json
{
  "newCardsPerDay": 20,
  "newTestQuestionsPerDay": 40,
  "maxReviewsPerDay": 100,
  "desiredRetention": 0.9,
  "examDate": "2027-03-15",
  "shuffleOptions": true,
  "daysToExam": 154,
  "examPassed": false,
  "effectiveRetention": 0.9,
  "retentionBoosted": false,
  "defaults": {
    "newCardsPerDay": 20, "newTestQuestionsPerDay": 40, "maxReviewsPerDay": 100, "desiredRetention": 0.9
  },
  "plan": {
    "newTestQuestionsLeft": 300,
    "newFlashcardsLeft": 40,
    "planningDays": 147,
    "suggestedTestQuestionsPerDay": 3,
    "suggestedFlashcardsPerDay": 1
  }
}
```

| Pole | Znaczenie |
|------|-----------|
| `newCardsPerDay` | Dzienny limit nowych fiszek |
| `newTestQuestionsPerDay` | Dzienny limit nowych pytań testowych |
| `maxReviewsPerDay` | Dzienny limit zaległych powtórek (osobno dla fiszek i testów) |
| `desiredRetention` | Docelowa szansa przypomnienia wybrana przez użytkownika |
| `examDate` | Zapisana data egzaminu (także gdy już minęła) |
| `shuffleOptions` | Czy mieszać kolejność odpowiedzi A/B/C w pytaniach (korzysta z tego frontend) |
| `daysToExam` | Ile dni do egzaminu; `null`, gdy daty nie ma albo egzamin już się odbył |
| `examPassed` | Zapisana data egzaminu jest w przeszłości (ustawienia egzaminacyjne wtedy nie działają) |
| `effectiveRetention` | Retencja faktycznie użyta do liczenia terminów (w 30 dniach przed egzaminem co najmniej 0,95) |
| `retentionBoosted` | Czy retencja jest podniesiona przez tryb egzaminacyjny |
| `defaults` | Wartości domyślne serwera (do podpowiedzi „przywróć domyślne”) |
| `plan` | `null`, gdy nie ma daty egzaminu albo już minęła. Inaczej: ile nowych pytań i fiszek zostało (`newTestQuestionsLeft`, `newFlashcardsLeft`), ile dni jest na planowanie nowych (`planningDays`: dni do egzaminu − 7, min. 1) i ile dziennie trzeba, żeby zdążyć (`suggested...PerDay`, zaokrąglone w górę) |

W planie nowe pytania testowe liczą się tylko z pochodzeń wybranych w filtrze.

### `PUT /api/me/settings`

Zapisuje **pełny zestaw** (zastępuje poprzedni). `null` w polu liczbowym oznacza „wartość domyślna”, `null` w
`examDate` usuwa datę egzaminu, a `null` w `shuffleOptions` to „tak”.

```json
{
  "newCardsPerDay": null,
  "newTestQuestionsPerDay": 30,
  "maxReviewsPerDay": 150,
  "desiredRetention": 0.92,
  "examDate": "2027-03-15",
  "shuffleOptions": true
}
```

| Pole | Dozwolone wartości |
|------|--------------------|
| `newCardsPerDay` | 0–200 |
| `newTestQuestionsPerDay` | 0–500 |
| `maxReviewsPerDay` | 10–1000 |
| `desiredRetention` | od 0,70 do 0,97 (70–97%) |
| `examDate` | od dziś do 3 lat w przód |
| `shuffleOptions` | prawda / fałsz |

**Odpowiedź `200`**: ten sam kształt co `GET`, już po zapisie. **Błędy `400`:** „Docelowa retencja musi mieścić się
między 70% a 97%.”, „Data egzaminu nie może być w przeszłości.”, „Data egzaminu jest zbyt odległa (najwyżej 3 lata od
dziś).”, a także komunikaty zakresów z walidacji pól („nie może być ujemna”, „najwyżej 200” itd.).

Zapis ustawień nauki **nie zmienia** filtra pochodzenia (to osobny zestaw, patrz niżej).

## Filtr pochodzenia pytań

Zawęża **Testy** do wybranych pochodzeń. Zawsze wybrane jest co najmniej jedno; domyślnie wszystkie. Filtr dotyczy
wyłącznie pytań testowych (katalog Testów, kolejki, „Powtórz moje błędy”, odznaka zaległych, plan do egzaminu, losowanie
egzaminu). Fiszki i statystyki go nie uwzględniają.

### `GET /api/me/origin-filter`

```json
{
  "selected": ["NOTARY", "BAILIFF"],
  "all": false,
  "options": [
    { "origin": "AUTHORED", "questionCount": 120 },
    { "origin": "ADVOCATE_LEGAL_ADVISER", "questionCount": 800 },
    { "origin": "JUDGE_PROSECUTOR", "questionCount": 650 },
    { "origin": "NOTARY", "questionCount": 500 },
    { "origin": "SUPPLEMENTARY_JUDGE", "questionCount": 90 },
    { "origin": "BAILIFF", "questionCount": 410 }
  ]
}
```

| Pole | Znaczenie |
|------|-----------|
| `selected` | Wybrane pochodzenia (zawsze co najmniej jedno), w kolejności wyliczenia |
| `all` | Czy wybrane są wszystkie (stan domyślny) |
| `options` | Wszystkie pochodzenia z liczbą pytań testowych (bez ukrytych), do okna wyboru |

### `PUT /api/me/origin-filter`

```json
{ "origins": ["NOTARY", "BAILIFF"] }
```

Powtórzenia są ignorowane. Odpowiedź `200`: stan filtra po zapisie (kształt jak `GET`). **Błędy `400`:** „Zaznacz co
najmniej jedno pochodzenie pytań.” (pusta lista), „Nieznane pochodzenie pytań.” (`null` na liście), „Nieprawidłowy
format żądania” (nieznany kod).

Zapis: gdy wybrane są wszystkie pochodzenia, w bazie zapisywane jest `NULL` (dzięki temu pochodzenia dodane w
przyszłości są od razu widoczne); w przeciwnym razie lista kodów oddzielona przecinkami.

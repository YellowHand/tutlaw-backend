# API: ustawienia (`/api/me/settings`, `/api/me/exam`, `/api/me/theme`, `/api/me/interface`, `/api/me/study-time`, `/api/me/origin-filter`, `/api/exam-terms`)

[← Przegląd API](README.md) · [Spis treści](../README.md)

Ustawienia zapisane **na koncie** (działają tak samo na każdym urządzeniu) i terminy egzaminów aplikacji. Wymagają tokenu.

## Ustawienia nauki

Wpływają na limity dzienne, terminy powtórek i tryb egzaminacyjny. Co dokładnie robi każde ustawienie:
[04](../04-nauka-testy-fiszki-fsrs.md#kolejka-na-dziś-i-limity-dzienne).

### `GET /api/me/settings`

Zwraca **obowiązujące** wartości (nigdy `null`: tam, gdzie użytkownik niczego nie ustawił, wstawiana jest wartość
domyślna z konfiguracji serwera), wybraną aplikację egzaminacyjną z jej terminem, wartości domyślne do podpowiedzi i, gdy termin jest znany, plan nowych pytań.

```json
{
  "newCardsPerDay": 20,
  "newTestQuestionsPerDay": 40,
  "newTestQuestionsAuto": true,
  "maxReviewsPerDay": 100,
  "desiredRetention": 0.9,
  "examOrigin": "NOTARY",
  "examDate": "2027-03-15",
  "shuffleOptions": true,
  "theme": "DARK",
  "startChoiceMade": true,
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
    "planningDays": 140,
    "requiredTestQuestionsPerDay": 3,
    "maxTestQuestionsPerDay": 100,
    "suggestedTestQuestionsPerDay": 3,
    "suggestedFlashcardsPerDay": 1,
    "newTestQuestionsToday": 2
  }
}
```

| Pole | Znaczenie |
|------|-----------|
| `newCardsPerDay` | Dzienny limit nowych fiszek |
| `newTestQuestionsPerDay` | Dzienny limit nowych pytań testowych, **który naprawdę obowiązuje**. Własny limit użytkownika, a bez niego (`newTestQuestionsAuto = true`) automatyczny: nie mniej niż domyślny (`defaults.newTestQuestionsPerDay`), a gdy plan do egzaminu wymaga więcej, tyle, ile trzeba, ale nie więcej niż `plan.maxTestQuestionsPerDay` (100). Bez terminu egzaminu i w ostatnich 14 dniach automatyczny limit to po prostu domyślny |
| `newTestQuestionsAuto` | `true`, gdy użytkownik nie ustawił własnego limitu nowych pytań i obowiązuje limit automatyczny |
| `theme` | Wygląd aplikacji: `DARK` (domyślnie) albo `LIGHT`. Zmienia się przez `PUT /api/me/theme` |
| `maxReviewsPerDay` | Dzienny limit zaległych powtórek (osobno dla fiszek i testów) |
| `desiredRetention` | Docelowa szansa przypomnienia wybrana przez użytkownika |
| `examOrigin` | Aplikacja wybrana przez użytkownika (`PUT /api/me/exam`); `null`, gdy jeszcze nie wybrał albo jest studentem |
| `startChoiceMade` | `true`, gdy użytkownik zrobił wybór w oknie startowym (aplikacja albo „Jestem studentem / jeszcze nie wybrałem”). Przy `false` aplikacja pokazuje okno startowe. Samo `examOrigin = null` nie wystarcza, bo student też go nie ma |
| `examDate` | Termin egzaminu wybranej aplikacji (ustawia go administrator, tylko do odczytu); `null`, gdy aplikacji nie wybrano albo termin jest nieogłoszony. Także gdy już minął |
| `shuffleOptions` | Czy mieszać kolejność odpowiedzi A/B/C w pytaniach (korzysta z tego frontend) |
| `daysToExam` | Ile dni do egzaminu; `null`, gdy terminu nie ma albo egzamin już się odbył |
| `examPassed` | Termin egzaminu wybranej aplikacji jest w przeszłości (ustawienia egzaminacyjne wtedy nie działają) |
| `effectiveRetention` | Retencja faktycznie użyta do liczenia terminów (w 30 dniach przed egzaminem co najmniej 0,95) |
| `retentionBoosted` | Czy retencja jest podniesiona przez tryb egzaminacyjny |
| `defaults` | Wartości domyślne serwera (do podpowiedzi „przywróć domyślne”) |
| `plan` | `null`, gdy nie ma terminu egzaminu albo już minął. Inaczej: ile nowych pytań i fiszek zostało (`newTestQuestionsLeft`, `newFlashcardsLeft`), ile dni jest na planowanie nowych (`planningDays`: dni do egzaminu − 14 dni rezerwy na same powtórki, najmniej 0), ile nowych pytań dziennie **trzeba**, żeby zdążyć (`requiredTestQuestionsPerDay`, bez żadnego sufitu) i sufit limitu automatycznego (`maxTestQuestionsPerDay`, 100), cel na dziś (`suggestedTestQuestionsPerDay` = trzeba, ale nie więcej niż sufit; `suggestedFlashcardsPerDay`; zaokrąglone w górę, **0 w ostatnich 14 dniach**) i ile nowych pytań użytkownik zaczął dzisiaj (`newTestQuestionsToday`) |

Gdy `requiredTestQuestionsPerDay` jest większe niż `maxTestQuestionsPerDay`, wszystkich nowych pytań nie da się zacząć przed rezerwą na powtórki; frontend mówi to wprost, zamiast prosić o podniesienie limitu.

Cel na dziś liczy się od stanu z **początku dnia** (pozostałe + zaczęte dzisiaj), więc nie maleje w miarę nauki w ciągu dnia. W planie nowe pytania testowe liczą się tylko z pochodzeń wybranych w filtrze.

### `PUT /api/me/settings`

Zapisuje **pełny zestaw** (zastępuje poprzedni). `null` w polu liczbowym oznacza „wartość domyślna”, a `null` w
`shuffleOptions` to „tak”. Wyjątek: `null` w `newTestQuestionsPerDay` to **limit automatyczny** (idzie za planem do
egzaminu), a nie stała wartość. Daty egzaminu tu nie ma: wybiera się ją przez aplikację (`PUT /api/me/exam`), a termin ustawia
administrator. Pole `examDate` wysłane przez starszego klienta jest ignorowane.

```json
{
  "newCardsPerDay": null,
  "newTestQuestionsPerDay": 30,
  "maxReviewsPerDay": 150,
  "desiredRetention": 0.92,
  "shuffleOptions": true
}
```

| Pole | Dozwolone wartości |
|------|--------------------|
| `newCardsPerDay` | 0–200 |
| `newTestQuestionsPerDay` | 0–500 albo `null` (automatyczny) |
| `maxReviewsPerDay` | 10–1000 |
| `desiredRetention` | od 0,70 do 0,97 (70–97%) |
| `shuffleOptions` | prawda / fałsz |

**Odpowiedź `200`**: ten sam kształt co `GET`, już po zapisie. **Błędy `400`:** „Docelowa retencja musi mieścić się
między 70% a 97%.”, a także komunikaty zakresów z walidacji pól („nie może być ujemna”, „najwyżej 200” itd.).

Zapis ustawień nauki **nie zmienia** ani wybranej aplikacji, ani wyglądu, ani filtra pochodzenia (to osobne zestawy, patrz niżej).

### `PUT /api/me/exam`

Wybór aplikacji, do której użytkownik się przygotowuje (z niej bierze się termin egzaminu). Jednocześnie **jednorazowo
ustawia filtr pochodzenia** na wybraną aplikację i pytania autorskie; późniejsze zmiany filtra są niezależne.
Ponowny wybór (inna aplikacja) znów ustawia filtr, nadpisując wcześniejszy.

```json
{ "origin": "NOTARY" }
```

albo, dla studenta („Jestem studentem / jeszcze nie wybrałem”):

```json
{ "student": true }
```

Student nie ma aplikacji: `examOrigin` zostaje `null`, nie ma terminu egzaminu ani planu dnia, a filtr pochodzenia
obejmuje wszystko. Przy `student = true` pole `origin` jest pomijane. Obie odmiany zapisują `startChoiceMade = true`.
Student może później wybrać aplikację tym samym endpointem, a aplikację zmienić z powrotem na studenta.

**Odpowiedź `200`**: ten sam kształt co `GET /api/me/settings`, już po zapisie. **Błędy `400`:** „Wybierz aplikację, do
której się przygotowujesz (pytania autorskie nie mają egzaminu).” (`AUTHORED` albo brak `origin` bez `student`), „Nieprawidłowy
format żądania” (nieznany kod).

### `PUT /api/me/theme`

Wybór wyglądu aplikacji: ciemny albo jasny. Wygląd jest zapisany na koncie, więc po zalogowaniu na innym urządzeniu jest
taki sam. Nowe konto ma wygląd ciemny. Frontend dodatkowo pamięta ostatni wygląd lokalnie, tylko po to, żeby strona nie
mignęła w złym kolorze przed odpowiedzią serwera; wygląd z konta ma pierwszeństwo.

```json
{ "theme": "LIGHT" }
```

**Odpowiedź `200`**: ten sam kształt co `GET /api/me/settings`, już po zapisie. **Błędy `400`:** „Nieprawidłowy format
żądania” (brak `theme` albo nieznana wartość; dozwolone `DARK`, `LIGHT`).

### `GET /api/me/interface`, `PUT /api/me/interface`

Ustawienia interfejsu zapisane **na koncie**, żeby były takie same na każdym urządzeniu: stałe ukrycie podpowiedzi o
skrótach klawiszowych (osobno w testach i w fiszkach) oraz ukrycie licznika czasu przy pytaniu. Nie ma ich w ustawieniach
Konta; przełącza się je małym „×” przy podpowiedzi i kliknięciem w licznik.

```json
{
  "testHintHidden": false,
  "flashcardHintHidden": true,
  "questionTimerHidden": false,
  "testAnswers": 10,
  "flashcardReviews": 4
}
```

| Pole | Znaczenie |
|------|-----------|
| `testHintHidden`, `flashcardHintHidden` | Podpowiedź skrótów jest stale ukryta w danym trybie |
| `questionTimerHidden` | Licznik czasu przy pytaniu jest ukryty (domyślnie widoczny) |
| `testAnswers`, `flashcardReviews` | Ile odpowiedzi w testach i ocen fiszek użytkownik już dał, liczone **najwyżej do 10** (z dziennika odpowiedzi). Frontend chowa podpowiedź po 10 odpowiedziach w danym trybie, także na nowym urządzeniu; przy `testAnswers = 0` pokazuje jednorazową podpowiedź o „Nie jestem pewien” |

`PUT` przyjmuje `testHintHidden`, `flashcardHintHidden` i `questionTimerHidden`; pole pominięte (albo `null`) zostaje bez
zmian. Odpowiedź ma ten sam kształt co `GET`.

### `POST /api/me/study-time`

Doliczanie **czasu nauki** do dzisiejszego dnia (czas serwera). Przeglądarka liczy go sama, tylko na ekranach nauki
(testy, powtórki, błędy, własny test, fiszki, egzamin), tylko gdy karta jest widoczna, i wstrzymuje liczenie po 3 minutach
bez ruchu; porcje wysyła co około 30 sekund (przy zamykaniu karty przez `fetch` z `keepalive`).

```json
{ "seconds": 30 }
```

| Pole | Reguła |
|------|--------|
| `seconds` | Liczba całkowita **1–300**: tyle sekund dolicza się do dzisiejszego dnia |

**Odpowiedź:** `204 No Content`. **Błędy:** `400` („liczba sekund musi wynosić od 1 do 300”). Suma z jednego dnia nie
przekracza 24 godzin. To inna miara niż czas odpowiedzi na pytanie (`timeSpentSeconds` przy odpowiedzi); obie widać w
[Podsumowaniu nauki](08-statystyki.md#get-apistatisticslearning-summary).

## Terminy egzaminów

Termin najbliższego egzaminu każdej aplikacji (bez pytań autorskich) ustawia administrator. `null` oznacza „termin
nieogłoszony”. Użytkownik ich nie zmienia; wybiera tylko aplikację.

### `GET /api/exam-terms`

Dostępny dla każdego zalogowanego. Zawsze wszystkie pięć aplikacji, w stałej kolejności, także te bez terminu.

```json
[
  { "origin": "ADVOCATE_LEGAL_ADVISER", "examDate": null, "daysToExam": null },
  { "origin": "JUDGE_PROSECUTOR", "examDate": "2027-03-20", "daysToExam": 159 },
  { "origin": "NOTARY", "examDate": "2027-03-15", "daysToExam": 154 },
  { "origin": "SUPPLEMENTARY_JUDGE", "examDate": null, "daysToExam": null },
  { "origin": "BAILIFF", "examDate": null, "daysToExam": null }
]
```

`daysToExam` jest `null`, gdy terminu nie ma albo już minął.

### `PUT /api/exam-terms/{origin}`

Tylko `ADMIN` (inni dostają `403`). Ustawia albo usuwa termin jednej aplikacji. Zmiana działa od razu u wszystkich
użytkowników, którzy wybrali tę aplikację; już wyznaczone terminy powtórek **nie są** przeliczane wstecz (nowe odpowiedzi
liczą się z nową datą).

```json
{ "examDate": "2027-03-15" }
```

`null` w `examDate` usuwa termin. Odpowiedź `200`: wiersz w kształcie jak w `GET`. Dozwolona data: od dziś do 3 lat w
przód. **Błędy `400`:** „Termin można ustawić tylko dla aplikacji, nie dla pytań autorskich.” (`AUTHORED`), „Termin
egzaminu nie może być w przeszłości.”, „Termin egzaminu jest zbyt odległy (najwyżej 3 lata od dziś).”, „Nieprawidłowy
format żądania” (nieznany kod aplikacji albo zły format daty).

## Filtr pochodzenia pytań

Zawęża **Testy** do wybranych pochodzeń. Zawsze wybrane jest co najmniej jedno; domyślnie wszystkie. Filtr dotyczy
wyłącznie pytań testowych (katalog Testów, kolejki, „Powtórz moje błędy”, odznaka zaległych, plan do egzaminu, losowanie
egzaminu). Wybór aplikacji (`PUT /api/me/exam`) ustawia go jednorazowo na aplikację i pytania autorskie. Fiszki i statystyki go nie uwzględniają.

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

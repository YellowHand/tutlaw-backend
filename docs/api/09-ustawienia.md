# API: ustawienia (`/api/me/settings`, `/api/me/track`, `/api/me/theme`, `/api/me/interface`, `/api/me/study-time`, `/api/exam-terms`)

[← Przegląd API](README.md) · [Spis treści](../README.md)

Ustawienia zapisane **na koncie** (działają tak samo na każdym urządzeniu) i terminy egzaminów aplikacji. Wymagają tokenu.
Pula pytań (zawężenie do aktów i filtr źródeł) ma osobne endpointy: [03. Ścieżki, pula, katalog](03-tresci.md#pula-pytań-użytkownika).

## Ustawienia nauki

Wpływają na limity dzienne, terminy powtórek i tryb egzaminacyjny. Co dokładnie robi każde ustawienie:
[04](../04-nauka-testy-fiszki-fsrs.md#kolejka-na-dziś-i-limity-dzienne).

### `GET /api/me/settings`

Zwraca **obowiązujące** wartości (nigdy `null`: tam, gdzie użytkownik niczego nie ustawił, wstawiana jest wartość
domyślna z konfiguracji serwera), wybraną ścieżkę z terminem egzaminu, wartości domyślne do podpowiedzi i, gdy termin jest znany, plan nowych pytań.

```json
{
  "newFlashcardsPerDay": 20,
  "newTestQuestionsPerDay": 40,
  "newTestQuestionsAuto": true,
  "maxReviewsPerDay": 100,
  "desiredRetention": 0.9,
  "track": { "id": 4, "slug": "notarialna", "name": "Aplikacja notarialna", "shortName": "Notarialna", "kind": "EXAM" },
  "examDate": "2027-03-15",
  "shuffleOptions": true,
  "theme": "DARK",
  "daysToExam": 154,
  "examPassed": false,
  "effectiveRetention": 0.9,
  "retentionBoosted": false,
  "defaults": {
    "newFlashcardsPerDay": 20, "newTestQuestionsPerDay": 40, "maxReviewsPerDay": 100, "desiredRetention": 0.9
  },
  "plan": {
    "newTestQuestionsLeft": 300,
    "planningDays": 140,
    "requiredTestQuestionsPerDay": 3,
    "maxTestQuestionsPerDay": 100,
    "suggestedTestQuestionsPerDay": 3,
    "newTestQuestionsToday": 2
  }
}
```

| Pole | Znaczenie |
|------|-----------|
| `newFlashcardsPerDay` | Dzienny limit **nowych fiszek** (nowych kart w trybie fiszek; dostają je pytania odblokowane pomyłką albo dodane ręcznie) |
| `newTestQuestionsPerDay` | Dzienny limit nowych pytań testowych, **który naprawdę obowiązuje**. Własny limit użytkownika, a bez niego (`newTestQuestionsAuto = true`) automatyczny: nie mniej niż domyślny (`defaults.newTestQuestionsPerDay`), a gdy plan do egzaminu wymaga więcej, tyle, ile trzeba, ale nie więcej niż `plan.maxTestQuestionsPerDay` (100). Bez terminu egzaminu i w ostatnich 14 dniach automatyczny limit to po prostu domyślny |
| `newTestQuestionsAuto` | `true`, gdy użytkownik nie ustawił własnego limitu nowych pytań i obowiązuje limit automatyczny |
| `theme` | Wygląd aplikacji: `DARK` (domyślnie) albo `LIGHT`. Zmienia się przez `PUT /api/me/theme` |
| `maxReviewsPerDay` | Dzienny limit zaległych powtórek (osobno dla fiszek i testów) |
| `desiredRetention` | Docelowa szansa przypomnienia wybrana przez użytkownika |
| `track` | Ścieżka wybrana przez użytkownika (`PUT /api/me/track`): jedna z pięciu aplikacji albo Student. `null`, gdy jeszcze nie wybrał: wtedy aplikacja pokazuje okno startowe, a pula obejmuje wszystko |
| `examDate` | Termin egzaminu wybranej aplikacji (ustawia go administrator, tylko do odczytu); `null`, gdy ścieżki nie wybrano, to Student albo termin jest nieogłoszony. Także gdy już minął |
| `shuffleOptions` | Czy mieszać kolejność odpowiedzi A/B/C w pytaniach (korzysta z tego frontend) |
| `daysToExam` | Ile dni do egzaminu; `null`, gdy terminu nie ma albo egzamin już się odbył |
| `examPassed` | Termin egzaminu wybranej aplikacji jest w przeszłości (ustawienia egzaminacyjne wtedy nie działają) |
| `effectiveRetention` | Retencja faktycznie użyta do liczenia terminów (w 30 dniach przed egzaminem co najmniej 0,95) |
| `retentionBoosted` | Czy retencja jest podniesiona przez tryb egzaminacyjny |
| `defaults` | Wartości domyślne serwera (do podpowiedzi „przywróć domyślne”) |
| `plan` | `null`, gdy nie ma terminu egzaminu albo już minął. Inaczej: ile nowych pytań testowych zostało (`newTestQuestionsLeft`), ile dni jest na planowanie nowych (`planningDays`: dni do egzaminu − 14 dni rezerwy na same powtórki, najmniej 0), ile nowych pytań dziennie **trzeba**, żeby zdążyć (`requiredTestQuestionsPerDay`, bez żadnego sufitu) i sufit limitu automatycznego (`maxTestQuestionsPerDay`, 100), cel na dziś (`suggestedTestQuestionsPerDay` = trzeba, ale nie więcej niż sufit; zaokrąglone w górę, **0 w ostatnich 14 dniach**) i ile nowych pytań użytkownik zaczął dzisiaj (`newTestQuestionsToday`). Fiszek się nie planuje: powstają z pomyłek, więc ich liczby nie da się przewidzieć |

Gdy `requiredTestQuestionsPerDay` jest większe niż `maxTestQuestionsPerDay`, wszystkich nowych pytań nie da się zacząć przed rezerwą na powtórki; frontend mówi to wprost, zamiast prosić o podniesienie limitu.

Cel na dziś liczy się od stanu z **początku dnia** (pozostałe + zaczęte dzisiaj), więc nie maleje w miarę nauki w ciągu dnia. W planie nowe pytania testowe to pytania z **puli** użytkownika (jego akty po zawężeniu i źródła z filtra), których jeszcze nie ruszył w teście.

### `PUT /api/me/settings`

Zapisuje **pełny zestaw** (zastępuje poprzedni). `null` w polu liczbowym oznacza „wartość domyślna”, a `null` w
`shuffleOptions` to „tak”. Wyjątek: `null` w `newTestQuestionsPerDay` to **limit automatyczny** (idzie za planem do
egzaminu), a nie stała wartość. Daty egzaminu tu nie ma: użytkownik wybiera ścieżkę (`PUT /api/me/track`), a termin
egzaminu ustawia administrator. Nieznane pola (np. `examDate`) są ignorowane.

```json
{
  "newFlashcardsPerDay": null,
  "newTestQuestionsPerDay": 30,
  "maxReviewsPerDay": 150,
  "desiredRetention": 0.92,
  "shuffleOptions": true
}
```

| Pole | Dozwolone wartości |
|------|--------------------|
| `newFlashcardsPerDay` | 0–200 |
| `newTestQuestionsPerDay` | 0–500 albo `null` (automatyczny) |
| `maxReviewsPerDay` | 10–1000 |
| `desiredRetention` | od 0,70 do 0,97 (70–97%) |
| `shuffleOptions` | prawda / fałsz |

**Odpowiedź `200`**: ten sam kształt co `GET`, już po zapisie. **Błędy `400`:** „Docelowa retencja musi mieścić się
między 70% a 97%.”, a także komunikaty zakresów z walidacji pól („nie może być ujemna”, „najwyżej 200” itd.).

Zapis ustawień nauki **nie zmienia** ani wybranej ścieżki, ani wyglądu, ani puli (zawężenia i filtra źródeł): to osobne zestawy.

### `PUT /api/me/track` — wybór ścieżki

Wybór ścieżki, do której użytkownik się przygotowuje: jednej z pięciu aplikacji albo **Studenta**. Od tej chwili pulą
pytań jest pula tej ścieżki ([03](03-tresci.md#pula-pytań-użytkownika)), a terminem egzaminu termin wybranej aplikacji.
Student nie ma terminu egzaminu ani planu dnia (`examDate` i `plan` są `null`).

```json
{ "trackId": 4 }
```

Identyfikatory ścieżek podaje [`GET /api/tracks`](03-tresci.md#get-apitracks).

**Zmiana ścieżki** (inna niż dotychczasowa) **czyści zawężenie puli do aktów i przywraca filtr źródeł** (wszystkie trzy
grupy włączone), bo oba dotyczyły poprzedniej ścieżki. Ponowny wybór tej samej ścieżki niczego nie zmienia. Stan nauki
(terminy powtórek, historia) zostaje: należy do pytań, nie do ścieżki.

**Odpowiedź `200`**: ten sam kształt co `GET /api/me/settings`, już po zapisie. **Błędy `400`:** „Nie ma takiej
ścieżki.”, „wybierz ścieżkę” (brak `trackId`).

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

Termin najbliższego egzaminu każdej aplikacji (ścieżki rodzaju `EXAM`; Student go nie ma) ustawia administrator. `null`
oznacza „termin nieogłoszony”. Użytkownik ich nie zmienia; wybiera tylko ścieżkę.

### `GET /api/exam-terms`

Dostępny dla każdego zalogowanego. Zawsze wszystkie pięć aplikacji, w kolejności ścieżek, także te bez terminu.

```json
[
  { "track": { "id": 1, "slug": "adwokacka-radcowska", "name": "Aplikacja adwokacka i radcowska",
               "shortName": "Adwokacka i radcowska", "kind": "EXAM" },
    "examDate": null, "daysToExam": null },
  { "track": { "id": 4, "slug": "notarialna", "name": "Aplikacja notarialna", "shortName": "Notarialna", "kind": "EXAM" },
    "examDate": "2027-03-15", "daysToExam": 154 }
]
```

`daysToExam` jest `null`, gdy terminu nie ma albo już minął.

### `PUT /api/exam-terms/{trackId}`

Tylko `ADMIN` (inni dostają `403`). Ustawia albo usuwa termin jednej aplikacji. Zmiana działa od razu u wszystkich
użytkowników, którzy wybrali tę aplikację; już wyznaczone terminy powtórek **nie są** przeliczane wstecz (nowe odpowiedzi
liczą się z nową datą).

```json
{ "examDate": "2027-03-15" }
```

`null` w `examDate` usuwa termin. Odpowiedź `200`: wiersz w kształcie jak w `GET`. Dozwolona data: od dziś do 3 lat w
przód. **Błędy `400`:** „Nie ma takiej ścieżki.”, „Termin można ustawić tylko dla aplikacji, nie dla ścieżki Student.”,
„Termin egzaminu nie może być w przeszłości.”, „Termin egzaminu jest zbyt odległy (najwyżej 3 lata od dziś).”,
„Nieprawidłowy format żądania” (zły format daty).

# API: widoczność pytań (administrator)

[← Przegląd API](README.md) · [Spis treści](../README.md)

Aplikacje, przedmioty Studenta, akty prawne z rozdziałami i arkusze egzaminacyjne mają od Etapu 35 własne endpointy
administratora: [10. Panel administratora](10-panel-administratora.md). Od Etapu 36 są tam też **pytania** (lista z
wyszukiwarką, dodawanie, edycja), **zgłoszenia błędów** i **import z CSV i JSON**. Treść można nadal wczytywać skryptami SQL
(katalog `baza/`, szablon `04-import-szablon.sql`; opis w
[05. Konfiguracja i uruchomienie](../05-konfiguracja-i-uruchomienie.md#wczytywanie-treści-skryptami-sql)), ale nie trzeba.
Ten plik opisuje **ukrywanie** pytań (poniżej), [oznaczenia](#oznaczenia-pytań-apiadminquestion-marks) i odsyła do
[ustawiania terminów egzaminów](09-ustawienia.md#put-apiexam-termstrackid).

Oba endpointy ukrywania wymagają roli **ADMIN** (zwykły użytkownik dostaje `403`). Wymagają tokenu jak cała aplikacja.

Jak pytanie jest zbudowane w bazie i jakie ma ograniczenia: [02. Baza danych](../02-baza-danych.md#questions--pytania).

## `PUT /api/questions/{id}/hidden` — ukrycie / przywrócenie pytania

```json
{ "hidden": true }
```

Ukryte pytanie znika **wszystkim użytkownikom**: z testów, fiszek, liczników, własnych testów, statystyk i losowania
egzaminu. Historia odpowiedzi zostaje (przywrócone pytanie wraca z dotychczasowym stanem nauki). Kto ma ukryte pytanie
już otwarte w sesji, kończy ją normalnie: odpowiedź na nie nadal się zapisze, ale nowe sesje go nie zawierają.

**Odpowiedź `200`**

```json
{ "id": 101, "hidden": true, "flashcardHidden": false }
```

**Błędy:** `404` („Question not found”) przy nieistniejącym pytaniu, `400` przy braku pola `hidden`
(„podaj, czy ukryć”), `403` dla zwykłego użytkownika.

## `PUT /api/questions/{id}/flashcard-hidden` — ukrycie / przywrócenie samej fiszki

```json
{ "hidden": true }
```

Ukrywa **tylko fiszkę** pytania: pytanie zostaje w testach, ale nikt nie ma z niego fiszki (np. gdy pytanie nie nadaje
się do zapamiętywania). Fiszka znika z kolejki Fiszek i z licznika, przycisk „Dodaj do fiszek” nie działa (`400`
„Z tego pytania nie ma fiszki.”), a odpowiedź w teście zwraca `flashcard: "UNAVAILABLE"`. Stan nauki w trybie fiszek
zostaje w bazie.

Odpowiedź i błędy jak wyżej (kształt `{ "id", "hidden", "flashcardHidden" }`).

## Co ukrycie robi z innymi danymi

| Ukrycie | Testy | Fiszki | Liczniki i statystyki | Historia odpowiedzi |
|---------|-------|--------|-----------------------|---------------------|
| `hidden = true` | znika | znika | nie liczy się | zostaje |
| `flashcardHidden = true` | zostaje | znika | tylko fiszki | zostaje |

Poprawianie i dodawanie pytań oraz obsługa zgłoszeń błędów mają od Etapu 36 własne endpointy
([10. Panel administratora](10-panel-administratora.md#pytania)). Pytania nadal się nie usuwa, tylko ukrywa.

## Oznaczenia pytań (`/api/admin/question-marks`)

Pytanie może mieć **oznaczenia**, które służą wyłącznie do układania testów „Pytania z terminów” i „Pułapki”
(rodzaje `TIME_LIMIT` i `TRAPS` w [teście z kreatora](05-nauka.md)). Użytkownik **nigdy ich nie widzi**: ani w pytaniach,
ani w odpowiedziach API; widzi tylko, że takie testy istnieją. Wszystkie endpointy poniżej wymagają roli **ADMIN**
(`403` dla zwykłego użytkownika; dodatkowo cała ścieżka `/api/admin/**` jest w konfiguracji zabezpieczeń tylko dla
administratora).

| Oznaczenie (`mark`) | Znaczenie |
|---------------------|-----------|
| `TIME_LIMIT` | W pytaniu pada termin (np. liczba dni na wniesienie środka), odpowiedzi różnią się terminami |
| `TRAP_NEGATION` | Pułapka: przeczenie albo wyjątek w poleceniu („nie jest”, „z wyjątkiem”, „wskaż zdanie fałszywe”) |
| `TRAP_ABSOLUTE` | Pułapka: słowo absolutne („zawsze”, „nigdy”, „wyłącznie”) odróżnia jedną albo dwie odpowiedzi |
| `TRAP_NUMBERS` | Pułapka: odpowiedzi różnią się tylko liczbami z tą samą jednostką (np. 7 / 14 / 30 dni) |
| `TRAP_CONJUNCTION` | Pułapka: odpowiedzi różnią się spójnikiem („i” zamiast „lub”) |
| `TRAP_OUTDATED_LAW` | Pułapka: odpowiedź zgodna z dawnym stanem prawnym. Z treści nie da się jej rozpoznać, więc **automat jej nie proponuje** (tylko ręcznie) |

Każde oznaczenie ma **pochodzenie** (`origin`: `AUTO` dla propozycji automatu, `MANUAL` dla nadanego ręcznie) i **stan**
(`status`):

| Stan | Znaczenie |
|------|-----------|
| `SUGGESTED` | Propozycja automatu, czeka na przejrzenie. **Nie liczy się** w testach |
| `CONFIRMED` | Zatwierdzone. **Tylko takie liczą się** w testach `TIME_LIMIT` i `TRAPS` (`TRAPS` to dowolny podtyp pułapki) |
| `REJECTED` | Odrzucone. Automat nie zaproponuje go ponownie |

Pytanie ma najwyżej jedno oznaczenie danego rodzaju, więc ponowne uruchomienie automatu **niczego nie nadpisuje**: dodaje
propozycje tylko dla par pytanie–oznaczenie, których jeszcze nie ma w bazie (także nie tam, gdzie administrator zdecydował).
Do propozycji nie ma powrotu. Oznaczenia można też wczytać skryptem SQL (`baza/09-oznaczenia.sql`). Ekran do ich przeglądania jest w panelu (`/admin/oznaczenia`).

### `GET /api/admin/question-marks/summary`

Ile oznaczeń każdego rodzaju jest w każdym stanie (wiersz na rodzaj, także z zerami) i ile pytań ma baza.

```json
{ "questions": 1240, "marks": [ { "mark": "TIME_LIMIT", "suggested": 85, "confirmed": 0, "rejected": 0 } ] }
```

### `POST /api/admin/question-marks/analyze`

Bez ciała. Uruchamia automat na wszystkich pytaniach (porcjami po 500) i dodaje **propozycje** (`SUGGESTED`, `AUTO`).
Nic nie zatwierdza. Można uruchamiać wielokrotnie, np. po wczytaniu nowych pytań.

```json
{ "questionsScanned": 1240, "suggested": 312,
  "suggestedByMark": { "TIME_LIMIT": 85, "TRAP_NEGATION": 120, "TRAP_ABSOLUTE": 40, "TRAP_NUMBERS": 55,
                       "TRAP_CONJUNCTION": 12, "TRAP_OUTDATED_LAW": 0 } }
```

### `GET /api/admin/question-marks`

Oznaczenia w danym stanie razem z treścią pytania, stronami. Parametry: `status` (`SUGGESTED` domyślnie, `CONFIRMED` albo
`REJECTED`), `mark` (opcjonalnie, zawęża do jednego rodzaju), `page`, `size` (jak w innych listach administratora).
Odpowiedź to strona (`items`, `page`, `size`, `totalElements`, `totalPages`); element ma `id`, `questionId`, `mark`, `origin`,
`status`, `body`, `optionA`, `optionB`, `optionC`, `correctOption`, `sourceName` i `actShortName`.

### `POST /api/admin/question-marks/bulk`

Hurtowa zmiana stanu: wszystkie oznaczenia w stanie `from` (tylko podanych rodzajów w `marks`, a gdy lista pusta albo jej
brak, wszystkich) dostają stan `to`. `to` to `CONFIRMED` albo `REJECTED`; `from` musi się różnić od `to`.

```json
{ "from": "SUGGESTED", "to": "CONFIRMED", "marks": ["TRAP_NEGATION"] }
```

Odpowiedź: `{ "updated": 120 }`.

### `PUT /api/admin/question-marks/questions/{questionId}/{mark}`

Decyzja o jednym oznaczeniu jednego pytania. Ciało `{ "status": "CONFIRMED" }` nadaje oznaczenie ręcznie (`MANUAL`, gdy go
nie było) albo zatwierdza propozycję; `{ "status": "REJECTED" }` cofa je (i automat go nie zaproponuje ponownie).
Odpowiedź: `{ "questionId": 101, "mark": "TIME_LIMIT", "origin": "AUTO", "status": "CONFIRMED" }`. Nieznane pytanie: `404`.

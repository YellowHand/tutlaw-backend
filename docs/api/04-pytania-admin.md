# API: widoczność pytań (administrator)

[← Przegląd API](README.md) · [Spis treści](../README.md)

Aplikacja nie ma panelu ani endpointów do dodawania, edycji i usuwania pytań, aktów prawnych, ścieżek i arkuszy. **Treść
wczytuje się skryptami SQL** (katalog `baza/`, szablon importu `04-import-szablon.sql`; opis w
[05. Konfiguracja i uruchomienie](../05-konfiguracja-i-uruchomienie.md#wczytywanie-treści-skryptami-sql)). Administrator
ma w API tylko dwie rzeczy: **ukrywanie pytań** (poniżej) i [ustawianie terminów egzaminów](09-ustawienia.md#put-apiexam-termstrackid).
Rozpatrywanie zgłoszeń błędów też odbywa się zapytaniami SQL.

Oba endpointy wymagają roli **ADMIN** (zwykły użytkownik dostaje `403`). Wymagają tokenu jak cała aplikacja.

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

Poprawianie treści pytania, dodawanie i usuwanie pytań, aktów i arkuszy robi się w bazie (SQL), zgodnie z
[instrukcją katalogu `baza/`](../05-konfiguracja-i-uruchomienie.md#wczytywanie-treści-skryptami-sql). Zgłoszenia błędów
trafiają do tabeli `question_reports`; zapytania do ich przeglądania są w
[02. Bazie danych](../02-baza-danych.md#przydatne-zapytania-sql-ręczna-administracja).

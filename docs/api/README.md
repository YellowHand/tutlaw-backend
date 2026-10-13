# API: przegląd

[← Spis treści](../README.md)

Backend udostępnia **58 endpointów REST** zwracających JSON. Ten plik zawiera wspólne zasady i listę wszystkich
endpointów. Szczegóły (parametry, przykłady, błędy) są w plikach grup.

> Interaktywna wersja generowana z kodu: po uruchomieniu backendu Swagger UI jest pod
> `http://localhost:8080/swagger-ui.html`, a opis w formacie OpenAPI pod `http://localhost:8080/v3/api-docs`.

## Wspólne zasady

| Temat | Zasada |
|-------|--------|
| Adres | Lokalnie `http://localhost:8080`. Wszystkie endpointy aplikacji zaczynają się od `/api/` |
| Format | Żądania i odpowiedzi to JSON (`Content-Type: application/json`), nazwy pól w `camelCase` |
| Logowanie | Nagłówek `Authorization: Bearer <accessToken>` (token z `POST /api/auth/login`). Bez niego wszystko poza `/api/auth/**` daje **401** |
| Role | Endpointy oznaczone **ADMIN** wymagają roli `ADMIN`; zwykły użytkownik dostaje **403** |
| Wyliczenia (enum) | Wartości jako napisy, np. `"A"`, `"TEST"`, `"NOTARY"`. Nieznana wartość daje 400 „Nieprawidłowy format żądania” |
| Daty | `LocalDate`: `"2026-10-12"`. `LocalDateTime` (czas lokalny bez strefy, zwykle czas polski): `"2026-10-12T08:15:30"`. W błędach i eksporcie danych `Instant`: `"2026-10-12T06:15:30.123Z"` |
| Puste wartości | Pola opcjonalne mogą być `null` albo pominięte |
| Stronicowanie | `?page=0&size=50`. Strona od 0; `size` od 1 do 200 (domyślnie 50); złe wartości są przycinane, nie dają błędu |

### Format odpowiedzi stronicowanej

```json
{ "items": [ ], "page": 0, "size": 50, "totalElements": 137, "totalPages": 3 }
```

### Kody błędów

Każdy błąd ma ten sam kształt:

```json
{
  "timestamp": "2026-10-12T06:15:30.123Z",
  "status": 409,
  "error": "Conflict",
  "message": "Taka karta już istnieje w tym źródle",
  "path": "/api/cards"
}
```

| Kod | Kiedy | Przykładowy komunikat |
|-----|-------|------------------------|
| **400** Bad Request | Błędne dane: walidacja pól, zły JSON, nieznana wartość enum, naruszona reguła (np. zbyt słabe hasło) | „Hasło: musi mieć od 8 do 72 znaków” |
| **401** Unauthorized | Brak tokenu, zły lub wygasły token, konto usunięte, sesja unieważniona zmianą hasła | „Wymagane zalogowanie” |
| **403** Forbidden | Brak uprawnień (endpoint tylko dla `ADMIN`) | — |
| **404** Not Found | Nie ma takiego zasobu albo jest niedostępny dla użytkownika (np. karta z nieprzeczytanego rozdziału) | „Card not found” |
| **405 / 415** | Zła metoda HTTP / zły typ treści (standardowe odpowiedzi Springa) | — |
| **409** Conflict | Duplikat, konflikt danych, równoległa zmiana tej samej karty, konto nieaktywowane, link wygasł | „Taka karta już istnieje w tym źródle” |
| **429** Too Many Requests | Przekroczony limit prób; nagłówek `Retry-After` podaje sekundy | „Zbyt wiele prób. Spróbuj ponownie za 7 min” |
| **500** Internal Server Error | Nieoczekiwany błąd; szczegóły tylko w logu serwera | „Wystąpił nieoczekiwany błąd. Spróbuj ponownie za chwilę.” |

Błędy walidacji pól łączą wiele problemów w jedno `message`, z polskimi nazwami pól, np.
`Mail prywatny: nieprawidłowy adres e-mail; Hasło: musi mieć od 8 do 72 znaków`.

## Wszystkie endpointy

Kolumna **Dostęp**: *publiczny* — bez logowania; *zalogowany* — wymaga tokenu; *ADMIN* — wymaga roli administratora.

### Konta i logowanie — [01-auth.md](01-auth.md)

| Metoda | Adres | Dostęp | Opis |
|--------|-------|--------|------|
| POST | `/api/auth/register` | publiczny | Rejestracja konta |
| GET | `/api/auth/verify?token=` | publiczny | Aktywacja konta linkiem z maila |
| POST | `/api/auth/login` | publiczny | Logowanie, zwraca token JWT |
| POST | `/api/auth/resend-verification` | publiczny | Ponowne wysłanie linku aktywacyjnego |
| POST | `/api/auth/forgot-password` | publiczny | Wysłanie linku do zmiany hasła |
| POST | `/api/auth/reset-password` | publiczny | Ustawienie nowego hasła tokenem z maila |

### Konto zalogowanego — [02-konto.md](02-konto.md)

| Metoda | Adres | Dostęp | Opis |
|--------|-------|--------|------|
| GET | `/api/me` | zalogowany | Adres e-mail zalogowanego (do sprawdzenia sesji) |
| GET | `/api/users/me/export` | zalogowany | Kopia wszystkich danych w JSON |
| PUT | `/api/users/me/password` | zalogowany | Zmiana hasła, zwraca nowy token |
| DELETE | `/api/users/me` | zalogowany | Usunięcie konta (wymaga hasła) |

### Struktura materiału — [03-tresci.md](03-tresci.md)

| Metoda | Adres | Dostęp | Opis |
|--------|-------|--------|------|
| GET | `/api/categories` | zalogowany | Kategorie z liczbą przedmiotów |
| GET | `/api/categories/{categoryId}/subjects` | zalogowany | Przedmioty w kategorii (po polsku posortowane) |
| POST | `/api/categories` | ADMIN | Dodanie kategorii |
| GET | `/api/subjects` | zalogowany | Wszystkie przedmioty |
| POST | `/api/subjects` | ADMIN | Dodanie przedmiotu |
| GET | `/api/subjects/{subjectId}/sources` | zalogowany | Źródła przedmiotu |
| POST | `/api/sources` | ADMIN | Dodanie źródła |
| GET | `/api/sources/{sourceId}/chapters` | zalogowany | Rozdziały źródła ze stanem „przeczytany” |
| POST | `/api/chapters` | ADMIN | Dodanie rozdziału |
| GET | `/api/tags` | zalogowany | Wszystkie tagi |
| POST | `/api/tags` | ADMIN | Dodanie tagu |
| PUT | `/api/chapters/{chapterId}/progress` | zalogowany | Oznaczenie rozdziału jako przeczytany / nieprzeczytany |
| GET | `/api/tests/catalog` | zalogowany | Drzewo ekranu Testów z licznikami pytań |

### Karty (administrator) — [04-karty-admin.md](04-karty-admin.md)

| Metoda | Adres | Dostęp | Opis |
|--------|-------|--------|------|
| GET | `/api/chapters/{chapterId}/cards` | ADMIN | Karty rozdziału (stronicowane) |
| GET | `/api/sources/{sourceId}/cards` | ADMIN | Karty źródła (stronicowane) |
| POST | `/api/cards` | ADMIN | Dodanie jednej karty |
| POST | `/api/cards/bulk` | ADMIN | Import zbiorczy kart |
| PUT | `/api/cards/{id}/hidden` | ADMIN | Ukrycie / przywrócenie karty wszystkim |

### Nauka — [05-nauka.md](05-nauka.md)

| Metoda | Adres | Dostęp | Opis |
|--------|-------|--------|------|
| GET | `/api/review/queue` | zalogowany | Kolejka fiszek na dziś |
| GET | `/api/review/test-queue` | zalogowany | Kolejka pytań testowych na dziś |
| GET | `/api/review/queue/new-cards-available` | zalogowany | Ile nowych kart czeka w zakresie |
| GET | `/api/review/queue/backlog` | zalogowany | Zaległości w zakresie |
| GET | `/api/review/due-count` | zalogowany | Ile kart czeka na powtórkę (licznik na pasku) |
| GET | `/api/review/practice-queue` | zalogowany | Sesja testowa z podanych pytań |
| GET | `/api/review/mistakes-queue` | zalogowany | Sesja „Powtórz moje błędy” |
| GET | `/api/review/mistakes-count` | zalogowany | Ile jest błędów do powtórzenia |
| POST | `/api/review` | zalogowany | Ocena fiszki (1–4) |
| POST | `/api/review/test-answer` | zalogowany | Odpowiedź na pytanie testowe |

### Zawieszanie, fiszki z pytań, zgłoszenia — [06-zawieszanie-fiszki-zgloszenia.md](06-zawieszanie-fiszki-zgloszenia.md)

| Metoda | Adres | Dostęp | Opis |
|--------|-------|--------|------|
| GET | `/api/me/difficult-cards` | zalogowany | Zawieszone i trudne pytania |
| PUT | `/api/me/suspended-cards/{cardId}` | zalogowany | Zawieszenie pytania |
| DELETE | `/api/me/suspended-cards/{cardId}` | zalogowany | Przywrócenie pytania |
| POST | `/api/questions/{questionId}/flashcard` | zalogowany | „Dodaj do fiszek” |
| DELETE | `/api/questions/{questionId}/flashcard` | zalogowany | „Usuń z fiszek” |
| POST | `/api/cards/{cardId}/reports` | zalogowany | Zgłoszenie błędu w pytaniu |

### Egzamin próbny — [07-egzaminy.md](07-egzaminy.md)

| Metoda | Adres | Dostęp | Opis |
|--------|-------|--------|------|
| POST | `/api/exams/start` | zalogowany | Losowanie pytań egzaminu |
| POST | `/api/exams/submit` | zalogowany | Ocena egzaminu i zapis podsumowania |
| GET | `/api/exams/history` | zalogowany | Ostatnie podejścia |

### Statystyki — [08-statystyki.md](08-statystyki.md)

| Metoda | Adres | Dostęp | Opis |
|--------|-------|--------|------|
| GET | `/api/statistics/overview` | zalogowany | Przegląd nauki i prognoza 7 dni |
| GET | `/api/statistics/weak-cards` | zalogowany | Karty, które najłatwiej zapomnisz |
| GET | `/api/statistics/chapters` | zalogowany | Opanowanie rozdziałów źródła |
| GET | `/api/statistics/sources` | zalogowany | Postęp w źródłach przedmiotu |
| GET | `/api/statistics/tests` | zalogowany | Statystyki Testów (30 dni) |
| GET | `/api/statistics/origins` | zalogowany | Statystyki wg aplikacji i roku |
| GET | `/api/statistics/activity` | zalogowany | Liczba odpowiedzi na dzień |

### Ustawienia — [09-ustawienia.md](09-ustawienia.md)

| Metoda | Adres | Dostęp | Opis |
|--------|-------|--------|------|
| GET | `/api/me/settings` | zalogowany | Ustawienia nauki |
| PUT | `/api/me/settings` | zalogowany | Zapis ustawień nauki |
| GET | `/api/me/origin-filter` | zalogowany | Filtr pochodzenia pytań w Testach |
| PUT | `/api/me/origin-filter` | zalogowany | Zapis filtra pochodzenia |

### Poza `/api`

| Adres | Dostęp | Opis |
|-------|--------|------|
| `/actuator/health` | publiczny | Stan aplikacji (Spring Boot domyślnie wystawia przez HTTP tylko ten jeden endpoint Actuatora; cały `/actuator/**` jest otwarty w regułach bezpieczeństwa) |
| `/swagger-ui.html`, `/v3/api-docs` | publiczny | Dokumentacja generowana z kodu |

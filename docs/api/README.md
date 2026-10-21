# API: przegląd

[← Spis treści](../README.md)

Backend udostępnia **70 endpointów REST** zwracających JSON. Ten plik zawiera wspólne zasady i listę wszystkich
endpointów. Szczegóły (parametry, przykłady, błędy) są w plikach grup.

> Interaktywna wersja generowana z kodu: po uruchomieniu backendu Swagger UI jest pod
> `http://localhost:8080/swagger-ui.html`, a opis w formacie OpenAPI pod `http://localhost:8080/v3/api-docs`.

## Wspólne zasady

| Temat | Zasada |
|-------|--------|
| Adres | Lokalnie `http://localhost:8080`. Wszystkie endpointy aplikacji zaczynają się od `/api/` |
| Format | Żądania i odpowiedzi to JSON (`Content-Type: application/json`), nazwy pól w `camelCase` |
| Logowanie | Nagłówek `Authorization: Bearer <accessToken>` (token dostępu z `POST /api/auth/login` albo `POST /api/auth/refresh`). Bez niego wszystko poza `/api/auth/**` daje **401** |
| Role | Endpointy oznaczone **ADMIN** wymagają roli `ADMIN`; zwykły użytkownik dostaje **403** |
| Wyliczenia (enum) | Wartości jako napisy, np. `"A"`, `"TEST"`, `"EXAM_SHEET"`. Nieznana wartość daje 400 „Nieprawidłowy format żądania” |
| Daty | `LocalDate`: `"2026-10-12"`. `LocalDateTime` (czas lokalny bez strefy, zwykle czas polski): `"2026-10-12T08:15:30"`. W błędach i eksporcie danych `Instant`: `"2026-10-12T06:15:30.123Z"` |
| Puste wartości | Pola opcjonalne mogą być `null` albo pominięte |
| Stronicowanie | `?page=0&size=50`. Strona od 0; `size` od 1 do 200 (domyślnie 50); złe wartości są przycinane, nie dają błędu. Listy „Moje testy” i historii egzaminów mają własne granice: domyślnie 20 na stronę, najwyżej 100 |

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
  "message": "Taki adres e-mail jest już zajęty",
  "path": "/api/auth/register"
}
```

| Kod | Kiedy | Przykładowy komunikat |
|-----|-------|------------------------|
| **400** Bad Request | Błędne dane: walidacja pól, zły JSON, nieznana wartość enum, naruszona reguła (np. zbyt słabe hasło) | „Hasło: musi mieć od 8 do 72 znaków” |
| **401** Unauthorized | Brak tokenu, zły lub wygasły token dostępu (30 minut), konto usunięte, sesja unieważniona zmianą hasła. Klient odpowiada na to wywołaniem `POST /api/auth/refresh` i ponawia żądanie | „Wymagane zalogowanie” |
| **403** Forbidden | Brak uprawnień (endpoint tylko dla `ADMIN`: ukrywanie pytań, terminy egzaminów, oznaczenia pytań `/api/admin/**`) | — |
| **404** Not Found | Nie ma takiego zasobu albo jest niedostępny dla użytkownika (np. nieistniejące albo ukryte pytanie) | „Question not found” |
| **405 / 415** | Zła metoda HTTP / zły typ treści (standardowe odpowiedzi Springa) | — |
| **409** Conflict | Duplikat, konflikt danych, równoległa zmiana tego samego stanu nauki, konto nieaktywowane, link wygasł | „Taki adres e-mail jest już zajęty” |
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
| POST | `/api/auth/login` | publiczny | Logowanie, zwraca token dostępu (30 min) i token odświeżający (30 dni) |
| POST | `/api/auth/refresh` | publiczny | Wymiana tokenu odświeżającego na nową parę tokenów (rotacja) |
| POST | `/api/auth/logout` | publiczny | Wylogowanie: unieważnienie sesji tokenu odświeżającego |
| POST | `/api/auth/resend-verification` | publiczny | Ponowne wysłanie linku aktywacyjnego |
| POST | `/api/auth/forgot-password` | publiczny | Wysłanie linku do zmiany hasła |
| POST | `/api/auth/reset-password` | publiczny | Ustawienie nowego hasła tokenem z maila |

### Konto zalogowanego — [02-konto.md](02-konto.md)

| Metoda | Adres | Dostęp | Opis |
|--------|-------|--------|------|
| GET | `/api/me` | zalogowany | Adres e-mail zalogowanego (do sprawdzenia sesji) |
| GET | `/api/users/me/export` | zalogowany | Kopia wszystkich danych w JSON |
| PUT | `/api/users/me/password` | zalogowany | Zmiana hasła, zwraca nową parę tokenów |
| DELETE | `/api/users/me` | zalogowany | Usunięcie konta (wymaga hasła) |

### Ścieżki, przedmioty, pula pytań, katalog Testów — [03-tresci.md](03-tresci.md)

| Metoda | Adres | Dostęp | Opis |
|--------|-------|--------|------|
| GET | `/api/tracks` | zalogowany | Sześć ścieżek: pięć aplikacji i Student |
| GET | `/api/courses` | zalogowany | Przedmioty studenta z aktami prawnymi i liczbą pytań |
| GET | `/api/me/pool` | zalogowany | Pula pytań użytkownika (zakres nauki): ścieżka, dziedziny z aktami do zaznaczenia (Student: przedmioty) |
| PUT | `/api/me/pool/acts` | zalogowany | Zawężenie puli do wybranych aktów prawnych (puste = bez zawężenia); tylko aplikacje |
| PUT | `/api/me/pool/courses` | zalogowany | Wybór przedmiotów na ścieżce Student (puste = wszystkie przedmioty) |
| GET | `/api/tests/catalog` | zalogowany | Drzewo kreatora Testów (dziedzina → akt prawny → rozdział, przedmioty Studenta) z licznikami pytań; parametr `source` wybiera źródło |

### Pytania (administrator) — [04-pytania-admin.md](04-pytania-admin.md)

| Metoda | Adres | Dostęp | Opis |
|--------|-------|--------|------|
| PUT | `/api/questions/{id}/hidden` | ADMIN | Ukrycie / przywrócenie pytania wszystkim |
| PUT | `/api/questions/{id}/flashcard-hidden` | ADMIN | Ukrycie / przywrócenie samej fiszki pytania |
| GET | `/api/admin/question-marks/summary` | ADMIN | Oznaczenia pytań: ile ich jest w każdym stanie |
| POST | `/api/admin/question-marks/analyze` | ADMIN | Automat proponuje oznaczenia (terminy, pułapki) dla wszystkich pytań |
| GET | `/api/admin/question-marks` | ADMIN | Oznaczenia w danym stanie z treścią pytań (kolejka do przejrzenia), stronami |
| POST | `/api/admin/question-marks/bulk` | ADMIN | Hurtowa zmiana stanu oznaczeń (np. zatwierdź wszystkie propozycje) |
| PUT | `/api/admin/question-marks/questions/{questionId}/{mark}` | ADMIN | Zatwierdzenie albo odrzucenie jednego oznaczenia jednego pytania |

### Nauka — [05-nauka.md](05-nauka.md)

| Metoda | Adres | Dostęp | Opis |
|--------|-------|--------|------|
| GET | `/api/review/flashcard-queue` | zalogowany | Kolejka fiszek na dziś |
| GET | `/api/review/test-queue` | zalogowany | Kolejka pytań testowych na dziś |
| GET | `/api/review/queue/new-available` | zalogowany | Ile nowych pytań czeka w zakresie (w trybie fiszek albo testów) |
| GET | `/api/review/queue/backlog` | zalogowany | Zaległości w zakresie |
| GET | `/api/review/due-count` | zalogowany | Ile pytań czeka na powtórkę (licznik na pasku) |
| GET | `/api/review/due-tomorrow` | zalogowany | Ile pytań wróci na powtórkę do końca jutra (podsumowanie sesji) |
| GET | `/api/review/practice-queue` | zalogowany | Sesja testowa z podanych pytań |
| GET | `/api/review/mistakes-queue` | zalogowany | Sesja „Powtórz moje błędy” |
| GET | `/api/review/mistakes-count` | zalogowany | Ile jest błędów do powtórzenia |
| GET | `/api/review/custom-test/counts` | zalogowany | Test z kreatora: ile pytań każdego rodzaju (w tym ze zmianą w prawie, o terminach i pułapek) w wybranym zakresie i źródle |
| POST | `/api/review/custom-test` | zalogowany | Test z kreatora, z „Losuj z całej puli” i z szybkiego zestawu: losowanie pytań z wybranego zakresu i źródła oraz zapis testu (10, 20, 50, 100 albo 150 pytań) |
| GET | `/api/custom-tests/sessions` | zalogowany | „Moje testy”: jedna strona listy zapisanych testów (stronicowana) |
| GET | `/api/custom-tests/sessions/{id}` | zalogowany | „Moje testy”: szczegóły testu z odpowiedziami |
| POST | `/api/review` | zalogowany | Ocena fiszki (1–4) |
| POST | `/api/review/test-answer` | zalogowany | Odpowiedź na pytanie testowe |

### Zawieszanie, fiszki z pytań, zgłoszenia — [06-zawieszanie-fiszki-zgloszenia.md](06-zawieszanie-fiszki-zgloszenia.md)

| Metoda | Adres | Dostęp | Opis |
|--------|-------|--------|------|
| GET | `/api/me/difficult-questions` | zalogowany | Zawieszone i trudne pytania |
| PUT | `/api/me/suspended-questions/{questionId}` | zalogowany | Zawieszenie pytania w trybie (`?mode=TEST` albo `FLASHCARD`) |
| DELETE | `/api/me/suspended-questions/{questionId}` | zalogowany | Przywrócenie pytania w trybie |
| POST | `/api/questions/{questionId}/flashcard` | zalogowany | „Dodaj do fiszek” |
| DELETE | `/api/questions/{questionId}/flashcard` | zalogowany | „Usuń z fiszek” |
| POST | `/api/questions/{questionId}/reports` | zalogowany | Zgłoszenie błędu w pytaniu |

### Egzamin próbny — [07-egzaminy.md](07-egzaminy.md)

| Metoda | Adres | Dostęp | Opis |
|--------|-------|--------|------|
| POST | `/api/exams/start` | zalogowany | Pytania egzaminu (stały format 150 pytań, bez ciała): `?source=` losuje z wybranego źródła, `?sheetId=` daje cały egzamin MS z wybranego roku |
| GET | `/api/exams/availability` | zalogowany | Ile pytań można wylosować na egzamin z każdego źródła |
| GET | `/api/exams/sheets` | zalogowany | Egzaminy MS z poprzednich lat według aplikacji i roku |
| POST | `/api/exams/submit` | zalogowany | Ocena egzaminu i zapis podsumowania |
| GET | `/api/exams/history` | zalogowany | Podejścia od najnowszego (stronicowane) |
| GET | `/api/exams/attempts/{id}` | zalogowany | Raport zapisanego podejścia (z zapisanych odpowiedzi) |

### Statystyki — [08-statystyki.md](08-statystyki.md)

| Metoda | Adres | Dostęp | Opis |
|--------|-------|--------|------|
| GET | `/api/statistics/overview` | zalogowany | Przegląd nauki i prognoza 7 dni |
| GET | `/api/statistics/weak-questions` | zalogowany | Pytania, które najłatwiej zapomnisz |
| GET | `/api/statistics/acts` | zalogowany | Opanowanie aktów prawnych z puli |
| GET | `/api/statistics/tests` | zalogowany | Statystyki Testów (30 dni) |
| GET | `/api/statistics/sources` | zalogowany | Statystyki wg źródła pytań (arkusze i lata, pytania autorskie) |
| GET | `/api/statistics/activity` | zalogowany | Liczba odpowiedzi na dzień |
| GET | `/api/statistics/learning-summary` | zalogowany | Podsumowanie nauki: odpowiedzi, egzaminy, własne testy, czas nauki, średni czas odpowiedzi |

### Ustawienia — [09-ustawienia.md](09-ustawienia.md)

| Metoda | Adres | Dostęp | Opis |
|--------|-------|--------|------|
| GET | `/api/me/settings` | zalogowany | Ustawienia nauki |
| PUT | `/api/me/settings` | zalogowany | Zapis ustawień nauki |
| PUT | `/api/me/track` | zalogowany | Wybór ścieżki: jedna z pięciu aplikacji albo Student (czyści zawężenie puli do aktów) |
| PUT | `/api/me/theme` | zalogowany | Wybór wyglądu aplikacji (ciemny / jasny) |
| GET | `/api/me/interface` | zalogowany | Ustawienia interfejsu: ukrycie podpowiedzi skrótów i licznika czasu, liczba dotychczasowych odpowiedzi |
| PUT | `/api/me/interface` | zalogowany | Zapis ustawień interfejsu |
| POST | `/api/me/study-time` | zalogowany | Doliczenie porcji czasu nauki (1–300 s) do dzisiejszego dnia |
| GET | `/api/me/study-progress` | zalogowany | Ile czego zniknie po wyzerowaniu postępów (do okna potwierdzenia) |
| DELETE | `/api/me/study-progress` | zalogowany | Zerowanie postępów w nauce (wymaga `confirm: "WYZERUJ"`) |
| GET | `/api/exam-terms` | zalogowany | Terminy egzaminów pięciu aplikacji |
| PUT | `/api/exam-terms/{trackId}` | ADMIN | Ustawienie albo usunięcie terminu egzaminu aplikacji |

### Poza `/api`

| Adres | Dostęp | Opis |
|-------|--------|------|
| `/actuator/health` | publiczny | Stan aplikacji (Spring Boot domyślnie wystawia przez HTTP tylko ten jeden endpoint Actuatora; cały `/actuator/**` jest otwarty w regułach bezpieczeństwa) |
| `/swagger-ui.html`, `/v3/api-docs` | publiczny | Dokumentacja generowana z kodu |

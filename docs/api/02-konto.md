# API: konto zalogowanego (`/api/me`, `/api/users/me`)

[← Przegląd API](README.md) · [Spis treści](../README.md)

Wszystkie endpointy wymagają tokenu. Tło (hasła, sesje, RODO) jest w
[03. Bezpieczeństwo i konta](../03-bezpieczenstwo-i-konta.md).

## `GET /api/me` — kim jestem

Najprostszy sposób, by sprawdzić, czy token nadal działa.

**Odpowiedź `200`**

```json
{ "email": "jan.kowalski@gmail.com", "id": 5 }
```

Zwraca prywatny adres e-mail z tokenu i numer konta (`id`, ten sam, który widać w dziennikach serwera jako „user 5”;
frontend przekazuje go do Sentry zamiast adresu e-mail). Przy nieważnym tokenie: `401`.

## `GET /api/users/me/export` — kopia wszystkich danych

Zwraca jeden obiekt JSON z **wszystkimi danymi**, które serwis przechowuje o użytkowniku (prawo dostępu i
przenoszenia danych). Bez hasła i tokenów.

**Odpowiedź `200` (skrócona)**

```json
{
  "exportedAt": "2026-10-12T06:15:30.123Z",
  "account": {
    "privateEmail": "jan.kowalski@gmail.com",
    "universityEmail": "jan.kowalski@student.uw.edu.pl",
    "universityEmailVerified": true,
    "role": "STUDENT",
    "createdAt": "2026-09-01T10:00:00",
    "termsAcceptedAt": "2026-09-01T10:00:00"
  },
  "questionStates": [
    { "questionId": 101, "mode": "TEST", "question": "Treść pytania…", "state": "REVIEW", "reps": 5, "lapses": 1,
      "stability": 46.28, "difficulty": 2.1, "dueDate": "2026-11-20T00:00:00",
      "lastReviewAt": "2026-10-05T19:00:00", "suspended": false }
  ],
  "reviewLogs": [ { "questionId": 101, "reviewedAt": "2026-10-05T19:00:00", "mode": "TEST", "rating": 3, "correct": true, "unsure": false } ],
  "questionReports": [ { "questionId": 55, "question": "Treść…", "reason": "OUTDATED", "comment": "Przepis się zmienił", "status": "NEW", "createdAt": "2026-10-02T12:00:00" } ],
  "examAttempts": [ { "finishedAt": "2026-10-01T20:00:00", "scope": "Cała pula", "questionCount": 150, "answeredCount": 150,
                      "correctCount": 121, "passThresholdPercent": 67, "passPoints": 100, "passed": true, "timeLimitMinutes": 180,
                      "durationSeconds": 9710, "timedOut": false } ],
  "flashcardAdditions": [ { "questionId": 101, "question": "Treść…", "addedAt": "2026-10-03T09:00:00" } ],
  "customTests": [ { "startedAt": "2026-10-10T18:30:00", "finishedAt": "2026-10-10T18:52:00", "scope": "Prawo karne i postępowanie karne",
                     "kind": "NEW", "requestedCount": 20, "questionCount": 20, "answeredCount": 20, "correctCount": 15,
                     "guessedCount": 2, "timeSeconds": 311.5 } ],
  "studyTime": [ { "day": "2026-10-10", "seconds": 1820 } ],
  "learningSettings": { "newFlashcardsPerDay": null, "newTestQuestionsPerDay": 30, "maxReviewsPerDay": null,
                        "desiredRetention": 0.92, "track": "notarialna", "theme": "LIGHT",
                        "poolActIds": [12, 14],
                        "hintHiddenTest": false, "hintHiddenFlashcard": false, "questionTimerHidden": false }
}
```

`questionStates` ma osobny wiersz dla każdego trybu (`TEST` albo `FLASHCARD`) każdego pytania, które użytkownik
ćwiczył: stan nauki testowej i fiszkowej jest liczony oddzielnie. `customTests` to zapisane własne testy („Moje testy”)
z konfiguracją i wynikiem; odpowiedzi na pojedyncze pytania są w `reviewLogs`. `studyTime` to czas nauki w sekundach,
osobno dla każdego dnia. W `learningSettings` pole `track` to `slug` ścieżki (np. `notarialna`, `student`),
`poolActIds` to akty prawne, do których
użytkownik zawęził pulę (pusta lista = bez zawężenia). Cały obiekt `learningSettings` jest `null`, jeśli użytkownik
nigdy nie zmieniał ustawień; `null` w polu liczbowym oznacza wartość domyślną.

## `PUT /api/users/me/password` — zmiana hasła

**Ciało żądania**

```json
{ "currentPassword": "mojeHaslo123", "newPassword": "noweHaslo456" }
```

| Pole | Reguła |
|------|--------|
| `currentPassword` | Wymagane, do 256 znaków |
| `newPassword` | 8–72 znaków i inne niż obecne |

**Odpowiedź `200`**

```json
{ "accessToken": "eyJhbGciOi...(nowy token)", "refreshToken": "8d1f0c52-7e0b-4c0a-b6a1-3f2b9d4e7a10" }
```

Wszystkie dotychczasowe sesje (także ta) przestają działać: tokeny dostępu przez znacznik czasu na koncie, a tokeny
odświeżające przez unieważnienie. Frontend **musi zapisać nową parę tokenów**. Otwarte linki do resetu hasła są
unieważniane, a na adres uczelniany idzie powiadomienie o zmianie.

**Błędy:** `400` — „Obecne hasło jest nieprawidłowe”, „Nowe hasło musi być inne niż obecne”, problem z hasłem;
`429` — limit jak przy logowaniu (błędne obecne hasło liczy się jako nieudana próba).

## `DELETE /api/users/me` — usunięcie konta

**Ciało żądania**

```json
{ "password": "mojeHaslo123" }
```

**Odpowiedź:** `204 No Content`. Konto i **cała** historia nauki (także zapisane własne testy, czas nauki i tokeny odświeżające) są usuwane bezpowrotnie (kaskada w bazie).

**Błędy:** `400` — „Nieprawidłowe hasło” (konto zostaje nietknięte); `429` — limit jak przy logowaniu.

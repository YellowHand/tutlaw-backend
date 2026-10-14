# API: konto zalogowanego (`/api/me`, `/api/users/me`)

[← Przegląd API](README.md) · [Spis treści](../README.md)

Wszystkie endpointy wymagają tokenu. Tło (hasła, sesje, RODO) jest w
[03. Bezpieczeństwo i konta](../03-bezpieczenstwo-i-konta.md).

## `GET /api/me` — kim jestem

Najprostszy sposób, by sprawdzić, czy token nadal działa.

**Odpowiedź `200`**

```json
{ "email": "jan.kowalski@gmail.com" }
```

Zwraca prywatny adres e-mail z tokenu. Przy nieważnym tokenie: `401`.

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
  "chapterProgress": [ { "chapterId": 7, "chapterTitle": "Zasady ogólne", "read": true, "readAt": "2026-09-03T18:20:00" } ],
  "cardStates": [
    { "cardId": 101, "question": "Treść pytania…", "state": "REVIEW", "reps": 5, "lapses": 1,
      "stability": 46.28, "difficulty": 2.1, "dueDate": "2026-11-20T00:00:00",
      "lastReviewAt": "2026-10-05T19:00:00", "suspended": false }
  ],
  "reviewLogs": [ { "cardId": 101, "reviewedAt": "2026-10-05T19:00:00", "mode": "TEST", "rating": 3, "correct": true, "unsure": false } ],
  "cardReports": [ { "cardId": 55, "question": "Treść…", "reason": "OUTDATED", "comment": "Przepis się zmienił", "status": "NEW", "createdAt": "2026-10-02T12:00:00" } ],
  "examAttempts": [ { "finishedAt": "2026-10-01T20:00:00", "scope": "Cały materiał", "questionCount": 50, "answeredCount": 50,
                      "correctCount": 41, "passThresholdPercent": 75, "passPoints": null, "passed": true, "timeLimitMinutes": 60,
                      "durationSeconds": 2710, "timedOut": false } ],
  "flashcardAdditions": [ { "questionId": 101, "question": "Treść…", "addedAt": "2026-10-03T09:00:00" } ],
  "learningSettings": { "newCardsPerDay": null, "newTestQuestionsPerDay": 30, "maxReviewsPerDay": null,
                        "desiredRetention": 0.92, "examDate": "2027-03-15", "shuffleOptions": true, "testOrigins": null }
}
```

`learningSettings` jest `null`, jeśli użytkownik nigdy nie zmieniał ustawień; `null` w polu liczbowym oznacza wartość
domyślną.

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
{ "accessToken": "eyJhbGciOi...(nowy token)" }
```

Wszystkie dotychczasowe sesje (także ta) przestają działać, więc frontend **musi zapisać nowy token**. Otwarte linki do
resetu hasła są unieważniane, a na adres uczelniany idzie powiadomienie o zmianie.

**Błędy:** `400` — „Obecne hasło jest nieprawidłowe”, „Nowe hasło musi być inne niż obecne”, problem z hasłem;
`429` — limit jak przy logowaniu (błędne obecne hasło liczy się jako nieudana próba).

## `DELETE /api/users/me` — usunięcie konta

**Ciało żądania**

```json
{ "password": "mojeHaslo123" }
```

**Odpowiedź:** `204 No Content`. Konto i **cała** historia nauki są usuwane bezpowrotnie (kaskada w bazie).

**Błędy:** `400` — „Nieprawidłowe hasło” (konto zostaje nietknięte); `429` — limit jak przy logowaniu.

# API: zawieszanie, fiszki z pytań, zgłoszenia błędów

[← Przegląd API](README.md) · [Spis treści](../README.md)

Trzy małe funkcje, które pomagają uczącemu się radzić sobie z trudnymi i błędnymi pytaniami. Wszystkie wymagają tokenu.
Tło: [04. Nauka](../04-nauka-testy-fiszki-fsrs.md).

## Trudne i zawieszone pytania

Zawieszenie dotyczy tylko jednego użytkownika i niczego nie kasuje: pytanie znika z kolejek i liczników powtórek, ale
jego stan (stabilność, termin, historia) zostaje. Zawieszenie jest **osobne dla każdego trybu**: pytanie zawieszone w
testach nadal może być w fiszkach i odwrotnie (każdy tryb ma własny stan nauki).

### `GET /api/me/difficult-questions`

```json
{
  "leechThreshold": 8,
  "suspended": [
    { "questionId": 101, "mode": "TEST", "body": "Czy czyn zabroniony…", "actName": "Kodeks karny",
      "lapses": 9, "suspendedAt": "2026-10-10T19:30:00" }
  ],
  "leeches": [
    { "questionId": 140, "mode": "FLASHCARD", "body": "Termin przedawnienia…", "actName": "Kodeks cywilny",
      "lapses": 8, "suspendedAt": null }
  ]
}
```

| Pole | Znaczenie |
|------|-----------|
| `leechThreshold` | Od ilu błędów pytanie uchodzi za trudne (domyślnie 8) |
| `suspended` | Zawieszone pytania, od ostatnio zawieszonych (maks. 200). To samo pytanie może wystąpić dwa razy: w trybie `TEST` i `FLASHCARD` |
| `leeches` | Trudne pytania, które **nadal są w nauce** (niezawieszone) i leżą w aktach puli użytkownika, od największej liczby błędów (maks. 200) |
| `items[].mode` | Tryb, którego dotyczy stan: `TEST` albo `FLASHCARD` |

### `PUT /api/me/suspended-questions/{questionId}?mode=TEST|FLASHCARD` — zawieś

Parametr `mode` jest **wymagany**. Odpowiedź `204 No Content`. **Idempotentne**: ponowne zawieszenie nic nie zmienia.
Zawiesić można tylko pytanie, które użytkownik już rozwiązywał w tym trybie (tylko takie ma stan); dla pozostałych: `404`.

### `DELETE /api/me/suspended-questions/{questionId}?mode=TEST|FLASHCARD` — przywróć

Odpowiedź `204 No Content`. Idempotentne. Termin powtórki zostaje taki, jaki był (zaległe pytanie pojawi się wśród
najsłabiej zapamiętanych). `404`, gdy użytkownik nie ma stanu tego pytania w tym trybie.

## „Dodaj do fiszek”

Każde pytanie ma fiszkę, ale nie jako osobny rekord: fiszka to **tryb nauki tego samego pytania**. Użytkownik widzi ją
w Fiszkach dopiero po pewnym błędzie w tym pytaniu (pomyłka bez „Nie jestem pewien”) albo po ręcznym dodaniu. Te dwa
endpointy to ręczne dodanie i jego cofnięcie. Ręcznie napisanych fiszek (kart bez pytania) nie ma.

### `POST /api/questions/{questionId}/flashcard` — dodaj

**Odpowiedź `200`**

```json
{ "status": "ADDED_MANUALLY" }
```

Pytanie musi istnieć, nie być ukryte (`hidden`) i mieć widoczną fiszkę (`flashcardHidden = false`). Powtórne dodanie
nic nie psuje. Jeśli użytkownik ma już pewny błąd w tym pytaniu, status to `ADDED_BY_MISTAKE` (nic nie jest zapisywane).

**Błędy:** `404` („Question not found”: brak pytania albo jest ukryte), `400` („Z tego pytania nie ma fiszki.”).

### `DELETE /api/questions/{questionId}/flashcard` — usuń

Cofa ręczne dodanie: fiszka znika z Fiszek, a jej dotychczasowy **harmonogram nauki w trybie fiszek jest kasowany**
(ponowne dodanie zaczyna ją od nowa; stan pytania w testach zostaje nietknięty). Jeśli fiszka jest w Fiszkach z powodu pewnego błędu, **zostaje** (tego nie da się cofnąć).

**Odpowiedź `200`**: `{ "status": "AVAILABLE" }` (albo `ADDED_BY_MISTAKE`, gdy fiszka zostaje). Błąd: `404`.

Możliwe wartości `status`:

| Status | Znaczenie |
|--------|-----------|
| `UNAVAILABLE` | Fiszka pytania jest ukryta przez administratora ([`PUT /api/questions/{id}/flashcard-hidden`](04-pytania-admin.md)) |
| `AVAILABLE` | Fiszki jeszcze nie ma w Fiszkach: można ją dodać |
| `ADDED_MANUALLY` | Użytkownik sam ją dodał i może ją usunąć |
| `ADDED_BY_MISTAKE` | Trafiła przez pewny błąd; nie da się jej wycofać |

## Zgłoszenie błędu w pytaniu

### `POST /api/questions/{questionId}/reports`

```json
{ "reason": "OUTDATED", "comment": "Od 1 stycznia przepis ma nowe brzmienie." }
```

| Pole | Wymagane | Opis |
|------|----------|------|
| `reason` | tak | `WRONG_ANSWER` (zaznaczona jako poprawna odpowiedź jest błędna), `OUTDATED` (przepis się zmienił), `UNCLEAR` (literówka lub niejasne sformułowanie), `OTHER` (inny problem) |
| `comment` | dla `OTHER` | Opis, do **1000 znaków** po obcięciu spacji (pusty = brak komentarza). Dla `OTHER` komentarz jest obowiązkowy: „Opisz krótko, co jest nie tak.” |

**Odpowiedź `200`**

```json
{ "id": 45, "updated": false }
```

`updated: true` oznacza, że użytkownik miał już **otwarte** zgłoszenie tego pytania i zostało ono **uzupełnione**
(zmieniony powód i komentarz) zamiast dodania nowego. Pozwala to uniknąć dublowania zgłoszeń.

**Zasady i błędy**

- Pytanie musi istnieć i nie być ukryte; dla nieistniejącego albo ukrytego: `404` („Nie znaleziono pytania”).
- Limit: **20 nowych zgłoszeń na dobę** na użytkownika (uzupełnienia się nie liczą). Po przekroczeniu `429` z
  nagłówkiem `Retry-After`.
- `400` przy braku powodu albo zbyt długim komentarzu (pole w żądaniu ma twardy limit 4000 znaków, reguła aplikacji
  1000 znaków).
- **Zgłoszenia rozpatruje administrator w panelu** ([10](10-panel-administratora.md#zgłoszenia-błędów), ekran
  `/admin/zgloszenia`). Aplikacja nie pokazuje użytkownikowi statusu jego zgłoszeń.

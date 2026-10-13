# API: zawieszanie, fiszki z pytań, zgłoszenia błędów

[← Przegląd API](README.md) · [Spis treści](../README.md)

Trzy małe funkcje, które pomagają uczącemu się radzić sobie z trudnymi i błędnymi pytaniami. Wszystkie wymagają tokenu.
Tło: [04. Nauka](../04-nauka-testy-fiszki-fsrs.md).

## Trudne i zawieszone pytania

Zawieszenie dotyczy tylko jednego użytkownika i niczego nie kasuje: karta znika z kolejek i liczników powtórek, ale jej
stan (stabilność, termin, historia) zostaje.

### `GET /api/me/difficult-cards`

```json
{
  "leechThreshold": 8,
  "suspended": [
    { "cardId": 101, "front": "Czy czyn zabroniony…", "sourceName": "Egzamin notarialny 2024",
      "lapses": 9, "suspendedAt": "2026-10-10T19:30:00" }
  ],
  "leeches": [
    { "cardId": 140, "front": "Termin przedawnienia…", "sourceName": "Egzamin notarialny 2024",
      "lapses": 8, "suspendedAt": null }
  ]
}
```

| Pole | Znaczenie |
|------|-----------|
| `leechThreshold` | Od ilu błędów pytanie uchodzi za trudne (domyślnie 8) |
| `suspended` | Zawieszone pytania, od ostatnio zawieszonych (maks. 200) |
| `leeches` | Trudne pytania, które **nadal są w nauce** (niezawieszone), od największej liczby błędów (maks. 200) |

### `PUT /api/me/suspended-cards/{cardId}` — zawieś

Odpowiedź `204 No Content`. **Idempotentne**: ponowne zawieszenie nic nie zmienia. Zawiesić można tylko pytanie, na które
użytkownik już odpowiadał; dla pozostałych: `404`.

### `DELETE /api/me/suspended-cards/{cardId}` — przywróć

Odpowiedź `204 No Content`. Idempotentne. Termin powtórki zostaje taki, jaki był (zaległa karta pojawi się wśród
najsłabiej zapamiętanych). `404`, gdy użytkownik nie ma stanu tej karty.

## „Dodaj do fiszek”

Każde pytanie testowe ma w bazie automatyczną fiszkę, którą użytkownik widzi dopiero po pewnym błędzie w tym pytaniu
albo po ręcznym dodaniu. Te dwa endpointy to ręczne dodanie i jego cofnięcie.

### `POST /api/questions/{questionId}/flashcard` — dodaj

**Odpowiedź `200`**

```json
{ "status": "ADDED_MANUALLY" }
```

Pytanie musi istnieć, być testowe, nieukryte, **dostępne dla użytkownika** i mieć widoczną fiszkę. Powtórne dodanie nic
nie psuje. Jeśli użytkownik ma już pewny błąd w tym pytaniu, status to `ADDED_BY_MISTAKE` (nic nie jest zapisywane).

**Błędy:** `404` („Question not found”), `400` („Do tego pytania nie ma fiszki.”).

### `DELETE /api/questions/{questionId}/flashcard` — usuń

Cofa ręczne dodanie: fiszka znika z Fiszek, a jej dotychczasowy **harmonogram nauki jest kasowany** (ponowne dodanie
zaczyna ją od nowa). Jeśli fiszka jest w Fiszkach z powodu pewnego błędu, **zostaje** (tego nie da się cofnąć).

**Odpowiedź `200`**: `{ "status": "AVAILABLE" }` (albo `ADDED_BY_MISTAKE`, gdy fiszka zostaje). Błąd: `404`.

Możliwe wartości `status`:

| Status | Znaczenie |
|--------|-----------|
| `UNAVAILABLE` | Pytanie nie ma widocznej fiszki (np. została ukryta) |
| `AVAILABLE` | Fiszki jeszcze nie ma w Fiszkach: można ją dodać |
| `ADDED_MANUALLY` | Użytkownik sam ją dodał i może ją usunąć |
| `ADDED_BY_MISTAKE` | Trafiła przez pewny błąd; nie da się jej wycofać |

## Zgłoszenie błędu w pytaniu

### `POST /api/cards/{cardId}/reports`

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

- Pytanie musi być **dostępne** dla użytkownika (jak przy odpowiadaniu); dla niedostępnego: `404` („Nie znaleziono
  pytania”).
- Limit: **20 nowych zgłoszeń na dobę** na użytkownika (uzupełnienia się nie liczą). Po przekroczeniu `429` z
  nagłówkiem `Retry-After`.
- `400` przy braku powodu albo zbyt długim komentarzu (pole w żądaniu ma twardy limit 4000 znaków, reguła aplikacji
  1000 znaków).
- **Rozpatrywanie zgłoszeń odbywa się poza aplikacją** (zapytaniem SQL, patrz
  [02](../02-baza-danych.md#przydatne-zapytania-sql-ręczna-administracja)). Aplikacja nie pokazuje użytkownikowi
  statusu jego zgłoszeń.

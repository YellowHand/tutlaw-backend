# API: konta i logowanie (`/api/auth`)

[← Przegląd API](README.md) · [Spis treści](../README.md)

Wszystkie endpointy tej grupy są **publiczne** (bez tokenu). Jak to działa w środku: rejestracja, aktywacja, logowanie,
tokeny i limity prób opisane są w [03. Bezpieczeństwo i konta](../03-bezpieczenstwo-i-konta.md).

## `POST /api/auth/register` — rejestracja

Zakłada konto (nieaktywne) i wysyła link aktywacyjny na adres uczelniany.

**Ciało żądania**

```json
{
  "privateEmail": "jan.kowalski@gmail.com",
  "universityEmail": "jan.kowalski@student.uw.edu.pl",
  "password": "mojeHaslo123",
  "acceptedTerms": true
}
```

| Pole | Wymagane | Reguła |
|------|----------|--------|
| `privateEmail` | tak | Poprawny adres, do 254 znaków. **Służy do logowania** |
| `universityEmail` | tak | Poprawny adres, do 254 znaków. Tu idzie link aktywacyjny i reset hasła |
| `password` | tak | 8–72 znaków (i najwyżej 72 bajty w UTF-8) |
| `acceptedTerms` | tak | Musi być `true` |

**Odpowiedź:** `200 OK`, bez treści. **Zawsze taka sama**, także gdy któryś z adresów ma już konto (wtedy nic się nie
dzieje i mail nie jest wysyłany). Dzięki temu nie da się sprawdzić, kto ma konto.

**Błędy:** `400` (walidacja, brak zgody na regulamin, zbyt słabe hasło), `429` (więcej niż 10 rejestracji na godzinę z
jednego IP).

## `GET /api/auth/verify?token=...` — aktywacja konta

Link z maila otwiera stronę `/verify?token=...` we frontendzie, a ta wywołuje ten endpoint.

| Parametr | Opis |
|----------|------|
| `token` | Token z linku (UUID) |

**Odpowiedź:** `200 OK`, bez treści. Ponowne użycie tego samego linku na już aktywnym koncie też daje `200`.

**Błędy:** `400` — „Nieprawidłowy link aktywacyjny”; `409` — „Link aktywacyjny wygasł. Poproś o nowy.” (ważność 24 godziny)
albo „Link aktywacyjny został już wykorzystany”.

## `POST /api/auth/login` — logowanie

**Ciało żądania**

```json
{ "privateEmail": "jan.kowalski@gmail.com", "password": "mojeHaslo123" }
```

**Odpowiedź `200`**

```json
{ "accessToken": "eyJhbGciOi...(długi podpisany token)" }
```

Token należy wysyłać w nagłówku `Authorization: Bearer <accessToken>`. Ważny 24 godziny.

**Błędy**

| Kod | Komunikat | Kiedy |
|-----|-----------|-------|
| 400 | Nieprawidłowy adres e-mail lub hasło | Zły adres albo hasło (ten sam komunikat dla obu przypadków) |
| 409 | Konto nie zostało jeszcze aktywowane. Kliknij link z maila uczelnianego albo poproś o nowy. | Poprawne dane, ale konto nieaktywne |
| 429 | Zbyt wiele prób. Spróbuj ponownie za ... | 30 prób / 5 min z IP albo 8 nieudanych / 15 min dla pary IP + adres |

## `POST /api/auth/resend-verification` — ponowne wysłanie linku aktywacyjnego

**Ciało żądania**

```json
{ "privateEmail": "jan.kowalski@gmail.com" }
```

**Odpowiedź:** zawsze `200 OK`, bez treści, niezależnie od tego, czy konto istnieje i czy jest już aktywne. Link idzie
tylko na istniejące, nieaktywne konto i nie częściej niż co 2 minuty.

**Błędy:** `400` (pusty lub za długi adres), `429` (więcej niż 10 próśb na godzinę z IP). Nadmiar próśb dla jednego
konta (3 na godzinę) jest cicho pomijany: odpowiedź to nadal `200`.

## `POST /api/auth/forgot-password` — „zapomniałem hasła”

Ciało i zasady jak wyżej (`{ "privateEmail": "..." }`). Link do ustawienia nowego hasła (ważny **1 godzinę**) idzie na
**adres uczelniany** istniejącego, aktywnego konta. Odpowiedź zawsze `200 OK`.

## `POST /api/auth/reset-password` — ustawienie nowego hasła

**Ciało żądania**

```json
{ "token": "Qk3v...43-znakowy-token-z-maila", "newPassword": "noweHaslo456" }
```

| Pole | Reguła |
|------|--------|
| `token` | Wymagane, do 200 znaków. Token z linku w mailu |
| `newPassword` | 8–72 znaków |

**Odpowiedź:** `200 OK`, bez treści. Efekt: nowe hasło, **wylogowanie wszystkich urządzeń**, unieważnienie pozostałych
linków resetu.

**Błędy:** `400` — „Link do zmiany hasła jest nieprawidłowy lub wygasł. Poproś o nowy.” albo problem z hasłem;
`429` — więcej niż 20 prób na godzinę z jednego IP.

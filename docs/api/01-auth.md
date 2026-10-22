# API: konta i logowanie (`/api/auth`)

[← Przegląd API](README.md) · [Spis treści](../README.md)

Wszystkie endpointy tej grupy są **publiczne** (bez tokenu dostępu). Jak to działa w środku: rejestracja, aktywacja, logowanie,
tokeny i limity prób opisane są w [03. Bezpieczeństwo i konta](../03-bezpieczenstwo-i-konta.md).

## Sprawdzenie „czy to człowiek” (Cloudflare Turnstile)

Cztery endpointy, które boty lubią (`register`, `login`, `forgot-password`, `resend-verification`), wymagają nagłówka
`X-Turnstile-Token` z tokenem z widgetu Cloudflare Turnstile na stronie. Serwer sprawdza token w Cloudflare (token jest
jednorazowy: po każdej próbie trzeba pobrać nowy). Pozostałe endpointy (`refresh`, `logout`, `verify`, `reset-password`)
mają własne tokeny i niczego nie wymagają.

| Sytuacja | Odpowiedź |
|----------|-----------|
| `TURNSTILE_SECRET` puste (np. lokalnie) | sprawdzenie wyłączone, nagłówek niepotrzebny |
| brak nagłówka, token odrzucony przez Cloudflare albo dłuższy niż 2048 znaków | `400` — „Potwierdź, że nie jesteś robotem.” albo „Weryfikacja nie powiodła się…” |
| Cloudflare nie odpowiada | `503` — „Nie udało się teraz sprawdzić zabezpieczenia.” (blokujemy, nie przepuszczamy) |

Sprawdzenie działa przed limitem prób i przed samą logiką konta, więc odrzucone żądanie nie dotyka bazy ani nie wysyła maili.

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

Token dostępu należy wysyłać w nagłówku `Authorization: Bearer <accessToken>`. Jest ważny **30 minut**
(`APP_JWT_EXPIRATION_MS`), a frontend trzyma go tylko w pamięci aplikacji.

**Token odświeżający nie jest w treści odpowiedzi.** Serwer ustawia go w ciasteczku (nagłówek `Set-Cookie`):
`gradus_refresh=...; Path=/api/auth; Max-Age=2592000; Secure; HttpOnly; SameSite=Strict`. Jest ważny **30 dni**
(`APP_JWT_REFRESH_DAYS`) i służy tylko do `POST /api/auth/refresh`, dzięki czemu użytkownik zostaje zalogowany bez
ponownego wpisywania hasła. Skrypty na stronie nie mogą go odczytać (`HttpOnly`). Żądania z przeglądarki do tego API muszą
być wysyłane z dołączaniem ciasteczek (`credentials: "include"` / `withCredentials: true`).

**Błędy**

| Kod | Komunikat | Kiedy |
|-----|-----------|-------|
| 400 | Nieprawidłowy adres e-mail lub hasło | Zły adres albo hasło (ten sam komunikat dla obu przypadków) |
| 409 | Konto nie zostało jeszcze aktywowane. Kliknij link z maila uczelnianego albo poproś o nowy. | Poprawne dane, ale konto nieaktywne |
| 429 | Zbyt wiele prób. Spróbuj ponownie za ... | 30 prób / 5 min z IP albo 8 nieudanych / 15 min dla pary IP + adres |

## `POST /api/auth/refresh` — odświeżenie sesji

Wymienia token odświeżający **z ciasteczka** na nowy token dostępu i nowe ciasteczko (rotacja). Endpoint jest publiczny, bo
token dostępu zwykle już wygasł. Frontend wywołuje go przy starcie aplikacji (żeby zdobyć token dostępu po odświeżeniu strony)
oraz gdy dostanie `401` na zwykłe żądanie, i ponawia to żądanie z nowym tokenem.

**Ciało żądania:** brak. Token jest w ciasteczku `gradus_refresh`, które przeglądarka dołącza sama.

**Odpowiedź `200`**

```json
{ "accessToken": "eyJhbGciOi...(nowy token)" }
```

oraz nagłówek `Set-Cookie` z nowym tokenem odświeżającym. Stary token **przestaje działać** (rotacja).

**Błędy**

| Kod | Komunikat | Kiedy |
|-----|-----------|-------|
| 401 | Sesja wygasła. Zaloguj się ponownie. | Brak ciasteczka, token nieznany, wygasły (po 30 dniach), unieważniony (wylogowanie, zmiana lub reset hasła), konto nieaktywne albo token **już zużyty** |
| 403 | Niedozwolone źródło żądania | Nagłówek `Origin` wskazuje stronę inną niż nasz frontend (ochrona przed fałszowaniem żądań z cudzych stron). Brak nagłówka `Origin` jest przepuszczany |
| 429 | Zbyt wiele prób. Spróbuj ponownie za ... | Więcej niż 300 żądań / 5 min z jednego IP (wspólny limit z `logout`) |

Ponowne użycie zużytego tokenu: w ciągu **10 sekund** od wymiany serwer wystawia kolejny token tej samej sesji (dwie
karty przeglądarki odświeżające naraz albo odpowiedź, która zgubiła się w drodze). Później uznaje to za kradzież kopii
tokenu, **unieważnia całą sesję** i zwraca `401`; użytkownik musi się zalogować ponownie.

## `POST /api/auth/logout` — wylogowanie

Unieważnia sesję (całą rodzinę tokenów odświeżających) tokenu z ciasteczka i usuwa ciasteczko (`Set-Cookie` z `Max-Age=0`).
Wylogowuje tylko to urządzenie.

**Ciało żądania:** brak (token jest w ciasteczku).

**Odpowiedź:** zawsze `204 No Content`, także bez ciasteczka lub dla nieznanego tokenu (nie da się w ten sposób sprawdzać,
które tokeny istnieją). Token dostępu działa jeszcze do końca swoich 30 minut, bo jest bezstanowy; frontend usuwa go od razu.

**Błędy:** `403` (obce źródło żądania, jak przy `refresh`), `429` (wspólny limit z `refresh`).

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

**Odpowiedź:** `200 OK`, bez treści. Efekt: nowe hasło, **wylogowanie wszystkich urządzeń** (tokeny dostępu i odświeżające), unieważnienie
pozostałych linków resetu.

**Błędy:** `400` — „Link do zmiany hasła jest nieprawidłowy lub wygasł. Poproś o nowy.” albo problem z hasłem;
`429` — więcej niż 20 prób na godzinę z jednego IP.

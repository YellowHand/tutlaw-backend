# 07. Znane ograniczenia

[← Spis treści](README.md)

Ta strona mówi wprost, czego w backendzie jeszcze nie ma, co zostało odłożone na koniec i co jest niedoskonałe.
Podzielona jest na cztery części:

1. [Zostawione na koniec, przed wdrożeniem na prawdziwy serwer](#1-zostawione-na-koniec-przed-wdrożeniem-na-prawdziwy-serwer)
2. [Niedoskonałości w kodzie, które znalazłem przy pisaniu dokumentacji](#2-niedoskonałości-w-kodzie)
3. [Ograniczenia funkcjonalne i skalowania](#3-ograniczenia-funkcjonalne-i-skalowania)
4. [Czego nie sprawdzono przy pisaniu tej dokumentacji](#4-czego-nie-sprawdzono)

Nic z poniższego nie zostało zmienione w kodzie: ta dokumentacja tylko opisuje stan faktyczny.

---

## 1. Zostawione na koniec, przed wdrożeniem na prawdziwy serwer

To są rzeczy, które celowo odkładamy na sam koniec prac. Lokalnie nie przeszkadzają, ale **nie wolno wystawiać
aplikacji pod publiczny adres, dopóki się ich nie załatwi**.

| # | Co | Stan dziś | Co trzeba zrobić |
|---|----|-----------|------------------|
| 1 | **Plik `.env` w repozytorium** | `.env` jest śledzony przez git (obok `.env.example`), mimo że wpis `.env` jest już w `.gitignore`: ignorowanie nie działa na pliki dodane do repozytorium wcześniej | Usunąć z repozytorium (`git rm --cached .env`), zmienić wszystkie hasła i sekrety, które kiedykolwiek w nim były (bo zostały w historii git) |
| 2 | **Domyślny sekret JWT** | `application.yml` ma wartość domyślną `APP_JWT_SECRET` („lokalny-dev-sekret-…”). Kto ją zna, może wystawiać prawdziwe tokeny | Ustawić długi, losowy sekret w środowisku produkcyjnym i usunąć wartość domyślną |
| 3 | **CORS** | `CorsConfig` ma na stałe wpisany adres `http://localhost:5173`. Ustawienie `app.cors.allowed-origin` (z `FRONTEND_URL`) istnieje w `application.yml`, ale **kod go nie czyta**. Z `FRONTEND_URL` korzysta tylko CORS endpointów Actuatora | Czytać dozwolony adres z konfiguracji i ustawić go na adres produkcyjnego frontendu; sprawdzić na uruchomionym serwerze, czy nagłówki CORS są odsyłane |
| 4 | **Link aktywacyjny w mailu** | `EmailService.sendVerificationEmail` ma wpisany na stałe `http://localhost:5173/verify?token=...`. Ten sam link trafia do logu (poziom INFO). Link resetu hasła i powiadomienie o zmianie hasła używają już `FRONTEND_URL` | Użyć `FRONTEND_URL` także w mailu aktywacyjnym i usunąć link z logu |
| 5 | **`show-sql`** | `spring.jpa.show-sql: true`: każde zapytanie SQL ląduje w logu (m.in. wolniejsze logi, więcej danych w logach) | Wyłączyć na produkcji |
| 6 | **Actuator** | `management.endpoint.health.show-details: always` i `/actuator/**` bez logowania. Spring Boot domyślnie wystawia tylko `/actuator/health`, ale ze szczegółami (stan bazy itp.) widocznymi dla każdego | Zmienić na `when-authorized` albo `never`, ograniczyć dostęp |
| 7 | **Swagger UI i `/v3/api-docs`** | Otwarte bez logowania | Wyłączyć na produkcji (`springdoc.api-docs.enabled=false`, `springdoc.swagger-ui.enabled=false`) albo zabezpieczyć |

Po stronie frontendu odłożone jest też m.in. trzymanie tokenów w `localStorage` (to świadoma decyzja na ten etap:
tokeny w `localStorage` są narażone na kradzież przy ewentualnym ataku XSS; docelowo token odświeżający powinien trafić do
ciasteczka `HttpOnly; Secure; SameSite`, razem z konfiguracją CORS i wdrożeniem).

---

## 2. Niedoskonałości w kodzie

Drobne rzeczy znalezione podczas czytania kodu. Żadna nie psuje działania, ale warto o nich wiedzieć.

| # | Co | Gdzie | Skutek |
|---|----|-------|--------|
| 1 | Tag o istniejącej nazwie daje komunikat „Subject with this slug already exists” (skopiowany z przedmiotów) | `TagService.create` | Mylący komunikat błędu; kod 400 |
| 2 | **Niespójne kody dla duplikatów**: kategoria o zajętym slugu → `409`, przedmiot o zajętym slugu i tag → `400`, duplikat karty → `409` | `CategoryService`, `SubjectService`, `TagService`, `CardService` | Frontend musi obsłużyć oba kody |
| 3 | **Część komunikatów błędów jest po angielsku**: „Card not found”, „Question not found”, „Category not found”, „Subject not found”, „Source not found”, „Parent chapter not found”, „Chapter not found” | `ReviewService`, `SuspensionService`, `FlashcardService`, `SubjectService`, `SourceService`, `ChapterService`, `ChapterProgressService` | Jeśli frontend pokaże `message` wprost, użytkownik zobaczy angielski tekst. Większość pozostałych komunikatów jest po polsku |
| 4 | Token aktywacyjny konta jest w bazie zapisany **wprost** (UUID), a token resetu hasła jako skrót SHA-256 | `email_verification_tokens` vs `password_reset_tokens` | Wyciek bazy ujawniłby działające linki aktywacyjne (reset hasła jest bezpieczniejszy). Wpływ mały, bo link tylko aktywuje konto |
| 5 | Nazwa pola `isRead` w JSON (zamiast `read`) | `ChapterResponse`, `SetProgressRequest` | Działa (frontend jej używa), ale to nietypowa nazwa; zmiana wymagałaby zmiany we frontendzie |
| 6 | `docs/database-schema.sql` jest **nieaktualny** (zapis pierwszego schematu) | `docs/database-schema.sql` | Nie opisuje obecnego schematu; prawdziwy to migracje Flyway. Dokumentacja bazy: [02](02-baza-danych.md) |
| 7 | Plik `README.md` w głównym katalogu był **pusty** | `README.md` | Uzupełniony w Etapie 23 odnośnikami do tej dokumentacji |
| 8 | Wartość `app.cors.allowed-origin` w `application.yml` jest nieużywana (opis w punkcie 1.3) | `application.yml` | Mylące: wygląda na konfigurowalne, a nie jest |

---

## 3. Ograniczenia funkcjonalne i skalowania

Czego aplikacja dziś nie robi albo robi w uproszczony sposób.

### Treści i administracja

- **Nie ma panelu administratora w backendzie.** Role nadaje się w bazie (SQL). API pozwala tylko **dodawać** kategorie,
  przedmioty, źródła, rozdziały, tagi i karty oraz ukrywać karty. Nie ma endpointów do edycji i usuwania treści; poprawki
  robi się w bazie.
- **Nie ma importu z pliku CSV/JSON.** Jest tylko import zbiorczy przez API (`POST /api/cards/bulk`), który przyjmuje
  listę kart w JSON-ie.
- **Zgłoszenia błędów rozpatruje się zapytaniem SQL.** Użytkownik nie widzi statusu swoich zgłoszeń.
- **Mail jest zwykłym tekstem** (bez HTML i bez logo).

### Nauka

- **Parametry FSRS są wspólne dla wszystkich** (domyślne w0–w20). Aplikacja nie dopasowuje ich do historii
  konkretnego użytkownika (brak „optymalizatora” FSRS).
- **Test nigdy nie daje oceny „Łatwe”** (decyzja projektowa: przy trzech opcjach trafienie nie odróżnia się od łatwości).
- **Egzamin próbny nie zapisuje pojedynczych odpowiedzi**, tylko podsumowanie; szczegółowy raport użytkownik dostaje raz,
  tuż po zakończeniu.
- **Egzamin nie odtwarza stałych proporcji działów.** Ma zawsze 150 pytań z całej puli i zaliczenie od 100 punktów, ale
  pytania są losowane bez podziału na działy (proporcje zależą od liczby pytań w bazie). Gdy w bazie jest mniej niż 150
  pytań, egzamin jest krótszy, a próg maleje proporcjonalnie.
- **Serwer nie pamięta, jakie pytania wydał w `start`.** Zaliczenie dotyczy zestawu przesłanego w `submit`, więc ktoś
  mógłby wysłać krótszy zestaw i „zdać” go. To ćwiczenie tylko dla jego właściciela (historia jest prywatna), więc nie
  blokujemy tego.
- **Jedna strefa czasowa dla wszystkich** (`Europe/Warsaw` albo `APP_TIMEZONE`): „dzień” dla limitów dziennych jest taki
  sam dla każdego użytkownika. Daty w bazie nie mają strefy.
- **Zawiesić można tylko kartę, na którą użytkownik już odpowiadał.**
- **„Powtórz moje błędy” analizuje do 20 000 ostatnich odpowiedzi** z 30 dni i bierze do 1000 błędnych pytań.

### Bezpieczeństwo i działanie

- **Limity prób są w pamięci jednej instancji aplikacji.** Po restarcie liczniki się zerują, a przy kilku instancjach
  każda liczyłaby osobno (potrzebny byłby wspólny magazyn, np. Redis). Za serwerem pośredniczącym trzeba ustawić
  `FORWARD_HEADERS_STRATEGY=native`.
- **Rola jest odczytywana z tokenu dostępu**, więc zmiana roli w bazie działa dopiero po następnym odświeżeniu sesji
  (token dostępu żyje 30 minut) albo ponownym zalogowaniu.
- **Token dostępu nie da się cofnąć przed czasem.** Jest bezstanowy i ważny 30 minut; wylogowanie unieważnia tokeny
  odświeżające, a wszystkie sesje kończy zmiana hasła (`tokens_valid_from`). Skradziony token dostępu działa więc do 30
  minut.
- **Kolejka maili jest w pamięci** (2 wątki, 200 miejsc). Gdy poczta stoi, nadmiar maili jest odrzucany z wpisem w logu,
  a maile nie są ponawiane po restarcie.

### Skalowanie

Dziś nie ma problemu, ale przy bardzo dużej bazie (setki tysięcy kart) kilka miejsc trzeba będzie zoptymalizować:

- Budowanie kolejki ładuje do pamięci odchudzone karty zakresu i wszystkie stany nauki użytkownika (bez treści pytań;
  pełne karty ładowane są tylko dla wybranych do sesji).
- Ranking „najsłabszych kart” pobiera podsumowania wszystkich rozpoczętych kart użytkownika i sortuje je w pamięci.
- Listy kategorii, przedmiotów i tagów nie są stronicowane (to małe tabele).

### Testy

- **Nie ma testów z prawdziwą bazą** (np. Testcontainers) ani testów integracyjnych HTTP end-to-end. Zapytania JPQL/SQL i
  wyzwalacze z migracji V18 nie mają automatycznych testów; sprawdza się je ręcznie na działającej bazie.
- **Nie ma automatycznego CI** (np. GitHub Actions).
- Jedyny test z kontekstem Springa (`BackendApplicationTests`) wymaga działającego PostgreSQL.

---

## 4. Czego nie sprawdzono

Uczciwie o weryfikacji tej dokumentacji:

- Dokumentacja powstała z **czytania całego kodu źródłowego i migracji**. Liczby w przykładzie FSRS (stabilność,
  odstępy, szanse przypomnienia, zakresy rozrzutu terminów) zostały **obliczone prawdziwym kodem silnika** (`FsrsEngine`
  i `SchedulingRules` skompilowane samodzielnie).
- **Nie uruchamiałem backendu** (Spring, Hibernate, PostgreSQL) ani testów Mavena: w środowisku, w którym powstawała
  dokumentacja, nie ma pobranych zależności ani bazy. Opisy działania endpointów, zapytań i wyzwalaczy pochodzą z
  kodu, a nie z obserwacji działającego serwera.
- **Przykłady JSON są ilustracyjne**: kształt pól pochodzi z klas DTO, ale wartości są wymyślone.
- **Nie sprawdzałem**, czy konfiguracja CORS z `CorsConfig` jest faktycznie podpinana przez Spring Security (w
  `SecurityConfig` nie ma jawnego wywołania `.cors(...)`). Do potwierdzenia przy konfigurowaniu CORS na serwerze
  produkcyjnym.
- Zgodność opisu endpointów i tabel z kodem sprawdza skrypt `docs/tools/check_docs.py` (patrz poniżej), ale
  sprawdza on tylko to, co da się porównać mechanicznie: listę endpointów, nazwy tabel i kolumn z migracji oraz
  odnośniki między plikami. Opisów słownych nie weryfikuje.

### Sprawdzanie spójności dokumentacji z kodem

```bash
python3 docs/tools/check_docs.py
```

Skrypt porównuje: (1) listę endpointów w kodzie kontrolerów z tabelami w `docs/api`, (2) nazwy tabel i kolumn z migracji
z opisem w `docs/02-baza-danych.md`, (3) odnośniki między plikami Markdown i kotwice nagłówków. Zwraca niezerowy kod
wyjścia, gdy coś się rozjeżdża. Warto go uruchomić po każdej zmianie kodu kontrolerów albo migracji.

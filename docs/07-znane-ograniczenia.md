# 07. Znane ograniczenia

[← Spis treści](README.md)

Ta strona mówi wprost, czego w backendzie jeszcze nie ma, co zostało odłożone na koniec i co jest niedoskonałe.
Podzielona jest na cztery części:

1. [Zostawione na koniec, przed wdrożeniem na prawdziwy serwer](#1-zostawione-na-koniec-przed-wdrożeniem-na-prawdziwy-serwer)
2. [Niedoskonałości w kodzie, które znalazłem przy pisaniu dokumentacji](#2-niedoskonałości-w-kodzie)
3. [Ograniczenia funkcjonalne i skalowania](#3-ograniczenia-funkcjonalne-i-skalowania)
4. [Czego nie sprawdzono przy pisaniu tej dokumentacji](#4-czego-nie-sprawdzono)

Ta strona opisuje stan faktyczny; niczego z poniższego nie naprawiono przy okazji pisania dokumentacji.

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
| 8 | **Nazwy techniczne z dawnej nazwy projektu** | Nazwa aplikacji dla użytkownika to „Gradus iuris” (maile, teksty, dokumentacja). Pod spodem zostały nazwy techniczne: pakiet Javy `pl.tutlaw.backend` (i `groupId` w `pom.xml`), baza, użytkownik i hasło bazy `tutlaw…`, kontener `tutlaw-postgres`, wolumen `tutlaw_postgres_data`, komentarz w pierwszej linii migracji `V1__init_schema.sql` | Zmienić razem z wyborem domeny i przygotowaniem wdrożenia: pakiet po ustaleniu domeny (reguła „domena odwrócona”), bazę przez kopię i przywrócenie (`pg_dump`). **Komentarza w `V1` nie ruszać**: zmiana pliku migracji zmienia jego sumę kontrolną i Flyway odmówi startu na istniejącej bazie |

Po stronie frontendu odłożone jest też m.in. trzymanie tokenów w `localStorage` (to świadoma decyzja na ten etap:
tokeny w `localStorage` są narażone na kradzież przy ewentualnym ataku XSS; docelowo token odświeżający powinien trafić do
ciasteczka `HttpOnly; Secure; SameSite`, razem z konfiguracją CORS i wdrożeniem).

---

## 2. Niedoskonałości w kodzie

Drobne rzeczy znalezione podczas czytania kodu. Żadna nie psuje działania, ale warto o nich wiedzieć.

| # | Co | Gdzie | Skutek |
|---|----|-------|--------|
| 1 | **Część komunikatów błędów jest po angielsku**: „Question not found” (nieistniejące albo ukryte pytanie) | `ReviewService`, `SuspensionService`, `QuestionService`, `FlashcardService` | Jeśli frontend pokaże `message` wprost, użytkownik zobaczy angielski tekst. Pozostałe komunikaty są po polsku |
| 2 | Token aktywacyjny konta jest w bazie zapisany **wprost** (UUID), a token resetu hasła jako skrót SHA-256 | `email_verification_tokens.token` vs `password_reset_tokens.token_hash` | Wyciek bazy ujawniłby działające linki aktywacyjne (reset hasła jest bezpieczniejszy). Wpływ mały, bo link tylko aktywuje konto |
| 3 | Wartość `app.cors.allowed-origin` w `application.yml` jest nieużywana (opis w punkcie 1.3) | `application.yml`, `CorsConfig` | Mylące: wygląda na konfigurowalne, a nie jest |
| 4 | **Komentarz do zgłoszenia ma dwa limity**: żądanie przyjmuje do 4000 znaków (`@Size`), a reguła biznesowa pozwala na 1000 | `QuestionReportRequest`, `ReportRules.COMMENT_MAX_LENGTH` | Dłuższy komentarz przejdzie walidację pola, ale zostanie odrzucony regułą; frontend i baza używają 1000 |
| 5 | `PageResponse` i `PageParams` (stronicowanie) nie są używane przez żaden endpoint | pakiet `common` | Martwy kod z czasów list kart dla administratora; ma własny test |
| 6 | Komunikat konfliktu 409 mówi o „pytaniu”, ale pojawia się przy każdej równoległej zmianie stanu nauki, także w trybie Fiszki | `GlobalExceptionHandler` | Niewielka nieścisłość; treść i tak jest zrozumiała |

---

## 3. Ograniczenia funkcjonalne i skalowania

Czego aplikacja dziś nie robi albo robi w uproszczony sposób.

### Treści i administracja

- **Nie ma panelu administratora w backendzie.** Role nadaje się w bazie (SQL). API pozwala administratorowi tylko
  **ukrywać pytania i fiszki** oraz **ustawiać terminy egzaminów**. Nie ma endpointów do dodawania, edycji i usuwania
  treści.
- **Treść wczytuje się skryptami SQL** (katalog `baza/`, opis w
  [05](05-konfiguracja-i-uruchomienie.md#wczytywanie-treści-skryptami-sql)). Nie ma importu z pliku CSV ani JSON ani
  importu zbiorczego przez API; poprawki pytań robi się w bazie.
- **Pytania z `03-pytania-przykladowe.sql` są przykładowe.** Służą do sprawdzenia aplikacji, nie odpowiadają
  prawdziwym egzaminom i przed publikacją wymagają weryfikacji merytorycznej (stan prawny, sformułowania).
- **Listy aktów ścieżek (`track_acts`) to propozycja startowa.** Trzeba je sprawdzić z aktualnym zakresem każdego
  egzaminu. Do puli aplikacji wchodzą też akty pytań z jej własnych arkuszy, więc nieścisłość listy niczego nie ukrywa.
- **Nie ma ręcznie pisanych fiszek.** Fiszka jest trybem nauki istniejącego pytania (przód to pytanie, tył to poprawna
  odpowiedź z wyjaśnieniem i podstawą prawną). Dawne fiszki o dowolnej treści zniknęły razem ze starym schematem.
- **Pytanie należy do jednego aktu prawnego.** Pytanie dotyczące dwóch ustaw trzeba przypisać do jednej z nich.
- **To samo pytanie w dwóch arkuszach to dwa wiersze** (dwa osobne stany nauki, dwie osobne zmiany w prawie). To świadoma
  decyzja: powtórzenia między arkuszami są rzadkie, a ich scalanie utrudniłoby wczytywanie treści.
- **Przedmioty i zmiany w prawie wpisuje się SQL-em.** Nie ma ekranu administratora do tworzenia przedmiotów,
  przypisywania im aktów ani oznaczania pytań zmienionych w prawie. Data zmiany zaczyna działać najpóźniej następnej nocy
  (00:10) albo po restarcie, chyba że administrator uruchomi ręcznie `UPDATE` z dokumentacji.
- **Płatne i darmowe przedmioty to przyszła funkcja.** Dziś student może wybrać dowolny przedmiot; podział na darmowe i
  płatne (z ograniczeniem wyboru) trzeba będzie dodać razem z płatnościami (schemat `courses` nie ma na to pola).
- **Prawo i postępowanie cywilne nie są rozdzielone** (decyzja projektowa): to jedna dziedzina z kilkoma aktami.
- **Zgłoszenia błędów rozpatruje się zapytaniem SQL.** Użytkownik nie widzi statusu swoich zgłoszeń.
- **Mail jest zwykłym tekstem** (bez HTML i bez logo).

### Nauka

- **Parametry FSRS są wspólne dla wszystkich** (domyślne w0–w20). Aplikacja nie dopasowuje ich do historii
  konkretnego użytkownika (brak „optymalizatora” FSRS).
- **Test nigdy nie daje oceny „Łatwe”** (decyzja projektowa: przy trzech opcjach trafienie nie odróżnia się od łatwości).
- **Egzamin próbny nie zapisuje pojedynczych odpowiedzi**, tylko podsumowanie; szczegółowy raport użytkownik dostaje raz,
  tuż po zakończeniu.
- **Egzamin nie odtwarza stałych proporcji dziedzin.** Ma zawsze 150 pytań z całej puli ścieżki (bez zawężenia do
  aktów) i zaliczenie od 100 punktów, ale pytania są losowane bez podziału na dziedziny (proporcje zależą od liczby
  pytań w bazie). Gdy w puli jest mniej niż 150 pytań, egzamin jest krótszy, a próg maleje proporcjonalnie.
- **Serwer nie pamięta, jakie pytania wydał w `start`.** Zaliczenie dotyczy zestawu przesłanego w `submit`, więc ktoś
  mógłby wysłać krótszy zestaw i „zdać” go. To ćwiczenie tylko dla jego właściciela (historia jest prywatna), więc nie
  blokujemy tego.
- **Jedna strefa czasowa dla wszystkich** (`Europe/Warsaw` albo `APP_TIMEZONE`): „dzień” dla limitów dziennych jest taki
  sam dla każdego użytkownika. Daty w bazie nie mają strefy.
- **Zawiesić można tylko pytanie, na które użytkownik już odpowiadał w danym trybie** (zawieszenie dotyczy stanu nauki
  w tym trybie, więc Testy i Fiszki zawiesza się osobno).
- **Tryby Testy i Fiszki mają niezależne stany FSRS.** Odpowiedź w teście nie przesuwa terminu fiszki tego samego
  pytania i odwrotnie. To decyzja projektowa, ale statystyki bez parametru `mode` liczą takie pytanie dwa razy.
- **Filtr źródeł nie dotyczy fiszek**, a zawężenie puli do aktów nie dotyczy egzaminu próbnego.
- **Zmiana ścieżki czyści zawężenie puli aktami i filtr źródeł** (akty jednej ścieżki nie muszą należeć do drugiej),
  ale **nie rusza postępów** ani wybranych przedmiotów Studenta. Postępy zeruje się osobno (WYZERUJ).
- **Zmiana w prawie nie cofa postępu pytania**, tylko przestawia termin na „teraz”: pytanie wraca raz i po odpowiedzi
  planuje się jak zwykle. Pytanie zawieszone zostaje zawieszone (użytkownik świadomie je wyłączył).
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

Dziś nie ma problemu, ale przy bardzo dużej bazie (setki tysięcy pytań) kilka miejsc trzeba będzie zoptymalizować:

- Budowanie kolejki ładuje do pamięci odchudzone pytania zakresu i wszystkie stany nauki użytkownika w danym trybie
  (bez treści pytań; pełne pytania ładowane są tylko dla wybranych do sesji).
- Ranking „najsłabszych pytań” pobiera podsumowania wszystkich rozpoczętych pytań użytkownika i sortuje je w pamięci.
- Katalogi dziedzin, aktów, ścieżek i przedmiotów nie są stronicowane (to małe tabele).

### Testy

- **Nie ma testów z prawdziwą bazą** (np. Testcontainers) ani testów integracyjnych HTTP end-to-end. Zapytania JPQL i SQL
  nie mają automatycznych testów na działającej bazie. Zastępują je testy statyczne z pakietu `mapping` (zgodność
  encji ze schematem z migracji, składnia i pola zapytań repozytoriów), opisane w
  [01](01-architektura.md#testy).
- **Nie ma automatycznego CI** (np. GitHub Actions).
- Jedyny test z kontekstem Springa (`BackendApplicationTests`) wymaga działającego PostgreSQL.

---

## 4. Czego nie sprawdzono

Uczciwie o weryfikacji tej dokumentacji:

- Dokumentacja powstała z **czytania kodu źródłowego i migracji** (wszystkie endpointy, DTO i serwisy Etapów 32 i 33). Liczby w przykładzie FSRS (stabilność,
  odstępy, szanse przypomnienia, zakresy rozrzutu terminów) zostały **obliczone prawdziwym kodem silnika** (`FsrsEngine`
  i `SchedulingRules` skompilowane samodzielnie).
- **Nie uruchamiałem backendu jako aplikacji Springa** (Spring, Hibernate): w środowisku, w którym powstawała
  przebudowa Etapu 32, nie da się uruchomić kontekstu Springa. Testy jednostkowe Mavena (bez bazy) przechodzą, a
  zgodność encji i zapytań ze schematem sprawdzają testy statyczne z pakietu `mapping`. To **nie jest** to samo co start
  aplikacji: pierwsze uruchomienie na prawdziwej bazie (Flyway, walidacja Hibernate, wiązanie zapytań Spring Data) może
  jeszcze ujawnić błąd, którego te testy nie widzą. Przed pierwszym użyciem uruchom backend i sprawdź
  `/actuator/health`.
- **Migracje `V1`–`V3` oraz skrypty z katalogu `baza/`** zostały wykonane na lokalnym PostgreSQL 16 (świeża baza,
  `ON_ERROR_STOP`). Opisy działania endpointów pochodzą z kodu i testów jednostkowych, a nie z obserwacji
  działającego serwera.
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
z opisem w `docs/02-baza-danych.md`, (3) odnośniki między plikami Markdown i kotwice nagłówków, (4) liczby: parametry FSRS,
domyślne limity z `application.yml`, endpointy i klasy testowe. Zwraca niezerowy kod
wyjścia, gdy coś się rozjeżdża. Warto go uruchomić po każdej zmianie kodu kontrolerów albo migracji.

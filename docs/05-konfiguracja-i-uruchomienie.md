# 05. Konfiguracja i uruchomienie

[← Spis treści](README.md)

## Dwa środowiska: lokalne (dev) i produkcyjne (prod)

Kod jest ten sam, różni się **konfiguracja**. Zasada: plik `application.yml` opisuje środowisko **produkcyjne** i jest
celowo „ostry”, a wygody programisty są w `application-dev.yml` i włączają się tylko jawnie.

|                                                              | Lokalnie (dev)                                                      | Produkcja (prod)                                                    |
|--------------------------------------------------------------|---------------------------------------------------------------------|---------------------------------------------------------------------|
| Jak się włącza                                               | `SPRING_PROFILES_ACTIVE=dev`                                        | bez profilu (nie ustawiasz niczego)                                 |
| Baza                                                         | PostgreSQL z `docker-compose.yml` na `localhost`                    | Amazon RDS (dane w `.env.prod` na serwerze)                         |
| Poczta                                                       | Mailpit z `docker-compose.yml` (podgląd na `http://localhost:8025`) | Amazon SES przez SMTP                                               |
| Sekret JWT, hasła, adresy                                    | wartości domyślne do nauki lokalnej                                 | **brak wartości domyślnych**: bez zmiennej aplikacja nie wystartuje |
| Swagger (`/swagger-ui.html`)                                 | włączony                                                            | wyłączony                                                           |
| Log zapytań SQL (`show-sql`), szczegóły w `/actuator/health` | włączone                                                            | wyłączone                                                           |
| Zewnętrzny dostęp do Actuatora                               | tylko `health`                                                      | tylko `health` (wyłącznie stan UP/DOWN, bez szczegółów)             |

**Bezpiecznik przy starcie** (`ProductionSafetyCheck`): poza profilem `dev` aplikacja odmawia uruchomienia, jeśli sekret
JWT
ma mniej niż 32 znaki albo wygląda na szablon (np. zaczyna się od `lokalny-dev`), jeśli `FRONTEND_URL` nie zaczyna się
od
`https://` albo jeśli hasło bazy jest puste lub jest hasłem z szablonu. Komunikat w logu mówi wprost, co poprawić.
Dzięki temu nie da się przez pomyłkę uruchomić publicznego serwera z sekretem „do nauki”.

Zalecany przepływ pracy: piszesz i testujesz lokalnie (profil `dev`), commitujesz, a na serwer wdrażasz gotowy plik JAR
skryptem `deploy/deploy-backend.sh` (kroki w sekcji „Wdrożenie na prawdziwy serwer” niżej oraz w osobnych instrukcjach
wdrożenia na AWS).

## Czego potrzebujesz (lokalnie)

| Narzędzie                   | Po co                                          |
|-----------------------------|------------------------------------------------|
| Java 17 (JDK)               | Uruchomienie backendu                          |
| Docker (z `docker compose`) | PostgreSQL 16 i Mailpit z `docker-compose.yml` |

Maven nie musi być zainstalowany: projekt ma wrapper (`./mvnw`, na Windows `mvnw.cmd`).

## Szybki start (lokalnie)

1. **Zmienne środowiskowe.** W głównym katalogu projektu skopiuj `.env.example` do `../.env.stary` (plik `../.env.stary`
   jest w
   `.gitignore` i nie trafia do repozytorium). Domyślne wartości z szablonu działają lokalnie bez zmian. Czyta go
   `docker compose` (dane bazy) i aplikacja (biblioteka `spring-dotenv`).
2. **Baza danych i poczta:**

   ```bash
   docker compose up -d
   ```

   Uruchamia kontener `gradusiuris-postgres` (PostgreSQL 16, port 5432 tylko na `127.0.0.1`, dane w wolumenie
   `gradusiuris_postgres_data`) oraz `gradusiuris-mailpit` (SMTP na 1025, podgląd maili w przeglądarce na
   `http://localhost:8025`).
3. **Backend z profilem `dev`:**

   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
   ```

   W IntelliJ: *Run > Edit Configurations > Active profiles: `dev`* (albo zmienna środowiskowa
   `SPRING_PROFILES_ACTIVE=dev`
   ustawiona w konfiguracji uruchomieniowej; sam plik `../.env.stary` do włączenia profilu nie wystarcza). Przy
   pierwszym starcie Flyway sam wykona migracje `V1` (schemat) i `V2` (dane słownikowe:
   dziedziny prawa, ścieżki, terminy egzaminów, źródło „Pytania autorskie”). Backend nasłuchuje na
   `http://localhost:8080`.
   Bez profilu `dev` aplikacja potraktuje start jako produkcyjny i odmówi go z komunikatem o brakujących zmiennych.
4. **Treść** (akty prawne i pytania) wczytuje się skryptami SQL albo przez panel administratora, opis w
   [Wczytywanie treści skryptami SQL](#wczytywanie-treści-skryptami-sql). Bez tego aplikacja działa, ale nie ma jeszcze
   czego ćwiczyć.
5. **Sprawdzenie:** `http://localhost:8080/actuator/health` powinno pokazać `"status":"UP"`, a pod
   `http://localhost:8080/swagger-ui.html` jest dokumentacja Swagger (tylko w `dev`).
6. **Frontend** (osobne repozytorium) łączy się z adresem z `VITE_API_BASE_URL` (lokalnie `http://localhost:8080`,
   ustawione
   w jego pliku `.env.development`). Mail z linkiem aktywacyjnym po rejestracji zobaczysz w Mailpit.

## Zmienne środowiskowe

Kolumna „Prod” mówi, czy zmienna jest **wymagana** na produkcji (bez niej aplikacja nie wystartuje). W profilu `dev`
każda ma wartość domyślną z `application-dev.yml`. Wzory plików: `.env.example` (lokalnie) i `deploy/.env.prod.example`
(serwer).

| Zmienna                                               | Do czego                                                                                                                                           | Prod                            | Domyślnie w `dev`                              |
|-------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------|------------------------------------------------|
| `SPRING_PROFILES_ACTIVE`                              | Profil. Lokalnie `dev`, na produkcji **nie ustawiaj**                                                                                              | nie                             | —                                              |
| `SPRING_DATASOURCE_URL`                               | Adres JDBC bazy (na RDS z `?sslmode=require`)                                                                                                      | wymagana                        | `jdbc:postgresql://localhost:5432/gradusiuris` |
| `SPRING_DATASOURCE_USERNAME`                          | Użytkownik bazy                                                                                                                                    | wymagana                        | `gradusiuris_user`                             |
| `SPRING_DATASOURCE_PASSWORD`                          | Hasło bazy                                                                                                                                         | wymagana                        | `gradusiuris_password`                         |
| `APP_JWT_SECRET`                                      | Sekret do podpisywania tokenów JWT (min. 32 znaki, losowy: `openssl rand -base64 48`). Zmiana wylogowuje wszystkich                                | wymagana                        | sekret deweloperski                            |
| `FRONTEND_URL`                                        | Adres strony. Z niego powstają linki w mailach (aktywacja konta, zmiana hasła) i domyślna lista CORS. Na produkcji musi zaczynać się od `https://` | wymagana                        | `http://localhost:5173`                        |
| `CORS_ALLOWED_ORIGINS`                                | Lista dozwolonych adresów (po przecinku), np. razem z `www`. Bez niej dozwolony jest tylko `FRONTEND_URL`                                          | nie                             | to samo co `FRONTEND_URL`                      |
| `MAIL_HOST`                                           | Serwer SMTP (SES: `email-smtp.eu-central-1.amazonaws.com`)                                                                                         | wymagana                        | `localhost`                                    |
| `MAIL_PORT`                                           | Port SMTP                                                                                                                                          | nie (587)                       | `1025`                                         |
| `MAIL_USERNAME` / `MAIL_PASSWORD`                     | Dane SMTP (w SES: „SMTP credentials”, nie klucze dostępu AWS)                                                                                      | wymagane przy SMTP z logowaniem | puste                                          |
| `MAIL_SMTP_AUTH`                                      | Czy SMTP wymaga logowania                                                                                                                          | nie (`true`)                    | `false`                                        |
| `MAIL_STARTTLS`                                       | Czy włączyć STARTTLS                                                                                                                               | nie (`true`)                    | `false`                                        |
| `MAIL_FROM`                                           | Nadawca maili, adres w domenie zweryfikowanej w SES, np. `Gradus iuris <no-reply@gradusiuris.pl>`                                                  | wymagana                        | adres lokalny                                  |
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | Baza tworzona przez lokalny `docker-compose.yml`. Muszą zgadzać się z `SPRING_DATASOURCE_*`                                                        | nie dotyczy                     | —                                              |

### Dodatkowe (opcjonalne)

| Zmienna                    | Do czego                                                                                                                                                     | Domyślnie                  |
|----------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------|
| `APP_JWT_EXPIRATION_MS`    | Ważność tokenu dostępu JWT w milisekundach                                                                                                                   | `1800000` (30 minut)       |
| `APP_JWT_REFRESH_DAYS`     | Ważność tokenu odświeżającego w dniach                                                                                                                       | `30`                       |
| `DB_POOL_SIZE`             | Maksymalna liczba połączeń z bazą                                                                                                                            | `10`                       |
| `PORT`                     | Port backendu                                                                                                                                                | `8080`                     |
| `APP_TIMEZONE`             | Strefa czasowa „doby” aplikacji                                                                                                                              | `Europe/Warsaw`            |
| `FORWARD_HEADERS_STRATEGY` | `native` za serwerem pośredniczącym (Caddy, nginx, balanser): limiter prób logowania widzi wtedy prawdziwy adres klienta, a nie adres proxy                  | `native` (w `dev`: `none`) |
| `SENTRY_DSN`               | Adres projektu w Sentry (raportowanie nieoczekiwanych błędów). Puste = wyłączone, nic nie jest wysyłane. Tylko błędy 500 i wpisy ERROR, bez danych osobowych | puste                      |
| `SENTRY_ENVIRONMENT`       | Nazwa środowiska w Sentry                                                                                                                                    | `production`               |

## Ustawienia aplikacji w plikach `application*.yml`

Poza zmiennymi środowiskowymi plik `src/main/resources/application.yml` zawiera ustawienia, które zmienia się w pliku:

| Klucz                                       | Wartość                           | Znaczenie                                                                                                         |
|---------------------------------------------|-----------------------------------|-------------------------------------------------------------------------------------------------------------------|
| `spring.jpa.hibernate.ddl-auto`             | `validate`                        | Hibernate tylko sprawdza zgodność ze schematem, nie tworzy tabel                                                  |
| `spring.jpa.show-sql`                       | `false` (w `dev`: `true`)         | Wypisywanie zapytań SQL w logu                                                                                    |
| `spring.flyway.enabled` / `locations`       | `true` / `classpath:db/migration` | Migracje przy starcie                                                                                             |
| `spring.flyway.clean-disabled`              | `true`                            | Flyway nigdy nie wyczyści bazy                                                                                    |
| `spring.mail.properties.mail.smtp.*timeout` | `10000`                           | Wolny serwer poczty nie zawiesza wątków                                                                           |
| `server.shutdown`                           | `graceful`                        | Kończenie rozpoczętych żądań przy zatrzymaniu (do 20 s)                                                           |
| `app.fsrs.request-retention`                | `0.9`                             | Domyślna docelowa retencja (90%)                                                                                  |
| `app.fsrs.new-flashcards-per-day`           | `20`                              | Domyślny dzienny limit nowych fiszek                                                                              |
| `app.fsrs.new-test-questions-per-day`       | `40`                              | Domyślny dzienny limit nowych pytań testowych (i dolna granica limitu automatycznego)                             |
| `app.fsrs.max-reviews-per-day`              | `100`                             | Domyślny dzienny limit zaległych powtórek (osobno dla fiszek i testów)                                            |
| `app.review.leech-threshold`                | `8`                               | Od ilu błędów pytanie uchodzi za „trudne”                                                                         |
| `app.questions.recent-change-days`          | `365`                             | Do ilu dni po wejściu w życie zmiana w prawie uchodzi za „świeżą” (kategoria „Zmiany w prawie” we własnym teście) |
| `management.health.mail.enabled`            | `false`                           | `/actuator/health` nie sprawdza serwera poczty (chwilowy kłopot z pocztą nie oznacza „aplikacja nie działa”)      |
| `management.endpoint.health.show-details`   | `never` (w `dev`: `always`)       | Szczegóły w `/actuator/health`                                                                                    |
| `springdoc.*.enabled`                       | `false` (w `dev`: `true`)         | Swagger                                                                                                           |

Każdy użytkownik może nadpisać limity i retencję we własnych ustawieniach ([API: ustawienia](api/09-ustawienia.md));
wartości z `application.yml` są domyślne dla tych, którzy niczego nie
zmienili.

## Baza danych i migracje

- Schemat tworzą wyłącznie migracje (`src/main/resources/db/migration`): `V1__init_schema.sql` (cały schemat),
  `V2__reference_data.sql` (dane słownikowe), `V3__law_changes_and_user_courses.sql` (zmiany w prawie, przedmioty
  Studenta), `V4` (rozdziały aktów), `V5__question_marks_and_exam_answers.sql` (oznaczenia pytań, odpowiedzi z
  egzaminów)
  i `V6__hide_acts_and_sheets.sql` (ukrywanie aktów i arkuszy). Opis tabel: [02](02-baza-danych.md).
- Dawne migracje `V1`–`V25` zostały zastąpione nowym stanem wyjściowym, więc **bazę ze starego modelu trzeba założyć od
  nowa** (konta użytkowników też). Nie ma migracji przenoszącej dane.
- Nowa zmiana schematu = nowy plik `V7__krotki_opis.sql`. Nie edytuj wykonanych migracji.
- **Kopia zapasowa bazy (lokalnie):**
  `docker exec gradusiuris-postgres pg_dump -U <POSTGRES_USER> <POSTGRES_DB> > kopia.sql`.
  Na produkcji kopie robi RDS (automatyczne kopie dobowe z odtwarzaniem do wybranej chwili, patrz instrukcje wdrożenia).
- **Reset postępów nauki** (nieodwracalne): `docs/reset-study-progress.sql`.
- **Pusta baza od nowa** (kasuje wszystko, także treść): `docker compose down -v` usuwa kontener i wolumen z danymi;
  po kolejnym `docker compose up -d` Flyway utworzy schemat od zera.

## Wczytywanie treści skryptami SQL

Akty prawne, rozdziały, listy aktów aplikacji, przedmioty Studenta i arkusze można od Etapu 35 prowadzić w **panelu
administratora** ([API](api/10-panel-administratora.md); ekran pod `/admin` we frontendzie), a od Etapu 36 także
**pytania**:
dodawanie, edycję, **import z plików CSV i JSON** (z podglądem przed zapisem), kolejkę oznaczeń i zgłoszenia błędów.
Skrypty SQL
działają dalej i są przydatne do wczytania całego katalogu na świeżej bazie albo do wsadu bez przeglądarki. Skrypty są w
katalogu `baza/` (poza repozytorium kodu, razem z plikiem
`INSTRUKCJA-BAZA.txt`; każdy skrypt jest też jako `.txt` do wklejenia w okno pgAdmin lub DBeaver).

| Plik                                  | Do czego                                                                                                                                                                                                 |
|---------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `01-wyczysc-baze.sql`                 | Czyści treść i historię nauki, zostawia konta i dane słownikowe (do ponownego zaczęcia od zera)                                                                                                          |
| `02-katalog-aktow-i-sciezek.sql`      | Akty prawne i zagadnienia w 11 dziedzinach, listy aktów pięciu ścieżek egzaminacyjnych, przedmioty ścieżki Student                                                                                       |
| `06-przedmioty-i-zmiany-w-prawie.sql` | Przykłady do wykonania ręcznie: nowy przedmiot z aktami, odpięcie aktu, zmiana w prawie przy pytaniu i natychmiastowy zwrot pytań do nauki                                                               |
| `03-pytania-przykladowe.sql`          | Arkusze i pytania **przykładowe** do testów aplikacji (wymagają weryfikacji merytorycznej przed publikacją)                                                                                              |
| `04-import-szablon.sql`               | Szablon do wczytywania własnych pytań (funkcje `pg_temp.pytanie`, `pg_temp.arkusz`, `pg_temp.zrodlo`)                                                                                                    |
| `05-kontrola.sql`                     | Raport tylko do odczytu: pytania według dziedzin, aktów i arkuszy oraz pula każdej ścieżki                                                                                                               |
| `09-oznaczenia.sql`                   | Oznaczenia pytań („Pytania z terminów”, „Pułapki”): zatwierdzanie i odrzucanie propozycji automatu oraz ręczne oznaczanie, jak panel administratora, ale zapytaniami SQL (domyślnie ROLLBACK z raportem) |

Pierwsze uruchomienie na świeżej bazie (kolejność ma znaczenie):

```bash
docker cp 02-katalog-aktow-i-sciezek.sql gradusiuris-postgres:/tmp/
docker cp 03-pytania-przykladowe.sql gradusiuris-postgres:/tmp/
docker exec gradusiuris-postgres psql -U gradusiuris_user -d gradusiuris -v ON_ERROR_STOP=1 -f /tmp/02-katalog-aktow-i-sciezek.sql
docker exec gradusiuris-postgres psql -U gradusiuris_user -d gradusiuris -v ON_ERROR_STOP=1 -f /tmp/03-pytania-przykladowe.sql
```

Każdy skrypt działa w jednej transakcji i sam sprawdza, czy ma co robić; przy problemie zatrzymuje się z komunikatem i
niczego nie zmienia. Szablon `04` domyślnie kończy się `ROLLBACK` i drukuje raport, więc nic nie zostaje zapisane,
dopóki nie zmienisz go na `COMMIT`. Przedmioty Studenta i zmiany w prawie to też zwykłe polecenia SQL (`06`; opis
w [02](02-baza-danych.md#przydatne-zapytania-sql-ręczna-administracja)).
Ukrycie pytania lub samej fiszki i zmiana terminu egzaminu to pojedyncze polecenia SQL (przykłady
w [API: pytania, administracja](api/04-pytania-admin.md)). Struktura tabel i przydatne zapytania:
[02](02-baza-danych.md#przydatne-zapytania-sql-ręczna-administracja).

## Testy

```bash
./mvnw test
```

Prawie wszystkie testy to testy jednostkowe działające **bez bazy i bez Dockera**. Wyjątek: `BackendApplicationTests`
uruchamia cały kontekst Springa (z profilem `dev`), łączy się z bazą i wykonuje migracje, więc przy `./mvnw test` baza
musi
działać (`docker compose up -d`). Bez bazy pomiń ten test: `./mvnw test "-Dtest=!BackendApplicationTests"`.
Szczegóły: [01. Architektura](01-architektura.md#testy).

## Budowanie paczki

```bash
./mvnw clean package
```

(bez bazy: dodaj `"-Dtest=!BackendApplicationTests"`). Powstaje `target/backend-0.0.1-SNAPSHOT.jar`. Uruchomienie takiej
paczki bez profilu `dev` wymaga wszystkich zmiennych
„wymaganych na produkcji” z tabeli wyżej (inaczej aplikacja odmówi startu); lokalnie dodaj
`--spring.profiles.active=dev`.

## Wdrożenie na prawdziwy serwer

Katalog `deploy/` zawiera wszystko, czego potrzebuje serwer produkcyjny (maszyna EC2 z Dockerem, baza RDS):

| Plik                              | Do czego                                                                                                                 |
|-----------------------------------|--------------------------------------------------------------------------------------------------------------------------|
| `Dockerfile` (w głównym katalogu) | Obraz uruchomieniowy (Java 17, użytkownik bez uprawnień roota); zawiera gotowy `app.jar`                                 |
| `deploy/docker-compose.prod.yml`  | Backend i Caddy (HTTPS z automatycznym certyfikatem Let's Encrypt)                                                       |
| `deploy/Caddyfile`                | Adres `api.gradusiuris.pl` i przekazywanie ruchu do backendu                                                             |
| `deploy/.env.prod.example`        | Szablon sekretów produkcyjnych (na serwerze jako `/opt/gradusiuris/.env.prod`, uprawnienia 600)                          |
| `deploy/server-setup.sh`          | Jednorazowe przygotowanie serwera (Docker, swap, katalog aplikacji)                                                      |
| `deploy/deploy-backend.sh`        | Wdrożenie jednym poleceniem: budowa JAR, wysyłka wydania, start, czekanie na „healthy”, automatyczny powrót przy błędzie |
| `deploy/rollback.sh`              | Ręczne cofnięcie do poprzedniego wydania                                                                                 |

Wdrożenie: `SSH_TARGET=ubuntu@api.gradusiuris.pl SSH_KEY=~/.ssh/gradusiuris.pem ./deploy/deploy-backend.sh`.
Migracje bazy wykonują się same przy starcie aplikacji. Cofnięcie kodu **nie** cofa migracji, dlatego migracje mają być
wstecznie zgodne (dokładanie kolumn i tabel, bez usuwania w tym samym wydaniu).

Rzeczy, które były „zostawione na koniec” (sekret JWT, CORS, link aktywacyjny, `show-sql`, Swagger, Actuator, plik
`../.env.stary`
w repozytorium), są od Etapu 37 załatwione konfiguracją i bezpiecznikiem przy starcie. Co jeszcze zostaje:
[07. Znane ograniczenia](07-znane-ograniczenia.md).

# 05. Konfiguracja i uruchomienie

[← Spis treści](README.md)

## Czego potrzebujesz

| Narzędzie | Po co |
|-----------|-------|
| Java 17 (JDK) | Uruchomienie backendu |
| Docker (z `docker compose`) | Baza PostgreSQL 16 z `docker-compose.yml` |
| Serwer poczty do testów, np. Mailpit | Odbiór maili z linkami aktywacyjnymi lokalnie (opcjonalny, ale bez niego nie dostaniesz linku w skrzynce) |

Maven nie musi być zainstalowany: projekt ma wrapper (`./mvnw`, na Windows `mvnw.cmd`).

## Szybki start (lokalnie)

1. **Zmienne środowiskowe.** W głównym katalogu projektu jest plik `.env` (wzór: `.env.example`). Uzupełnij wartości
   (lista niżej). Czyta go zarówno `docker compose`, jak i aplikacja (biblioteka `spring-dotenv`).
2. **Baza danych:**

   ```bash
   docker compose up -d
   ```

   Uruchamia kontener `tutlaw-postgres` (PostgreSQL 16, port 5432, dane w wolumenie `tutlaw_postgres_data`).
3. **Poczta (opcjonalnie):** domyślnie backend wysyła maile na `localhost:1025`. Wystarczy lokalny serwer SMTP do
   testów, np. [Mailpit](https://mailpit.axllent.org/) (SMTP na 1025, podgląd maili w przeglądarce na 8025):

   ```bash
   docker run -d --name mailpit -p 1025:1025 -p 8025:8025 axllent/mailpit
   ```

   W logu backendu przy rejestracji pojawia się też linia z linkiem aktywacyjnym.
4. **Backend:**

   ```bash
   ./mvnw spring-boot:run
   ```

   Przy pierwszym starcie Flyway sam wykona migracje `V1`–`V25`. Backend nasłuchuje na `http://localhost:8080`.
5. **Sprawdzenie:** `http://localhost:8080/actuator/health` powinno pokazać `"status":"UP"`, a pod
   `http://localhost:8080/swagger-ui.html` jest dokumentacja Swagger.
6. **Frontend** (osobne repozytorium) łączy się z adresem z `VITE_API_BASE_URL` (lokalnie `http://localhost:8080`).

## Zmienne środowiskowe

Wartości domyślne w pliku `application.yml` pozwalają uruchomić projekt lokalnie bez żadnego `.env`, ale baza z
`docker-compose.yml` wymaga `POSTGRES_*`.

### Z pliku `.env.example`

| Zmienna | Do czego | Domyślnie w `application.yml` |
|---------|----------|-------------------------------|
| `POSTGRES_DB` | Nazwa bazy tworzonej przez `docker-compose.yml` | — |
| `POSTGRES_USER` | Użytkownik bazy w kontenerze | — |
| `POSTGRES_PASSWORD` | Hasło bazy w kontenerze | — |
| `SPRING_DATASOURCE_URL` | Adres JDBC bazy używany przez backend | `jdbc:postgresql://localhost:5432/tutlaw` |
| `SPRING_DATASOURCE_USERNAME` | Użytkownik bazy | `tutlaw_user` |
| `SPRING_DATASOURCE_PASSWORD` | Hasło bazy | `tutlaw_password` |
| `APP_JWT_SECRET` | Sekret do podpisywania tokenów JWT | lokalny sekret deweloperski (**do zmiany na produkcji**) |
| `MAIL_HOST` | Serwer SMTP | `localhost` |
| `MAIL_PORT` | Port SMTP | `1025` |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | Dane logowania do SMTP | puste |
| `FRONTEND_URL` | Adres frontendu (używany w linkach z maili o zmianie hasła i w konfiguracji CORS) | `http://localhost:5173` |

`POSTGRES_*` muszą zgadzać się z `SPRING_DATASOURCE_*`: baza tworzona w kontenerze ma mieć te same dane, z którymi
łączy się backend (w adresie JDBC nazwa bazy to `POSTGRES_DB`).

### Dodatkowe (opcjonalne)

| Zmienna | Do czego | Domyślnie |
|---------|----------|-----------|
| `APP_JWT_EXPIRATION_MS` | Ważność tokenu dostępu JWT w milisekundach | `1800000` (30 minut) |
| `APP_JWT_REFRESH_DAYS` | Ważność tokenu odświeżającego w dniach | `30` |
| `MAIL_SMTP_AUTH` | Czy SMTP wymaga logowania | `false` |
| `MAIL_STARTTLS` | Czy włączyć STARTTLS | `false` |
| `PORT` | Port backendu | `8080` |
| `APP_TIMEZONE` | Strefa czasowa „doby” aplikacji | `Europe/Warsaw` |
| `FORWARD_HEADERS_STRATEGY` | Ustaw na `native` za serwerem pośredniczącym (nginx, hosting), inaczej limiter prób widzi adres serwera pośredniczącego zamiast klienta i wszyscy mają jeden wspólny limit | `none` |

## Ustawienia aplikacji w `application.yml`

Poza zmiennymi środowiskowymi plik `src/main/resources/application.yml` zawiera ustawienia, które zmienia się w pliku:

| Klucz | Wartość | Znaczenie |
|-------|---------|-----------|
| `spring.jpa.hibernate.ddl-auto` | `validate` | Hibernate tylko sprawdza zgodność ze schematem, nie tworzy tabel |
| `spring.jpa.show-sql` | `true` | Wypisuje zapytania SQL w logu (do wyłączenia przed produkcją) |
| `spring.flyway.enabled` / `locations` | `true` / `classpath:db/migration` | Migracje przy starcie |
| `app.fsrs.request-retention` | `0.9` | Domyślna docelowa retencja (90%) |
| `app.fsrs.new-cards-per-day` | `20` | Domyślny dzienny limit nowych fiszek |
| `app.fsrs.new-test-questions-per-day` | `40` | Domyślny dzienny limit nowych pytań testowych (i dolna granica limitu automatycznego) |
| `app.fsrs.max-reviews-per-day` | `100` | Domyślny dzienny limit zaległych powtórek (osobno dla fiszek i testów) |
| `app.review.leech-threshold` | `8` | Od ilu błędów pytanie uchodzi za „trudne” |
| `management.endpoint.health.show-details` | `always` | Pełne szczegóły w `/actuator/health` |

Każdy użytkownik może nadpisać limity i retencję we własnych ustawieniach
([API: ustawienia](api/09-ustawienia.md)); wartości z `application.yml` są domyślne dla tych, którzy niczego nie
zmienili.

## Baza danych i migracje

- Schemat tworzą wyłącznie migracje (`src/main/resources/db/migration`). Opis tabel: [02](02-baza-danych.md).
- Nowa zmiana schematu = nowy plik `V26__krotki_opis.sql`. Nie edytuj wykonanych migracji.
- **Kopia zapasowa bazy:** `docker exec tutlaw-postgres pg_dump -U <POSTGRES_USER> <POSTGRES_DB> > kopia.sql`.
- **Reset postępów nauki** (nieodwracalne): `docs/reset-study-progress.sql`.
- **Pusta baza od nowa** (kasuje wszystko, także treść): `docker compose down -v` usuwa kontener i wolumen z danymi;
  po kolejnym `docker compose up -d` Flyway utworzy schemat od zera.

## Pierwsze konto administratora

1. Zarejestruj się normalnie w aplikacji i aktywuj konto linkiem z maila (lub z logu backendu).
2. Nadaj rolę w bazie:

   ```sql
   UPDATE users SET role = 'ADMIN' WHERE private_email = 'ty@example.com';
   ```

3. Wyloguj się i zaloguj ponownie (rola jest zapisana w tokenie).

Administrator dodaje treści przez API (kategorie, przedmioty, źródła, rozdziały, tagi, karty, import zbiorczy; patrz
[API: karty](api/04-karty-admin.md)).

## Testy

```bash
./mvnw test
```

Prawie wszystkie testy to testy jednostkowe działające **bez bazy i bez Dockera**. Wyjątek: `BackendApplicationTests`
uruchamia cały kontekst Springa, łączy się z bazą i wykonuje migracje, więc przy `./mvnw test` baza musi działać
(`docker compose up -d`). Szczegóły: [01. Architektura](01-architektura.md#testy).

## Budowanie paczki

```bash
./mvnw clean package
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

Zmienne środowiskowe (przede wszystkim `SPRING_DATASOURCE_*`, `APP_JWT_SECRET`, `MAIL_*`, `FRONTEND_URL`) podaje się
wtedy środowisku, w którym działa serwer.

## Wdrożenie na prawdziwy serwer

Zanim aplikacja trafi pod publiczny adres, trzeba przejść listę rzeczy zostawionych celowo na koniec (sekret JWT, CORS,
link aktywacyjny, `show-sql`, Swagger i Actuator, plik `.env` w repozytorium). Pełny opis:
[07. Znane ograniczenia](07-znane-ograniczenia.md).

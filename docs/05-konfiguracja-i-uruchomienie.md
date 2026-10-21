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

   Przy pierwszym starcie Flyway sam wykona migracje `V1` (schemat) i `V2` (dane słownikowe: dziedziny prawa, ścieżki,
   terminy egzaminów, źródło „Pytania autorskie”). Backend nasłuchuje na `http://localhost:8080`.
5. **Treść** (akty prawne i pytania) wczytuje się skryptami SQL, opis w
   [Wczytywanie treści skryptami SQL](#wczytywanie-treści-skryptami-sql). Bez tego aplikacja działa, ale nie ma jeszcze
   czego ćwiczyć.
6. **Sprawdzenie:** `http://localhost:8080/actuator/health` powinno pokazać `"status":"UP"`, a pod
   `http://localhost:8080/swagger-ui.html` jest dokumentacja Swagger.
7. **Frontend** (osobne repozytorium) łączy się z adresem z `VITE_API_BASE_URL` (lokalnie `http://localhost:8080`).

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
| `app.fsrs.new-flashcards-per-day` | `20` | Domyślny dzienny limit nowych fiszek |
| `app.fsrs.new-test-questions-per-day` | `40` | Domyślny dzienny limit nowych pytań testowych (i dolna granica limitu automatycznego) |
| `app.fsrs.max-reviews-per-day` | `100` | Domyślny dzienny limit zaległych powtórek (osobno dla fiszek i testów) |
| `app.review.leech-threshold` | `8` | Od ilu błędów pytanie uchodzi za „trudne” |
| `app.questions.recent-change-days` | `365` | Do ilu dni po wejściu w życie zmiana w prawie uchodzi za „świeżą” (kategoria „Zmiany w prawie” we własnym teście) |
| `management.endpoint.health.show-details` | `always` | Pełne szczegóły w `/actuator/health` |

Każdy użytkownik może nadpisać limity i retencję we własnych ustawieniach
([API: ustawienia](api/09-ustawienia.md)); wartości z `application.yml` są domyślne dla tych, którzy niczego nie
zmienili.

## Baza danych i migracje

- Schemat tworzą wyłącznie migracje (`src/main/resources/db/migration`): `V1__init_schema.sql` (cały schemat),
  `V2__reference_data.sql` (dane słownikowe), `V3__law_changes_and_user_courses.sql` (zmiany w prawie, przedmioty
  Studenta), `V4` (rozdziały aktów), `V5__question_marks_and_exam_answers.sql` (oznaczenia pytań, odpowiedzi z egzaminów)
  i `V6__hide_acts_and_sheets.sql` (ukrywanie aktów i arkuszy). Opis tabel: [02](02-baza-danych.md).
- Dawne migracje `V1`–`V25` zostały zastąpione nowym stanem wyjściowym, więc **bazę ze starego modelu trzeba założyć od
  nowa** (konta użytkowników też). Nie ma migracji przenoszącej dane.
- Nowa zmiana schematu = nowy plik `V7__krotki_opis.sql`. Nie edytuj wykonanych migracji.
- **Kopia zapasowa bazy:** `docker exec tutlaw-postgres pg_dump -U <POSTGRES_USER> <POSTGRES_DB> > kopia.sql`.
- **Reset postępów nauki** (nieodwracalne): `docs/reset-study-progress.sql`.
- **Pusta baza od nowa** (kasuje wszystko, także treść): `docker compose down -v` usuwa kontener i wolumen z danymi;
  po kolejnym `docker compose up -d` Flyway utworzy schemat od zera.

## Wczytywanie treści skryptami SQL

Akty prawne, rozdziały, listy aktów aplikacji, przedmioty Studenta i arkusze można od Etapu 35 prowadzić w **panelu
administratora** ([API](api/10-panel-administratora.md); ekran pod `/admin` we frontendzie). Nie ma jeszcze edycji ani importu
**pytań** z pliku CSV lub JSON: pytania wczytuje się **skryptami SQL**; skrypty wczytują też akty, listy i arkusze hurtem. Skrypty są w katalogu `baza/` (poza repozytorium kodu, razem z plikiem
`INSTRUKCJA-BAZA.txt`; każdy skrypt jest też jako `.txt` do wklejenia w okno pgAdmin lub DBeaver).

| Plik | Do czego |
|------|----------|
| `01-wyczysc-baze.sql` | Czyści treść i historię nauki, zostawia konta i dane słownikowe (do ponownego zaczęcia od zera) |
| `02-katalog-aktow-i-sciezek.sql` | Akty prawne i zagadnienia w 11 dziedzinach, listy aktów pięciu ścieżek egzaminacyjnych, przedmioty ścieżki Student |
| `06-przedmioty-i-zmiany-w-prawie.sql` | Przykłady do wykonania ręcznie: nowy przedmiot z aktami, odpięcie aktu, zmiana w prawie przy pytaniu i natychmiastowy zwrot pytań do nauki |
| `03-pytania-przykladowe.sql` | Arkusze i pytania **przykładowe** do testów aplikacji (wymagają weryfikacji merytorycznej przed publikacją) |
| `04-import-szablon.sql` | Szablon do wczytywania własnych pytań (funkcje `pg_temp.pytanie`, `pg_temp.arkusz`, `pg_temp.zrodlo`) |
| `05-kontrola.sql` | Raport tylko do odczytu: pytania według dziedzin, aktów i arkuszy oraz pula każdej ścieżki |
| `09-oznaczenia.sql` | Oznaczenia pytań („Pytania z terminów”, „Pułapki”): zatwierdzanie i odrzucanie propozycji automatu oraz ręczne oznaczanie, jak panel administratora, ale zapytaniami SQL (domyślnie ROLLBACK z raportem) |

Pierwsze uruchomienie na świeżej bazie (kolejność ma znaczenie):

```bash
docker cp 02-katalog-aktow-i-sciezek.sql tutlaw-postgres:/tmp/
docker cp 03-pytania-przykladowe.sql tutlaw-postgres:/tmp/
docker exec tutlaw-postgres psql -U tutlaw_user -d tutlaw -v ON_ERROR_STOP=1 -f /tmp/02-katalog-aktow-i-sciezek.sql
docker exec tutlaw-postgres psql -U tutlaw_user -d tutlaw -v ON_ERROR_STOP=1 -f /tmp/03-pytania-przykladowe.sql
```

Każdy skrypt działa w jednej transakcji i sam sprawdza, czy ma co robić; przy problemie zatrzymuje się z komunikatem i
niczego nie zmienia. Szablon `04` domyślnie kończy się `ROLLBACK` i drukuje raport, więc nic nie zostaje zapisane,
dopóki nie zmienisz go na `COMMIT`. Przedmioty Studenta i zmiany w prawie to też zwykłe polecenia SQL (`06`; opis w [02](02-baza-danych.md#przydatne-zapytania-sql-ręczna-administracja)).
Ukrycie pytania lub samej fiszki i zmiana terminu egzaminu to pojedyncze polecenia SQL
(przykłady w [API: pytania, administracja](api/04-pytania-admin.md)). Struktura tabel i przydatne zapytania:
[02](02-baza-danych.md#przydatne-zapytania-sql-ręczna-administracja).

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

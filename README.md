# Tutlaw: backend

Serwer (REST API) aplikacji **Tutlaw**, darmowej aplikacji do nauki na egzaminy prawnicze. Uczysz się na pytaniach ABC
(trzy odpowiedzi, jedna poprawna) i na fiszkach, a aplikacja sama układa powtórki algorytmem FSRS-6.

Java 17 · Spring Boot 4.1.1 · PostgreSQL 16 · Flyway · Spring Security (JWT)

## Dokumentacja

Pełna dokumentacja po polsku jest w katalogu [`docs/`](docs/README.md):

| Dokument | O czym |
|----------|--------|
| [Spis treści](docs/README.md) | Od tego zacznij |
| [01. Architektura](docs/01-architektura.md) | Technologie, pakiety, warstwy, droga jednego żądania |
| [02. Baza danych](docs/02-baza-danych.md) | Tabele, wyzwalacze, migracje V1–V21 |
| [03. Bezpieczeństwo i konta](docs/03-bezpieczenstwo-i-konta.md) | Rejestracja, logowanie, JWT, role, limity prób |
| [04. Nauka: testy, fiszki, FSRS](docs/04-nauka-testy-fiszki-fsrs.md) | Jak działają powtórki i kolejki |
| [API: przegląd](docs/api/README.md) | Wszystkie 58 endpointów |
| [05. Konfiguracja i uruchomienie](docs/05-konfiguracja-i-uruchomienie.md) | Zmienne środowiskowe, baza, poczta |
| [06. Słownik](docs/06-slownik.md) | Pojęcia i ich nazwy w kodzie |
| [07. Znane ograniczenia](docs/07-znane-ograniczenia.md) | Co jest odłożone na koniec |

## Szybki start

```bash
# 1. uzupełnij plik .env (wzór: .env.example)
# 2. baza danych
docker compose up -d
# 3. backend (migracje wykonają się same)
./mvnw spring-boot:run
```

Backend działa na `http://localhost:8080`. Swagger UI: `http://localhost:8080/swagger-ui.html`. Więcej (poczta do
testów, zmienne środowiskowe, pierwszy administrator, testy):
[docs/05-konfiguracja-i-uruchomienie.md](docs/05-konfiguracja-i-uruchomienie.md).

## Testy

```bash
./mvnw test
```

Większość testów działa bez bazy; `BackendApplicationTests` wymaga działającego PostgreSQL.

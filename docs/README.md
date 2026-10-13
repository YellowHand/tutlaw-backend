# Dokumentacja backendu Tutlaw

Ten katalog opisuje, jak działa serwer (backend) aplikacji **Tutlaw**: co robi, z czego się składa, jakie ma
endpointy, jak liczy powtórki i gdzie w kodzie szukać konkretnej funkcji. Wszystko jest po polsku i pisane tak, żeby
dało się to zrozumieć bez znajomości kodu.

> Stan dokumentacji: backend po **Etapie 19** (migracje bazy V1–V19). Opisuje to, co faktycznie jest w kodzie.
> Tam, gdzie coś jest niedoskonałe albo celowo odłożone na koniec, jest to powiedziane wprost w
> [Znanych ograniczeniach](07-znane-ograniczenia.md).

## Czym jest Tutlaw (w trzech zdaniach)

Tutlaw to darmowa aplikacja do nauki na egzaminy prawnicze (aplikacje: adwokacka, radcowska, sędziowska, prokuratorska,
notarialna, komornicza). Uczysz się na **pytaniach ABC** (trzy odpowiedzi, dokładnie jedna poprawna) i na **fiszkach**.
Aplikacja zapamiętuje, co naprawdę umiesz, a co tylko zgadłeś, i sama układa powtórki algorytmem **FSRS-6**.

## Spis treści

| Plik | O czym jest | Kiedy czytać |
|------|-------------|--------------|
| [01. Architektura](01-architektura.md) | Technologie, pakiety, warstwy, droga jednego żądania, wspólne rozwiązania | Na początek, żeby wiedzieć, gdzie co leży |
| [02. Baza danych](02-baza-danych.md) | Wszystkie tabele, relacje, wyzwalacze, ograniczenia, historia migracji V1–V19 | Gdy chcesz zrozumieć dane albo napisać zapytanie SQL |
| [03. Bezpieczeństwo i konta](03-bezpieczenstwo-i-konta.md) | Rejestracja, aktywacja, logowanie, JWT, reset hasła, role, limity prób, usuwanie konta | Gdy chcesz wiedzieć, kto co może i jak chronione są konta |
| [04. Nauka: testy, fiszki, FSRS](04-nauka-testy-fiszki-fsrs.md) | Serce aplikacji: jak działa algorytm powtórek, kolejki, limity dzienne, tryb egzaminacyjny | Gdy chcesz zrozumieć, dlaczego pytanie wraca akurat dziś |
| [API: przegląd](api/README.md) | Wspólne zasady API i lista wszystkich 58 endpointów | Gdy łączysz frontend z backendem |
| [API: konta i logowanie](api/01-auth.md) | `/api/auth/*` | |
| [API: konto zalogowanego](api/02-konto.md) | `/api/me`, `/api/users/me/*` | |
| [API: kategorie, przedmioty, źródła, rozdziały](api/03-tresci.md) | Struktura materiału, tagi, postęp rozdziałów, katalog Testów | |
| [API: karty (administrator)](api/04-karty-admin.md) | Dodawanie, import zbiorczy, ukrywanie | |
| [API: nauka](api/05-nauka.md) | Kolejki, odpowiedzi, błędy, liczniki | |
| [API: zawieszanie, fiszki z pytań, zgłoszenia](api/06-zawieszanie-fiszki-zgloszenia.md) | Trudne pytania, „Dodaj do fiszek”, zgłaszanie błędów | |
| [API: egzamin próbny](api/07-egzaminy.md) | `/api/exams/*` | |
| [API: statystyki](api/08-statystyki.md) | `/api/statistics/*` | |
| [API: ustawienia](api/09-ustawienia.md) | `/api/me/settings`, `/api/me/origin-filter` | |
| [05. Konfiguracja i uruchomienie](05-konfiguracja-i-uruchomienie.md) | Zmienne środowiskowe, baza, poczta, Swagger, testy | Gdy uruchamiasz projekt |
| [06. Słownik](06-slownik.md) | Pojęcia z aplikacji i ich nazwy w kodzie | Gdy nie wiesz, co znaczy „leech” albo „pula płaska” |
| [07. Znane ograniczenia](07-znane-ograniczenia.md) | Co jest odłożone na koniec, co jest niedoskonałe, czego nie sprawdzono | Przed wdrożeniem na prawdziwy serwer |

## Jak szybko znaleźć odpowiedź

- **„Co robi ten endpoint?”** → [API: przegląd](api/README.md), tabela wszystkich endpointów, potem plik grupy.
- **„Dlaczego to pytanie wróciło dopiero za 11 dni?”** → [04](04-nauka-testy-fiszki-fsrs.md), część o odstępach.
- **„Skąd się wzięła fiszka, której nie dodałem?”** → [04](04-nauka-testy-fiszki-fsrs.md), część o fiszkach z pytań.
- **„Co jest w tabeli `user_card_state`?”** → [02](02-baza-danych.md).
- **„Jak ustawić użytkownika jako administratora?”** → [03](03-bezpieczenstwo-i-konta.md), część o rolach.
- **„Jak uruchomić backend lokalnie?”** → [05](05-konfiguracja-i-uruchomienie.md).

## Pliki pomocnicze w tym katalogu

Poza dokumentacją w `docs/` leżą trzy skrypty SQL do ręcznego uruchamiania (nie są częścią migracji i nic nie
uruchamia ich samo) oraz jedno narzędzie do sprawdzania dokumentacji:

| Plik | Do czego |
|------|----------|
| `assign-subjects-to-categories.sql` | Przenoszenie przedmiotów i aktów prawnych do właściwych działów prawa (kategorii) |
| `reset-study-progress.sql` | Kasowanie postępów nauki (stan fiszek i historia powtórek). **Nieodwracalne** |
| `database-schema.sql` | **Historyczny** zapis pierwszego schematu bazy. Jest nieaktualny, prawdziwy schemat to migracje Flyway w `src/main/resources/db/migration` (opis w [02](02-baza-danych.md)) |
| `tools/check_docs.py` | Skrypt sprawdzający, czy dokumentacja zgadza się z kodem (endpointy, tabele, odnośniki). Uruchomienie: `python3 docs/tools/check_docs.py` |

## Konwencje w tej dokumentacji

- Nazwy z kodu (klasy, tabele, pola JSON) są pisane `takim fontem`. Nazwy pól w JSON są po angielsku, bo tak
  są w kodzie; opisy i komunikaty błędów dla użytkownika są po polsku.
- „Użytkownik” to zalogowane konto. „Administrator” to konto z rolą `ADMIN`.
- Diagramy są w formacie Mermaid. GitHub i większość edytorów Markdown rysuje je automatycznie.
- Przykłady JSON są ilustracyjne: pokazują kształt odpowiedzi, a nie prawdziwe dane z bazy.

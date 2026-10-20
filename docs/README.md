# Dokumentacja backendu Gradus iuris

Ten katalog opisuje, jak działa serwer (backend) aplikacji **Gradus iuris**: co robi, z czego się składa, jakie ma
endpointy, jak liczy powtórki i gdzie w kodzie szukać konkretnej funkcji. Wszystko jest po polsku i pisane tak, żeby
dało się to zrozumieć bez znajomości kodu.

> Stan dokumentacji: backend po **Etapie 33** (baza: schemat `V1`, dane słownikowe `V2`, przedmioty Studenta i zmiany w prawie `V3`). Opisuje to, co faktycznie jest w kodzie.
> Tam, gdzie coś jest niedoskonałe albo celowo odłożone na koniec, jest to powiedziane wprost w
> [Znanych ograniczeniach](07-znane-ograniczenia.md).

## Czym jest Gradus iuris (w trzech zdaniach)

Gradus iuris to darmowa aplikacja do nauki na egzaminy prawnicze (pięć aplikacji: adwokacka i radcowska, sędziowska i
prokuratorska, uzupełniająca sędziowska, notarialna, komornicza) oraz dla **studenta**. Uczysz się na **pytaniach ABC**
(trzy odpowiedzi, dokładnie jedna poprawna), a **fiszka** to drugi tryb nauki tego samego pytania. Pytania są wspólne dla
wszystkich ścieżek i podzielone na dziedziny i akty prawne; każdy widzi swoją pulę. Aplikacja zapamiętuje, co naprawdę
umiesz, a co tylko zgadłeś, i sama układa powtórki algorytmem **FSRS-6**.

## Spis treści

| Plik | O czym jest | Kiedy czytać |
|------|-------------|--------------|
| [01. Architektura](01-architektura.md) | Technologie, pakiety, warstwy, droga jednego żądania, wspólne rozwiązania | Na początek, żeby wiedzieć, gdzie co leży |
| [02. Baza danych](02-baza-danych.md) | Wszystkie tabele, relacje, ograniczenia, model puli pytań, migracje V1 (schemat), V2 (dane słownikowe) i V3 (zmiany w prawie, przedmioty Studenta) | Gdy chcesz zrozumieć dane albo napisać zapytanie SQL |
| [03. Bezpieczeństwo i konta](03-bezpieczenstwo-i-konta.md) | Rejestracja, aktywacja, logowanie, tokeny (dostępu i odświeżające), reset hasła, role, limity prób, usuwanie konta | Gdy chcesz wiedzieć, kto co może i jak chronione są konta |
| [04. Nauka: testy, fiszki, FSRS](04-nauka-testy-fiszki-fsrs.md) | Serce aplikacji: jak działa algorytm powtórek, kolejki, limity dzienne, tryb egzaminacyjny | Gdy chcesz zrozumieć pulę pytań i dlaczego pytanie wraca akurat dziś |
| [API: przegląd](api/README.md) | Wspólne zasady API i lista wszystkich 63 endpointów | Gdy łączysz frontend z backendem |
| [API: konta i logowanie](api/01-auth.md) | `/api/auth/*` | |
| [API: konto zalogowanego](api/02-konto.md) | `/api/me`, `/api/users/me/*` | |
| [API: ścieżki, przedmioty, pula, katalog Testów](api/03-tresci.md) | `/api/tracks`, `/api/courses`, `/api/me/pool*`, `/api/tests/catalog` | |
| [API: widoczność pytań (administrator)](api/04-pytania-admin.md) | Ukrywanie pytań i fiszek (treść wczytuje się skryptami SQL) | |
| [API: nauka](api/05-nauka.md) | Kolejki, odpowiedzi, błędy, liczniki | |
| [API: zawieszanie, fiszki z pytań, zgłoszenia](api/06-zawieszanie-fiszki-zgloszenia.md) | Trudne pytania, „Dodaj do fiszek”, zgłaszanie błędów | |
| [API: egzamin próbny](api/07-egzaminy.md) | `/api/exams/*` (stały format: 150 pytań, zaliczenie od 100 punktów) | |
| [API: statystyki](api/08-statystyki.md) | `/api/statistics/*` | |
| [API: ustawienia](api/09-ustawienia.md) | `/api/me/settings`, `/api/me/track`, `/api/exam-terms` | |
| [05. Konfiguracja i uruchomienie](05-konfiguracja-i-uruchomienie.md) | Zmienne środowiskowe, baza, poczta, Swagger, testy | Gdy uruchamiasz projekt |
| [06. Słownik](06-slownik.md) | Pojęcia z aplikacji i ich nazwy w kodzie | Gdy nie wiesz, co znaczy „leech” albo „pula” |
| [07. Znane ograniczenia](07-znane-ograniczenia.md) | Co jest odłożone na koniec, co jest niedoskonałe, czego nie sprawdzono | Przed wdrożeniem na prawdziwy serwer |

## Jak szybko znaleźć odpowiedź

- **„Co robi ten endpoint?”** → [API: przegląd](api/README.md), tabela wszystkich endpointów, potem plik grupy.
- **„Dlaczego to pytanie wróciło dopiero za 11 dni?”** → [04](04-nauka-testy-fiszki-fsrs.md), część o odstępach.
- **„Skąd się wzięła fiszka, której nie dodałem?”** → [04](04-nauka-testy-fiszki-fsrs.md), część o fiszkach z pytań.
- **„Dlaczego użytkownik widzi właśnie te pytania?”** → [04](04-nauka-testy-fiszki-fsrs.md), część o puli pytań.
- **„Jak wczytać nowe pytania i akty prawne?”** → [05](05-konfiguracja-i-uruchomienie.md), część o skryptach SQL.
- **„Co jest w tabeli `user_question_states`?”** → [02](02-baza-danych.md).
- **„Jak ustawić użytkownika jako administratora?”** → [03](03-bezpieczenstwo-i-konta.md), część o rolach.
- **„Jak uruchomić backend lokalnie?”** → [05](05-konfiguracja-i-uruchomienie.md).

## Pliki pomocnicze w tym katalogu

Poza dokumentacją w `docs/` leży jeden skrypt SQL do ręcznego uruchamiania (nie jest częścią migracji i nic nie
uruchamia go samo) oraz jedno narzędzie do sprawdzania dokumentacji. Skrypty wczytujące treść (akty prawne, ścieżki,
pytania) są w osobnym katalogu `baza/` poza repozytorium kodu (patrz [05](05-konfiguracja-i-uruchomienie.md#wczytywanie-treści-skryptami-sql)).

| Plik | Do czego |
|------|----------|
| `reset-study-progress.sql` | Kasowanie postępów nauki (stan nauki, historia odpowiedzi, własne testy, egzaminy, czas nauki). **Nieodwracalne** |
| `tools/check_docs.py` | Skrypt sprawdzający, czy dokumentacja zgadza się z kodem (endpointy, tabele, odnośniki). Uruchomienie: `python3 docs/tools/check_docs.py` |

Prawdziwy schemat bazy to migracje Flyway w `src/main/resources/db/migration` (opis w [02](02-baza-danych.md)).

## Konwencje w tej dokumentacji

- Nazwy z kodu (klasy, tabele, pola JSON) są pisane `takim fontem`. Nazwy pól w JSON są po angielsku, bo tak
  są w kodzie; opisy i komunikaty błędów dla użytkownika są po polsku.
- „Użytkownik” to zalogowane konto. „Administrator” to konto z rolą `ADMIN`.
- Diagramy są w formacie Mermaid. GitHub i większość edytorów Markdown rysuje je automatycznie.
- Przykłady JSON są ilustracyjne: pokazują kształt odpowiedzi, a nie prawdziwe dane z bazy.

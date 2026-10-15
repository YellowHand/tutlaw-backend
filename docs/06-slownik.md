# 06. Słownik pojęć

[← Spis treści](README.md)

Pojęcia używane w aplikacji i dokumentacji, razem z nazwami, pod którymi występują w kodzie i w bazie. Hasła są
ułożone alfabetycznie.

| Pojęcie | Znaczenie | W kodzie / bazie |
|---------|-----------|------------------|
| **Akt prawny** | Ustawa lub kodeks (np. „Kodeks karny”) jako pozycja w kategorii. W bazie to zwykły przedmiot; „Akty prawne” to etykieta kategorii | `subjects`, `categories.items_label` |
| **Aplikacja (egzamin)** | Egzamin zawodowy: adwokacki i radcowski, sędziowski i prokuratorski, notarialny, uzupełniający sędziowski, komorniczy. Z którego egzaminu pochodzi pytanie, mówi pochodzenie | `QuestionOrigin` |
| **Blokada optymistyczna** | Zabezpieczenie przed jednoczesną zmianą tego samego stanu karty w dwóch oknach: drugi zapis dostaje błąd 409 | `UserCardState.version` (`@Version`) |
| **Czas nauki** | Czas spędzony na ekranach nauki przy widocznej karcie, razem z czytaniem wyjaśnień. Osobny od czasu odpowiedzi na pytanie | `study_time_daily`, `POST /api/me/study-time` |
| **Dostępna karta** | Karta, którą użytkownik może teraz ćwiczyć: nieukryta i z puli płaskiej albo z przeczytanego rozdziału | `CardRepository.findReadableRefs...` |
| **Egzamin próbny** | Losowy zestaw pytań testowych oceniany przez serwer; nie wpływa na harmonogram powtórek | `exam`, `exam_attempts` |
| **Fiszka** | Karta z przodem i odwrotem, bez opcji ABC. Oceniasz ją sam | `Card` bez `correctOption` |
| **Fiszka automatyczna** | Fiszka tworzona przez wyzwalacz w bazie z każdego pytania testowego (przód = pytanie, tył = poprawna odpowiedź i wyjaśnienie) | `cards.auto_generated`, `linked_test_card_id` |
| **Filtr pochodzenia** | Ustawienie użytkownika zawężające Testy do wybranych pochodzeń pytań | `user_settings.test_origins`, `OriginFilterService` |
| **FSRS-6** | Algorytm powtórek rozłożonych w czasie (21 parametrów). Wylicza, kiedy karta ma wrócić | `review.fsrs.FsrsEngine` |
| **Fuzz (rozrzut terminów)** | Losowe przesunięcie terminu o kilka dni, żeby karty uczone tego samego dnia nie wracały naraz | `SchedulingRules.fuzz`, `IntervalFuzzer` |
| **Guessed (zgadywane)** | Zaznaczenie „Nie jestem pewien” przy odpowiedzi w teście | `guessed`, `review_logs.was_guessed` |
| **Idempotentny** | Operacja, którą można powtórzyć bez skutków ubocznych (np. ponowne zawieszenie tej samej karty) | `SuspensionService`, `AuthService.verify` |
| **JWT** | Podpisany token logowania wysyłany w nagłówku `Authorization: Bearer ...` | `JwtService`, `JwtAuthenticationFilter` |
| **Kategoria (rodzaj)** | Najwyższy poziom materiału, np. „Prawo karne i postępowanie karne” | `categories`, pakiet `category` |
| **Karta** | Wspólna nazwa pytania testowego i fiszki | `Card`, tabela `cards` |
| **Katalog Testów** | Drzewo rodzaj → przedmiot → źródło z licznikami pytań dla ekranu Testów | `TestCatalogService`, `GET /api/tests/catalog` |
| **Kolejka** | Lista kart na dzisiejszą sesję: zaległe, potem nowe | `ReviewQueueService`, `QueueRules` |
| **Lapse (błąd)** | Licznik pomyłek na karcie. W teście rośnie przy błędnej odpowiedzi, w fiszkach przy „Nie pamiętam” | `user_card_state.lapses` |
| **Leech (trudne pytanie)** | Karta, na której pomyłek jest co najmniej 8 (próg w konfiguracji). Aplikacja podpowiada zawieszenie albo zgłoszenie | `LeechPolicy`, `app.review.leech-threshold` |
| **Limit dzienny** | Ile nowych fiszek, nowych pytań i zaległych powtórek wchodzi do sesji danego dnia | `app.fsrs.*`, `user_settings` |
| **Mode (tryb)** | Skąd pochodzi odpowiedź: z fiszek (`FLASHCARD`) czy z testu (`TEST`). Ma osobne limity i statystyki | `ReviewMode`, `review_logs.mode` |
| **Moje testy** | Lista zapisanych własnych testów (także nieukończonych) ze szczegółami i odpowiedziami; z niej można ułożyć test ponownie | `custom_test_sessions`, `GET /api/custom-tests/sessions` |
| **Nowa karta** | Karta, na którą użytkownik jeszcze nie odpowiadał (brak wiersza w `user_card_state`) | — |
| **Opanowane** | Karty o stabilności powyżej 21 dni | `StatisticsService.MATURE_STABILITY_THRESHOLD` |
| **Pewny błąd** | Błędna odpowiedź w teście **bez** „Nie jestem pewien”. Oznacza utrwalone błędne przekonanie i odblokowuje fiszkę pytania | `countConfidentMistakes`, `FlashcardUnlockService` |
| **Podręcznik** | Źródło z rozdziałami; jego karty są dostępne dopiero po przeczytaniu rozdziału | `SourceType.TEXTBOOK` |
| **Pochodzenie pytania** | Skąd jest pytanie: autorskie albo z jednej z pięciu aplikacji (z rokiem egzaminu) | `QuestionOrigin`, `cards.origin`, `cards.exam_year` |
| **Powtórka (review)** | Pojedyncza odpowiedź na kartę zapisana w historii | `ReviewLog`, `review_logs` |
| **Pula płaska** | Źródło bez rozdziałów, np. „Egzamin notarialny 2024”. Wszystkie karty od razu dostępne | `SourceType.FLAT_POOL` |
| **Przeczytany rozdział** | Rozdział podręcznika oznaczony przez użytkownika; odblokowuje jego karty | `user_chapter_progress` |
| **Przypominalność (retrievability, R)** | Szansa, że użytkownik przypomni sobie kartę teraz (0–100%) | `FsrsEngine.retrievability` |
| **Przedmiot** | Drugi poziom materiału, np. „Kodeks karny” | `subjects`, pakiet `subject` |
| **Pytanie testowe** | Karta ABC: trzy odpowiedzi, dokładnie jedna poprawna | `Card` z `correctOption` |
| **Retencja (docelowa)** | Do jakiej szansy przypomnienia ma spaść karta, zanim wróci (domyślnie 90%; do wyboru 70–97%) | `desired_retention`, `app.fsrs.request-retention` |
| **Retencja efektywna** | Retencja faktycznie użyta do liczenia terminów; w 30 dniach przed egzaminem co najmniej 95% | `EffectiveSettings.effectiveRetention` |
| **Rola** | `STUDENT` (zwykły użytkownik) albo `ADMIN` (może dodawać treści) | `Role`, `users.role` |
| **Rozdział** | Część podręcznika, do której należą karty | `chapters`, pakiet `chapter` |
| **Stabilność (stability, S)** | Po ilu dniach szansa przypomnienia karty spada do 90%. Im wyższa, tym trwalej pamiętasz | `user_card_state.stability` |
| **Student** | Wybór w oknie startowym „Jestem studentem / jeszcze nie wybrałem”: bez aplikacji, terminu egzaminu i planu dnia | `PUT /api/me/exam { student: true }`, `user_settings.start_choice_made` |
| **Trudność (difficulty, D)** | Jak trudna jest karta dla użytkownika (1–10) | `user_card_state.difficulty` |
| **Tryb egzaminacyjny** | Zachowanie, gdy wybrana aplikacja ma ogłoszony termin egzaminu: wyższa retencja w ostatnich 30 dniach, odstępy nie wykraczają poza egzamin, plan nowych pytań (ostatnie 14 dni bez nowych) | `SchedulingRules`, `LearningSettingsService` |
| **Termin (due date)** | Kiedy karta ma wrócić; od niego karta jest „zaległa” | `user_card_state.due_date` |
| **Token odświeżający** | Długo żyjący (30 dni) jednorazowy token, którym przeglądarka zdobywa nowy token dostępu bez logowania; wymieniany przy każdym użyciu | `RefreshTokenService`, `refresh_tokens` |
| **Własny test** | Sesja nauki ułożona przez użytkownika: wybrane rodzaje i akty prawne, rodzaj pytań (wszystkie, nowe, błędy, zaległe) i liczba pytań (10, 20, 50, 100 albo 150). Bez progu zaliczenia, odpowiedzi jak w zwykłym teście. Zapisywany jako sesja | `CustomTestService`, `CustomTestSessionService`, `POST /api/review/custom-test` |
| **Źródło** | Zbiór pytań lub rozdziałów w przedmiocie: podręcznik albo pula płaska | `sources`, pakiet `source` |
| **Zaległa karta** | Karta z minionym terminem powtórki | `ReviewQueueService.splitByDue` |
| **Zawieszenie** | Wyłączenie karty z kolejek i liczników dla jednego użytkownika, bez utraty postępu | `user_card_state.suspended`, `SuspensionService` |
| **Zgłoszenie błędu** | Informacja od użytkownika, że pytanie jest błędne, nieaktualne albo niejasne; rozpatrywana ręcznie w bazie | `card_reports`, `CardReportService` |
| **Ukryta karta** | Karta wyłączona przez administratora dla wszystkich; historia zostaje | `cards.hidden` |

## Oceny pamięci (1–4)

| Liczba | Nazwa w FSRS | Przycisk w aplikacji | Skąd się bierze |
|--------|--------------|----------------------|-----------------|
| 1 | AGAIN | Nie pamiętam | Fiszki: wybór użytkownika. Testy: każda odpowiedź błędna lub zgadywana |
| 2 | HARD | Trudne | Tylko fiszki |
| 3 | GOOD | Znam | Fiszki: wybór użytkownika. Testy: odpowiedź poprawna i pewna |
| 4 | EASY | Łatwe | Tylko fiszki (test nigdy nie daje tej oceny) |

## Skróty nazw kodu

| Przyrostek / nazwa | Typowo oznacza |
|--------------------|----------------|
| `*Controller` | Klasa przyjmująca żądania HTTP |
| `*Service` | Logika działania |
| `*Repository` | Dostęp do bazy |
| `*Rules`, `*Policy`, `*Math` | Czysta logika bez Springa i bazy (łatwa do testowania) |
| `*Request` / `*Response` | Kształt JSON-a wchodzącego / wychodzącego (w pakiecie `dto`) |
| `CardRef` | Odchudzona karta (same numery i kilka pól) używana do budowania kolejek bez ładowania treści |

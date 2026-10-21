# 06. Słownik pojęć

[← Spis treści](README.md)

Pojęcia używane w aplikacji i dokumentacji, razem z nazwami, pod którymi występują w kodzie i w bazie. Hasła są
ułożone alfabetycznie.

| Pojęcie | Znaczenie | W kodzie / bazie |
|---------|-----------|------------------|
| **Akt prawny** | Ustawa, kodeks albo zagadnienie (np. „Kodeks karny”, „Paremie łacińskie”) w jednej dziedzinie. Pytanie należy do dokładnie jednego aktu. Rodzaj `ACT` (ustawa) lub `TOPIC` (zagadnienie, które nie jest ustawą) | `legal_acts`, `LegalAct`, `LegalActKind` |
| **Aplikacja (egzamin)** | Egzamin zawodowy: adwokacki i radcowski, sędziowski i prokuratorski, uzupełniający sędziowski, notarialny, komorniczy. Każda ma własną ścieżkę rodzaju `EXAM`, termin egzaminu i arkusze | `tracks.kind = 'EXAM'` |
| **Arkusz** | Źródło pytań z egzaminu jednej aplikacji w danym roku, np. „Notarialna 2024”. Pytania z arkusza należą do puli tej aplikacji | `question_sources.kind = 'EXAM_SHEET'`, `SourceKind.EXAM_SHEET` |
| **Blokada optymistyczna** | Zabezpieczenie przed jednoczesną zmianą tego samego stanu nauki w dwóch oknach: drugi zapis dostaje błąd 409 | `UserQuestionState.version` (`@Version`) |
| **Czas nauki** | Czas spędzony na ekranach nauki przy widocznej karcie przeglądarki, razem z czytaniem wyjaśnień. Osobny od czasu odpowiedzi na pytanie | `study_time_daily`, `POST /api/me/study-time` |
| **Dziedzina** | Najwyższy poziom podziału materiału, np. „Prawo karne i proces karny”. Jest 11 dziedzin; każdy użytkownik widzi je wszystkie, ale w każdej tylko akty swojej puli | `legal_fields`, pakiet `legal` |
| **Egzamin próbny** | Losowy zestaw 150 pytań z całej puli ścieżki (bez zawężenia do aktów), oceniany przez serwer; nie wpływa na harmonogram powtórek | `exam`, `exam_attempts` |
| **Fiszka** | **Tryb nauki pytania**, a nie osobna treść: przód to pytanie, tył to poprawna odpowiedź z wyjaśnieniem i podstawą prawną. Ma własny stan FSRS, osobny od stanu w Testach. Pojawia się w Fiszkach po pewnym błędzie w teście albo po ręcznym dodaniu | `user_question_states.mode = 'FLASHCARD'`, pakiet `flashcard` |
| **Fiszka dodana ręcznie** | Fiszka, którą użytkownik sam dodał przyciskiem „Dodaj do fiszek”; można ją usunąć („Usuń z fiszek”) | `flashcard_additions`, `FlashcardStatus.ADDED_MANUALLY` |
| **Fiszka odblokowana** | Fiszka dostępna w Fiszkach użytkownika: po pewnym błędzie (`ADDED_BY_MISTAKE`, nie da się wycofać) albo po ręcznym dodaniu | `FlashcardUnlockService`, `FlashcardStatus` |
| **Źródło pytań w teście** (`SourceMode`) | Wybór przy każdym teście w kreatorze: wszystkie źródła, Ministerstwo Sprawiedliwości (arkusze egzaminacyjne) albo autorskie. Zastąpił trwały filtr źródeł na koncie | `SourceMode`, `SourceFilter`, `SourceGroups` |
| **Rozdział** | Część aktu prawnego (np. „XIX. Przestępstwa przeciwko życiu i zdrowiu”); pytanie ma najwyżej jeden rozdział. Test można zawęzić do wybranych rozdziałów | `act_sections`, `questions.section_id` |
| **FSRS-6** | Algorytm powtórek rozłożonych w czasie (21 parametrów). Wylicza, kiedy pytanie ma wrócić | `review.fsrs.FsrsEngine` |
| **Fuzz (rozrzut terminów)** | Losowe przesunięcie terminu o kilka dni, żeby pytania uczone tego samego dnia nie wracały naraz | `SchedulingRules.fuzz`, `IntervalFuzzer` |
| **Guessed (zgadywane)** | Zaznaczenie „Nie jestem pewien” przy odpowiedzi w teście | `guessed`, `review_logs.was_guessed` |
| **Idempotentny** | Operacja, którą można powtórzyć bez skutków ubocznych (np. ponowne zawieszenie tego samego pytania) | `SuspensionService`, `AuthService.verify` |
| **JWT** | Podpisany token logowania wysyłany w nagłówku `Authorization: Bearer ...` | `JwtService`, `JwtAuthenticationFilter` |
| **Katalog Testów** | Drzewo dziedzina → akt → źródło (u Studenta: przedmioty) z licznikami pytań dla ekranu Testów | `TestCatalogService`, `GET /api/tests/catalog` |
| **Kolejka** | Lista pytań na dzisiejszą sesję: zaległe, potem nowe | `ReviewQueueService`, `QueueRules` |
| **Lapse (błąd)** | Licznik pomyłek na pytaniu w danym trybie. W teście rośnie przy błędnej odpowiedzi, w fiszkach przy „Nie pamiętam” | `user_question_states.lapses` |
| **Leech (trudne pytanie)** | Pytanie, na którym pomyłek jest co najmniej 8 (próg w konfiguracji). Aplikacja podpowiada zawieszenie albo zgłoszenie | `LeechPolicy`, `app.review.leech-threshold` |
| **Limit dzienny** | Ile nowych fiszek, nowych pytań i zaległych powtórek wchodzi do sesji danego dnia | `app.fsrs.*`, `user_settings` |
| **Mode (tryb)** | Skąd pochodzi odpowiedź i którego stanu dotyczy: z fiszek (`FLASHCARD`) czy z testu (`TEST`). Ma osobny stan FSRS, limity i statystyki | `ReviewMode`, `review_logs.mode`, `user_question_states.mode` |
| **Moje testy** | Lista zapisanych własnych testów (także nieukończonych) ze szczegółami i odpowiedziami; z niej można ułożyć test ponownie | `custom_test_sessions`, `GET /api/custom-tests/sessions` |
| **Nowe pytanie** | Pytanie, na które użytkownik jeszcze nie odpowiadał w danym trybie (brak wiersza w `user_question_states`) | — |
| **Opanowane** | Pytania o stabilności powyżej 21 dni | `StatisticsService.MATURE_STABILITY_THRESHOLD` |
| **Paremie** | Łacińskie maksymy prawnicze. To zwykłe pytania ABC w akcie-zagadnieniu (`TOPIC`), nie osobny rodzaj treści | `legal_acts.kind = 'TOPIC'` |
| **Pewny błąd** | Błędna odpowiedź w teście **bez** „Nie jestem pewien”. Oznacza utrwalone błędne przekonanie i odblokowuje fiszkę pytania | `review_logs` (`was_correct = false`, `was_guessed = false`), `FlashcardUnlockService` |
| **Powtórka (review)** | Pojedyncza odpowiedź na pytanie zapisana w historii | `ReviewLog`, `review_logs` |
| **Podstawa prawna** | Przepis, na którym opiera się poprawna odpowiedź (np. „art. 187 KC”); pole opcjonalne, widoczne w fiszce | `questions.legal_basis` |
| **Pula (pytań użytkownika)** | Zbiór pytań, które użytkownik może ćwiczyć. Wyliczana regułą: dla aplikacji to pytania z jej arkuszy oraz z aktów jej listy, dla Studenta pytania z aktów wybranych przedmiotów (bez wyboru: wszystkich); potem opcjonalne zawężenie do wybranych aktów | `Pool`, `PoolService`, pakiet `pool` |
| **Przedmiot** | Pozycja ścieżki Student porządkująca akty ze wspólnej bazy (np. „Prawo karne”). Nie ma własnych pytań. Administrator tworzy go i przypisuje akty SQL-em; student wybiera przedmioty, z których się uczy, i dostaje wszystkie pytania ich aktów. Płatne i darmowe przedmioty to przyszła funkcja | `courses`, `course_acts`, `user_courses`, pakiet `course` |
| **Przypominalność (retrievability, R)** | Szansa, że użytkownik przypomni sobie pytanie teraz (0–100%) | `FsrsEngine.retrievability` |
| **Pytanie** | Podstawowa jednostka treści: trzy odpowiedzi ABC, dokładnie jedna poprawna, wyjaśnienie i podstawa prawna. Służy zarówno do Testów, jak i (jako fiszka) do Fiszek | `questions`, `Question` |
| **Pytania autorskie** | Pytania spoza arkuszy egzaminów, napisane na potrzeby aplikacji; jedno źródło „Pytania autorskie” | `question_sources.kind = 'AUTHORED'`, `SourceKind.AUTHORED` |
| **Retencja (docelowa)** | Do jakiej szansy przypomnienia ma spaść pytanie, zanim wróci (domyślnie 90%; do wyboru 70–97%) | `desired_retention`, `app.fsrs.request-retention` |
| **Retencja efektywna** | Retencja faktycznie użyta do liczenia terminów; w 30 dniach przed egzaminem co najmniej 95% | `EffectiveSettings.effectiveRetention` |
| **Rola** | `STUDENT` (zwykły użytkownik) albo `ADMIN` (może ukrywać pytania i ustawiać terminy egzaminów) | `Role`, `users.role` |
| **Stabilność (stability, S)** | Po ilu dniach szansa przypomnienia pytania spada do 90%. Im wyższa, tym trwalej pamiętasz | `user_question_states.stability` |
| **Student** | Ścieżka rodzaju `STUDENT` o nazwie „Student”: bez egzaminu, terminu i planu dnia; pula to akty wybranych przedmiotów (bez wyboru: wszystkich). Wybiera się ją tak samo jak aplikację | `tracks.kind = 'STUDENT'`, `PUT /api/me/track` |
| **Ścieżka** | To, co użytkownik wybiera na starcie: jedna z pięciu aplikacji albo Student. Wyznacza pulę pytań, termin egzaminu i plan | `tracks`, `user_settings.track_id`, pakiet `track` |
| **Trudność (difficulty, D)** | Jak trudne jest pytanie dla użytkownika (1–10) | `user_question_states.difficulty` |
| **Tryb egzaminacyjny** | Zachowanie, gdy wybrana aplikacja ma ogłoszony termin egzaminu: wyższa retencja w ostatnich 30 dniach, odstępy nie wykraczają poza egzamin, plan nowych pytań (ostatnie 14 dni bez nowych) | `SchedulingRules`, `LearningSettingsService` |
| **Termin (due date)** | Kiedy pytanie ma wrócić; od niego jest „zaległe” | `user_question_states.due_date` |
| **Token odświeżający** | Długo żyjący (30 dni) jednorazowy token, którym przeglądarka zdobywa nowy token dostępu bez logowania; wymieniany przy każdym użyciu | `RefreshTokenService`, `refresh_tokens` |
| **Ukryte pytanie** | Pytanie wyłączone przez administratora dla wszystkich; historia zostaje. Osobno można ukryć samą fiszkę | `questions.hidden`, `questions.flashcard_hidden` |
| **Własny test** | Sesja nauki ułożona przez użytkownika: wybrane dziedziny, akty prawne i przedmioty (Student), rodzaj pytań (wszystkie, nowe, błędy, zaległe, ze zmianą w prawie) i liczba pytań (10, 20, 50, 100 albo 150). Bez progu zaliczenia, odpowiedzi jak w zwykłym teście. Zapisywany jako sesja | `CustomTestService`, `CustomTestSessionService`, `POST /api/review/custom-test` |
| **Zaległe pytanie** | Pytanie z minionym terminem powtórki | `ReviewQueueService.splitByDue` |
| **Zerowanie postępów** | Świadome, nieodwracalne usunięcie postępów użytkownika (stany nauki, historia odpowiedzi, ręczne fiszki, własne testy, egzaminy, czas nauki) przy zachowaniu konta, ścieżki, ustawień i wybranych przedmiotów. Wymaga wpisania słowa WYZERUJ | `StudyProgressService`, `DELETE /api/me/study-progress` |
| **Zmiana w prawie** | Data (i opis) zapisana przy pytaniu, którego treść uwzględnia nowelizację. Od tej daty pytanie wraca jako zaległe u tych, którzy odpowiadali przed nią, a przez rok jest w kategorii „Zmiany w prawie” własnego testu; plakietka „Zmiana w prawie: data” towarzyszy mu w nauce | `questions.law_changed_at`, `law_change_note`, `LawChangeService`, `LawChangePolicy`, `CustomTestKind.LAW_CHANGE` |
| **Zawężenie puli** | Trwały wybór konkretnych aktów (Student: przedmiotów), do których użytkownik ogranicza swoją pulę (nie dotyczy egzaminu próbnego). Zmiana ścieżki czyści zawężenie aktami; wybrane przedmioty zostają | `user_pool_acts`, `user_courses`, `PoolSettingsService` |
| **Zawieszenie** | Wyłączenie pytania z kolejek i liczników w jednym trybie dla jednego użytkownika, bez utraty postępu | `user_question_states.suspended`, `SuspensionService` |
| **Zgłoszenie błędu** | Informacja od użytkownika, że pytanie jest błędne, nieaktualne albo niejasne; rozpatrywana ręcznie w bazie | `question_reports`, `QuestionReportService` |
| **Źródło** | Skąd pochodzi pytanie: arkusz egzaminu aplikacji w danym roku albo pytania autorskie | `question_sources`, pakiet `source` |

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
| `QuestionRef` | Odchudzone pytanie (same numery i kilka pól) używane do budowania kolejek bez ładowania treści |

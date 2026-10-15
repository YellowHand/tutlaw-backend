package pl.tutlaw.backend.review;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import pl.tutlaw.backend.card.Card;
import pl.tutlaw.backend.card.CardRepository;
import pl.tutlaw.backend.card.CorrectOption;
import pl.tutlaw.backend.card.QuestionOrigin;
import pl.tutlaw.backend.category.Category;
import pl.tutlaw.backend.category.CategoryRepository;
import pl.tutlaw.backend.common.exception.ResourceNotFoundException;
import pl.tutlaw.backend.review.dto.CustomTestRequest;
import pl.tutlaw.backend.review.dto.CustomTestSelectionResponse;
import pl.tutlaw.backend.review.dto.CustomTestSessionDetailResponse;
import pl.tutlaw.backend.review.dto.CustomTestSessionSummaryResponse;
import pl.tutlaw.backend.settings.OriginFilterService;
import pl.tutlaw.backend.source.Source;
import pl.tutlaw.backend.subject.Subject;
import pl.tutlaw.backend.subject.SubjectRepository;
import pl.tutlaw.backend.testsupport.FakeRepos;
import pl.tutlaw.backend.testsupport.MutableClock;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Zapisane własne testy na prawdziwym serwisie; baza "w pamięci" (atrapy repozytoriów na listach). */
class CustomTestSessionServiceTest {

    private final MutableClock clock = MutableClock.at("2026-10-12T10:00:00Z");
    private final List<CustomTestSession> sessions = new ArrayList<>();
    private final List<CustomTestSessionQuestion> questions = new ArrayList<>();
    private final Map<Long, Card> cards = new java.util.HashMap<>();
    private final Authentication authentication = mock(Authentication.class);
    private final CustomTestService selector = mock(CustomTestService.class);
    private final OriginFilterService originFilter = mock(OriginFilterService.class);
    private final CurrentUserResolver resolver = mock(CurrentUserResolver.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final SubjectRepository subjects = mock(SubjectRepository.class);
    private final CardRepository cardRepository = mock(CardRepository.class);

    private CustomTestSessionService service;
    private User jan;
    private User ola;

    @BeforeEach
    void setUp() {
        jan = user(5L);
        ola = user(6L);
        when(resolver.resolve(authentication)).thenReturn(jan);
        when(originFilter.activeFor(5L)).thenReturn(EnumSet.allOf(QuestionOrigin.class));
        for (long id = 1; id <= 12; id++) {
            cards.put(id, card(id));
        }
        when(cardRepository.findAllById(org.mockito.ArgumentMatchers.<Iterable<Long>>any())).thenAnswer(inv -> {
            List<Card> found = new ArrayList<>();
            for (Long id : (Iterable<Long>) inv.getArgument(0)) {
                if (cards.containsKey(id)) found.add(cards.get(id));
            }
            return found;
        });
        service = new CustomTestSessionService(selector, sessionRepo(), questionRepo(), cardRepository,
                categories, subjects, originFilter, resolver, clock);
    }

    private static User user(long id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    private static Card card(long id) {
        Card c = new Card();
        c.setId(id);
        c.setFront("Pytanie " + id);
        c.setOptionA("a" + id);
        c.setOptionB("b" + id);
        c.setOptionC("c" + id);
        c.setCorrectOption(CorrectOption.B);
        c.setExplanation("Bo " + id);
        c.setOrigin(QuestionOrigin.NOTARY);
        c.setExamYear(2023);
        Source source = new Source();
        source.setName("Źródło");
        c.setSource(source);
        return c;
    }

    private CustomTestSessionRepository sessionRepo() {
        AtomicLong ids = new AtomicLong(100);
        return FakeRepos.proxy(CustomTestSessionRepository.class, (method, args) -> {
            switch (method) {
                case "save": {
                    CustomTestSession s = (CustomTestSession) args[0];
                    if (s.getId() == null) s.setId(ids.getAndIncrement());
                    if (!sessions.contains(s)) sessions.add(s);
                    return s;
                }
                case "findByIdAndUserId":
                    return sessions.stream()
                            .filter(s -> s.getId().equals(args[0]) && s.getUser().getId().equals(args[1])).findFirst();
                case "findRecent":
                case "findAllForUser":
                    return sessions.stream().filter(s -> s.getUser().getId().equals(args[0]))
                            .sorted((a, b) -> b.getStartedAt().compareTo(a.getStartedAt()) != 0
                                    ? b.getStartedAt().compareTo(a.getStartedAt()) : b.getId().compareTo(a.getId()))
                            .toList();
                case "countByUserIdAndFinishedAtIsNotNull":
                    return sessions.stream()
                            .filter(s -> s.getUser().getId().equals(args[0]) && s.isFinished()).count();
                default:
                    throw new UnsupportedOperationException("CustomTestSessionRepository." + method);
            }
        });
    }

    private CustomTestSessionQuestionRepository questionRepo() {
        AtomicLong ids = new AtomicLong(1);
        return FakeRepos.proxy(CustomTestSessionQuestionRepository.class, (method, args) -> {
            switch (method) {
                case "save": {
                    CustomTestSessionQuestion q = (CustomTestSessionQuestion) args[0];
                    if (q.getId() == null) q.setId(ids.getAndIncrement());
                    if (!questions.contains(q)) questions.add(q);
                    return q;
                }
                case "saveAll": {
                    List<CustomTestSessionQuestion> saved = new ArrayList<>();
                    for (Object o : (Iterable<?>) args[0]) {
                        CustomTestSessionQuestion q = (CustomTestSessionQuestion) o;
                        if (q.getId() == null) q.setId(ids.getAndIncrement());
                        questions.add(q);
                        saved.add(q);
                    }
                    return saved;
                }
                case "findBySessionIds":
                    return questions.stream()
                            .filter(q -> ((java.util.Collection<?>) args[0]).contains(q.getSession().getId())).toList();
                case "findDetailed":
                    return questions.stream().filter(q -> q.getSession().getId().equals(args[0]))
                            .sorted((a, b) -> Integer.compare(a.getPosition(), b.getPosition())).toList();
                case "findBySessionIdAndCardId":
                    return questions.stream()
                            .filter(q -> q.getSession().getId().equals(args[0]) && q.getCard().getId().equals(args[1]))
                            .findFirst();
                case "countBySessionIdAndAnsweredAtIsNull":
                    return questions.stream()
                            .filter(q -> q.getSession().getId().equals(args[0]) && !q.isAnswered()).count();
                default:
                    throw new UnsupportedOperationException("CustomTestSessionQuestionRepository." + method);
            }
        });
    }

    private Long startTest(List<Long> cardIds, CustomTestRequest request) {
        when(selector.select(request, authentication))
                .thenReturn(new CustomTestSelectionResponse(cardIds, 40, request.questionCount(), null));
        return service.draw(request, authentication).sessionId();
    }

    private Long startTest(List<Long> cardIds) {
        return startTest(cardIds, new CustomTestRequest(null, null, CustomTestKind.ALL, 10));
    }

    private void answer(Long sessionId, long cardId, CorrectOption selected, boolean correct, boolean guessed, Double time) {
        service.recordAnswer(5L, sessionId, cardId, selected, correct, guessed, time);
    }

    // ---------------------------------------------------------------- zapis przy ułożeniu testu

    @Test
    void drawSavesTheConfigurationAndTheQuestionsInOrder() {
        Category karne = new Category();
        karne.setId(2L);
        karne.setName("Prawo karne");
        Subject kk = new Subject();
        kk.setId(9L);
        kk.setName("Kodeks cywilny");
        when(categories.findAllById(List.of(2L))).thenReturn(List.of(karne));
        when(subjects.findAllById(List.of(9L))).thenReturn(List.of(kk));
        when(originFilter.activeFor(5L)).thenReturn(EnumSet.of(QuestionOrigin.NOTARY, QuestionOrigin.AUTHORED));

        CustomTestRequest request = new CustomTestRequest(List.of(2L), java.util.Arrays.asList(9L, 9L, null),
                CustomTestKind.MISTAKES, 20);
        Long id = startTest(List.of(7L, 3L, 11L), request);

        assertThat(id).isNotNull();
        CustomTestSession saved = sessions.get(0);
        assertThat(saved.getUser()).isSameAs(jan);
        assertThat(saved.getKind()).isEqualTo(CustomTestKind.MISTAKES);
        assertThat(saved.getScopeLabel()).isEqualTo("Prawo karne i Kodeks cywilny");
        assertThat(saved.getCategoryIds()).isEqualTo("2");
        assertThat(saved.getSubjectIds()).isEqualTo("9");
        assertThat(saved.getOrigins()).isEqualTo("AUTHORED,NOTARY");
        assertThat(saved.getRequestedCount()).isEqualTo(20);
        assertThat(saved.getAvailableCount()).isEqualTo(40);
        assertThat(saved.getStartedAt()).isNotNull();
        assertThat(saved.isFinished()).isFalse();
        assertThat(questions).extracting(q -> q.getCard().getId()).containsExactly(7L, 3L, 11L);
        assertThat(questions).extracting(CustomTestSessionQuestion::getPosition).containsExactly(1, 2, 3);
        assertThat(questions).noneMatch(CustomTestSessionQuestion::isAnswered);
    }

    @Test
    void aTestOverTheWholeMaterialAndAllOriginsIsStoredWithoutRestrictions() {
        startTest(List.of(1L, 2L));

        CustomTestSession saved = sessions.get(0);
        assertThat(saved.getScopeLabel()).isEqualTo("Cały materiał");
        assertThat(saved.getCategoryIds()).isEmpty();
        assertThat(saved.getSubjectIds()).isEmpty();
        assertThat(saved.getOrigins()).isNull();
    }

    @Test
    void selectionFailureSavesNothing() {
        CustomTestRequest request = new CustomTestRequest(null, null, CustomTestKind.NEW, 10);
        when(selector.select(request, authentication)).thenThrow(new IllegalArgumentException("Brak pytań"));

        assertThatThrownBy(() -> service.draw(request, authentication)).isInstanceOf(IllegalArgumentException.class);

        assertThat(sessions).isEmpty();
        assertThat(questions).isEmpty();
    }

    @Test
    void unknownSelectedItemsFallBackToAGeneralLabel() {
        CustomTestRequest request = new CustomTestRequest(List.of(77L), null, CustomTestKind.ALL, 10);
        when(categories.findAllById(List.of(77L))).thenReturn(List.of());

        startTest(List.of(1L), request);

        assertThat(sessions.get(0).getScopeLabel()).isEqualTo("Wybrane działy");
    }

    // ---------------------------------------------------------------- odpowiedzi

    @Test
    void answersAreRecordedAndTheTestFinishesWithTheLastOne() {
        Long id = startTest(List.of(1L, 2L, 3L));

        answer(id, 1, CorrectOption.B, true, false, 8.0);
        answer(id, 2, CorrectOption.A, false, false, 12.0);
        assertThat(sessions.get(0).isFinished()).isFalse();
        clock.advance(Duration.ofMinutes(2));
        answer(id, 3, CorrectOption.B, true, true, 20.0);

        CustomTestSession saved = sessions.get(0);
        assertThat(saved.isFinished()).isTrue();
        assertThat(saved.getFinishedAt()).isAfter(saved.getStartedAt());
        CustomTestSessionQuestion second = questions.get(1);
        assertThat(second.getSelectedOption()).isEqualTo(CorrectOption.A);
        assertThat(second.getWasCorrect()).isFalse();
        assertThat(second.getTimeSpentSeconds()).isEqualTo(12.0);
        assertThat(questions.get(2).isWasGuessed()).isTrue();
    }

    @Test
    void anAbandonedTestStaysUnfinishedWithItsPartialResults() {
        Long id = startTest(List.of(1L, 2L, 3L));
        answer(id, 1, CorrectOption.B, true, false, 5.0);

        CustomTestSessionSummaryResponse summary = service.list(authentication).get(0);

        assertThat(summary.finished()).isFalse();
        assertThat(summary.finishedAt()).isNull();
        assertThat(summary.questionCount()).isEqualTo(3);
        assertThat(summary.answeredCount()).isEqualTo(1);
        assertThat(summary.correctCount()).isEqualTo(1);
    }

    @Test
    void theFirstAnswerWins_aRepeatedAnswerDoesNotChangeTheResult() {
        Long id = startTest(List.of(1L, 2L));
        answer(id, 1, CorrectOption.A, false, false, 5.0);

        // druga runda ("Powtórz teraz") albo ponowione żądanie
        answer(id, 1, CorrectOption.B, true, false, 3.0);

        CustomTestSessionQuestion first = questions.get(0);
        assertThat(first.getSelectedOption()).isEqualTo(CorrectOption.A);
        assertThat(first.getWasCorrect()).isFalse();
        assertThat(first.getTimeSpentSeconds()).isEqualTo(5.0);
    }

    @Test
    void answersWithoutAValidTestAreIgnoredQuietly() {
        Long id = startTest(List.of(1L, 2L));

        answer(null, 1, CorrectOption.B, true, false, 1.0);       // zwykły test, bez numeru sesji
        answer(9999L, 1, CorrectOption.B, true, false, 1.0);      // nie ma takiego testu
        answer(id, 8, CorrectOption.B, true, false, 1.0);         // pytanie spoza testu
        service.recordAnswer(6L, id, 1L, CorrectOption.B, true, false, 1.0); // test innego użytkownika

        assertThat(questions).noneMatch(CustomTestSessionQuestion::isAnswered);
        assertThat(sessions.get(0).isFinished()).isFalse();
    }

    // ---------------------------------------------------------------- lista i szczegóły

    @Test
    void theListSummarisesResultsTimesAndConfiguration() {
        Long id = startTest(List.of(1L, 2L, 3L, 4L));
        answer(id, 1, CorrectOption.B, true, false, 10.0);
        answer(id, 2, CorrectOption.B, true, true, 30.0);
        answer(id, 3, CorrectOption.A, false, false, 500.0);   // przycinane do 120 s
        answer(id, 4, CorrectOption.B, true, false, null);     // brak pomiaru

        CustomTestSessionSummaryResponse summary = service.list(authentication).get(0);

        assertThat(summary.id()).isEqualTo(id);
        assertThat(summary.finished()).isTrue();
        assertThat(summary.questionCount()).isEqualTo(4);
        assertThat(summary.answeredCount()).isEqualTo(4);
        assertThat(summary.correctCount()).isEqualTo(3);
        assertThat(summary.guessedCount()).isEqualTo(1);
        assertThat(summary.timeSeconds()).isEqualTo(10.0 + 30.0 + 120.0);
        assertThat(summary.kind()).isEqualTo(CustomTestKind.ALL);
        assertThat(summary.requestedCount()).isEqualTo(10);
        assertThat(summary.origins()).containsExactlyInAnyOrderElementsOf(EnumSet.allOf(QuestionOrigin.class));
    }

    @Test
    void theListIsNewestFirstAndOnlyContainsTheCallersTests() {
        startTest(List.of(1L));
        clock.advance(Duration.ofHours(1));
        Long newer = startTest(List.of(2L));
        CustomTestSession foreign = new CustomTestSession();
        foreign.setId(500L);
        foreign.setUser(ola);
        foreign.setStartedAt(java.time.LocalDateTime.now(clock));
        foreign.setKind(CustomTestKind.ALL);
        foreign.setScopeLabel("Cudzy");
        sessions.add(foreign);

        List<CustomTestSessionSummaryResponse> list = service.list(authentication);

        assertThat(list).hasSize(2);
        assertThat(list.get(0).id()).isEqualTo(newer);
    }

    @Test
    void theListIsEmptyWhenThereAreNoTests() {
        assertThat(service.list(authentication)).isEmpty();
    }

    @Test
    void detailListsEveryQuestionWithTheUsersAnswer() {
        Long id = startTest(List.of(4L, 5L));
        answer(id, 4, CorrectOption.A, false, true, 9.5);

        CustomTestSessionDetailResponse detail = service.detail(id, authentication);

        assertThat(detail.summary().id()).isEqualTo(id);
        assertThat(detail.questions()).hasSize(2);
        CustomTestSessionDetailResponse.Question first = detail.questions().get(0);
        assertThat(first.position()).isEqualTo(1);
        assertThat(first.cardId()).isEqualTo(4L);
        assertThat(first.front()).isEqualTo("Pytanie 4");
        assertThat(first.optionA()).isEqualTo("a4");
        assertThat(first.correctOption()).isEqualTo(CorrectOption.B);
        assertThat(first.selectedOption()).isEqualTo(CorrectOption.A);
        assertThat(first.correct()).isFalse();
        assertThat(first.guessed()).isTrue();
        assertThat(first.timeSeconds()).isEqualTo(9.5);
        assertThat(first.explanation()).isEqualTo("Bo 4");
        assertThat(first.sourceName()).isEqualTo("Źródło");
        assertThat(first.origin()).isEqualTo(QuestionOrigin.NOTARY);
        CustomTestSessionDetailResponse.Question second = detail.questions().get(1);
        assertThat(second.selectedOption()).isNull();
        assertThat(second.correct()).isNull();
        assertThat(second.timeSeconds()).isNull();
    }

    @Test
    void detailOfSomeoneElsesOrMissingTestIsNotFound() {
        startTest(List.of(1L));
        CustomTestSession foreign = new CustomTestSession();
        foreign.setId(500L);
        foreign.setUser(ola);
        sessions.add(foreign);

        assertThatThrownBy(() -> service.detail(500L, authentication)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.detail(1234L, authentication)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void finishedTestsAreCountedForStatistics() {
        Long a = startTest(List.of(1L));
        startTest(List.of(2L));   // nieukończony
        answer(a, 1, CorrectOption.B, true, false, 3.0);

        assertThat(service.countFinished(5L)).isEqualTo(1);
        assertThat(service.countFinished(6L)).isZero();
    }

    // ---------------------------------------------------------------- pomocnicze

    @Test
    void idListsRoundTripAndSkipDamagedEntries() {
        assertThat(CustomTestSessionService.joinIds(List.of(3L, 1L, 20L))).isEqualTo("3,1,20");
        assertThat(CustomTestSessionService.joinIds(List.of())).isEmpty();
        assertThat(CustomTestSessionService.parseIds("3,1,20")).containsExactly(3L, 1L, 20L);
        assertThat(CustomTestSessionService.parseIds("")).isEmpty();
        assertThat(CustomTestSessionService.parseIds(null)).isEmpty();
        assertThat(CustomTestSessionService.parseIds("3,x, 7")).containsExactly(3L, 7L);
    }

    @Test
    void originsAreStoredAsNullWhenAllAreActive() {
        assertThat(CustomTestSessionService.serializeOrigins(EnumSet.allOf(QuestionOrigin.class))).isNull();
        assertThat(CustomTestSessionService.serializeOrigins(EnumSet.of(QuestionOrigin.NOTARY)))
                .isEqualTo("NOTARY");
        assertThat(OriginFilterService.parse(
                CustomTestSessionService.serializeOrigins(EnumSet.of(QuestionOrigin.BAILIFF, QuestionOrigin.AUTHORED))))
                .containsExactlyInAnyOrder(QuestionOrigin.BAILIFF, QuestionOrigin.AUTHORED);
    }

    // --- Strażnik mapowania encji ---
    // Po Etapie 31 aplikacja nie wstała, bo pole enum zapisane jako tekst z length = 1 (selected_option) Hibernate mapuje
    // na CHAR(1), a kolumna jest VARCHAR(1); przy ddl-auto: validate kończy się to błędem "wrong column type". Testy
    // jednostkowe nie startują Hibernate, więc pilnujemy tego wzorca, czytając źródła encji.

    private static final Pattern ENUM_STRING_WITH_LENGTH_ONE = Pattern.compile(
            "@Enumerated\\(\\s*EnumType\\.STRING\\s*\\)\\s*@Column\\([^)]*\\blength\\s*=\\s*1\\b[^)]*\\)", Pattern.DOTALL);

    @Test
    void noEntityMapsAnEnumAsTextWithLengthOne() throws IOException {
        Path sources = Path.of("src", "main", "java");
        assumeTrue(Files.isDirectory(sources), "uruchomiono poza katalogiem projektu");

        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(sources)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                if (ENUM_STRING_WITH_LENGTH_ONE.matcher(Files.readString(file)).find()) {
                    offenders.add(sources.relativize(file).toString());
                }
            }
        }

        assertThat(offenders)
                .as("enum jako tekst z length = 1 jest mapowany na CHAR(1), a kolumny w bazie są VARCHAR(1)")
                .isEmpty();
    }

    @Test
    void theEnumLengthOneGuardRecognisesTheOffendingPattern() {
        String bad = "@Enumerated(EnumType.STRING)\n@Column(name = \"selected_option\", length = 1)\nprivate CorrectOption x;";
        String good = "@Enumerated(EnumType.STRING)\n@Column(name = \"selected_option\")\nprivate CorrectOption x;";

        assertThat(ENUM_STRING_WITH_LENGTH_ONE.matcher(bad).find()).isTrue();
        assertThat(ENUM_STRING_WITH_LENGTH_ONE.matcher(good).find()).isFalse();
    }
}

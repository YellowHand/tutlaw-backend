package pl.gradusiuris.backend.review;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import pl.gradusiuris.backend.common.PageParams;
import pl.gradusiuris.backend.common.PageResponse;
import pl.gradusiuris.backend.common.exception.ResourceNotFoundException;
import pl.gradusiuris.backend.course.Course;
import pl.gradusiuris.backend.course.CourseRepository;
import pl.gradusiuris.backend.legal.ActSection;
import pl.gradusiuris.backend.legal.ActSectionRepository;
import pl.gradusiuris.backend.legal.LegalAct;
import pl.gradusiuris.backend.legal.LegalActRepository;
import pl.gradusiuris.backend.legal.LegalField;
import pl.gradusiuris.backend.legal.LegalFieldRepository;
import pl.gradusiuris.backend.pool.SourceFilter;
import pl.gradusiuris.backend.pool.SourceMode;
import pl.gradusiuris.backend.question.CorrectOption;
import pl.gradusiuris.backend.question.Question;
import pl.gradusiuris.backend.question.QuestionRepository;
import pl.gradusiuris.backend.review.dto.CustomTestRequest;
import pl.gradusiuris.backend.review.dto.CustomTestSelectionResponse;
import pl.gradusiuris.backend.review.dto.CustomTestSessionDetailResponse;
import pl.gradusiuris.backend.review.dto.CustomTestSessionSummaryResponse;
import pl.gradusiuris.backend.source.SourceKind;
import pl.gradusiuris.backend.testsupport.FakeRepos;
import pl.gradusiuris.backend.testsupport.MutableClock;
import pl.gradusiuris.backend.testsupport.Pools;
import pl.gradusiuris.backend.testsupport.QuestionRefs;
import pl.gradusiuris.backend.user.CurrentUserResolver;
import pl.gradusiuris.backend.user.User;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Zapisane własne testy na prawdziwym serwisie; baza "w pamięci" (atrapy repozytoriów na listach). */
class CustomTestSessionServiceTest {

    private final MutableClock clock = MutableClock.at("2026-10-12T10:00:00Z");
    private final List<CustomTestSession> sessions = new ArrayList<>();
    private final List<CustomTestSessionQuestion> rows = new ArrayList<>();
    private final Map<Long, Question> questions = new HashMap<>();
    private final Authentication authentication = mock(Authentication.class);
    private final CustomTestService selector = mock(CustomTestService.class);
    private final CurrentUserResolver resolver = mock(CurrentUserResolver.class);
    private final LegalFieldRepository fields = mock(LegalFieldRepository.class);
    private final LegalActRepository acts = mock(LegalActRepository.class);
    private final CourseRepository courses = mock(CourseRepository.class);
    private final QuestionRepository questionRepository = mock(QuestionRepository.class);
    private final ActSectionRepository sections = mock(ActSectionRepository.class);

    private CustomTestSessionService service;
    private User jan;
    private User ola;

    @BeforeEach
    void setUp() {
        jan = user(5L);
        ola = user(6L);
        when(resolver.resolve(authentication)).thenReturn(jan);
        for (long id = 1; id <= 12; id++) {
            questions.put(id, question(id));
        }
        when(questionRepository.findAllById(org.mockito.ArgumentMatchers.<Iterable<Long>>any())).thenAnswer(inv -> {
            List<Question> found = new ArrayList<>();
            for (Long id : (Iterable<Long>) inv.getArgument(0)) {
                if (questions.containsKey(id)) found.add(questions.get(id));
            }
            return found;
        });
        service = new CustomTestSessionService(selector, sessionRepo(), questionRepo(), questionRepository,
                fields, acts, courses, sections, resolver, clock);
    }

    private static User user(long id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    private static Question question(long id) {
        Question q = QuestionRefs.question(id, 1, 1, QuestionRefs.OWN_SHEET);
        q.setBody("Pytanie " + id);
        q.setOptionA("a" + id);
        q.setOptionB("b" + id);
        q.setOptionC("c" + id);
        q.setCorrectOption(CorrectOption.B);
        q.setExplanation("Bo " + id);
        q.setLegalBasis("art. " + id + " k.c.");
        q.getSource().setName("Egzamin notarialny 2021");
        return q;
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
                case "findAllForUser":
                    return newestFirst(args[0]);
                case "findPage": {
                    List<CustomTestSession> all = newestFirst(args[0]);
                    Pageable pageable = (Pageable) args[1];
                    int from = Math.min(all.size(), pageable.getPageNumber() * pageable.getPageSize());
                    int to = Math.min(all.size(), from + pageable.getPageSize());
                    return new PageImpl<>(all.subList(from, to), pageable, all.size());
                }
                case "countByUserIdAndFinishedAtIsNotNull":
                    return sessions.stream()
                            .filter(s -> s.getUser().getId().equals(args[0]) && s.isFinished()).count();
                default:
                    throw new UnsupportedOperationException("CustomTestSessionRepository." + method);
            }
        });
    }

    private List<CustomTestSession> newestFirst(Object userId) {
        return sessions.stream().filter(s -> s.getUser().getId().equals(userId))
                .sorted((a, b) -> b.getStartedAt().compareTo(a.getStartedAt()) != 0
                        ? b.getStartedAt().compareTo(a.getStartedAt()) : b.getId().compareTo(a.getId()))
                .toList();
    }

    private PageResponse<CustomTestSessionSummaryResponse> firstPage() {
        return service.list(PageParams.ofList(null, null), authentication);
    }

    private CustomTestSessionQuestionRepository questionRepo() {
        AtomicLong ids = new AtomicLong(1);
        return FakeRepos.proxy(CustomTestSessionQuestionRepository.class, (method, args) -> {
            switch (method) {
                case "save": {
                    CustomTestSessionQuestion q = (CustomTestSessionQuestion) args[0];
                    if (q.getId() == null) q.setId(ids.getAndIncrement());
                    if (!rows.contains(q)) rows.add(q);
                    return q;
                }
                case "saveAll": {
                    List<CustomTestSessionQuestion> saved = new ArrayList<>();
                    for (Object o : (Iterable<?>) args[0]) {
                        CustomTestSessionQuestion q = (CustomTestSessionQuestion) o;
                        if (q.getId() == null) q.setId(ids.getAndIncrement());
                        rows.add(q);
                        saved.add(q);
                    }
                    return saved;
                }
                case "findBySessionIds":
                    return rows.stream()
                            .filter(q -> ((java.util.Collection<?>) args[0]).contains(q.getSession().getId())).toList();
                case "findDetailed":
                    return rows.stream().filter(q -> q.getSession().getId().equals(args[0]))
                            .sorted((a, b) -> Integer.compare(a.getPosition(), b.getPosition())).toList();
                case "findBySessionIdAndQuestionId":
                    return rows.stream()
                            .filter(q -> q.getSession().getId().equals(args[0]) && q.getQuestion().getId().equals(args[1]))
                            .findFirst();
                case "countBySessionIdAndAnsweredAtIsNull":
                    return rows.stream()
                            .filter(q -> q.getSession().getId().equals(args[0]) && !q.isAnswered()).count();
                default:
                    throw new UnsupportedOperationException("CustomTestSessionQuestionRepository." + method);
            }
        });
    }

    private Long startTest(List<Long> questionIds, CustomTestRequest request) {
        when(selector.select(request, authentication))
                .thenReturn(new CustomTestSelectionResponse(questionIds, 40, request.questionCount(), null));
        return service.draw(request, authentication).sessionId();
    }

    private Long startTest(List<Long> questionIds) {
        return startTest(questionIds, new CustomTestRequest(null, null, null, null, null, CustomTestKind.ALL, 10));
    }

    private void answer(Long sessionId, long questionId, CorrectOption selected, boolean correct, boolean guessed, Double time) {
        service.recordAnswer(5L, sessionId, questionId, selected, correct, guessed, time);
    }

    private static LegalField field(long id, String name) {
        LegalField f = QuestionRefs.field(id);
        f.setName(name);
        return f;
    }

    private static Course course(long id, String name) {
        Course c = new Course();
        c.setId(id);
        c.setName(name);
        return c;
    }

    private static LegalAct act(long id, String name) {
        LegalAct a = QuestionRefs.act(id, 1);
        a.setName(name);
        return a;
    }

    // ---------------------------------------------------------------- zapis przy ułożeniu testu

    @Test
    void drawSavesTheConfigurationAndTheQuestionsInOrder() {
        when(fields.findAllById(List.of(2L))).thenReturn(List.of(field(2L, "Prawo karne i proces karny")));
        when(acts.findAllById(List.of(9L))).thenReturn(List.of(act(9L, "Kodeks cywilny")));

        CustomTestRequest request = new CustomTestRequest(List.of(2L), Arrays.asList(9L, 9L, null), null, null,
                SourceMode.AUTHORED, CustomTestKind.MISTAKES, 20);
        Long id = startTest(List.of(7L, 3L, 11L), request);

        assertThat(id).isNotNull();
        CustomTestSession saved = sessions.get(0);
        assertThat(saved.getUser()).isSameAs(jan);
        assertThat(saved.getKind()).isEqualTo(CustomTestKind.MISTAKES);
        assertThat(saved.getScopeLabel()).isEqualTo("Prawo karne i proces karny i Kodeks cywilny");
        assertThat(saved.getFieldIds()).isEqualTo("2");
        assertThat(saved.getActIds()).isEqualTo("9");
        assertThat(saved.getSourceGroups()).isEqualTo("AUTHORED");
        assertThat(saved.getRequestedCount()).isEqualTo(20);
        assertThat(saved.getAvailableCount()).isEqualTo(40);
        assertThat(saved.getStartedAt()).isNotNull();
        assertThat(saved.isFinished()).isFalse();
        assertThat(rows).extracting(q -> q.getQuestion().getId()).containsExactly(7L, 3L, 11L);
        assertThat(rows).extracting(CustomTestSessionQuestion::getPosition).containsExactly(1, 2, 3);
        assertThat(rows).noneMatch(CustomTestSessionQuestion::isAnswered);
    }

    @Test
    void aTestOverTheWholePoolAndAllSourcesIsStoredWithoutRestrictions() {
        startTest(List.of(1L, 2L));

        CustomTestSession saved = sessions.get(0);
        assertThat(saved.getScopeLabel()).isEqualTo("Cała pula");
        assertThat(saved.getFieldIds()).isEmpty();
        assertThat(saved.getActIds()).isEmpty();
        assertThat(saved.getSectionIds()).isEmpty();
        assertThat(saved.getSourceGroups()).isNull();
    }

    @Test
    void aTestFromSubjectsStoresTheSubjectsAndTheirActsAndNamesTheScopeAfterThem() {
        Course karne = course(10L, "Prawo karne");
        Course cywilne = course(12L, "Prawo cywilne");
        when(courses.findAllById(List.of(10L, 12L))).thenReturn(List.of(cywilne, karne));
        when(courses.findActIdsByCourseIds(List.of(10L, 12L))).thenReturn(List.of(4L, 5L, 4L, 9L));
        when(acts.findAllById(List.of(9L))).thenReturn(List.of(act(9L, "Kodeks cywilny")));

        CustomTestRequest request = new CustomTestRequest(null, List.of(9L), List.of(10L, 12L, 10L), null, null,
                CustomTestKind.LAW_CHANGE, 10);
        startTest(List.of(1L, 2L), request);

        CustomTestSession saved = sessions.get(0);
        assertThat(saved.getKind()).isEqualTo(CustomTestKind.LAW_CHANGE);
        assertThat(saved.getCourseIds()).isEqualTo("10,12");
        // akty wybrane wprost najpierw, potem akty przedmiotów bez powtórzeń
        assertThat(saved.getActIds()).isEqualTo("9,4,5");
        // nazwy w kolejności wyboru: przedmioty, potem akty wybrane wprost (nie akty przedmiotów)
        assertThat(saved.getScopeLabel()).isEqualTo("Prawo karne, Prawo cywilne i Kodeks cywilny");
    }

    @Test
    void aTestWithoutSubjectsStoresNoCourseIds() {
        startTest(List.of(1L));

        assertThat(sessions.get(0).getCourseIds()).isEmpty();
    }

    @Test
    void aSubjectThatNoLongerExistsFallsBackToAGeneralLabel() {
        CustomTestRequest request = new CustomTestRequest(null, null, List.of(77L), null, null, CustomTestKind.ALL, 10);
        when(courses.findAllById(List.of(77L))).thenReturn(List.of());
        when(courses.findActIdsByCourseIds(List.of(77L))).thenReturn(List.of());

        startTest(List.of(1L), request);

        assertThat(sessions.get(0).getScopeLabel()).isEqualTo("Wybrane działy");
        assertThat(sessions.get(0).getCourseIds()).isEqualTo("77");
    }

    @Test
    void idListsAreCutOnAWholeNumberToFitTheirColumns() {
        assertThat(CustomTestSessionService.fitting("1,2,3", 500)).isEqualTo("1,2,3");
        assertThat(CustomTestSessionService.fitting("", 500)).isEmpty();
        assertThat(CustomTestSessionService.fitting("11,22,33", 7)).isEqualTo("11,22");
        assertThat(CustomTestSessionService.fitting("11,22,33", 5)).isEqualTo("11,22");
        assertThat(CustomTestSessionService.fitting("11,22,33", 4)).isEqualTo("11");
        assertThat(CustomTestSessionService.fitting("123456789", 4)).isEmpty();

        String longList = java.util.stream.LongStream.rangeClosed(1_000_000, 1_000_200)
                .mapToObj(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        String fitted = CustomTestSessionService.fitting(longList, CustomTestSessionService.COURSE_IDS_LENGTH);
        assertThat(fitted.length()).isLessThanOrEqualTo(CustomTestSessionService.COURSE_IDS_LENGTH);
        assertThat(longList).startsWith(fitted);
        assertThat(fitted).doesNotEndWith(",");
        assertThat(CustomTestSessionService.parseIds(fitted)).allMatch(id -> id >= 1_000_000);
    }

    @Test
    void selectionFailureSavesNothing() {
        CustomTestRequest request = new CustomTestRequest(null, null, null, null, null, CustomTestKind.NEW, 10);
        when(selector.select(request, authentication)).thenThrow(new IllegalArgumentException("Brak pytań"));

        assertThatThrownBy(() -> service.draw(request, authentication)).isInstanceOf(IllegalArgumentException.class);

        assertThat(sessions).isEmpty();
        assertThat(rows).isEmpty();
    }

    @Test
    void unknownSelectedItemsFallBackToAGeneralLabel() {
        CustomTestRequest request = new CustomTestRequest(List.of(77L), null, null, null, null, CustomTestKind.ALL, 10);
        when(fields.findAllById(List.of(77L))).thenReturn(List.of());

        startTest(List.of(1L), request);

        assertThat(sessions.get(0).getScopeLabel()).isEqualTo("Wybrane działy");
    }

    @Test
    void questionsThatNoLongerExistAreSkippedAndPositionsStayContinuous() {
        startTest(List.of(1L, 999L, 2L));

        assertThat(rows).extracting(q -> q.getQuestion().getId()).containsExactly(1L, 2L);
        assertThat(rows).extracting(CustomTestSessionQuestion::getPosition).containsExactly(1, 2);
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
        CustomTestSessionQuestion second = rows.get(1);
        assertThat(second.getSelectedOption()).isEqualTo(CorrectOption.A);
        assertThat(second.getWasCorrect()).isFalse();
        assertThat(second.getTimeSpentSeconds()).isEqualTo(12.0);
        assertThat(rows.get(2).isWasGuessed()).isTrue();
    }

    @Test
    void anAbandonedTestStaysUnfinishedWithItsPartialResults() {
        Long id = startTest(List.of(1L, 2L, 3L));
        answer(id, 1, CorrectOption.B, true, false, 5.0);

        CustomTestSessionSummaryResponse summary = firstPage().items().get(0);

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

        CustomTestSessionQuestion first = rows.get(0);
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

        assertThat(rows).noneMatch(CustomTestSessionQuestion::isAnswered);
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

        CustomTestSessionSummaryResponse summary = firstPage().items().get(0);

        assertThat(summary.id()).isEqualTo(id);
        assertThat(summary.finished()).isTrue();
        assertThat(summary.questionCount()).isEqualTo(4);
        assertThat(summary.answeredCount()).isEqualTo(4);
        assertThat(summary.correctCount()).isEqualTo(3);
        assertThat(summary.guessedCount()).isEqualTo(1);
        assertThat(summary.timeSeconds()).isEqualTo(10.0 + 30.0 + 120.0);
        assertThat(summary.kind()).isEqualTo(CustomTestKind.ALL);
        assertThat(summary.requestedCount()).isEqualTo(10);
        assertThat(summary.source()).isEqualTo(SourceMode.ALL);
        assertThat(summary.fieldIds()).isEmpty();
        assertThat(summary.actIds()).isEmpty();
        assertThat(summary.sectionIds()).isEmpty();
    }

    @Test
    void theListShowsTheSavedScopeSoTheTestCanBeBuiltAgain() {
        when(fields.findAllById(List.of(2L, 4L))).thenReturn(List.of(field(2L, "Podatki i opłaty"), field(4L, "Prawo pracy")));
        when(acts.findAllById(List.of(9L))).thenReturn(List.of(act(9L, "Kodeks pracy")));

        startTest(List.of(1L), new CustomTestRequest(List.of(2L, 4L), List.of(9L), null, null, SourceMode.MINISTRY,
                CustomTestKind.DUE, 10));

        CustomTestSessionSummaryResponse summary = firstPage().items().get(0);
        assertThat(summary.fieldIds()).containsExactly(2L, 4L);
        assertThat(summary.actIds()).containsExactly(9L);
        assertThat(summary.source()).isEqualTo(SourceMode.MINISTRY);
        assertThat(summary.kind()).isEqualTo(CustomTestKind.DUE);
    }

    @Test
    void theListIsNewestFirstAndOnlyContainsTheCallersTests() {
        startTest(List.of(1L));
        clock.advance(Duration.ofHours(1));
        Long newer = startTest(List.of(2L));
        CustomTestSession foreign = new CustomTestSession();
        foreign.setId(500L);
        foreign.setUser(ola);
        foreign.setStartedAt(LocalDateTime.now(clock));
        foreign.setKind(CustomTestKind.ALL);
        foreign.setScopeLabel("Cudzy");
        sessions.add(foreign);

        List<CustomTestSessionSummaryResponse> list = firstPage().items();

        assertThat(list).hasSize(2);
        assertThat(list.get(0).id()).isEqualTo(newer);
    }

    @Test
    void theListIsEmptyWhenThereAreNoTests() {
        assertThat(firstPage().items()).isEmpty();
        assertThat(firstPage().totalElements()).isZero();
    }

    @Test
    void theListIsPaged_withTheTotalsOfAllTheCallersTests() {
        Long oldest = startTest(List.of(1L));
        clock.advance(Duration.ofHours(1));
        Long middle = startTest(List.of(2L));
        clock.advance(Duration.ofHours(1));
        Long newest = startTest(List.of(3L));

        PageResponse<CustomTestSessionSummaryResponse> first = service.list(PageParams.ofList(0, 2), authentication);
        PageResponse<CustomTestSessionSummaryResponse> second = service.list(PageParams.ofList(1, 2), authentication);

        assertThat(first.items()).extracting(CustomTestSessionSummaryResponse::id).containsExactly(newest, middle);
        assertThat(second.items()).extracting(CustomTestSessionSummaryResponse::id).containsExactly(oldest);
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(second.page()).isEqualTo(1);
        assertThat(second.size()).isEqualTo(2);
    }

    @Test
    void aPageBeyondTheEndIsEmpty_butStillReportsTheTotals() {
        startTest(List.of(1L));

        PageResponse<CustomTestSessionSummaryResponse> beyond = service.list(PageParams.ofList(5, 20), authentication);

        assertThat(beyond.items()).isEmpty();
        assertThat(beyond.totalElements()).isEqualTo(1);
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
        assertThat(first.questionId()).isEqualTo(4L);
        assertThat(first.body()).isEqualTo("Pytanie 4");
        assertThat(first.optionA()).isEqualTo("a4");
        assertThat(first.correctOption()).isEqualTo(CorrectOption.B);
        assertThat(first.selectedOption()).isEqualTo(CorrectOption.A);
        assertThat(first.correct()).isFalse();
        assertThat(first.guessed()).isTrue();
        assertThat(first.timeSeconds()).isEqualTo(9.5);
        assertThat(first.explanation()).isEqualTo("Bo 4");
        assertThat(first.legalBasis()).isEqualTo("art. 4 k.c.");
        assertThat(first.actName()).isEqualTo("Akt 1");
        assertThat(first.sourceName()).isEqualTo("Egzamin notarialny 2021");
        assertThat(first.sourceKind()).isEqualTo(SourceKind.EXAM_SHEET);
        assertThat(first.examYear()).isEqualTo(2021);
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
    void theSourceIsStoredAsTheGroupsOfItsFilter_andReadBackAsTheSameMode() {
        assertThat(SourceFilter.ALL.serialize()).isNull();
        for (SourceMode mode : SourceMode.values()) {
            assertThat(SourceMode.fromFilter(SourceFilter.parse(mode.filter().serialize()))).isEqualTo(mode);
        }
        assertThat(SourceMode.MINISTRY.filter().serialize()).isEqualTo("OWN_SHEETS,OTHER_SHEETS");
        assertThat(SourceMode.AUTHORED.filter().serialize()).isEqualTo("AUTHORED");
    }

    @Test
    void aTestSavedWithAnOlderFilterIsReadBackAsTheClosestMode() {
        // zapisane przed Etapem 34, kiedy filtr źródeł był ustawieniem konta i mógł być dowolną mieszanką grup
        assertThat(SourceMode.fromFilter(SourceFilter.parse(null))).isEqualTo(SourceMode.ALL);
        assertThat(SourceMode.fromFilter(SourceFilter.parse("OWN_SHEETS"))).isEqualTo(SourceMode.MINISTRY);
        assertThat(SourceMode.fromFilter(SourceFilter.parse("OTHER_SHEETS"))).isEqualTo(SourceMode.MINISTRY);
        assertThat(SourceMode.fromFilter(SourceFilter.parse("OWN_SHEETS,AUTHORED"))).isEqualTo(SourceMode.ALL);
        assertThat(SourceMode.fromFilter(SourceFilter.parse("AUTHORED"))).isEqualTo(SourceMode.AUTHORED);
        // uszkodzony zapis nie ukrywa materiału
        assertThat(SourceMode.fromFilter(SourceFilter.parse("COS,INNEGO"))).isEqualTo(SourceMode.ALL);
    }

    // ---------------------------------------------------------------- rozdziały

    private static ActSection section(long id, String actName, String name) {
        ActSection section = new ActSection();
        section.setId(id);
        section.setLegalAct(act(100 + id, actName));
        section.setName(name);
        return section;
    }

    @Test
    void aTestFromSectionsStoresTheirNumbersAndNamesThemAfterTheirAct() {
        when(sections.findAllById(List.of(7L, 9L))).thenReturn(List.of(
                section(9L, "Kodeks karny", "XXXV. Przeciwko mieniu"),
                section(7L, "Kodeks karny", "XIX. Przeciwko życiu i zdrowiu")));

        CustomTestRequest request = new CustomTestRequest(null, null, null, Arrays.asList(7L, 9L, 7L, null), null,
                CustomTestKind.ALL, 20);
        startTest(List.of(1L, 2L), request);

        CustomTestSession saved = sessions.get(0);
        assertThat(saved.getSectionIds()).isEqualTo("7,9");
        // kolejność wyboru, nie kolejność z bazy
        assertThat(saved.getScopeLabel()).isEqualTo(
                "Kodeks karny: XIX. Przeciwko życiu i zdrowiu i Kodeks karny: XXXV. Przeciwko mieniu");
    }

    @Test
    void aSectionThatNoLongerExistsFallsBackToAGeneralLabel() {
        when(sections.findAllById(List.of(77L))).thenReturn(List.of());

        startTest(List.of(1L), new CustomTestRequest(null, null, null, List.of(77L), null, CustomTestKind.ALL, 10));

        assertThat(sessions.get(0).getScopeLabel()).isEqualTo("Wybrane działy");
        assertThat(sessions.get(0).getSectionIds()).isEqualTo("77");
    }

    @Test
    void theListShowsTheSavedSectionsSoTheTestCanBeBuiltAgain() {
        when(sections.findAllById(List.of(7L))).thenReturn(List.of(section(7L, "Kodeks karny", "XIX. Przeciwko życiu")));

        startTest(List.of(1L), new CustomTestRequest(null, null, null, List.of(7L), SourceMode.AUTHORED, CustomTestKind.NEW, 10));

        CustomTestSessionSummaryResponse summary = firstPage().items().get(0);
        assertThat(summary.sectionIds()).containsExactly(7L);
        assertThat(summary.source()).isEqualTo(SourceMode.AUTHORED);
    }
}

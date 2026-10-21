package pl.tutlaw.backend.review;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import pl.tutlaw.backend.course.CourseRepository;
import pl.tutlaw.backend.mark.QuestionMarkRepository;
import pl.tutlaw.backend.mark.QuestionMarkType;
import pl.tutlaw.backend.pool.Pool;
import pl.tutlaw.backend.pool.SourceMode;
import pl.tutlaw.backend.question.LawChangePolicy;
import pl.tutlaw.backend.question.QuestionRef;
import pl.tutlaw.backend.question.QuestionRepository;
import pl.tutlaw.backend.review.dto.CustomTestCountsResponse;
import pl.tutlaw.backend.review.dto.CustomTestRequest;
import pl.tutlaw.backend.review.dto.CustomTestSelectionResponse;
import pl.tutlaw.backend.review.fsrs.FsrsEngine;
import pl.tutlaw.backend.review.fsrs.FsrsWeights;
import pl.tutlaw.backend.testsupport.MutableClock;
import pl.tutlaw.backend.testsupport.Pools;
import pl.tutlaw.backend.testsupport.QuestionRefs;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Własny test: pula pytań, rodzaje, zakres, losowanie i komunikaty, na prawdziwym serwisie z atrapami repozytoriów. */
@ExtendWith(MockitoExtension.class)
class CustomTestServiceTest {

    // 2026-10-12 12:00 czasu warszawskiego
    private final MutableClock clock = MutableClock.at("2026-10-12T10:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 12, 0);

    @Mock private QuestionRepository questionRepository;
    @Mock private UserQuestionStateRepository stateRepository;
    @Mock private MistakesService mistakesService;
    @Mock private CurrentUserResolver currentUserResolver;
    @Mock private CourseRepository courseRepository;
    @Mock private QuestionMarkRepository markRepository;
    private final Authentication authentication = mock(Authentication.class);
    /** Pula użytkownika; testy filtra źródeł ją podmieniają. */
    private Pool pool = Pools.all();

    private CustomTestService service;

    @BeforeEach
    void setUp() {
        service = new CustomTestService(questionRepository, stateRepository, mistakesService, currentUserResolver,
                new FsrsEngine(FsrsWeights.defaults(), 0.9), clock, Pools.holding(() -> pool),
                new LawChangePolicy(clock, 365), courseRepository, markRepository);
        User user = new User();
        user.setId(5L);
        lenient().when(currentUserResolver.resolve(authentication)).thenReturn(user);
        lenient().when(stateRepository.findScheduleByUserIdAndMode(5L, ReviewMode.TEST)).thenReturn(List.of());
        lenient().when(mistakesService.recentMistakeQuestionIds(5L)).thenReturn(List.of());
    }

    // ---------------------------------------------------------------- dane

    /** Pytanie w akcie 1, dziedzinie 1 i własnym arkuszu. */
    private static QuestionRef ref(long id) {
        return ref(id, 1, 1, QuestionRefs.OWN_SHEET);
    }

    private static QuestionRef ref(long id, long actId, long fieldId, long sourceId) {
        return QuestionRefs.refOf(QuestionRefs.question(id, actId, fieldId, sourceId));
    }

    /** Pytanie w akcie 1 i własnym arkuszu ze zmianą w prawie z podanego dnia. */
    private static QuestionRef lawChanged(long id, LocalDate changedAt) {
        var question = QuestionRefs.question(id, 1, 1, QuestionRefs.OWN_SHEET);
        question.setLawChangedAt(changedAt);
        return QuestionRefs.refOf(question);
    }

    private static List<QuestionRef> refs(long from, long to) {
        List<QuestionRef> list = new ArrayList<>();
        for (long id = from; id <= to; id++) {
            list.add(ref(id));
        }
        return list;
    }

    private static UserQuestionStateRepository.QuestionDue state(
            long questionId, LocalDateTime due, LocalDateTime lastReview, boolean suspended) {
        return new UserQuestionStateRepository.QuestionDue() {
            public Long getQuestionId() { return questionId; }
            public LocalDateTime getDueDate() { return due; }
            public Double getStability() { return 5.0; }
            public LocalDateTime getLastReviewAt() { return lastReview; }
            public Integer getLapses() { return 0; }
            public Boolean getSuspended() { return suspended; }
        };
    }

    /** Pytanie z minionym terminem, ostatnio powtarzane {@code daysAgo} dni temu (im dawniej, tym pilniej). */
    private static UserQuestionStateRepository.QuestionDue overdue(long questionId, int daysAgo) {
        return state(questionId, NOW.minusDays(1), NOW.minusDays(daysAgo), false);
    }

    private static UserQuestionStateRepository.QuestionDue notYetDue(long questionId) {
        return state(questionId, NOW.plusDays(3), NOW.minusDays(2), false);
    }

    /** Pytania puli użytkownika (repozytorium zwraca je niezależnie od argumentów: filtry stosuje serwis). */
    private void wholePool(List<QuestionRef> refs) {
        when(questionRepository.findRefsInPool(
                ArgumentMatchers.<java.util.Collection<Long>>any(), ArgumentMatchers.<java.util.Collection<Long>>any()))
                .thenReturn(refs);
    }

    private void schedule(UserQuestionStateRepository.QuestionDue... rows) {
        when(stateRepository.findScheduleByUserIdAndMode(5L, ReviewMode.TEST)).thenReturn(List.of(rows));
    }

    private static CustomTestRequest request(CustomTestKind kind, int count) {
        return new CustomTestRequest(null, null, null, null, null, kind, count);
    }

    private static CustomTestRequest request(SourceMode source, CustomTestKind kind, int count) {
        return new CustomTestRequest(null, null, null, null, source, kind, count);
    }

    private CustomTestCountsResponse counts() {
        return service.counts(null, null, null, null, null, authentication);
    }

    private CustomTestCountsResponse counts(SourceMode source) {
        return service.counts(null, null, null, null, source, authentication);
    }

    private static Set<Long> set(List<Long> ids) {
        return ids.stream().collect(Collectors.toSet());
    }

    // ---------------------------------------------------------------- liczniki

    @Test
    void counts_splitQuestionsIntoKinds() {
        wholePool(refs(1, 6));
        // 1, 2: zaległe; 3: jeszcze nie ich czas; 4: zawieszone; 5, 6: nowe
        schedule(overdue(1, 10), overdue(2, 20), notYetDue(3), state(4, NOW.minusDays(1), NOW.minusDays(5), true));
        when(mistakesService.recentMistakeQuestionIds(5L)).thenReturn(List.of(3L, 4L, 9L));

        CustomTestCountsResponse counts = counts();

        assertThat(counts.all()).isEqualTo(5);          // bez zawieszonej 4
        assertThat(counts.newQuestions()).isEqualTo(2); // 5, 6
        assertThat(counts.due()).isEqualTo(2);          // 1, 2
        assertThat(counts.mistakes()).isEqualTo(1);     // tylko 3: 4 jest zawieszona, 9 poza zakresem
    }

    @Test
    void counts_areZeroForEmptyScopeAndDoNotScanMistakes() {
        wholePool(List.of());

        CustomTestCountsResponse counts = counts();

        assertThat(counts).isEqualTo(new CustomTestCountsResponse(0, 0, 0, 0, 0, 0, 0));
        verify(mistakesService, never()).recentMistakeQuestionIds(ArgumentMatchers.anyLong());
    }

    // ---------------------------------------------------------------- zakres

    @Test
    void withoutScope_usesTheWholePool() {
        wholePool(refs(1, 3));

        assertThat(counts().all()).isEqualTo(3);
    }

    @Test
    void scope_isUnionOfFieldsAndActs_withoutDuplicatesOrNulls() {
        wholePool(List.of(
                ref(1, 10, 2, QuestionRefs.OWN_SHEET),   // dziedzina 2
                ref(2, 11, 2, QuestionRefs.OWN_SHEET),   // dziedzina 2
                ref(3, 7, 3, QuestionRefs.OWN_SHEET),    // akt 7
                ref(4, 8, 3, QuestionRefs.OWN_SHEET),    // akt 8
                ref(5, 9, 3, QuestionRefs.OWN_SHEET),    // poza zakresem
                ref(6, 12, 4, QuestionRefs.OWN_SHEET))); // poza zakresem

        CustomTestCountsResponse counts = service.counts(
                java.util.Arrays.asList(2L, 2L, null), List.of(7L, 8L), null, null, null, authentication);

        assertThat(counts.all()).isEqualTo(4);
        assertThat(set(service.select(
                new CustomTestRequest(List.of(2L), List.of(7L, 8L), null, null, null, CustomTestKind.ALL, 10), authentication).questionIds()))
                .containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
    }

    @Test
    void scope_withOnlyActs_ignoresFields() {
        wholePool(List.of(ref(1, 7, 3, QuestionRefs.OWN_SHEET), ref(2, 8, 3, QuestionRefs.OWN_SHEET)));

        assertThat(service.counts(null, List.of(7L), null, null, null, authentication).all()).isEqualTo(1);
        assertThat(service.counts(List.of(), List.of(), null, null, null, authentication).all()).isEqualTo(2);
    }

    // ---------------------------------------------------------------- rozdziały

    @Test
    void sections_narrowTheScopeToTheirQuestions() {
        wholePool(List.of(
                ref(1, 10, 2, QuestionRefs.OWN_SHEET),   // rozdział 200 aktu 10
                ref(2, 10, 2, QuestionRefs.OWN_SHEET),   // rozdział 201 aktu 10
                ref(3, 10, 2, QuestionRefs.OWN_SHEET),   // akt 10 bez rozdziału
                ref(4, 11, 2, QuestionRefs.OWN_SHEET))); // inny akt
        when(questionRepository.findIdsBySectionIds(List.of(200L))).thenReturn(List.of(1L));

        CustomTestCountsResponse counts = service.counts(null, null, null, List.of(200L), null, authentication);

        assertThat(counts.all()).isEqualTo(1);
        assertThat(service.select(
                new CustomTestRequest(null, null, null, List.of(200L), null, CustomTestKind.ALL, 10), authentication)
                .questionIds()).containsExactly(1L);
    }

    @Test
    void sections_areUnitedWithWholeActsAndFields() {
        wholePool(List.of(
                ref(1, 10, 2, QuestionRefs.OWN_SHEET),   // rozdział 200 aktu 10
                ref(2, 10, 2, QuestionRefs.OWN_SHEET),   // akt 10 bez rozdziału: poza zakresem
                ref(3, 7, 3, QuestionRefs.OWN_SHEET),    // akt 7 wybrany w całości
                ref(4, 12, 5, QuestionRefs.OWN_SHEET),   // dziedzina 5 wybrana w całości
                ref(5, 13, 6, QuestionRefs.OWN_SHEET))); // poza zakresem
        when(questionRepository.findIdsBySectionIds(List.of(200L))).thenReturn(List.of(1L));

        CustomTestRequest request = new CustomTestRequest(
                List.of(5L), List.of(7L), null, List.of(200L), null, CustomTestKind.ALL, 10);

        assertThat(set(service.select(request, authentication).questionIds())).containsExactlyInAnyOrder(1L, 3L, 4L);
    }

    @Test
    void sections_withoutAnyQuestion_doNotWidenTheScopeToTheWholePool() {
        wholePool(refs(1, 5));
        when(questionRepository.findIdsBySectionIds(List.of(999L))).thenReturn(List.of());

        assertThat(service.counts(null, null, null, List.of(999L), null, authentication).all()).isZero();
        assertThatThrownBy(() -> service.select(
                new CustomTestRequest(null, null, null, List.of(999L), null, CustomTestKind.ALL, 10), authentication))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sections_areNotLookedUpWhenNoneAreChosen() {
        wholePool(refs(1, 3));

        assertThat(counts().all()).isEqualTo(3);
        assertThat(service.counts(null, null, null, List.of(), null, authentication).all()).isEqualTo(3);
        verify(questionRepository, never()).findIdsBySectionIds(ArgumentMatchers.<java.util.Collection<Long>>any());
    }

    @Test
    void sections_aQuestionOutsideThePoolIsNotLetInBySection() {
        // pytanie 9 jest w rozdziale, ale poza pulą użytkownika (repozytorium puli go nie zwraca)
        wholePool(List.of(ref(1, 10, 2, QuestionRefs.OWN_SHEET)));
        when(questionRepository.findIdsBySectionIds(List.of(200L))).thenReturn(List.of(1L, 9L));

        assertThat(service.counts(null, null, null, List.of(200L), null, authentication).all()).isEqualTo(1);
    }

    // ---------------------------------------------------------------- źródło

    private static List<QuestionRef> oneFromEachSource() {
        return List.of(
                ref(1, 1, 1, QuestionRefs.OWN_SHEET),
                ref(2, 1, 1, QuestionRefs.OTHER_SHEET),
                ref(3, 1, 1, QuestionRefs.AUTHORED));
    }

    @Test
    void source_allIsTheDefault_andTakesEveryGroup() {
        wholePool(oneFromEachSource());

        assertThat(counts().all()).isEqualTo(3);
        assertThat(counts(SourceMode.ALL).all()).isEqualTo(3);
    }

    @Test
    void source_ministry_takesBothKindsOfExamSheetsAndNoAuthoredQuestions() {
        wholePool(oneFromEachSource());

        assertThat(counts(SourceMode.MINISTRY).all()).isEqualTo(2);
        assertThat(set(service.select(request(SourceMode.MINISTRY, CustomTestKind.ALL, 10), authentication).questionIds()))
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void source_authored_takesOnlyAuthoredQuestions() {
        wholePool(oneFromEachSource());

        assertThat(counts(SourceMode.AUTHORED).all()).isEqualTo(1);
        assertThat(service.select(request(SourceMode.AUTHORED, CustomTestKind.ALL, 10), authentication).questionIds())
                .containsExactly(3L);
    }

    @Test
    void source_appliesToEveryKind() {
        wholePool(List.of(
                QuestionRefs.refOf(withLawChange(QuestionRefs.question(1, 1, 1, QuestionRefs.OWN_SHEET))),
                QuestionRefs.refOf(withLawChange(QuestionRefs.question(2, 1, 1, QuestionRefs.AUTHORED))),
                ref(3, 1, 1, QuestionRefs.OWN_SHEET),
                ref(4, 1, 1, QuestionRefs.AUTHORED)));
        schedule(overdue(1, 5), overdue(2, 5));
        when(mistakesService.recentMistakeQuestionIds(5L)).thenReturn(List.of(1L, 2L));

        CustomTestCountsResponse authored = counts(SourceMode.AUTHORED);

        assertThat(authored.all()).isEqualTo(2);          // 2, 4
        assertThat(authored.newQuestions()).isEqualTo(1); // 4
        assertThat(authored.due()).isEqualTo(1);          // 2
        assertThat(authored.mistakes()).isEqualTo(1);     // 2
        assertThat(authored.lawChanges()).isEqualTo(1);   // 2
    }

    @Test
    void source_combinesWithTheScope() {
        wholePool(List.of(
                ref(1, 7, 3, QuestionRefs.OWN_SHEET),
                ref(2, 7, 3, QuestionRefs.AUTHORED),
                ref(3, 8, 3, QuestionRefs.AUTHORED)));

        assertThat(service.counts(null, List.of(7L), null, null, SourceMode.AUTHORED, authentication).all()).isEqualTo(1);
    }

    @Test
    void suspendedQuestions_neverEnterAnyKind() {
        LocalDate recent = LocalDate.of(2026, 10, 1);
        wholePool(List.of(lawChanged(1, recent), lawChanged(2, recent), lawChanged(3, recent), lawChanged(4, recent)));
        // 1 i 2 zawieszone (z minionym terminem i z błędem), 3 zaległe, 4 nowe; wszystkie ze świeżą zmianą w prawie
        schedule(state(1, NOW.minusDays(1), NOW.minusDays(5), true), state(2, NOW.minusDays(1), NOW.minusDays(5), true),
                overdue(3, 5));
        when(mistakesService.recentMistakeQuestionIds(5L)).thenReturn(List.of(1L, 2L, 3L));
        confirmedMarks(List.of(1L, 2L, 3L, 4L), List.of(1L, 2L, 3L, 4L)); // także oznaczone pytania zawieszone nie wchodzą

        for (CustomTestKind kind : CustomTestKind.values()) {
            assertThat(service.select(request(kind, 10), authentication).questionIds())
                    .as(kind.name()).doesNotContain(1L, 2L);
        }
    }

    // ---------------------------------------------------------------- zmiany w prawie

    @Test
    void lawChange_takesOnlyQuestionsWhoseChangeAlreadyAppliesAndIsWithinTheLastYear() {
        wholePool(List.of(
                lawChanged(1, LocalDate.of(2026, 10, 12)),   // dziś: świeża
                lawChanged(2, LocalDate.of(2025, 10, 12)),   // dokładnie rok temu: jeszcze świeża
                lawChanged(3, LocalDate.of(2025, 10, 11)),   // rok i dzień temu: za stara
                lawChanged(4, LocalDate.of(2026, 10, 13)),   // jutro: jeszcze nie obowiązuje
                ref(5)));                                     // bez zmiany

        CustomTestCountsResponse counts = counts();

        assertThat(counts.lawChanges()).isEqualTo(2);
        assertThat(counts.all()).isEqualTo(5);
        assertThat(set(service.select(request(CustomTestKind.LAW_CHANGE, 10), authentication).questionIds()))
                .containsExactlyInAnyOrder(1L, 2L);
    }

    // ---------------------------------------------------------------- terminy i pułapki (oznaczenia administratora)

    /** Numery pytań z zatwierdzonym oznaczeniem terminu i któregokolwiek podtypu pułapki (propozycji baza nie zwraca). */
    private void confirmedMarks(List<Long> timeLimit, List<Long> traps) {
        lenient().doReturn(timeLimit).when(markRepository).findConfirmedQuestionIds(List.of(QuestionMarkType.TIME_LIMIT));
        lenient().doReturn(traps).when(markRepository).findConfirmedQuestionIds(CustomTestService.TRAP_MARKS);
    }

    @Test
    void timeLimit_takesOnlyQuestionsWithAConfirmedMark_thatAreInThePool() {
        wholePool(refs(1, 5));
        // 3 ma zatwierdzone oznaczenie, 99 jest poza pulą; propozycji i odrzuconych repozytorium w ogóle nie zwraca
        confirmedMarks(List.of(3L, 99L), List.of());

        assertThat(counts().timeLimits()).isEqualTo(1);
        assertThat(set(service.select(request(CustomTestKind.TIME_LIMIT, 10), authentication).questionIds()))
                .containsExactly(3L);
    }

    @Test
    void traps_takeQuestionsWithAnyConfirmedTrapMark_butNotTheTimeLimitOnes() {
        wholePool(refs(1, 6));
        confirmedMarks(List.of(5L), List.of(2L, 4L));

        assertThat(counts().traps()).isEqualTo(2);
        assertThat(set(service.select(request(CustomTestKind.TRAPS, 10), authentication).questionIds()))
                .containsExactlyInAnyOrder(2L, 4L);
        // do pułapek należą wszystkie podtypy TRAP_*, a pytanie o termin nie jest pułapką samo z siebie
        assertThat(CustomTestService.TRAP_MARKS).containsExactlyInAnyOrder(
                QuestionMarkType.TRAP_NEGATION, QuestionMarkType.TRAP_ABSOLUTE, QuestionMarkType.TRAP_NUMBERS,
                QuestionMarkType.TRAP_CONJUNCTION, QuestionMarkType.TRAP_OUTDATED_LAW);
    }

    @Test
    void timeLimitAndTraps_areStillSubjectToTheSuspendedQuestions() {
        wholePool(refs(1, 3));
        schedule(state(2, NOW.plusDays(1), NOW.minusDays(5), true)); // 2 zawieszone w testach
        confirmedMarks(List.of(1L, 2L), List.of(2L, 3L));

        CustomTestCountsResponse counts = counts();

        assertThat(counts.timeLimits()).isEqualTo(1);
        assertThat(counts.traps()).isEqualTo(1);
    }

    @Test
    void timeLimitAndTraps_withNothingMarkedSaySo() {
        wholePool(refs(1, 3));
        confirmedMarks(List.of(), List.of());

        assertThatThrownBy(() -> service.select(request(CustomTestKind.TIME_LIMIT, 10), authentication))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("terminy");
        assertThatThrownBy(() -> service.select(request(CustomTestKind.TRAPS, 10), authentication))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pułapek");
    }

    @Test
    void lawChange_withNothingChangedSaysSo() {
        wholePool(refs(1, 3));

        assertThat(counts().lawChanges()).isZero();
        assertThatThrownBy(() -> service.select(request(CustomTestKind.LAW_CHANGE, 10), authentication))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("zmianą w prawie");
    }

    @Test
    void lawChange_isStillSubjectToTheChosenSource() {
        wholePool(List.of(
                QuestionRefs.refOf(withLawChange(QuestionRefs.question(1, 1, 1, QuestionRefs.OWN_SHEET))),
                QuestionRefs.refOf(withLawChange(QuestionRefs.question(2, 1, 1, QuestionRefs.AUTHORED)))));

        assertThat(counts(SourceMode.MINISTRY).lawChanges()).isEqualTo(1);
    }

    private static pl.tutlaw.backend.question.Question withLawChange(pl.tutlaw.backend.question.Question question) {
        question.setLawChangedAt(LocalDate.of(2026, 9, 1));
        return question;
    }

    // ---------------------------------------------------------------- przedmioty

    @Test
    void courses_areTheActsAssignedToThemAndAddToTheScope() {
        wholePool(List.of(
                ref(1, 4, 9, QuestionRefs.OWN_SHEET),   // akt 4 przedmiotu
                ref(2, 5, 9, QuestionRefs.OWN_SHEET),   // akt 5 przedmiotu
                ref(3, 7, 3, QuestionRefs.OWN_SHEET),   // akt wybrany wprost
                ref(4, 8, 3, QuestionRefs.OWN_SHEET))); // poza zakresem
        when(courseRepository.findActIdsByCourseIds(List.of(10L))).thenReturn(List.of(4L, 5L));

        CustomTestCountsResponse counts = service.counts(null, List.of(7L), List.of(10L), null, null, authentication);

        assertThat(counts.all()).isEqualTo(3);
        assertThat(set(service.select(
                new CustomTestRequest(null, List.of(7L), List.of(10L), null, null, CustomTestKind.ALL, 10), authentication).questionIds()))
                .containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    void courses_withoutAnyAct_doNotWidenTheScopeToTheWholePool() {
        wholePool(refs(1, 5));
        when(courseRepository.findActIdsByCourseIds(List.of(13L))).thenReturn(List.of());

        assertThat(service.counts(null, null, List.of(13L), null, null, authentication).all()).isZero();
        assertThatThrownBy(() -> service.select(
                new CustomTestRequest(null, null, List.of(13L), null, null, CustomTestKind.ALL, 10), authentication))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void courses_overlappingActsCountEachQuestionOnce() {
        wholePool(List.of(ref(1, 4, 9, QuestionRefs.OWN_SHEET), ref(2, 5, 9, QuestionRefs.OWN_SHEET)));
        // dwa przedmioty z aktem 4 w obu
        when(courseRepository.findActIdsByCourseIds(List.of(10L, 12L))).thenReturn(List.of(4L, 5L));

        assertThat(service.counts(null, null, List.of(10L, 12L), null, null, authentication).all()).isEqualTo(2);
    }

    // ---------------------------------------------------------------- losowanie

    @Test
    void select_drawsRequestedNumberWithoutDuplicates() {
        wholePool(refs(1, 50));

        CustomTestSelectionResponse response = service.select(request(CustomTestKind.ALL, 20), authentication);

        assertThat(response.questionIds()).hasSize(20);
        assertThat(set(response.questionIds())).hasSize(20);
        assertThat(response.questionIds()).allMatch(id -> id >= 1 && id <= 50);
        assertThat(response.available()).isEqualTo(50);
        assertThat(response.requested()).isEqualTo(20);
    }

    @Test
    void select_returnsEverythingWhenThereAreFewerQuestionsThanRequested() {
        wholePool(refs(1, 7));

        CustomTestSelectionResponse response = service.select(request(CustomTestKind.ALL, 100), authentication);

        assertThat(set(response.questionIds())).containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L, 7L);
        assertThat(response.available()).isEqualTo(7);
        assertThat(response.requested()).isEqualTo(100);
    }

    @Test
    void select_withoutKindMeansAll() {
        wholePool(refs(1, 5));
        schedule(overdue(1, 10));

        CustomTestSelectionResponse response = service.select(
                new CustomTestRequest(null, null, null, null, null, null, 10), authentication);

        assertThat(response.questionIds()).hasSize(5);
    }

    @Test
    void select_newTakesOnlyUntouchedQuestions() {
        wholePool(refs(1, 6));
        schedule(overdue(1, 10), notYetDue(2), overdue(3, 4));

        CustomTestSelectionResponse response = service.select(request(CustomTestKind.NEW, 10), authentication);

        assertThat(set(response.questionIds())).containsExactlyInAnyOrder(4L, 5L, 6L);
    }

    @Test
    void select_dueTakesTheMostForgottenFirst() {
        wholePool(refs(1, 15));
        // 1-12 po terminie: im dawniej ostatnia powtórka (przy tej samej stabilności), tym mniejsza szansa przypomnienia;
        // 13-15 jeszcze nie ich czas
        List<UserQuestionStateRepository.QuestionDue> rows = new ArrayList<>();
        for (long id = 1; id <= 12; id++) {
            rows.add(overdue(id, (int) (id * 5))); // karta 12 najdawniej, karta 1 najświeżej
        }
        for (long id = 13; id <= 15; id++) {
            rows.add(notYetDue(id));
        }
        schedule(rows.toArray(new UserQuestionStateRepository.QuestionDue[0]));

        CustomTestSelectionResponse response = service.select(request(CustomTestKind.DUE, 10), authentication);

        assertThat(set(response.questionIds())).containsExactlyInAnyOrder(3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L);
        assertThat(response.available()).isEqualTo(12); // 13-15 nie są zaległe
    }

    @Test
    void select_mistakesTakesTheFreshestFirstAndOnlyThoseInScope() {
        wholePool(refs(1, 20));
        // od najświeższej pomyłki; 99 jest poza zakresem
        List<Long> mistakes = new ArrayList<>(List.of(99L));
        for (long id = 20; id >= 1; id--) {
            mistakes.add(id);
        }
        when(mistakesService.recentMistakeQuestionIds(5L)).thenReturn(mistakes);

        CustomTestSelectionResponse response = service.select(request(CustomTestKind.MISTAKES, 10), authentication);

        assertThat(set(response.questionIds())).containsExactlyInAnyOrder(11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 19L, 20L);
        assertThat(response.available()).isEqualTo(20);
    }

    @Test
    void select_acceptsOnlyTheOfferedCounts() {
        wholePool(refs(1, 200));
        for (int count : new int[]{10, 20, 50, 100, 150}) {
            assertThat(service.select(request(CustomTestKind.ALL, count), authentication).questionIds()).hasSize(count);
        }
        for (int count : new int[]{1, 5, 15, 30, 149}) {
            assertThatThrownBy(() -> service.select(request(CustomTestKind.ALL, count), authentication))
                    .as("liczba " + count)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("10, 20, 50, 100 albo 150");
        }
    }

    @Test
    void select_inATinyScopeTheOnlyChoiceIsEverything() {
        wholePool(refs(1, 7));

        assertThat(set(service.select(request(CustomTestKind.ALL, 7), authentication).questionIds())).hasSize(7);
        assertThatThrownBy(() -> service.select(request(CustomTestKind.ALL, 5), authentication))
                .isInstanceOf(IllegalArgumentException.class);
        // liczba z listy też jest dozwolona: test dostaje po prostu wszystkie pytania
        assertThat(service.select(request(CustomTestKind.ALL, 10), authentication).questionIds()).hasSize(7);
    }

    @Test
    void select_ofTenOrMoreQuestionsDoesNotAllowAnArbitraryNumber() {
        wholePool(refs(1, 35));

        assertThatThrownBy(() -> service.select(request(CustomTestKind.ALL, 35), authentication))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.select(request(CustomTestKind.ALL, 20), authentication).questionIds()).hasSize(20);
    }

    // ---------------------------------------------------------------- błędy

    @Test
    void select_failsWithAClearMessageWhenScopeIsEmpty() {
        wholePool(List.of());

        assertThatThrownBy(() -> service.select(request(CustomTestKind.ALL, 10), authentication))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nie ma teraz pytań");
    }

    @Test
    void select_hintsAtTheSourceWhenItRemovedEverything() {
        wholePool(List.of(ref(1, 1, 1, QuestionRefs.OTHER_SHEET)));

        assertThatThrownBy(() -> service.select(request(SourceMode.AUTHORED, CustomTestKind.ALL, 10), authentication))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Wybierz inne źródło");
    }

    @Test
    void select_failsWithAMessageThatNamesTheKind() {
        wholePool(refs(1, 3));

        assertThatThrownBy(() -> service.select(request(CustomTestKind.MISTAKES, 10), authentication))
                .hasMessageContaining("błędną ostatnią odpowiedzią");
        assertThatThrownBy(() -> service.select(request(CustomTestKind.DUE, 10), authentication))
                .hasMessageContaining("zaległych");
        schedule(overdue(1, 5), overdue(2, 5), overdue(3, 5));
        assertThatThrownBy(() -> service.select(request(CustomTestKind.NEW, 10), authentication))
                .hasMessageContaining("były już rozwiązywane");
    }

    @Test
    void select_rejectsQuestionCountOutsideTheLimits() {
        assertThatThrownBy(() -> service.select(request(CustomTestKind.ALL, 0), authentication))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.select(request(CustomTestKind.ALL, 151), authentication))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void maxQuestions_fitsInThePracticeQueue() {
        // sesję otwiera kolejka ćwiczeń, więc losowanie nie może dać więcej, niż ona przyjmie
        assertThat(CustomTestService.MAX_QUESTIONS).isEqualTo(150).isLessThanOrEqualTo(PracticeQueueService.MAX_QUESTIONS);
        assertThat(CustomTestService.COUNT_CHOICES).containsExactly(10, 20, 50, 100, 150);
    }
}

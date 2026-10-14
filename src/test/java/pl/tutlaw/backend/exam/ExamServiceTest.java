package pl.tutlaw.backend.exam;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import pl.tutlaw.backend.card.Card;
import pl.tutlaw.backend.card.CardRepository;
import pl.tutlaw.backend.card.CorrectOption;
import pl.tutlaw.backend.card.QuestionOrigin;
import pl.tutlaw.backend.exam.dto.ExamAttemptResponse;
import pl.tutlaw.backend.exam.dto.ExamResultResponse;
import pl.tutlaw.backend.exam.dto.ExamStartResponse;
import pl.tutlaw.backend.exam.dto.ExamSubmitRequest;
import pl.tutlaw.backend.review.UserCardStateRepository;
import pl.tutlaw.backend.review.dto.TestQueueItemResponse;
import pl.tutlaw.backend.settings.OriginFilterService;
import pl.tutlaw.backend.source.Source;
import pl.tutlaw.backend.subject.Subject;
import pl.tutlaw.backend.testsupport.CardRefs;
import pl.tutlaw.backend.testsupport.MutableClock;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamServiceTest {

    // 2026-10-12 12:00 czasu warszawskiego
    private final MutableClock clock = MutableClock.at("2026-10-12T10:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 12, 0);

    @Mock private CardRepository cardRepository;
    @Mock private UserCardStateRepository stateRepository;
    @Mock private ExamAttemptRepository attemptRepository;
    @Mock private CurrentUserResolver currentUserResolver;
    @Mock private OriginFilterService originFilter;
    private final Authentication authentication = mock(Authentication.class);

    private ExamService service;

    @BeforeEach
    void setUp() {
        service = new ExamService(cardRepository, stateRepository, attemptRepository, currentUserResolver, clock,
                originFilter);
        User user = new User();
        user.setId(5L);
        when(currentUserResolver.resolve(authentication)).thenReturn(user);
        // Domyślnie wszystkie pochodzenia (filtr nie zawęża niczego); testy filtra ustawiają własny zbiór.
        lenient().when(originFilter.activeFor(5L)).thenReturn(EnumSet.allOf(QuestionOrigin.class));
    }

    // ---------------------------------------------------------------- dane

    private static Source source(String name) {
        Subject subject = new Subject();
        subject.setName("Prawo karne");
        Source source = new Source();
        source.setName(name);
        source.setSubject(subject);
        return source;
    }

    private static Card card(long id, CorrectOption correct, Source source) {
        Card card = new Card();
        card.setId(id);
        card.setFront("Pytanie " + id);
        card.setSource(source);
        card.setCorrectOption(correct);
        if (correct != null) {
            card.setOptionA("a");
            card.setOptionB("b");
            card.setOptionC("c");
        }
        return card;
    }

    private static Card test(long id) {
        return card(id, CorrectOption.A, source("Egzamin 2024"));
    }

    private static List<Card> tests(long from, long to) {
        List<Card> cards = new ArrayList<>();
        for (long id = from; id <= to; id++) {
            cards.add(test(id));
        }
        return cards;
    }

    private static ExamSubmitRequest.Answer answer(long cardId, CorrectOption selected) {
        return new ExamSubmitRequest.Answer(cardId, selected);
    }

    private static ExamSubmitRequest submitRequest(ExamSubmitRequest.Answer... answers) {
        return new ExamSubmitRequest(150, 3_600, false, List.of(answers));
    }

    /** Odpowiedzi do pytań 1..total, z czego pierwsze {@code correct} poprawne (A), a reszta błędna (B). */
    private static ExamSubmitRequest.Answer[] answers(int total, int correct) {
        ExamSubmitRequest.Answer[] answers = new ExamSubmitRequest.Answer[total];
        for (int i = 0; i < total; i++) {
            answers[i] = answer(i + 1L, i < correct ? CorrectOption.A : CorrectOption.B);
        }
        return answers;
    }

    private void readable(Card... cards) {
        when(cardRepository.findReadableTestCardsByIds(ArgumentMatchers.<Collection<Long>>any(), eq(5L)))
                .thenReturn(List.of(cards));
    }

    /** Pełne karty wylosowanych numerów (jedno zapytanie po numerach). Lenient, bo nie każdy test do niego dociera. */
    private void givenLoadable(List<Card> cards) {
        lenient().when(cardRepository.findAllWithSourceByIdIn(any()))
                .thenAnswer(invocation -> CardRefs.byIds(cards, invocation.getArgument(0)));
    }

    private ExamAttempt savedAttempt() {
        ArgumentCaptor<ExamAttempt> captor = ArgumentCaptor.forClass(ExamAttempt.class);
        verify(attemptRepository).save(captor.capture());
        return captor.getValue();
    }

    // ---------------------------------------------------------------- losowanie

    @Test
    void start_drawsExactly150DistinctQuestions_andTellsThePassThreshold() {
        when(cardRepository.findReadableRefsForUser(5L)).thenReturn(CardRefs.of(tests(1, 400)));
        givenLoadable(tests(1, 400));
        when(stateRepository.findSuspendedCardIds(5L)).thenReturn(List.of());

        ExamStartResponse response = service.start(authentication);

        assertThat(response.questions()).hasSize(150);
        assertThat(response.requestedCount()).isEqualTo(150);
        assertThat(response.passPoints()).isEqualTo(100);
        assertThat(response.questions().stream().map(TestQueueItemResponse::cardId).collect(Collectors.toSet()))
                .hasSize(150)
                .allMatch(id -> id >= 1 && id <= 400);
        assertThat(response.scopeLabel()).isEqualTo("Cały materiał");
    }

    @Test
    void start_loadsFullCardsOnlyForTheDrawnQuestions_inTheDrawnOrder() {
        when(cardRepository.findReadableRefsForUser(5L)).thenReturn(CardRefs.of(tests(1, 600)));
        givenLoadable(tests(1, 600));
        when(stateRepository.findSuspendedCardIds(5L)).thenReturn(List.of());

        ExamStartResponse response = service.start(authentication);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(cardRepository).findAllWithSourceByIdIn(captor.capture());
        // z 600 dostępnych pytań do bazy po pełne treści idzie tylko 150 numerów, a kolejność egzaminu to kolejność losowania
        assertThat(captor.getValue()).hasSize(150);
        assertThat(response.questions()).extracting(TestQueueItemResponse::cardId)
                .containsExactlyElementsOf(new ArrayList<>(captor.getValue()));
    }

    @Test
    void start_alwaysTakesTheWholeMaterial_notASingleCategorySubjectOrSource() {
        when(cardRepository.findReadableRefsForUser(5L)).thenReturn(CardRefs.of(tests(1, 200)));
        givenLoadable(tests(1, 200));
        when(stateRepository.findSuspendedCardIds(5L)).thenReturn(List.of());

        service.start(authentication);

        verify(cardRepository).findReadableRefsForUser(5L);
        verify(cardRepository, never()).findReadableRefsForUserAndCategory(any(), any());
        verify(cardRepository, never()).findReadableRefsForUserAndSubject(any(), any());
        verify(cardRepository, never()).findReadableRefsForUserAndSource(any(), any());
    }

    @Test
    void start_neverDrawsSuspendedOrNonTestCards() {
        List<Card> pool = new ArrayList<>(tests(1, 6));
        pool.add(card(7, null, source("Podręcznik"))); // fiszka bez formatu testowego
        when(cardRepository.findReadableRefsForUser(5L)).thenReturn(CardRefs.of(pool));
        givenLoadable(pool);
        when(stateRepository.findSuspendedCardIds(5L)).thenReturn(List.of(2L, 4L));

        // w materiale jest mniej niż 150 pytań, więc egzamin ma wszystkie możliwe
        ExamStartResponse response = service.start(authentication);

        assertThat(response.questions()).extracting(TestQueueItemResponse::cardId)
                .containsExactlyInAnyOrder(1L, 3L, 5L, 6L);
        assertThat(response.requestedCount()).isEqualTo(150);
    }

    private static Card withOrigin(Card card, QuestionOrigin origin) {
        card.setOrigin(origin);
        card.setExamYear(origin == QuestionOrigin.AUTHORED ? null : 2022);
        return card;
    }

    @Test
    void start_drawsOnlyFromTheSelectedOrigins() {
        List<Card> pool = List.of(
                withOrigin(test(1), QuestionOrigin.AUTHORED),
                withOrigin(test(2), QuestionOrigin.NOTARY),
                withOrigin(test(3), QuestionOrigin.BAILIFF),
                withOrigin(test(4), QuestionOrigin.NOTARY),
                withOrigin(test(5), QuestionOrigin.JUDGE_PROSECUTOR));
        when(cardRepository.findReadableRefsForUser(5L)).thenReturn(CardRefs.of(pool));
        givenLoadable(pool);
        when(stateRepository.findSuspendedCardIds(5L)).thenReturn(List.of());
        when(originFilter.activeFor(5L)).thenReturn(EnumSet.of(QuestionOrigin.NOTARY, QuestionOrigin.BAILIFF));

        ExamStartResponse response = service.start(authentication);

        assertThat(response.questions()).extracting(TestQueueItemResponse::cardId)
                .containsExactlyInAnyOrder(2L, 3L, 4L);
    }

    @Test
    void start_whenTheFilterExcludesEverythingTheErrorPointsAtTheFilter() {
        List<Card> pool = List.of(withOrigin(test(1), QuestionOrigin.AUTHORED), withOrigin(test(2), QuestionOrigin.AUTHORED));
        when(cardRepository.findReadableRefsForUser(5L)).thenReturn(CardRefs.of(pool));
        when(stateRepository.findSuspendedCardIds(5L)).thenReturn(List.of());
        when(originFilter.activeFor(5L)).thenReturn(EnumSet.of(QuestionOrigin.BAILIFF));

        assertThatThrownBy(() -> service.start(authentication))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("filtr pochodzenia");
    }

    @Test
    void start_withNoQuestionsAtAllKeepsTheOriginalMessage() {
        when(cardRepository.findReadableRefsForUser(5L)).thenReturn(List.of());
        when(stateRepository.findSuspendedCardIds(5L)).thenReturn(List.of());
        when(originFilter.activeFor(5L)).thenReturn(EnumSet.of(QuestionOrigin.BAILIFF));

        assertThatThrownBy(() -> service.start(authentication))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nie ma teraz pytań testowych")
                .hasMessageNotContaining("filtr");
    }

    @Test
    void submit_gradesEveryShownQuestionEvenIfItsOriginIsNowFilteredOut() {
        Card shown = withOrigin(test(1), QuestionOrigin.AUTHORED);
        readable(shown);
        lenient().when(originFilter.activeFor(5L)).thenReturn(EnumSet.of(QuestionOrigin.BAILIFF));

        ExamResultResponse result = service.submit(submitRequest(answer(1, CorrectOption.A)), authentication);

        assertThat(result.questions()).hasSize(1);
        assertThat(result.questions().get(0).correct()).isTrue();
    }

    @Test
    void start_withFewerThan150QuestionsReturnsAllOfThem_andScalesTheThreshold() {
        when(cardRepository.findReadableRefsForUser(5L)).thenReturn(CardRefs.of(tests(1, 12)));
        givenLoadable(tests(1, 12));
        when(stateRepository.findSuspendedCardIds(5L)).thenReturn(List.of());

        ExamStartResponse response = service.start(authentication);

        assertThat(response.questions()).hasSize(12);
        assertThat(response.requestedCount()).isEqualTo(150);
        assertThat(response.passPoints()).isEqualTo(8); // 100 z 150, proporcjonalnie, w górę
    }

    @Test
    void start_withNothingToDrawIsABadRequest() {
        when(cardRepository.findReadableRefsForUser(5L)).thenReturn(CardRefs.of(tests(1, 3)));
        givenLoadable(tests(1, 3));
        when(stateRepository.findSuspendedCardIds(5L)).thenReturn(List.of(1L, 2L, 3L));

        assertThatThrownBy(() -> service.start(authentication))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void start_writesNothingToTheDatabase() {
        when(cardRepository.findReadableRefsForUser(5L)).thenReturn(CardRefs.of(tests(1, 10)));
        givenLoadable(tests(1, 10));
        when(stateRepository.findSuspendedCardIds(5L)).thenReturn(List.of());

        service.start(authentication);

        verify(attemptRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- ocena

    @Test
    void submit_scoresOnTheServer_andTreatsMissingAnswersAsWrong() {
        Card c1 = card(1, CorrectOption.A, source("Egzamin 2024"));
        Card c2 = card(2, CorrectOption.B, source("Egzamin 2024"));
        Card c3 = card(3, CorrectOption.C, source("Egzamin 2024"));
        readable(c1, c2, c3);

        ExamResultResponse result = service.submit(
                submitRequest(answer(1, CorrectOption.A), answer(2, CorrectOption.C), answer(3, null)), authentication);

        ExamAttemptResponse attempt = result.attempt();
        assertThat(attempt.questionCount()).isEqualTo(3);
        assertThat(attempt.answeredCount()).isEqualTo(2);
        assertThat(attempt.correctCount()).isEqualTo(1);
        assertThat(attempt.scorePercent()).isEqualTo(33.3);
        assertThat(attempt.passed()).isFalse();
        assertThat(result.excludedCount()).isZero();

        assertThat(result.questions()).extracting(ExamResultResponse.QuestionResult::correct)
                .containsExactly(true, false, false);
        assertThat(result.questions().get(1).selectedOption()).isEqualTo(CorrectOption.C);
        assertThat(result.questions().get(1).correctOption()).isEqualTo(CorrectOption.B);
        assertThat(result.questions().get(2).selectedOption()).isNull();
        assertThat(result.questions().get(2).correctOption()).isEqualTo(CorrectOption.C);
    }

    @Test
    void submit_decidesPassingAgainstTheServerThreshold() {
        Card c1 = test(1);
        Card c2 = test(2);
        Card c3 = test(3);
        Card c4 = test(4);
        readable(c1, c2, c3, c4);

        // 4 pytania: próg to 100/150 z 4, w górę, czyli 3 punkty
        ExamResultResponse atThreshold = service.submit(submitRequest(
                answer(1, CorrectOption.A), answer(2, CorrectOption.A), answer(3, CorrectOption.A), answer(4, CorrectOption.B)),
                authentication);
        ExamResultResponse below = service.submit(submitRequest(
                answer(1, CorrectOption.A), answer(2, CorrectOption.A), answer(3, CorrectOption.B), answer(4, CorrectOption.B)),
                authentication);

        assertThat(atThreshold.attempt().passed()).isTrue();
        assertThat(atThreshold.attempt().passPoints()).isEqualTo(3);
        assertThat(below.attempt().passed()).isFalse();
    }

    @Test
    void submit_fullExam_passesFromExactly100Points() {
        List<Card> all = tests(1, 150);
        readable(all.toArray(new Card[0]));

        ExamResultResponse with100 = service.submit(submitRequest(answers(150, 100)), authentication);
        ExamResultResponse with99 = service.submit(submitRequest(answers(150, 99)), authentication);

        assertThat(with100.attempt().questionCount()).isEqualTo(150);
        assertThat(with100.attempt().correctCount()).isEqualTo(100);
        assertThat(with100.attempt().passPoints()).isEqualTo(100);
        assertThat(with100.attempt().passed()).isTrue();
        assertThat(with99.attempt().correctCount()).isEqualTo(99);
        assertThat(with99.attempt().passed()).isFalse();
    }

    @Test
    void submit_savesThePassThresholdInPointsAndAsARoundedPercentage() {
        readable(tests(1, 150).toArray(new Card[0]));

        service.submit(submitRequest(answers(150, 120)), authentication);

        ExamAttempt saved = savedAttempt();
        assertThat(saved.getPassPoints()).isEqualTo(100);
        assertThat(saved.getPassThresholdPercent()).isEqualTo(67); // 100/150 = 66,7%, w górę do pełnego procentu
        assertThat(saved.isPassed()).isTrue();
    }

    @Test
    void submit_savesTheSummaryOfTheAttempt() {
        readable(test(1), test(2));

        ExamSubmitRequest request = new ExamSubmitRequest(90, 4_321, true,
                List.of(answer(1, CorrectOption.A), answer(2, null)));
        service.submit(request, authentication);

        ExamAttempt saved = savedAttempt();
        assertThat(saved.getUser().getId()).isEqualTo(5L);
        assertThat(saved.getFinishedAt()).isEqualTo(NOW);
        assertThat(saved.getScopeLabel()).isEqualTo("Cały materiał");
        assertThat(saved.getQuestionCount()).isEqualTo(2);
        assertThat(saved.getAnsweredCount()).isEqualTo(1);
        assertThat(saved.getCorrectCount()).isEqualTo(1);
        assertThat(saved.getPassPoints()).isEqualTo(2); // 100/150 z 2 pytań, w górę
        assertThat(saved.getPassThresholdPercent()).isEqualTo(100);
        assertThat(saved.isPassed()).isFalse(); // 1 punkt z wymaganych 2
        assertThat(saved.getTimeLimitMinutes()).isEqualTo(90);
        assertThat(saved.getDurationSeconds()).isEqualTo(4_321);
        assertThat(saved.isTimedOut()).isTrue();
    }

    @Test
    void submit_withoutTimeLimitStoresNull() {
        readable(test(1));

        service.submit(new ExamSubmitRequest(null, 10, null, List.of(answer(1, CorrectOption.A))), authentication);

        ExamAttempt saved = savedAttempt();
        assertThat(saved.getTimeLimitMinutes()).isNull();
        assertThat(saved.isTimedOut()).isFalse();
    }

    @Test
    void submit_keepsTheExamOrder_andBreaksDownBySource() {
        Source big = source("Egzamin 2024");
        Source small = source("Podręcznik");
        readable(
                card(1, CorrectOption.A, small),
                card(2, CorrectOption.A, big),
                card(3, CorrectOption.A, big),
                card(4, CorrectOption.A, big));

        ExamResultResponse result = service.submit(submitRequest(
                answer(3, CorrectOption.A), answer(1, CorrectOption.B), answer(4, CorrectOption.A), answer(2, CorrectOption.C)),
                authentication);

        assertThat(result.questions()).extracting(ExamResultResponse.QuestionResult::cardId)
                .containsExactly(3L, 1L, 4L, 2L);
        // najpierw źródło z największą liczbą pytań
        assertThat(result.bySource()).extracting(ExamResultResponse.SourceBreakdown::sourceName)
                .containsExactly("Egzamin 2024", "Podręcznik");
        assertThat(result.bySource().get(0).total()).isEqualTo(3);
        assertThat(result.bySource().get(0).correct()).isEqualTo(2);
        assertThat(result.bySource().get(1).total()).isEqualTo(1);
        assertThat(result.bySource().get(1).correct()).isZero();
    }

    @Test
    void submit_dropsQuestionsThatCannotBeScored_andSaysSo() {
        readable(test(1), test(3)); // pytanie 2 zostało usunięte albo jest już niedostępne

        ExamResultResponse result = service.submit(submitRequest(
                answer(1, CorrectOption.A), answer(2, CorrectOption.A), answer(3, CorrectOption.A)), authentication);

        assertThat(result.excludedCount()).isEqualTo(1);
        assertThat(result.attempt().questionCount()).isEqualTo(2);
        assertThat(savedAttempt().getQuestionCount()).isEqualTo(2);
        assertThat(result.questions()).extracting(ExamResultResponse.QuestionResult::cardId).containsExactly(1L, 3L);
    }

    @Test
    void submit_withNothingToScoreIsABadRequest_andSavesNothing() {
        readable();

        assertThatThrownBy(() -> service.submit(submitRequest(answer(1, CorrectOption.A)), authentication))
                .isInstanceOf(IllegalArgumentException.class);
        verify(attemptRepository, never()).save(any());
    }

    @Test
    void submit_rejectsTheSameQuestionTwice_andSavesNothing() {
        assertThatThrownBy(() -> service.submit(
                submitRequest(answer(1, CorrectOption.A), answer(1, CorrectOption.B)), authentication))
                .isInstanceOf(IllegalArgumentException.class);
        verify(attemptRepository, never()).save(any());
        verify(cardRepository, never()).findReadableTestCardsByIds(
                ArgumentMatchers.<Collection<Long>>any(), ArgumentMatchers.<Long>any());
    }

    @Test
    void submit_doesNotTouchTheLearningState() {
        readable(test(1));

        service.submit(submitRequest(answer(1, CorrectOption.B)), authentication);

        // egzamin nie zmienia terminów powtórek ani liczników błędów: do stanów nauki w ogóle nie zagląda
        verify(stateRepository, never()).save(any());
        verify(stateRepository, never()).findByUserIdAndCardId(any(), any());
    }

    @Test
    void submit_resultCarriesTheExplanationOnlyWhenThereIsOne() {
        Card withExplanation = test(1);
        withExplanation.setExplanation("Bo tak stanowi art. 1.");
        Card without = test(2);
        readable(withExplanation, without);

        ExamResultResponse result = service.submit(
                submitRequest(answer(1, CorrectOption.A), answer(2, CorrectOption.A)), authentication);

        assertThat(result.questions().get(0).explanation()).isEqualTo("Bo tak stanowi art. 1.");
        assertThat(result.questions().get(1).explanation()).isNull();
    }

    // ---------------------------------------------------------------- historia

    @Test
    void history_mapsAttemptsNewestFirst_withTheScoreInPercent() {
        ExamAttempt newer = attempt(2L, NOW, 100, 150, 67, 100, true);
        ExamAttempt older = attempt(1L, NOW.minusDays(3), 99, 150, 67, 100, false);
        when(attemptRepository.findRecent(eq(5L), ArgumentMatchers.<Pageable>any())).thenReturn(List.of(newer, older));

        List<ExamAttemptResponse> history = service.history(authentication);

        assertThat(history).extracting(ExamAttemptResponse::id).containsExactly(2L, 1L);
        assertThat(history.get(0).scorePercent()).isEqualTo(66.7);
        assertThat(history.get(0).passPoints()).isEqualTo(100);
        assertThat(history.get(0).passed()).isTrue();
        assertThat(history.get(1).scorePercent()).isEqualTo(66.0);
        assertThat(history.get(1).passed()).isFalse();
    }

    @Test
    void history_keepsOlderAttemptsWithTheirOwnPercentageThreshold() {
        // podejście sprzed stałego formatu: własny próg 75%, bez progu w punktach
        ExamAttempt old = attempt(7L, NOW.minusDays(40), 113, 150, 75, null, true);
        when(attemptRepository.findRecent(eq(5L), ArgumentMatchers.<Pageable>any())).thenReturn(List.of(old));

        ExamAttemptResponse response = service.history(authentication).get(0);

        assertThat(response.passPoints()).isNull();
        assertThat(response.passThresholdPercent()).isEqualTo(75);
        assertThat(response.passed()).isTrue();
        assertThat(response.scopeLabel()).isEqualTo("Cały materiał");
    }

    @Test
    void history_isEmptyWhenThereAreNoAttempts() {
        when(attemptRepository.findRecent(eq(5L), ArgumentMatchers.<Pageable>any())).thenReturn(List.of());

        assertThat(service.history(authentication)).isEmpty();
    }

    private static ExamAttempt attempt(long id, LocalDateTime finishedAt, int correct, int total, int threshold,
                                       Integer passPoints, boolean passed) {
        ExamAttempt attempt = new ExamAttempt();
        attempt.setId(id);
        attempt.setFinishedAt(finishedAt);
        attempt.setScopeLabel("Cały materiał");
        attempt.setQuestionCount(total);
        attempt.setAnsweredCount(total);
        attempt.setCorrectCount(correct);
        attempt.setPassThresholdPercent(threshold);
        attempt.setPassPoints(passPoints);
        attempt.setPassed(passed);
        attempt.setTimeLimitMinutes(150);
        attempt.setDurationSeconds(7_000);
        return attempt;
    }
}

package pl.tutlaw.backend.review;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import pl.tutlaw.backend.card.CardRepository;
import pl.tutlaw.backend.card.QuestionOrigin;
import pl.tutlaw.backend.review.dto.CustomTestCountsResponse;
import pl.tutlaw.backend.review.dto.CustomTestRequest;
import pl.tutlaw.backend.review.dto.CustomTestSelectionResponse;
import pl.tutlaw.backend.review.fsrs.FsrsEngine;
import pl.tutlaw.backend.review.fsrs.FsrsWeights;
import pl.tutlaw.backend.settings.OriginFilterService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Własny test: pula pytań, rodzaje, lata, losowanie i komunikaty, na prawdziwym serwisie z atrapami repozytoriów. */
@ExtendWith(MockitoExtension.class)
class CustomTestServiceTest {

    // 2026-10-12 12:00 czasu warszawskiego
    private final MutableClock clock = MutableClock.at("2026-10-12T10:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 12, 0);

    @Mock private CardRepository cardRepository;
    @Mock private UserCardStateRepository stateRepository;
    @Mock private MistakesService mistakesService;
    @Mock private CurrentUserResolver currentUserResolver;
    @Mock private OriginFilterService originFilter;
    private final Authentication authentication = mock(Authentication.class);

    private CustomTestService service;

    @BeforeEach
    void setUp() {
        service = new CustomTestService(cardRepository, stateRepository, mistakesService, currentUserResolver,
                new FsrsEngine(FsrsWeights.defaults(), 0.9), clock, originFilter);
        User user = new User();
        user.setId(5L);
        lenient().when(currentUserResolver.resolve(authentication)).thenReturn(user);
        lenient().when(originFilter.activeFor(5L)).thenReturn(EnumSet.allOf(QuestionOrigin.class));
        lenient().when(stateRepository.findCardDueByUserId(5L)).thenReturn(List.of());
        lenient().when(mistakesService.recentMistakeCardIds(5L)).thenReturn(List.of());
    }

    // ---------------------------------------------------------------- dane

    private static CardRepository.TestRef ref(long id, QuestionOrigin origin, Integer year) {
        return new CardRepository.TestRef() {
            public Long getId() { return id; }
            public QuestionOrigin getOrigin() { return origin; }
            public Integer getExamYear() { return year; }
        };
    }

    private static CardRepository.TestRef ref(long id) {
        return ref(id, QuestionOrigin.ADVOCATE_LEGAL_ADVISER, 2024);
    }

    private static List<CardRepository.TestRef> refs(long from, long to) {
        List<CardRepository.TestRef> list = new ArrayList<>();
        for (long id = from; id <= to; id++) {
            list.add(ref(id));
        }
        return list;
    }

    private static UserCardStateRepository.CardDue state(
            long cardId, LocalDateTime due, LocalDateTime lastReview, boolean suspended) {
        return new UserCardStateRepository.CardDue() {
            public Long getCardId() { return cardId; }
            public LocalDateTime getDueDate() { return due; }
            public Double getStability() { return 5.0; }
            public LocalDateTime getLastReviewAt() { return lastReview; }
            public Integer getLapses() { return 0; }
            public Boolean getSuspended() { return suspended; }
        };
    }

    /** Karta z minionym terminem, ostatnio powtarzana {@code daysAgo} dni temu (im dawniej, tym pilniej). */
    private static UserCardStateRepository.CardDue overdue(long cardId, int daysAgo) {
        return state(cardId, NOW.minusDays(1), NOW.minusDays(daysAgo), false);
    }

    private static UserCardStateRepository.CardDue notYetDue(long cardId) {
        return state(cardId, NOW.plusDays(3), NOW.minusDays(2), false);
    }

    private void wholeMaterial(List<CardRepository.TestRef> refs) {
        when(cardRepository.findReadableTestRefsForUser(5L)).thenReturn(refs);
    }

    private void schedule(UserCardStateRepository.CardDue... rows) {
        when(stateRepository.findCardDueByUserId(5L)).thenReturn(List.of(rows));
    }

    private static CustomTestRequest request(CustomTestKind kind, int count) {
        return new CustomTestRequest(null, null, kind, count);
    }

    private CustomTestCountsResponse counts() {
        return service.counts(null, null, authentication);
    }

    private static Set<Long> set(List<Long> ids) {
        return ids.stream().collect(Collectors.toSet());
    }

    // ---------------------------------------------------------------- liczniki

    @Test
    void counts_splitQuestionsIntoKinds() {
        wholeMaterial(refs(1, 6));
        // 1, 2: zaległe; 3: jeszcze nie ich czas; 4: zawieszone; 5, 6: nowe
        schedule(overdue(1, 10), overdue(2, 20), notYetDue(3), state(4, NOW.minusDays(1), NOW.minusDays(5), true));
        when(mistakesService.recentMistakeCardIds(5L)).thenReturn(List.of(3L, 4L, 9L));

        CustomTestCountsResponse counts = counts();

        assertThat(counts.all()).isEqualTo(5);          // bez zawieszonej 4
        assertThat(counts.newQuestions()).isEqualTo(2); // 5, 6
        assertThat(counts.due()).isEqualTo(2);          // 1, 2
        assertThat(counts.mistakes()).isEqualTo(1);     // tylko 3: 4 jest zawieszona, 9 poza zakresem
    }

    @Test
    void counts_areZeroForEmptyScopeAndDoNotScanMistakes() {
        wholeMaterial(List.of());

        CustomTestCountsResponse counts = counts();

        assertThat(counts).isEqualTo(new CustomTestCountsResponse(0, 0, 0, 0));
        verify(mistakesService, never()).recentMistakeCardIds(ArgumentMatchers.anyLong());
    }

    // ---------------------------------------------------------------- zakres

    @Test
    void withoutScope_usesWholeMaterialQuery() {
        wholeMaterial(refs(1, 3));

        assertThat(counts().all()).isEqualTo(3);

        verify(cardRepository, never()).findReadableTestRefsInScope(
                ArgumentMatchers.anyLong(), ArgumentMatchers.<Collection<Long>>any(), ArgumentMatchers.<Collection<Long>>any());
    }

    @Test
    void scope_isUnionOfCategoriesAndSubjects_withoutDuplicatesOrNulls() {
        when(cardRepository.findReadableTestRefsInScope(eq(5L), ArgumentMatchers.<Collection<Long>>any(),
                ArgumentMatchers.<Collection<Long>>any())).thenReturn(refs(1, 4));

        CustomTestCountsResponse counts = service.counts(
                java.util.Arrays.asList(2L, 2L, null), List.of(7L, 8L), authentication);

        assertThat(counts.all()).isEqualTo(4);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> categories = ArgumentCaptor.forClass(Collection.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> subjects = ArgumentCaptor.forClass(Collection.class);
        verify(cardRepository).findReadableTestRefsInScope(eq(5L), categories.capture(), subjects.capture());
        assertThat(categories.getValue()).containsExactly(2L);
        assertThat(subjects.getValue()).containsExactly(7L, 8L);
    }

    @Test
    void scope_withOnlySubjects_passesAnIdThatMatchesNothingInsteadOfAnEmptyList() {
        when(cardRepository.findReadableTestRefsInScope(eq(5L), ArgumentMatchers.<Collection<Long>>any(),
                ArgumentMatchers.<Collection<Long>>any())).thenReturn(refs(1, 2));

        service.counts(null, List.of(7L), authentication);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> categories = ArgumentCaptor.forClass(Collection.class);
        verify(cardRepository).findReadableTestRefsInScope(eq(5L), categories.capture(), ArgumentMatchers.<Collection<Long>>any());
        assertThat(categories.getValue()).isNotEmpty().containsExactly(-1L);
    }

    // ---------------------------------------------------------------- filtry

    @Test
    void originFilter_removesQuestionsFromOtherOrigins() {
        wholeMaterial(List.of(
                ref(1, QuestionOrigin.NOTARY, 2023),
                ref(2, QuestionOrigin.BAILIFF, 2023),
                ref(3, QuestionOrigin.AUTHORED, null)));
        when(originFilter.activeFor(5L)).thenReturn(EnumSet.of(QuestionOrigin.NOTARY, QuestionOrigin.AUTHORED));

        assertThat(counts().all()).isEqualTo(2);
    }

    @Test
    void suspendedQuestions_neverEnterAnyKind() {
        wholeMaterial(refs(1, 4));
        // 1 i 2 zawieszone (z minionym terminem i z błędem), 3 zaległe, 4 nowe
        schedule(state(1, NOW.minusDays(1), NOW.minusDays(5), true), state(2, NOW.minusDays(1), NOW.minusDays(5), true),
                overdue(3, 5));
        when(mistakesService.recentMistakeCardIds(5L)).thenReturn(List.of(1L, 2L, 3L));

        for (CustomTestKind kind : CustomTestKind.values()) {
            assertThat(service.select(request(kind, 10), authentication).cardIds())
                    .as(kind.name()).doesNotContain(1L, 2L);
        }
    }

    // ---------------------------------------------------------------- losowanie

    @Test
    void select_drawsRequestedNumberWithoutDuplicates() {
        wholeMaterial(refs(1, 50));

        CustomTestSelectionResponse response = service.select(request(CustomTestKind.ALL, 20), authentication);

        assertThat(response.cardIds()).hasSize(20);
        assertThat(set(response.cardIds())).hasSize(20);
        assertThat(response.cardIds()).allMatch(id -> id >= 1 && id <= 50);
        assertThat(response.available()).isEqualTo(50);
        assertThat(response.requested()).isEqualTo(20);
    }

    @Test
    void select_returnsEverythingWhenThereAreFewerQuestionsThanRequested() {
        wholeMaterial(refs(1, 7));

        CustomTestSelectionResponse response = service.select(request(CustomTestKind.ALL, 100), authentication);

        assertThat(set(response.cardIds())).containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L, 7L);
        assertThat(response.available()).isEqualTo(7);
        assertThat(response.requested()).isEqualTo(100);
    }

    @Test
    void select_withoutKindMeansAll() {
        wholeMaterial(refs(1, 5));
        schedule(overdue(1, 10));

        CustomTestSelectionResponse response = service.select(
                new CustomTestRequest(null, null, null, 10), authentication);

        assertThat(response.cardIds()).hasSize(5);
    }

    @Test
    void select_newTakesOnlyUntouchedQuestions() {
        wholeMaterial(refs(1, 6));
        schedule(overdue(1, 10), notYetDue(2), overdue(3, 4));

        CustomTestSelectionResponse response = service.select(request(CustomTestKind.NEW, 10), authentication);

        assertThat(set(response.cardIds())).containsExactlyInAnyOrder(4L, 5L, 6L);
    }

    @Test
    void select_dueTakesTheMostForgottenFirst() {
        wholeMaterial(refs(1, 15));
        // 1-12 po terminie: im dawniej ostatnia powtórka (przy tej samej stabilności), tym mniejsza szansa przypomnienia;
        // 13-15 jeszcze nie ich czas
        List<UserCardStateRepository.CardDue> rows = new ArrayList<>();
        for (long id = 1; id <= 12; id++) {
            rows.add(overdue(id, (int) (id * 5))); // karta 12 najdawniej, karta 1 najświeżej
        }
        for (long id = 13; id <= 15; id++) {
            rows.add(notYetDue(id));
        }
        schedule(rows.toArray(new UserCardStateRepository.CardDue[0]));

        CustomTestSelectionResponse response = service.select(request(CustomTestKind.DUE, 10), authentication);

        assertThat(set(response.cardIds())).containsExactlyInAnyOrder(3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L);
        assertThat(response.available()).isEqualTo(12); // 13-15 nie są zaległe
    }

    @Test
    void select_mistakesTakesTheFreshestFirstAndOnlyThoseInScope() {
        wholeMaterial(refs(1, 20));
        // od najświeższej pomyłki; 99 jest poza zakresem
        List<Long> mistakes = new ArrayList<>(List.of(99L));
        for (long id = 20; id >= 1; id--) {
            mistakes.add(id);
        }
        when(mistakesService.recentMistakeCardIds(5L)).thenReturn(mistakes);

        CustomTestSelectionResponse response = service.select(request(CustomTestKind.MISTAKES, 10), authentication);

        assertThat(set(response.cardIds())).containsExactlyInAnyOrder(11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 19L, 20L);
        assertThat(response.available()).isEqualTo(20);
    }

    @Test
    void select_acceptsOnlyTheOfferedCounts() {
        wholeMaterial(refs(1, 200));
        for (int count : new int[]{10, 20, 50, 100, 150}) {
            assertThat(service.select(request(CustomTestKind.ALL, count), authentication).cardIds()).hasSize(count);
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
        wholeMaterial(refs(1, 7));

        assertThat(set(service.select(request(CustomTestKind.ALL, 7), authentication).cardIds())).hasSize(7);
        assertThatThrownBy(() -> service.select(request(CustomTestKind.ALL, 5), authentication))
                .isInstanceOf(IllegalArgumentException.class);
        // liczba z listy też jest dozwolona: test dostaje po prostu wszystkie pytania
        assertThat(service.select(request(CustomTestKind.ALL, 10), authentication).cardIds()).hasSize(7);
    }

    @Test
    void select_ofTenOrMoreQuestionsDoesNotAllowAnArbitraryNumber() {
        wholeMaterial(refs(1, 35));

        assertThatThrownBy(() -> service.select(request(CustomTestKind.ALL, 35), authentication))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.select(request(CustomTestKind.ALL, 20), authentication).cardIds()).hasSize(20);
    }

    // ---------------------------------------------------------------- błędy

    @Test
    void select_failsWithAClearMessageWhenScopeIsEmpty() {
        wholeMaterial(List.of());

        assertThatThrownBy(() -> service.select(request(CustomTestKind.ALL, 10), authentication))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nie ma teraz pytań testowych");
    }

    @Test
    void select_hintsAtTheFiltersWhenTheyRemovedEverything() {
        wholeMaterial(List.of(ref(1, QuestionOrigin.BAILIFF, 2023)));
        when(originFilter.activeFor(5L)).thenReturn(EnumSet.of(QuestionOrigin.NOTARY));

        assertThatThrownBy(() -> service.select(request(CustomTestKind.ALL, 10), authentication))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("filtr pochodzenia");
    }

    @Test
    void select_failsWithAMessageThatNamesTheKind() {
        wholeMaterial(refs(1, 3));

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
        assertThat(CustomTestService.MAX_QUESTIONS).isEqualTo(150).isLessThanOrEqualTo(PracticeQueueService.MAX_CARDS);
        assertThat(CustomTestService.COUNT_CHOICES).containsExactly(10, 20, 50, 100, 150);
    }
}

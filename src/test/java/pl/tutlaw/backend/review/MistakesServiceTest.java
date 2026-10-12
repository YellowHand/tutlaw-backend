package pl.tutlaw.backend.review;

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
import pl.tutlaw.backend.review.dto.MistakesCountResponse;
import pl.tutlaw.backend.review.dto.TestQueueItemResponse;
import pl.tutlaw.backend.testsupport.MutableClock;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MistakesServiceTest {

    // 2026-10-12 12:00 czasu warszawskiego
    private final MutableClock clock = MutableClock.at("2026-10-12T10:00:00Z");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 12, 0);

    @Mock private ReviewLogRepository reviewLogRepository;
    @Mock private CardRepository cardRepository;
    @Mock private CurrentUserResolver currentUserResolver;
    private final Authentication authentication = mock(Authentication.class);

    private MistakesService service;

    @BeforeEach
    void setUp() {
        service = new MistakesService(reviewLogRepository, cardRepository, currentUserResolver, clock);
        User user = new User();
        user.setId(5L);
        when(currentUserResolver.resolve(authentication)).thenReturn(user);
    }

    private static Card card(long id) {
        Card card = new Card();
        card.setId(id);
        card.setFront("Pytanie " + id);
        card.setCorrectOption(CorrectOption.A);
        card.setOptionA("a");
        card.setOptionB("b");
        card.setOptionC("c");
        return card;
    }

    private static ReviewLogRepository.TestAnswerRow answer(long cardId, boolean correct, int daysAgo) {
        return new ReviewLogRepository.TestAnswerRow() {
            public Long getCardId() { return cardId; }
            public Boolean getWasCorrect() { return correct; }
            public LocalDateTime getReviewedAt() { return NOW.minusDays(daysAgo); }
        };
    }

    /** Odpowiedzi w kolejności, w jakiej zwraca je repozytorium: od najnowszej. */
    private void answers(ReviewLogRepository.TestAnswerRow... rows) {
        when(reviewLogRepository.findTestAnswersSince(
                eq(5L), ArgumentMatchers.<LocalDateTime>any(), ArgumentMatchers.<Pageable>any()))
                .thenReturn(List.of(rows));
    }

    /** Karty, które zapytanie o dostępność uzna za ćwiczalne (bez zawieszonych, bez niedostępnych). */
    private void practicable(Card... cards) {
        when(cardRepository.findPracticeTestCards(ArgumentMatchers.<Collection<Long>>any(), eq(5L)))
                .thenReturn(List.of(cards));
    }

    private List<Long> idsAskedAboutAvailability() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(cardRepository).findPracticeTestCards(captor.capture(), eq(5L));
        return new ArrayList<>(captor.getValue());
    }

    // ---------------------------------------------------------------- co jest błędem

    @Test
    void count_includesCardsWhoseLatestAnswerWasWrong() {
        answers(answer(1, false, 1), answer(2, false, 2));
        practicable(card(1), card(2));

        MistakesCountResponse response = service.count(authentication);

        assertThat(response.count()).isEqualTo(2);
        assertThat(response.lookbackDays()).isEqualTo(30);
        assertThat(response.sessionSize()).isEqualTo(50);
    }

    @Test
    void aWrongAnswerFollowedByACorrectOneIsNoLongerAMistake() {
        // karta 1: najnowsza (wczoraj) poprawna, wcześniejsza błędna; karta 2: tylko błędna
        answers(answer(1, true, 1), answer(2, false, 2), answer(1, false, 5));
        practicable(card(2));

        service.count(authentication);

        assertThat(idsAskedAboutAvailability()).containsExactly(2L);
    }

    @Test
    void aCorrectAnswerFollowedByAWrongOneIsAMistake() {
        // karta 1: najnowsza błędna, starsza poprawna
        answers(answer(1, false, 1), answer(1, true, 6));
        practicable(card(1));

        assertThat(service.count(authentication).count()).isEqualTo(1);
        assertThat(idsAskedAboutAvailability()).containsExactly(1L);
    }

    @Test
    void anUnsureButCorrectAnswerIsNotAMistake() {
        // "poprawna, ale niepewna" jest w dzienniku wasCorrect = true, więc nie trafia do błędów
        answers(answer(1, true, 1), answer(2, true, 2));

        assertThat(service.count(authentication).count()).isZero();
    }

    @Test
    void mistakesAreListedFromTheFreshestOne() {
        answers(answer(3, false, 1), answer(1, false, 4), answer(2, false, 9));
        practicable(card(1), card(2), card(3));

        service.count(authentication);

        assertThat(idsAskedAboutAvailability()).containsExactly(3L, 1L, 2L);
    }

    // ---------------------------------------------------------------- dostępność

    @Test
    void onlyCardsThatCanStillBePracticedSurvive() {
        // karty 1 i 3 są zawieszone albo w niedostępnym rozdziale: zapytanie o dostępność ich nie zwraca
        answers(answer(1, false, 1), answer(2, false, 2), answer(3, false, 3));
        practicable(card(2));

        MistakesCountResponse response = service.count(authentication);

        assertThat(response.count()).isEqualTo(1);
        assertThat(service.queue(authentication)).extracting(TestQueueItemResponse::cardId).containsExactly(2L);
    }

    @Test
    void withoutMistakesNothingIsLookedUp() {
        answers();

        assertThat(service.count(authentication).count()).isZero();
        assertThat(service.queue(authentication)).isEmpty();

        verify(cardRepository, never()).findPracticeTestCards(
                ArgumentMatchers.<Collection<Long>>any(), ArgumentMatchers.<Long>any());
    }

    @Test
    void theLookbackWindowIsThirtyDaysBack() {
        answers();

        service.count(authentication);

        ArgumentCaptor<LocalDateTime> since = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(reviewLogRepository).findTestAnswersSince(eq(5L), since.capture(), ArgumentMatchers.<Pageable>any());
        assertThat(since.getValue()).isEqualTo(NOW.minusDays(30));
    }

    // ---------------------------------------------------------------- sesja

    @Test
    void sessionHoldsAtMostFiftyCards_butTheCountShowsAllEligible() {
        List<ReviewLogRepository.TestAnswerRow> rows = new ArrayList<>();
        List<Card> cards = new ArrayList<>();
        for (long id = 1; id <= 60; id++) {
            rows.add(answer(id, false, 1));
            cards.add(card(id));
        }
        answers(rows.toArray(new ReviewLogRepository.TestAnswerRow[0]));
        practicable(cards.toArray(new Card[0]));

        assertThat(service.count(authentication).count()).isEqualTo(60);

        List<TestQueueItemResponse> queue = service.queue(authentication);
        assertThat(queue).hasSize(50);
        // do sesji wchodzi 50 najświeższych (tu: pierwsze 50 z listy), nie dowolne
        assertThat(queue).extracting(TestQueueItemResponse::cardId).doesNotHaveDuplicates()
                .allMatch(id -> id <= 50);
    }

    @Test
    void queueItemsCarryTheOptionsButNotTheCorrectAnswer() {
        answers(answer(1, false, 1));
        practicable(card(1));

        List<TestQueueItemResponse> queue = service.queue(authentication);

        assertThat(queue).hasSize(1);
        TestQueueItemResponse item = queue.get(0);
        assertThat(item.cardId()).isEqualTo(1L);
        assertThat(item.front()).isEqualTo("Pytanie 1");
        assertThat(item.optionA()).isEqualTo("a");
        assertThat(item.optionB()).isEqualTo("b");
        assertThat(item.optionC()).isEqualTo("c");
    }

    @Test
    void theNumberOfScannedCardsIsCapped() {
        List<ReviewLogRepository.TestAnswerRow> rows = new ArrayList<>();
        for (long id = 1; id <= 1_200; id++) {
            rows.add(answer(id, false, 1));
        }
        answers(rows.toArray(new ReviewLogRepository.TestAnswerRow[0]));
        practicable();

        service.count(authentication);

        assertThat(idsAskedAboutAvailability()).hasSize(1_000);
    }
}

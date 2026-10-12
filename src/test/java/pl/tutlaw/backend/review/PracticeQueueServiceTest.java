package pl.tutlaw.backend.review;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import pl.tutlaw.backend.card.Card;
import pl.tutlaw.backend.card.CardRepository;
import pl.tutlaw.backend.card.CorrectOption;
import pl.tutlaw.backend.review.dto.TestQueueItemResponse;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PracticeQueueServiceTest {

    @Mock private CardRepository cardRepository;
    @Mock private CurrentUserResolver currentUserResolver;
    private final Authentication authentication = mock(Authentication.class);

    private PracticeQueueService service;

    @BeforeEach
    void setUp() {
        service = new PracticeQueueService(cardRepository, currentUserResolver);
        User user = new User();
        user.setId(5L);
        // Walidacja listy odbywa się przed ustaleniem użytkownika, więc nie każdy test z niego korzysta.
        lenient().when(currentUserResolver.resolve(authentication)).thenReturn(user);
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

    private void available(Card... cards) {
        when(cardRepository.findPracticeTestCards(ArgumentMatchers.<Collection<Long>>any(), eq(5L)))
                .thenReturn(List.of(cards));
    }

    @Test
    void returnsTheQuestionsInTheRequestedOrder() {
        // repozytorium zwraca w dowolnej kolejności
        available(card(1), card(2), card(3));

        List<TestQueueItemResponse> queue = service.queue(List.of(3L, 1L, 2L), authentication);

        assertThat(queue).extracting(TestQueueItemResponse::cardId).containsExactly(3L, 1L, 2L);
    }

    @Test
    void silentlySkipsQuestionsThatAreNoLongerAvailable() {
        available(card(1), card(3)); // 2: zawieszone, niedostępne albo bez formatu testowego

        List<TestQueueItemResponse> queue = service.queue(List.of(1L, 2L, 3L), authentication);

        assertThat(queue).extracting(TestQueueItemResponse::cardId).containsExactly(1L, 3L);
    }

    @Test
    void duplicatedIdsAreAskedAboutAndReturnedOnce() {
        available(card(1), card(2));

        List<TestQueueItemResponse> queue = service.queue(List.of(1L, 2L, 1L, 2L), authentication);

        assertThat(queue).extracting(TestQueueItemResponse::cardId).containsExactly(1L, 2L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(cardRepository).findPracticeTestCards(captor.capture(), eq(5L));
        assertThat(captor.getValue()).hasSize(2);
    }

    @Test
    void anEmptyListIsABadRequest() {
        assertThatThrownBy(() -> service.queue(List.of(), authentication)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.queue(null, authentication)).isInstanceOf(IllegalArgumentException.class);
        verify(cardRepository, never()).findPracticeTestCards(
                ArgumentMatchers.<Collection<Long>>any(), ArgumentMatchers.<Long>any());
    }

    @Test
    void tooManyQuestionsAreRejected() {
        List<Long> ids = new ArrayList<>();
        for (long id = 1; id <= 201; id++) {
            ids.add(id);
        }

        assertThatThrownBy(() -> service.queue(ids, authentication))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("200");
    }

    @Test
    void exactlyTheMaximumIsAccepted() {
        List<Long> ids = new ArrayList<>();
        List<Card> cards = new ArrayList<>();
        for (long id = 1; id <= 200; id++) {
            ids.add(id);
            cards.add(card(id));
        }
        available(cards.toArray(new Card[0]));

        assertThat(service.queue(ids, authentication)).hasSize(200);
    }

    @Test
    void whenNothingIsAvailableTheQueueIsEmpty() {
        available();

        assertThat(service.queue(List.of(1L, 2L), authentication)).isEmpty();
    }
}

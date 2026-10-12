package pl.tutlaw.backend.card;

import org.junit.jupiter.api.Test;
import pl.tutlaw.backend.testsupport.CardRefs;
import pl.tutlaw.backend.testsupport.FakeRepos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CardScopesTest {

    private static Card card(long id) {
        Card card = new Card();
        card.setId(id);
        card.setFront("P" + id);
        return card;
    }

    /** Repozytorium, które zapisuje, które zapytanie o zakres zostało użyte, i ładuje karty po numerach. */
    private static final class Spy {
        final List<String> calls = new ArrayList<>();
        final List<Card> stored = new ArrayList<>();
        final CardRepository repository = FakeRepos.proxy(CardRepository.class, (method, args) -> {
            calls.add(method);
            if (method.startsWith("findReadableRefs")) {
                return List.of();
            }
            if (method.equals("findAllWithSourceByIdIn")) {
                @SuppressWarnings("unchecked")
                Collection<Long> ids = (Collection<Long>) args[0];
                return CardRefs.byIds(stored, ids);
            }
            throw new UnsupportedOperationException(method);
        });
    }

    @Test
    void theNarrowestScopeWins() {
        Spy spy = new Spy();

        CardScopes.readableRefs(spy.repository, 1L, 2L, 3L, 9L);
        CardScopes.readableRefs(spy.repository, 1L, 2L, null, 9L);
        CardScopes.readableRefs(spy.repository, 1L, null, null, 9L);
        CardScopes.readableRefs(spy.repository, null, null, null, 9L);

        assertThat(spy.calls).containsExactly(
                "findReadableRefsForUserAndSource", "findReadableRefsForUserAndSubject",
                "findReadableRefsForUserAndCategory", "findReadableRefsForUser");
    }

    @Test
    void aCardIsATestQuestionOnlyWhenItHasACorrectOption() {
        assertThat(CardScopes.isTest(CardRefs.of(1, null, CorrectOption.B))).isTrue();
        assertThat(CardScopes.isTest(CardRefs.of(2, 1, null))).isFalse();
    }

    @Test
    void loadInOrderReturnsTheCardsInTheRequestedOrder_evenIfTheDatabaseDoesNot() {
        Spy spy = new Spy();
        for (long id = 1; id <= 5; id++) {
            spy.stored.add(card(id));
        }

        List<Card> loaded = CardScopes.loadInOrder(spy.repository, List.of(4L, 1L, 5L, 2L));

        assertThat(loaded).extracting(Card::getId).containsExactly(4L, 1L, 5L, 2L);
    }

    @Test
    void loadInOrderSkipsCardsThatDisappearedInTheMeantime() {
        Spy spy = new Spy();
        spy.stored.add(card(1));
        spy.stored.add(card(3));

        List<Card> loaded = CardScopes.loadInOrder(spy.repository, List.of(1L, 2L, 3L));

        assertThat(loaded).extracting(Card::getId).containsExactly(1L, 3L);
    }

    @Test
    void loadInOrderWithNoIdsDoesNotTouchTheDatabase() {
        Spy spy = new Spy();

        assertThat(CardScopes.loadInOrder(spy.repository, List.of())).isEmpty();
        assertThat(spy.calls).isEmpty();
    }
}

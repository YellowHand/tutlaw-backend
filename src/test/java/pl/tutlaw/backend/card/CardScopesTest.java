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

    @Test
    void testQuestionsAndFlashcardsAreDisjoint() {
        CardRepository.CardRef test = CardRefs.of(1, null, CorrectOption.A);
        CardRepository.CardRef flashcard = CardRefs.of(2, null, null);

        assertThat(CardScopes.isTest(test)).isTrue();
        assertThat(CardScopes.isFlashcard(test)).isFalse();
        assertThat(CardScopes.isTest(flashcard)).isFalse();
        assertThat(CardScopes.isFlashcard(flashcard)).isTrue();
    }

    @Test
    void anUnlinkedFlashcardIsAlwaysUnlocked_aLinkedOneOnlyAfterTheMistake() {
        CardRepository.CardRef plain = CardRefs.of(2, null, null);
        CardRepository.CardRef linked = CardRefs.of(3, null, null, 1L);

        assertThat(CardScopes.isUnlocked(plain, java.util.Set.of())).isTrue();
        assertThat(CardScopes.isUnlocked(linked, java.util.Set.of())).isFalse();
        assertThat(CardScopes.isUnlocked(linked, java.util.Set.of(9L))).isFalse();
        assertThat(CardScopes.isUnlocked(linked, java.util.Set.of(1L))).isTrue();
    }

    @Test
    void anyLinkedSpotsLinkedFlashcards() {
        List<CardRepository.CardRef> none = List.of(CardRefs.of(1, null, null), CardRefs.of(2, null, CorrectOption.B));
        List<CardRepository.CardRef> some = List.of(CardRefs.of(1, null, null), CardRefs.of(3, null, null, 2L));

        assertThat(CardScopes.anyLinked(none)).isFalse();
        assertThat(CardScopes.anyLinked(some)).isTrue();
    }

    @Test
    void matchesOrigins_acceptsOnlySelectedOrigins_andNeverRejectsAnUnknownOne() {
        java.util.Set<QuestionOrigin> chosen = java.util.EnumSet.of(QuestionOrigin.NOTARY);

        assertThat(CardScopes.matchesOrigins(
                CardRefs.of(1, null, CorrectOption.A, null, QuestionOrigin.NOTARY), chosen)).isTrue();
        assertThat(CardScopes.matchesOrigins(
                CardRefs.of(2, null, CorrectOption.A, null, QuestionOrigin.AUTHORED), chosen)).isFalse();
        // baza nie dopuszcza pustego pochodzenia, ale filtr ma nie ukrywać niczego przez pomyłkę
        assertThat(CardScopes.matchesOrigins(CardRefs.of(3, null, CorrectOption.A, null, null), chosen)).isTrue();
    }
}

package pl.tutlaw.backend.question;

import org.junit.jupiter.api.Test;
import pl.tutlaw.backend.testsupport.QuestionRefs;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestionScopesTest {

    /** 1: akt 1/dz. 1/arkusz 1; 2: akt 2/dz. 1/arkusz 2; 3: akt 3/dz. 2/arkusz 1; 4: akt 3/dz. 2/autorskie. */
    private final List<QuestionRef> refs = List.of(
            QuestionRefs.refOf(QuestionRefs.question(1, 1, 1, 1)),
            QuestionRefs.refOf(QuestionRefs.question(2, 2, 1, 2)),
            QuestionRefs.refOf(QuestionRefs.question(3, 3, 2, 1)),
            QuestionRefs.refOf(QuestionRefs.question(4, 3, 2, 3)));

    private static List<Long> ids(List<QuestionRef> list) {
        return list.stream().map(QuestionRef::getId).toList();
    }

    @Test
    void noScope_keepsTheWholeList() {
        assertThat(ids(QuestionScopes.inScope(refs, null, null, null))).containsExactly(1L, 2L, 3L, 4L);
    }

    @Test
    void eachScope_narrowsToItsQuestions() {
        assertThat(ids(QuestionScopes.inScope(refs, 1L, null, null))).containsExactly(1L, 2L);
        assertThat(ids(QuestionScopes.inScope(refs, null, 3L, null))).containsExactly(3L, 4L);
        assertThat(ids(QuestionScopes.inScope(refs, null, null, 1L))).containsExactly(1L, 3L);
    }

    @Test
    void theNarrowestScopeWins_scopesAreNotCombined() {
        // źródło przed aktem przed dziedziną
        assertThat(ids(QuestionScopes.inScope(refs, 1L, 3L, 1L))).containsExactly(1L, 3L);
        assertThat(ids(QuestionScopes.inScope(refs, 1L, 3L, null))).containsExactly(3L, 4L);
    }

    @Test
    void anUnknownScope_givesAnEmptyList() {
        assertThat(QuestionScopes.inScope(refs, null, 99L, null)).isEmpty();
    }

    @Test
    void aFlashcardIsUnlockedOnlyForAListedQuestionWithAVisibleFlashcard() {
        QuestionRef plain = refs.get(0);
        assertThat(QuestionScopes.isUnlocked(plain, Set.of(1L))).isTrue();
        assertThat(QuestionScopes.isUnlocked(plain, Set.of(2L))).isFalse();

        var hidden = QuestionRefs.question(5);
        hidden.setFlashcardHidden(true);
        assertThat(QuestionScopes.isUnlocked(QuestionRefs.refOf(hidden), Set.of(5L))).isFalse();
    }

    @Test
    void loadInOrder_returnsTheRequestedOrder_andSkipsQuestionsThatDisappeared() {
        QuestionRepository repository = mock(QuestionRepository.class);
        Question q1 = QuestionRefs.question(1);
        Question q3 = QuestionRefs.question(3);
        when(repository.findAllWithRelationsByIdIn(List.of(3L, 2L, 1L))).thenReturn(List.of(q1, q3));

        List<Question> loaded = QuestionScopes.loadInOrder(repository, List.of(3L, 2L, 1L));

        assertThat(loaded).containsExactly(q3, q1);
    }

    @Test
    void loadInOrder_withNoIds_doesNotTouchTheDatabase() {
        QuestionRepository repository = mock(QuestionRepository.class);

        assertThat(QuestionScopes.loadInOrder(repository, List.of())).isEmpty();
        verify(repository, never()).findAllWithRelationsByIdIn(org.mockito.ArgumentMatchers.anyList());
    }
}

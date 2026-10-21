package pl.gradusiuris.backend.review;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import pl.gradusiuris.backend.question.Question;
import pl.gradusiuris.backend.question.QuestionRepository;
import pl.gradusiuris.backend.review.dto.TestQueueItemResponse;
import pl.gradusiuris.backend.testsupport.Pools;
import pl.gradusiuris.backend.testsupport.QuestionRefs;
import pl.gradusiuris.backend.user.CurrentUserResolver;
import pl.gradusiuris.backend.user.User;

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

    @Mock private QuestionRepository questionRepository;
    @Mock private CurrentUserResolver currentUserResolver;
    private final Authentication authentication = mock(Authentication.class);

    private PracticeQueueService service;

    @BeforeEach
    void setUp() {
        service = new PracticeQueueService(questionRepository, currentUserResolver, Pools.holding(Pools::all));
        User user = new User();
        user.setId(5L);
        // Walidacja listy odbywa się przed ustaleniem użytkownika, więc nie każdy test z niego korzysta.
        lenient().when(currentUserResolver.resolve(authentication)).thenReturn(user);
    }

    private static Question question(long id) {
        Question question = QuestionRefs.question(id);
        question.setBody("Pytanie " + id);
        return question;
    }

    private void available(Question... questions) {
        when(questionRepository.findPracticeQuestions(
                ArgumentMatchers.<Collection<Long>>any(), eq(5L), ArgumentMatchers.<Collection<Long>>any()))
                .thenReturn(List.of(questions));
    }

    @Test
    void returnsTheQuestionsInTheRequestedOrder() {
        // repozytorium zwraca w dowolnej kolejności
        available(question(1), question(2), question(3));

        List<TestQueueItemResponse> queue = service.queue(List.of(3L, 1L, 2L), authentication);

        assertThat(queue).extracting(TestQueueItemResponse::questionId).containsExactly(3L, 1L, 2L);
    }

    @Test
    void silentlySkipsQuestionsThatAreNoLongerAvailable() {
        available(question(1), question(3)); // 2: zawieszone, ukryte albo spoza aktów użytkownika

        List<TestQueueItemResponse> queue = service.queue(List.of(1L, 2L, 3L), authentication);

        assertThat(queue).extracting(TestQueueItemResponse::questionId).containsExactly(1L, 3L);
    }

    @Test
    void duplicatedIdsAreAskedAboutAndReturnedOnce() {
        available(question(1), question(2));

        List<TestQueueItemResponse> queue = service.queue(List.of(1L, 2L, 1L, 2L), authentication);

        assertThat(queue).extracting(TestQueueItemResponse::questionId).containsExactly(1L, 2L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(questionRepository).findPracticeQuestions(captor.capture(), eq(5L), ArgumentMatchers.<Collection<Long>>any());
        assertThat(captor.getValue()).hasSize(2);
    }

    @Test
    void anEmptyListIsABadRequest() {
        assertThatThrownBy(() -> service.queue(List.of(), authentication)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.queue(null, authentication)).isInstanceOf(IllegalArgumentException.class);
        verify(questionRepository, never()).findPracticeQuestions(
                ArgumentMatchers.<Collection<Long>>any(), ArgumentMatchers.<Long>any(), ArgumentMatchers.<Collection<Long>>any());
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
        List<Question> questions = new ArrayList<>();
        for (long id = 1; id <= 200; id++) {
            ids.add(id);
            questions.add(question(id));
        }
        available(questions.toArray(new Question[0]));

        assertThat(service.queue(ids, authentication)).hasSize(200);
    }

    @Test
    void whenNothingIsAvailableTheQueueIsEmpty() {
        available();

        assertThat(service.queue(List.of(1L, 2L), authentication)).isEmpty();
    }
}

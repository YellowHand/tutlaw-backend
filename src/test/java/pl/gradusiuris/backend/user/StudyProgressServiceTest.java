package pl.gradusiuris.backend.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import pl.gradusiuris.backend.exam.ExamAttemptRepository;
import pl.gradusiuris.backend.flashcard.FlashcardAdditionRepository;
import pl.gradusiuris.backend.review.CustomTestSessionRepository;
import pl.gradusiuris.backend.review.ReviewLogRepository;
import pl.gradusiuris.backend.review.ReviewMode;
import pl.gradusiuris.backend.review.UserQuestionStateRepository;
import pl.gradusiuris.backend.studytime.StudyTimeRepository;
import pl.gradusiuris.backend.user.dto.StudyProgressResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudyProgressServiceTest {

    private static final long USER = 5L;

    private final CurrentUserResolver resolver = mock(CurrentUserResolver.class);
    private final UserQuestionStateRepository states = mock(UserQuestionStateRepository.class);
    private final ReviewLogRepository logs = mock(ReviewLogRepository.class);
    private final FlashcardAdditionRepository additions = mock(FlashcardAdditionRepository.class);
    private final CustomTestSessionRepository customTests = mock(CustomTestSessionRepository.class);
    private final ExamAttemptRepository exams = mock(ExamAttemptRepository.class);
    private final StudyTimeRepository studyTime = mock(StudyTimeRepository.class);
    private final Authentication authentication = mock(Authentication.class);

    private StudyProgressService service;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(USER);
        when(resolver.resolve(authentication)).thenReturn(user);
        when(states.countByUserIdAndMode(USER, ReviewMode.TEST)).thenReturn(120L);
        when(states.countByUserIdAndMode(USER, ReviewMode.FLASHCARD)).thenReturn(45L);
        when(logs.countByUserIdAndMode(USER, ReviewMode.TEST)).thenReturn(800L);
        when(logs.countByUserIdAndMode(USER, ReviewMode.FLASHCARD)).thenReturn(300L);
        when(additions.countByUserId(USER)).thenReturn(9L);
        when(customTests.countByUserId(USER)).thenReturn(14L);
        when(exams.countByUserId(USER)).thenReturn(3L);
        when(studyTime.sumAll(USER)).thenReturn(36_000L);
        service = new StudyProgressService(resolver, states, logs, additions, customTests, exams, studyTime);
    }

    private void assertNothingDeleted() {
        verify(states, never()).deleteAllByUser(any());
        verify(logs, never()).deleteAllByUser(any());
        verify(additions, never()).deleteAllByUser(any());
        verify(customTests, never()).deleteAllByUser(any());
        verify(exams, never()).deleteAllByUser(any());
        verify(studyTime, never()).deleteAllByUser(any());
    }

    @Test
    void summary_countsWhatWouldDisappear_andDeletesNothing() {
        StudyProgressResponse summary = service.summary(authentication);

        assertThat(summary).isEqualTo(new StudyProgressResponse(120, 45, 1_100, 9, 14, 3, 36_000));
        assertNothingDeleted();
    }

    @Test
    void reset_withTheConfirmationWord_deletesEveryKindOfProgressOfThatUser() {
        StudyProgressResponse removed = service.reset("WYZERUJ", authentication);

        verify(customTests).deleteAllByUser(USER);
        verify(additions).deleteAllByUser(USER);
        verify(logs).deleteAllByUser(USER);
        verify(states).deleteAllByUser(USER);
        verify(exams).deleteAllByUser(USER);
        verify(studyTime).deleteAllByUser(USER);
        // odpowiedź to liczby sprzed kasowania
        assertThat(removed).isEqualTo(new StudyProgressResponse(120, 45, 1_100, 9, 14, 3, 36_000));
    }

    @Test
    void reset_acceptsTheWordTypedWithSpacesAroundIt() {
        service.reset("  WYZERUJ \n", authentication);

        verify(states).deleteAllByUser(USER);
    }

    @Test
    void reset_withoutTheExactWord_changesNothing() {
        for (String wrong : new String[]{null, "", "   ", "wyzeruj", "WYZERUJ!", "TAK", "WYZERUJ WSZYSTKO"}) {
            assertThatThrownBy(() -> service.reset(wrong, authentication))
                    .as("potwierdzenie: %s", wrong)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("WYZERUJ");
        }

        assertNothingDeleted();
    }

    @Test
    void reset_neverTouchesQuestionStateOfOtherUsersNorAnythingElse() {
        service.reset("WYZERUJ", authentication);

        // tylko kasowanie po użytkowniku; żadnego deleteAll()/delete(...) i żadnego save
        for (Object repo : new Object[]{states, logs, additions, customTests, exams, studyTime}) {
            assertThat(mockingDetails(repo).getInvocations())
                    .extracting(invocation -> invocation.getMethod().getName())
                    .noneMatch(name -> name.equals("deleteAll") || name.equals("delete") || name.equals("deleteById")
                            || name.startsWith("save"));
        }
    }

    @Test
    void confirmationWordIsTheOneTheAppAsksFor() {
        assertThat(StudyProgressService.CONFIRMATION_WORD).isEqualTo("WYZERUJ");
    }
}

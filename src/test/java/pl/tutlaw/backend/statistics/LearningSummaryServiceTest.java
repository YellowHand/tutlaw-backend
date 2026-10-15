package pl.tutlaw.backend.statistics;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import pl.tutlaw.backend.exam.ExamAttemptRepository;
import pl.tutlaw.backend.review.AnswerTimes;
import pl.tutlaw.backend.review.CustomTestSessionService;
import pl.tutlaw.backend.review.ReviewLogRepository;
import pl.tutlaw.backend.review.ReviewMode;
import pl.tutlaw.backend.statistics.dto.LearningSummaryResponse;
import pl.tutlaw.backend.studytime.StudyTimeService;
import pl.tutlaw.backend.testsupport.FakeRepos;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LearningSummaryServiceTest {

    private final Authentication authentication = mock(Authentication.class);

    private LearningSummaryService serviceWith(
            long tests, long flashcards, Double avgTest, Double avgFlash, long exams, long customTests,
            StudyTimeService.Totals time) {
        User user = new User();
        user.setId(5L);
        CurrentUserResolver resolver = mock(CurrentUserResolver.class);
        when(resolver.resolve(authentication)).thenReturn(user);
        ReviewLogRepository logs = FakeRepos.proxy(ReviewLogRepository.class, (method, args) -> {
            assertThat(args[0]).isEqualTo(5L);
            switch (method) {
                case "countByUserIdAndMode":
                    return args[1] == ReviewMode.TEST ? tests : flashcards;
                case "averageAnswerSeconds":
                    return args[1] == ReviewMode.TEST ? avgTest : avgFlash;
                default:
                    throw new UnsupportedOperationException("ReviewLogRepository." + method);
            }
        });
        ExamAttemptRepository exam = FakeRepos.proxy(ExamAttemptRepository.class, (method, args) -> {
            assertThat(method).isEqualTo("countByUserId");
            return exams;
        });
        CustomTestSessionService custom = mock(CustomTestSessionService.class);
        when(custom.countFinished(5L)).thenReturn(customTests);
        StudyTimeService study = mock(StudyTimeService.class);
        when(study.totals(5L)).thenReturn(time);
        return new LearningSummaryService(logs, exam, custom, study, resolver);
    }

    @Test
    void combinesAnswersExamsOwnTestsStudyTimeAndAverages() {
        LearningSummaryService service = serviceWith(
                120, 30, 14.5, 6.0, 3, 8, new StudyTimeService.Totals(600, 7_200, 90_000));

        LearningSummaryResponse r = service.get(authentication);

        assertThat(r.totalAnswers()).isEqualTo(150);
        assertThat(r.testAnswers()).isEqualTo(120);
        assertThat(r.flashcardReviews()).isEqualTo(30);
        assertThat(r.examsDone()).isEqualTo(3);
        assertThat(r.customTestsDone()).isEqualTo(8);
        assertThat(r.studySecondsToday()).isEqualTo(600);
        assertThat(r.studySecondsLast7Days()).isEqualTo(7_200);
        assertThat(r.studySecondsTotal()).isEqualTo(90_000);
        assertThat(r.averageSecondsPerQuestion()).isEqualTo(14.5);
        assertThat(r.averageSecondsPerFlashcard()).isEqualTo(6.0);
    }

    @Test
    void aNewAccountHasZerosAndNoAverages() {
        LearningSummaryService service = serviceWith(0, 0, null, null, 0, 0, new StudyTimeService.Totals(0, 0, 0));

        LearningSummaryResponse r = service.get(authentication);

        assertThat(r.totalAnswers()).isZero();
        assertThat(r.averageSecondsPerQuestion()).isNull();
        assertThat(r.averageSecondsPerFlashcard()).isNull();
        assertThat(r.studySecondsTotal()).isZero();
    }

    @Test
    void theAverageQueryCapsAnswersAtTheSameLimitAsTheSessionTimes() throws Exception {
        // Zapytanie ma limit wpisany na sztywno (adnotacja nie przyjmuje zmiennej), więc pilnujemy, żeby zgadzał się ze stałą.
        Method method = ReviewLogRepository.class.getMethod("averageAnswerSeconds", Long.class, ReviewMode.class);
        String query = method.getAnnotation(org.springframework.data.jpa.repository.Query.class).value();

        assertThat(query).contains("> " + AnswerTimes.CAP_SECONDS + " THEN " + AnswerTimes.CAP_SECONDS);
    }
}

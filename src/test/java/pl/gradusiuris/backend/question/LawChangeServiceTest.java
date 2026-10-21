package pl.gradusiuris.backend.question;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pl.gradusiuris.backend.review.UserQuestionStateRepository;
import pl.gradusiuris.backend.testsupport.MutableClock;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LawChangeServiceTest {

    // 2026-10-12 12:00 czasu warszawskiego
    private final MutableClock clock = MutableClock.at("2026-10-12T10:00:00Z");
    private final UserQuestionStateRepository stateRepository = mock(UserQuestionStateRepository.class);
    private final LawChangeService service = new LawChangeService(stateRepository, clock);

    @Test
    void requeue_passesNowAndTodayInTheAppTimeZone() {
        when(stateRepository.requeueAfterLawChange(any(), any())).thenReturn(7);

        int changed = service.requeueChangedQuestions();

        assertThat(changed).isEqualTo(7);
        ArgumentCaptor<LocalDateTime> now = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDate> today = ArgumentCaptor.forClass(LocalDate.class);
        verify(stateRepository).requeueAfterLawChange(now.capture(), today.capture());
        assertThat(now.getValue()).isEqualTo(LocalDateTime.of(2026, 10, 12, 12, 0));
        assertThat(today.getValue()).isEqualTo(LocalDate.of(2026, 10, 12));
    }

    @Test
    void requeue_whenNothingChangedReturnsZero() {
        when(stateRepository.requeueAfterLawChange(any(), any())).thenReturn(0);

        assertThat(service.requeueChangedQuestions()).isZero();
    }

    @Test
    void job_runsTheServiceAtStartupAndDaily() {
        LawChangeService mocked = mock(LawChangeService.class);
        LawChangeJob job = new LawChangeJob(mocked);

        job.run(null);
        job.daily();

        verify(mocked, times(2)).requeueChangedQuestions();
    }

    @Test
    void job_aFailureDoesNotStopTheApplicationNorTheNextRun() {
        LawChangeService mocked = mock(LawChangeService.class);
        when(mocked.requeueChangedQuestions())
                .thenThrow(new IllegalStateException("baza niedostępna"))
                .thenReturn(3);
        LawChangeJob job = new LawChangeJob(mocked);

        job.run(null);   // nie rzuca: aplikacja ma wstać
        job.daily();     // kolejny przebieg działa

        verify(mocked, times(2)).requeueChangedQuestions();
    }

    @Test
    void job_isScheduledJustAfterMidnight() throws Exception {
        var scheduled = LawChangeJob.class.getMethod("daily")
                .getAnnotation(org.springframework.scheduling.annotation.Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo("0 10 0 * * *");
        verify(stateRepository, never()).requeueAfterLawChange(any(), any());
    }
}

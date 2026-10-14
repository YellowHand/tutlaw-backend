package pl.tutlaw.backend.settings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.tutlaw.backend.card.QuestionOrigin;
import pl.tutlaw.backend.settings.dto.ExamTermResponse;
import pl.tutlaw.backend.settings.dto.UpdateExamTermRequest;
import pl.tutlaw.backend.testsupport.MutableClock;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamTermServiceTest {

    private final MutableClock clock = MutableClock.at("2026-10-12T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);

    @Mock private ExamTermRepository repository;

    private ExamTermService service;

    @BeforeEach
    void setUp() {
        service = new ExamTermService(repository, clock);
    }

    private static ExamTerm term(QuestionOrigin origin, LocalDate date) {
        ExamTerm term = new ExamTerm(origin);
        term.setExamDate(date);
        return term;
    }

    // ---------------------------------------------------------------- lista

    @Test
    void list_returnsEveryApplicationInEnumOrder_withoutAuthoredQuestions() {
        when(repository.findAll()).thenReturn(List.of());

        List<ExamTermResponse> terms = service.list();

        assertThat(terms).extracting(ExamTermResponse::origin).containsExactly(
                QuestionOrigin.ADVOCATE_LEGAL_ADVISER,
                QuestionOrigin.JUDGE_PROSECUTOR,
                QuestionOrigin.NOTARY,
                QuestionOrigin.SUPPLEMENTARY_JUDGE,
                QuestionOrigin.BAILIFF);
        assertThat(terms).allSatisfy(t -> {
            assertThat(t.examDate()).isNull();
            assertThat(t.daysToExam()).isNull();
        });
    }

    @Test
    void list_showsTheStoredDateAndDaysLeft() {
        when(repository.findAll()).thenReturn(List.of(
                term(QuestionOrigin.NOTARY, TODAY.plusDays(40)),
                term(QuestionOrigin.BAILIFF, null)));

        List<ExamTermResponse> terms = service.list();

        ExamTermResponse notary = terms.stream().filter(t -> t.origin() == QuestionOrigin.NOTARY).findFirst().orElseThrow();
        assertThat(notary.examDate()).isEqualTo(TODAY.plusDays(40));
        assertThat(notary.daysToExam()).isEqualTo(40L);
        ExamTermResponse bailiff = terms.stream().filter(t -> t.origin() == QuestionOrigin.BAILIFF).findFirst().orElseThrow();
        assertThat(bailiff.examDate()).isNull();
    }

    @Test
    void list_examDay_isZeroDays_andPastDate_hasNoCountdown() {
        when(repository.findAll()).thenReturn(List.of(
                term(QuestionOrigin.NOTARY, TODAY),
                term(QuestionOrigin.BAILIFF, TODAY.minusDays(3))));

        List<ExamTermResponse> terms = service.list();

        assertThat(terms.stream().filter(t -> t.origin() == QuestionOrigin.NOTARY).findFirst().orElseThrow().daysToExam())
                .isEqualTo(0L);
        ExamTermResponse bailiff = terms.stream().filter(t -> t.origin() == QuestionOrigin.BAILIFF).findFirst().orElseThrow();
        assertThat(bailiff.examDate()).isEqualTo(TODAY.minusDays(3));
        assertThat(bailiff.daysToExam()).isNull();
    }

    // ---------------------------------------------------------------- zapis

    @Test
    void update_setsTheDateOfAnExistingTerm() {
        ExamTerm existing = term(QuestionOrigin.NOTARY, null);
        when(repository.findById(QuestionOrigin.NOTARY)).thenReturn(Optional.of(existing));

        ExamTermResponse response = service.update(QuestionOrigin.NOTARY, new UpdateExamTermRequest(TODAY.plusDays(60)));

        ArgumentCaptor<ExamTerm> captor = ArgumentCaptor.forClass(ExamTerm.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(existing);
        assertThat(captor.getValue().getExamDate()).isEqualTo(TODAY.plusDays(60));
        assertThat(response.daysToExam()).isEqualTo(60L);
    }

    @Test
    void update_createsTheRowWhenMissing() {
        when(repository.findById(QuestionOrigin.BAILIFF)).thenReturn(Optional.empty());

        service.update(QuestionOrigin.BAILIFF, new UpdateExamTermRequest(TODAY.plusDays(10)));

        ArgumentCaptor<ExamTerm> captor = ArgumentCaptor.forClass(ExamTerm.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getOrigin()).isEqualTo(QuestionOrigin.BAILIFF);
        assertThat(captor.getValue().getExamDate()).isEqualTo(TODAY.plusDays(10));
    }

    @Test
    void update_nullClearsTheDate() {
        ExamTerm existing = term(QuestionOrigin.NOTARY, TODAY.plusDays(30));
        when(repository.findById(QuestionOrigin.NOTARY)).thenReturn(Optional.of(existing));

        ExamTermResponse response = service.update(QuestionOrigin.NOTARY, new UpdateExamTermRequest(null));

        assertThat(existing.getExamDate()).isNull();
        assertThat(response.examDate()).isNull();
        assertThat(response.daysToExam()).isNull();
    }

    @Test
    void update_todayIsAllowed() {
        when(repository.findById(QuestionOrigin.NOTARY)).thenReturn(Optional.empty());

        ExamTermResponse response = service.update(QuestionOrigin.NOTARY, new UpdateExamTermRequest(TODAY));

        assertThat(response.daysToExam()).isEqualTo(0L);
    }

    @Test
    void update_rejectsAuthoredQuestions() {
        assertThatThrownBy(() -> service.update(QuestionOrigin.AUTHORED, new UpdateExamTermRequest(TODAY.plusDays(5))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tylko dla aplikacji");

        verify(repository, never()).save(any());
    }

    @Test
    void update_rejectsPastDate() {
        assertThatThrownBy(() -> service.update(QuestionOrigin.NOTARY, new UpdateExamTermRequest(TODAY.minusDays(1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("w przeszłości");

        verify(repository, never()).save(any());
    }

    @Test
    void update_rejectsDateMoreThanThreeYearsAway() {
        assertThatThrownBy(() -> service.update(QuestionOrigin.NOTARY, new UpdateExamTermRequest(TODAY.plusYears(3).plusDays(1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("zbyt odległy");

        verify(repository, never()).save(any());
    }
}

package pl.gradusiuris.backend.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.gradusiuris.backend.admin.dto.AdminOverviewResponse;
import pl.gradusiuris.backend.course.CourseRepository;
import pl.gradusiuris.backend.legal.ActSectionRepository;
import pl.gradusiuris.backend.legal.LegalAct;
import pl.gradusiuris.backend.legal.LegalActRepository;
import pl.gradusiuris.backend.legal.LegalFieldRepository;
import pl.gradusiuris.backend.question.QuestionRepository;
import pl.gradusiuris.backend.report.QuestionReportRepository;
import pl.gradusiuris.backend.report.QuestionReportStatus;
import pl.gradusiuris.backend.source.QuestionSource;
import pl.gradusiuris.backend.source.QuestionSourceRepository;
import pl.gradusiuris.backend.track.TrackKind;
import pl.gradusiuris.backend.track.TrackRepository;

import java.util.List;

/** Liczby na ekranie startowym panelu administratora. */
@Service
@RequiredArgsConstructor
public class AdminOverviewService {

    private final TrackRepository trackRepository;
    private final CourseRepository courseRepository;
    private final LegalFieldRepository fieldRepository;
    private final LegalActRepository actRepository;
    private final ActSectionRepository sectionRepository;
    private final QuestionSourceRepository sourceRepository;
    private final QuestionRepository questionRepository;
    private final QuestionReportRepository reportRepository;

    @Transactional(readOnly = true)
    public AdminOverviewResponse overview() {
        int applications = (int) trackRepository.findAll().stream().filter(t -> t.getKind() == TrackKind.EXAM).count();
        List<LegalAct> acts = actRepository.findAll();
        List<QuestionSource> sheets = sourceRepository.findAllSheetsWithTrack();
        return new AdminOverviewResponse(
                applications,
                (int) courseRepository.count(),
                (int) fieldRepository.count(),
                acts.size(),
                (int) acts.stream().filter(LegalAct::isHidden).count(),
                (int) sectionRepository.count(),
                sheets.size(),
                (int) sheets.stream().filter(QuestionSource::isHidden).count(),
                questionRepository.count(),
                questionRepository.countHidden(),
                reportRepository.countByStatus(QuestionReportStatus.NEW));
    }
}

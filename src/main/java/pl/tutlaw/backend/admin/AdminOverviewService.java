package pl.tutlaw.backend.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tutlaw.backend.admin.dto.AdminOverviewResponse;
import pl.tutlaw.backend.course.CourseRepository;
import pl.tutlaw.backend.legal.ActSectionRepository;
import pl.tutlaw.backend.legal.LegalAct;
import pl.tutlaw.backend.legal.LegalActRepository;
import pl.tutlaw.backend.legal.LegalFieldRepository;
import pl.tutlaw.backend.question.QuestionRepository;
import pl.tutlaw.backend.report.QuestionReportRepository;
import pl.tutlaw.backend.report.QuestionReportStatus;
import pl.tutlaw.backend.source.QuestionSource;
import pl.tutlaw.backend.source.QuestionSourceRepository;
import pl.tutlaw.backend.track.TrackKind;
import pl.tutlaw.backend.track.TrackRepository;

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

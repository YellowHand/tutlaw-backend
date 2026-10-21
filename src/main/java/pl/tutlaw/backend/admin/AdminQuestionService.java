package pl.tutlaw.backend.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tutlaw.backend.admin.dto.AdminQuestionMark;
import pl.tutlaw.backend.admin.dto.AdminQuestionRequest;
import pl.tutlaw.backend.admin.dto.AdminQuestionResponse;
import pl.tutlaw.backend.admin.dto.AdminSourceResponse;
import pl.tutlaw.backend.common.GroupCount;
import pl.tutlaw.backend.common.PageParams;
import pl.tutlaw.backend.common.PageResponse;
import pl.tutlaw.backend.common.exception.ConflictException;
import pl.tutlaw.backend.common.exception.ResourceNotFoundException;
import pl.tutlaw.backend.legal.ActSection;
import pl.tutlaw.backend.legal.ActSectionRepository;
import pl.tutlaw.backend.legal.LegalAct;
import pl.tutlaw.backend.legal.LegalActRepository;
import pl.tutlaw.backend.mark.MarkStatus;
import pl.tutlaw.backend.mark.QuestionMark;
import pl.tutlaw.backend.mark.QuestionMarkRepository;
import pl.tutlaw.backend.mark.QuestionMarkType;
import pl.tutlaw.backend.question.Question;
import pl.tutlaw.backend.question.QuestionBody;
import pl.tutlaw.backend.question.QuestionRepository;
import pl.tutlaw.backend.report.QuestionReportRepository;
import pl.tutlaw.backend.source.QuestionSource;
import pl.tutlaw.backend.source.QuestionSourceRepository;
import pl.tutlaw.backend.source.SourceKind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pytania w panelu administratora: lista z wyszukiwarką i filtrami (także ukryte), podgląd, dodawanie i edycja.
 * Pytań się nie usuwa, tylko ukrywa (istniejące {@code PUT /api/questions/{id}/hidden}), bo historia odpowiedzi
 * użytkowników zostaje. Zmiana treści nie przelicza oznaczeń: automat dopisze nowe propozycje przy kolejnym uruchomieniu.
 */
@Service
@RequiredArgsConstructor
public class AdminQuestionService {

    /** Wzorzec LIKE bez ograniczenia: pasuje do wszystkiego. */
    private static final String MATCH_ALL = "%";
    private static final long NO_ID = -1L;

    private final QuestionRepository questionRepository;
    private final LegalActRepository actRepository;
    private final ActSectionRepository sectionRepository;
    private final QuestionSourceRepository sourceRepository;
    private final QuestionMarkRepository markRepository;
    private final QuestionReportRepository reportRepository;

    @Transactional(readOnly = true)
    public PageResponse<AdminQuestionResponse> list(AdminQuestionFilter filter, PageParams params) {
        String query = filter.query() == null ? "" : filter.query().strip();
        long idMatch = query.matches("#?\\d{1,18}") ? Long.parseLong(query.replace("#", "")) : NO_ID;
        boolean anyMark = filter.mark() == null;
        Page<Question> page = questionRepository.findForAdmin(
                query.isEmpty() ? MATCH_ALL : likePattern(query),
                idMatch,
                filter.actId() == null, orNone(filter.actId()),
                filter.sectionId() == null, orNone(filter.sectionId()),
                filter.withoutSection(),
                filter.sourceId() == null, orNone(filter.sourceId()),
                filter.hidden() == null, Boolean.TRUE.equals(filter.hidden()),
                filter.reported(),
                anyMark,
                anyMark ? QuestionMarkType.values()[0] : filter.mark(),
                filter.markStatus() == null ? MarkStatus.CONFIRMED : filter.markStatus(),
                PageRequest.of(params.page(), params.size()));
        List<Question> questions = page.getContent();
        Map<Long, Long> openReports = openReports(questions);
        Map<Long, List<AdminQuestionMark>> marks = marks(questions);
        List<AdminQuestionResponse> items = new ArrayList<>();
        for (Question question : questions) {
            items.add(toResponse(question, openReports.getOrDefault(question.getId(), 0L),
                    marks.getOrDefault(question.getId(), List.of())));
        }
        return new PageResponse<>(items, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    /** Wszystkie źródła pytań do listy wyboru: najpierw pytania autorskie, potem arkusze od najnowszego roku (także ukryte). */
    @Transactional(readOnly = true)
    public List<AdminSourceResponse> sources() {
        return sourceRepository.findAll().stream()
                .sorted(Comparator.<QuestionSource>comparingInt(s -> s.getKind() == SourceKind.AUTHORED ? 0 : 1)
                        .thenComparing(s -> s.getExamYear() == null ? 0 : s.getExamYear(), Comparator.reverseOrder())
                        .thenComparing(QuestionSource::getName))
                .map(s -> new AdminSourceResponse(s.getId(), s.getSlug(), s.getName(), s.getKind(), s.isHidden()))
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminQuestionResponse get(Long id) {
        return describe(find(id));
    }

    @Transactional
    public AdminQuestionResponse create(AdminQuestionRequest request) {
        Question question = new Question();
        apply(question, request);
        requireNoDuplicate(question.getSource().getId(), question.getBody(), null);
        return describe(questionRepository.save(question));
    }

    @Transactional
    public AdminQuestionResponse update(Long id, AdminQuestionRequest request) {
        Question question = find(id);
        apply(question, request);
        requireNoDuplicate(question.getSource().getId(), question.getBody(), question.getId());
        return describe(questionRepository.save(question));
    }

    private void apply(Question question, AdminQuestionRequest request) {
        QuestionDraft draft = QuestionRules.draft(request.body(), request.optionA(), request.optionB(), request.optionC(),
                request.correctOption(), request.explanation(), request.legalBasis(), request.validAsOf(),
                request.lawChangedAt(), request.lawChangeNote());
        LegalAct act = actRepository.findById(request.actId())
                .orElseThrow(() -> new IllegalArgumentException("Nie ma takiego aktu prawnego."));
        QuestionSource source = sourceRepository.findById(request.sourceId())
                .orElseThrow(() -> new IllegalArgumentException("Nie ma takiego źródła pytań."));
        ActSection section = null;
        if (request.sectionId() != null) {
            section = sectionRepository.findById(request.sectionId())
                    .orElseThrow(() -> new IllegalArgumentException("Nie ma takiego rozdziału."));
            if (!section.getLegalAct().getId().equals(act.getId())) {
                throw new IllegalArgumentException("Rozdział nie należy do wybranego aktu prawnego.");
            }
        }
        question.setLegalAct(act);
        question.setSection(section);
        question.setSource(source);
        question.setBody(draft.body());
        question.setOptionA(draft.optionA());
        question.setOptionB(draft.optionB());
        question.setOptionC(draft.optionC());
        question.setCorrectOption(draft.correctOption());
        question.setExplanation(draft.explanation());
        question.setLegalBasis(draft.legalBasis());
        question.setValidAsOf(draft.validAsOf());
        question.setLawChangedAt(draft.lawChangedAt());
        question.setLawChangeNote(draft.lawChangeNote());
    }

    /** To samo pytanie (z dokładnością do odstępów i wielkości liter) w tym samym źródle to powtórzenie. */
    private void requireNoDuplicate(Long sourceId, String body, Long ownId) {
        String key = QuestionRules.duplicateKey(body);
        for (QuestionBody other : questionRepository.findBodiesInSources(List.of(sourceId))) {
            if (!other.getId().equals(ownId) && QuestionRules.duplicateKey(other.getBody()).equals(key)) {
                throw new ConflictException("W tym źródle jest już takie pytanie (numer " + other.getId() + ").");
            }
        }
    }

    private Question find(Long id) {
        return questionRepository.findForAdminById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Nie ma takiego pytania."));
    }

    private AdminQuestionResponse describe(Question question) {
        List<Question> one = List.of(question);
        return toResponse(question, openReports(one).getOrDefault(question.getId(), 0L),
                marks(one).getOrDefault(question.getId(), List.of()));
    }

    private Map<Long, Long> openReports(Collection<Question> questions) {
        Map<Long, Long> result = new HashMap<>();
        if (questions.isEmpty()) {
            return result;
        }
        for (GroupCount row : reportRepository.countOpenByQuestion(questions.stream().map(Question::getId).toList())) {
            result.put(row.getGroupId(), row.getTotal());
        }
        return result;
    }

    private Map<Long, List<AdminQuestionMark>> marks(Collection<Question> questions) {
        Map<Long, List<AdminQuestionMark>> result = new HashMap<>();
        if (questions.isEmpty()) {
            return result;
        }
        for (QuestionMark mark : markRepository.findAllByQuestionIds(questions.stream().map(Question::getId).toList())) {
            result.computeIfAbsent(mark.getQuestionId(), id -> new ArrayList<>())
                    .add(new AdminQuestionMark(mark.getMark(), mark.getStatus()));
        }
        return result;
    }

    private static AdminQuestionResponse toResponse(Question q, long openReports, List<AdminQuestionMark> marks) {
        ActSection section = q.getSection();
        return new AdminQuestionResponse(
                q.getId(), q.getLegalAct().getId(), q.getLegalAct().getName(), q.getLegalAct().getShortName(),
                section == null ? null : section.getId(), section == null ? null : section.getName(),
                q.getSource().getId(), q.getSource().getName(),
                q.getBody(), q.getOptionA(), q.getOptionB(), q.getOptionC(), q.getCorrectOption(),
                q.getExplanation(), q.getLegalBasis(), q.getValidAsOf(), q.getLawChangedAt(), q.getLawChangeNote(),
                q.isHidden(), q.isFlashcardHidden(), q.getCreatedAt(), openReports, marks);
    }

    private static long orNone(Long id) {
        return id == null ? NO_ID : id;
    }

    /** Wzorzec LIKE z małych liter, ze znakami specjalnymi zabezpieczonymi znakiem {@code !}. */
    static String likePattern(String text) {
        String escaped = text.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }
}

package pl.tutlaw.backend.review;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tutlaw.backend.common.exception.ResourceNotFoundException;
import pl.tutlaw.backend.course.Course;
import pl.tutlaw.backend.course.CourseRepository;
import pl.tutlaw.backend.legal.ActSection;
import pl.tutlaw.backend.legal.ActSectionRepository;
import pl.tutlaw.backend.legal.LegalAct;
import pl.tutlaw.backend.legal.LegalActRepository;
import pl.tutlaw.backend.legal.LegalField;
import pl.tutlaw.backend.legal.LegalFieldRepository;
import pl.tutlaw.backend.pool.SourceFilter;
import pl.tutlaw.backend.pool.SourceMode;
import pl.tutlaw.backend.question.CorrectOption;
import pl.tutlaw.backend.question.Question;
import pl.tutlaw.backend.question.dto.LawChangeInfo;
import pl.tutlaw.backend.question.QuestionRepository;
import pl.tutlaw.backend.review.dto.CustomTestRequest;
import pl.tutlaw.backend.review.dto.CustomTestSelectionResponse;
import pl.tutlaw.backend.review.dto.CustomTestSessionDetailResponse;
import pl.tutlaw.backend.review.dto.CustomTestSessionSummaryResponse;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Własne testy jako sesje ("Moje testy"). Test powstaje w chwili ułożenia: zapisujemy konfigurację (zakres, źródło,
 * rodzaj pytań, liczba) i wylosowane pytania. Odpowiedzi dopisują się do niego w trakcie
 * ({@link #recordAnswer}); po odpowiedzi na ostatnie pytanie test jest ukończony. Test, którego nikt nie skończył,
 * zostaje na liście jako nieukończony. Zapisuje się wyłącznie własny test: powtórki, "Popraw błędy" i egzamin mają
 * swoje miejsca (egzamin: historia podejść).
 */
@Service
@RequiredArgsConstructor
public class CustomTestSessionService {

    /** Ile ostatnich testów zwraca lista. */
    static final int LIST_LIMIT = 50;

    private final CustomTestService customTestService;
    private final CustomTestSessionRepository sessionRepository;
    private final CustomTestSessionQuestionRepository sessionQuestionRepository;
    private final QuestionRepository questionRepository;
    private final LegalFieldRepository fieldRepository;
    private final LegalActRepository actRepository;
    private final CourseRepository courseRepository;
    private final ActSectionRepository sectionRepository;
    private final CurrentUserResolver currentUserResolver;
    private final Clock clock;

    /** Losuje pytania ({@link CustomTestService#select}) i zapisuje test. Odpowiedź zawiera numer zapisanego testu. */
    @Transactional
    public CustomTestSelectionResponse draw(CustomTestRequest request, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        CustomTestSelectionResponse selection = customTestService.select(request, authentication);

        List<Long> fieldIds = distinctIds(request.fieldIds());
        List<Long> chosenActIds = distinctIds(request.actIds());
        List<Long> courseIds = distinctIds(request.courseIds());
        List<Long> sectionIds = distinctIds(request.sectionIds());
        // Akty wybranych przedmiotów zapisujemy razem z aktami wybranymi wprost: test da się ułożyć ponownie z tych numerów.
        List<Long> actIds = new ArrayList<>(chosenActIds);
        if (!courseIds.isEmpty()) {
            courseRepository.findActIdsByCourseIds(courseIds).stream()
                    .filter(id -> !actIds.contains(id))
                    .forEach(actIds::add);
        }

        CustomTestSession session = new CustomTestSession();
        session.setUser(user);
        session.setStartedAt(LocalDateTime.now(clock));
        session.setKind(request.kind() == null ? CustomTestKind.ALL : request.kind());
        session.setScopeLabel(CustomTestScopeLabel.describe(scopeNames(courseIds, fieldIds, chosenActIds, sectionIds)));
        session.setFieldIds(fitting(joinIds(fieldIds), FIELD_IDS_LENGTH));
        session.setActIds(fitting(joinIds(actIds), ACT_IDS_LENGTH));
        session.setCourseIds(fitting(joinIds(courseIds), COURSE_IDS_LENGTH));
        session.setSectionIds(fitting(joinIds(sectionIds), SECTION_IDS_LENGTH));
        session.setSourceGroups(SourceMode.orAll(request.source()).filter().serialize());
        session.setRequestedCount(selection.requested());
        session.setAvailableCount(selection.available());
        sessionRepository.save(session);

        // Pytania wczytujemy po numerach jednym zapytaniem; kolejność pytań w sesji to kolejność z losowania.
        Map<Long, Question> questions = new HashMap<>();
        for (Question question : questionRepository.findAllById(selection.questionIds())) {
            questions.put(question.getId(), question);
        }
        List<CustomTestSessionQuestion> rows = new ArrayList<>();
        int position = 1;
        for (Long questionId : selection.questionIds()) {
            Question question = questions.get(questionId);
            if (question == null) {
                continue;
            }
            CustomTestSessionQuestion row = new CustomTestSessionQuestion();
            row.setSession(session);
            row.setQuestion(question);
            row.setPosition(position++);
            rows.add(row);
        }
        sessionQuestionRepository.saveAll(rows);

        return new CustomTestSelectionResponse(
                selection.questionIds(), selection.available(), selection.requested(), session.getId());
    }

    /**
     * Zapisuje odpowiedź w teście. Wołane przy każdej odpowiedzi w sesji; błędny numer testu, test innego
     * użytkownika, pytanie spoza testu i pytanie już zapisane są po cichu pomijane (odpowiedź w nauce i tak się zapisała).
     * Po odpowiedzi na ostatnie pytanie test jest ukończony.
     */
    @Transactional
    public void recordAnswer(
            Long userId, Long sessionId, Long questionId, CorrectOption selected,
            boolean correct, boolean guessed, Double timeSpentSeconds) {
        if (sessionId == null) {
            return;
        }
        CustomTestSession session = sessionRepository.findByIdAndUserId(sessionId, userId).orElse(null);
        if (session == null) {
            return;
        }
        CustomTestSessionQuestion row = sessionQuestionRepository.findBySessionIdAndQuestionId(sessionId, questionId).orElse(null);
        if (row == null || row.isAnswered()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        row.setSelectedOption(selected);
        row.setWasCorrect(correct);
        row.setWasGuessed(guessed);
        row.setTimeSpentSeconds(timeSpentSeconds);
        row.setAnsweredAt(now);
        sessionQuestionRepository.save(row);

        if (!session.isFinished() && sessionQuestionRepository.countBySessionIdAndAnsweredAtIsNull(sessionId) == 0) {
            session.setFinishedAt(now);
            sessionRepository.save(session);
        }
    }

    /** Ostatnie testy użytkownika, od najnowszego. */
    @Transactional(readOnly = true)
    public List<CustomTestSessionSummaryResponse> list(Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        List<CustomTestSession> sessions = sessionRepository.findRecent(user.getId(), PageRequest.of(0, LIST_LIMIT));
        return summarize(sessions);
    }

    /** Wszystkie testy użytkownika (do eksportu danych). */
    @Transactional(readOnly = true)
    public List<CustomTestSessionSummaryResponse> listAll(Long userId) {
        return summarize(sessionRepository.findAllForUser(userId));
    }

    @Transactional(readOnly = true)
    public CustomTestSessionDetailResponse detail(Long sessionId, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        CustomTestSession session = sessionRepository.findByIdAndUserId(sessionId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Nie znaleziono takiego testu."));
        List<CustomTestSessionQuestion> rows = sessionQuestionRepository.findDetailed(session.getId());

        List<CustomTestSessionDetailResponse.Question> questions = rows.stream().map(row -> {
            Question question = row.getQuestion();
            return new CustomTestSessionDetailResponse.Question(
                    row.getPosition(), question.getId(), question.getBody(),
                    question.getOptionA(), question.getOptionB(), question.getOptionC(),
                    question.getCorrectOption(), row.getSelectedOption(), row.getWasCorrect(), row.isWasGuessed(),
                    row.getTimeSpentSeconds(), question.getExplanation(), question.getLegalBasis(),
                    question.getValidAsOf(), question.getLegalAct().getName(),
                    question.getSource().getName(), question.getSource().getKind(),
                    question.getSource().getExamYear(), LawChangeInfo.of(question));
        }).toList();
        return new CustomTestSessionDetailResponse(summaryOf(session, rows), questions);
    }

    @Transactional(readOnly = true)
    public long countFinished(Long userId) {
        return sessionRepository.countByUserIdAndFinishedAtIsNotNull(userId);
    }

    // ---------------------------------------------------------------- pomocnicze

    private List<CustomTestSessionSummaryResponse> summarize(List<CustomTestSession> sessions) {
        if (sessions.isEmpty()) {
            return List.of();
        }
        Map<Long, List<CustomTestSessionQuestion>> bySession = sessionQuestionRepository
                .findBySessionIds(sessions.stream().map(CustomTestSession::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(q -> q.getSession().getId()));
        return sessions.stream()
                .map(s -> summaryOf(s, bySession.getOrDefault(s.getId(), List.of())))
                .toList();
    }

    static CustomTestSessionSummaryResponse summaryOf(CustomTestSession s, List<CustomTestSessionQuestion> rows) {
        int answered = 0;
        int correct = 0;
        int guessed = 0;
        double time = 0;
        for (CustomTestSessionQuestion row : rows) {
            if (!row.isAnswered()) {
                continue;
            }
            answered++;
            if (Boolean.TRUE.equals(row.getWasCorrect())) {
                correct++;
            }
            if (row.isWasGuessed()) {
                guessed++;
            }
            time += AnswerTimes.capped(row.getTimeSpentSeconds());
        }
        return new CustomTestSessionSummaryResponse(
                s.getId(), s.getStartedAt(), s.getFinishedAt(), s.isFinished(), s.getScopeLabel(), s.getKind(),
                parseIds(s.getFieldIds()), parseIds(s.getActIds()), parseIds(s.getCourseIds()), parseIds(s.getSectionIds()),
                SourceMode.fromFilter(SourceFilter.parse(s.getSourceGroups())),
                s.getRequestedCount(), rows.size(), answered, correct, guessed, time);
    }

    /**
     * Nazwy wybranych przedmiotów, dziedzin, aktów prawnych i rozdziałów (w tej kolejności, w kolejności wyboru; rozdział
     * z nazwą aktu: „Kodeks karny: XIX. Przeciwko życiu i zdrowiu"); nieistniejące numery są pomijane.
     */
    private List<String> scopeNames(List<Long> courseIds, List<Long> fieldIds, List<Long> actIds, List<Long> sectionIds) {
        List<String> names = new ArrayList<>();
        if (!courseIds.isEmpty()) {
            Map<Long, String> byId = courseRepository.findAllById(courseIds).stream()
                    .collect(Collectors.toMap(Course::getId, Course::getName));
            courseIds.stream().map(byId::get).filter(Objects::nonNull).forEach(names::add);
        }
        if (!fieldIds.isEmpty()) {
            Map<Long, String> byId = fieldRepository.findAllById(fieldIds).stream()
                    .collect(Collectors.toMap(LegalField::getId, LegalField::getName));
            fieldIds.stream().map(byId::get).filter(Objects::nonNull).forEach(names::add);
        }
        if (!actIds.isEmpty()) {
            Map<Long, String> byId = actRepository.findAllById(actIds).stream()
                    .collect(Collectors.toMap(LegalAct::getId, LegalAct::getName));
            actIds.stream().map(byId::get).filter(Objects::nonNull).forEach(names::add);
        }
        if (!sectionIds.isEmpty()) {
            Map<Long, ActSection> byId = sectionRepository.findAllById(sectionIds).stream()
                    .collect(Collectors.toMap(ActSection::getId, section -> section));
            sectionIds.stream().map(byId::get).filter(Objects::nonNull)
                    .map(section -> section.getLegalAct().getName() + ": " + section.getName())
                    .forEach(names::add);
        }
        // Wybrano coś, czego już nie ma: lepsza ogólna nazwa niż "Cała pula".
        if (names.isEmpty() && (!courseIds.isEmpty() || !fieldIds.isEmpty() || !actIds.isEmpty() || !sectionIds.isEmpty())) {
            names.add("Wybrane działy");
        }
        return names;
    }

    private static List<Long> distinctIds(List<Long> raw) {
        return raw == null ? List.of() : raw.stream().filter(Objects::nonNull).distinct().toList();
    }

    /** Długości kolumn field_ids, act_ids, course_ids i section_ids w bazie. */
    static final int FIELD_IDS_LENGTH = 2000;
    static final int ACT_IDS_LENGTH = 2000;
    static final int COURSE_IDS_LENGTH = 500;
    static final int SECTION_IDS_LENGTH = 2000;

    /** Lista numerów mieszcząca się w kolumnie: za długą ucinamy na ostatnim całym numerze. */
    static String fitting(String joined, int maxLength) {
        if (joined.length() <= maxLength) {
            return joined;
        }
        int cut = joined.lastIndexOf(',', maxLength);
        return cut < 0 ? "" : joined.substring(0, cut);
    }

    static String joinIds(List<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    static List<Long> parseIds(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String part : stored.split(",")) {
            try {
                ids.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ignored) {
                // uszkodzony wpis: pomijamy
            }
        }
        return ids;
    }
}

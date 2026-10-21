package pl.tutlaw.backend.exam;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tutlaw.backend.common.PageParams;
import pl.tutlaw.backend.common.PageResponse;
import pl.tutlaw.backend.common.exception.ResourceNotFoundException;
import pl.tutlaw.backend.pool.Pool;
import pl.tutlaw.backend.pool.PoolService;
import pl.tutlaw.backend.pool.SourceMode;
import pl.tutlaw.backend.question.CorrectOption;
import pl.tutlaw.backend.question.Question;
import pl.tutlaw.backend.question.dto.LawChangeInfo;
import pl.tutlaw.backend.question.QuestionRef;
import pl.tutlaw.backend.question.QuestionRepository;
import pl.tutlaw.backend.question.QuestionScopes;
import pl.tutlaw.backend.exam.dto.ExamAttemptResponse;
import pl.tutlaw.backend.exam.dto.ExamAvailabilityResponse;
import pl.tutlaw.backend.exam.dto.ExamResultResponse;
import pl.tutlaw.backend.exam.dto.ExamSheetsResponse;
import pl.tutlaw.backend.exam.dto.ExamStartResponse;
import pl.tutlaw.backend.exam.dto.ExamSubmitRequest;
import pl.tutlaw.backend.review.ReviewMode;
import pl.tutlaw.backend.review.ReviewQueueMapper;
import pl.tutlaw.backend.review.UserQuestionStateRepository;
import pl.tutlaw.backend.review.dto.TestQueueItemResponse;
import pl.tutlaw.backend.settings.UserSettings;
import pl.tutlaw.backend.settings.UserSettingsRepository;
import pl.tutlaw.backend.source.QuestionSource;
import pl.tutlaw.backend.source.QuestionSourceRepository;
import pl.tutlaw.backend.source.SourceKind;
import pl.tutlaw.backend.track.Track;
import pl.tutlaw.backend.track.TrackKind;
import pl.tutlaw.backend.track.TrackRepository;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Egzamin próbny w stałym formacie ({@link ExamFormat}): losowe {@value ExamFormat#QUESTION_COUNT} pytań z całej
 * puli ścieżki użytkownika, zaliczenie od {@value ExamFormat#PASS_POINTS} punktów. Zakresu, liczby pytań ani progu
 * nie wybiera użytkownik (zawężenie puli do wybranych aktów też go nie dotyczy, tak jak prawdziwego egzaminu); wybiera
 * tylko źródło pytań ({@link SourceMode}: wszystkie, Ministerstwo Sprawiedliwości albo autorskie) albo konkretny
 * <b>egzamin MS</b>: arkusz jednej aplikacji z jednego roku, w całości i w oryginalnej kolejności ({@link #sheets},
 * {@link #start(SourceMode, Long, Authentication)}).
 * Ocena i próg są po stronie serwera, a podsumowanie podejścia jest zapisywane razem z odpowiedziami, żeby do raportu dało się
 * wrócić w „Moich testach" ({@link #attempt}).
 * Egzamin jest odizolowany od nauki: nie zmienia terminów powtórek (FSRS), dziennika odpowiedzi, dziennych limitów
 * ani licznika błędów. Po egzaminie użytkownik może przerobić swoje błędy zwykłą sesją testową.
 * Ćwiczenie z wybranego działu to osobna funkcja bez oceny "zdał / nie zdał" (własny test).
 */
@Service
@RequiredArgsConstructor
public class ExamService {

    /** Nazwa zakresu w raporcie i historii: egzamin zawsze obejmuje cały materiał wybranego źródła. */
    static final String SCOPE_LABEL = "Cała pula";
    /** Początek podpisu egzaminu MS z wybranego roku („Egzamin MS: Notarialna 2024"). */
    static final String SHEET_LABEL_PREFIX = "Egzamin MS: ";
    /** Podpis egzaminu MS, gdy arkusza o podanym numerze już nie ma (wynik i tak zapisujemy). */
    static final String SHEET_LABEL_FALLBACK = "Egzamin MS";

    private final QuestionRepository questionRepository;
    private final UserQuestionStateRepository stateRepository;
    private final ExamAttemptRepository attemptRepository;
    private final ExamAttemptAnswerRepository answerRepository;
    private final CurrentUserResolver currentUserResolver;
    private final Clock clock;
    private final PoolService poolService;
    private final QuestionSourceRepository sourceRepository;
    private final TrackRepository trackRepository;
    private final UserSettingsRepository settingsRepository;

    private final Random random = new Random();

    /**
     * Losuje {@value ExamFormat#QUESTION_COUNT} pytań z całej puli ścieżki (bez zawężenia do aktów) z wybranego źródła
     * i niezawieszonych w testach. Gdy jest ich mniej niż 150, egzamin ma tyle, ile jest, a próg zaliczenia
     * maleje proporcjonalnie (zob. {@link ExamFormat#passPointsFor}). Ocena egzaminu (submit) niczego nie filtruje:
     * ocenia to, co zostało pokazane.
     *
     * @param source  z jakiego źródła losować; brak (null) to wszystkie źródła
     * @param sheetId numer arkusza egzaminu MS: gdy jest podany, egzamin to cały ten arkusz (bez losowania, w kolejności
     *                arkusza), a {@code source} jest pomijane
     */
    @Transactional(readOnly = true)
    public ExamStartResponse start(SourceMode source, Long sheetId, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        if (sheetId != null) {
            return startSheet(user, sheetId);
        }
        SourceMode mode = SourceMode.orAll(source);

        // Losujemy na samych numerach; pełne pytania ładujemy dopiero dla wylosowanych (jedno zapytanie).
        List<Long> drawable = drawableIds(user, mode);
        if (drawable.isEmpty()) {
            throw new IllegalArgumentException(mode == SourceMode.ALL
                    ? "Nie ma teraz pytań, które możesz rozwiązać na egzaminie."
                    : "Nie ma teraz pytań z tego źródła, które możesz rozwiązać na egzaminie. Wybierz inne źródło.");
        }

        Collections.shuffle(drawable, random);
        List<Long> drawn = drawable.stream().limit(ExamFormat.QUESTION_COUNT).toList();
        List<TestQueueItemResponse> questions = QuestionScopes.loadInOrder(questionRepository, drawn).stream()
                .map(ReviewQueueMapper::toTest)
                .toList();
        return new ExamStartResponse(scopeLabel(mode), ExamFormat.QUESTION_COUNT, ExamFormat.passPointsFor(questions.size()), questions);
    }

    /**
     * Egzamin MS z wybranego roku: wszystkie widoczne pytania arkusza, w kolejności wczytania arkusza (bez losowania),
     * ale bez pytań zawieszonych przez użytkownika w testach. Arkusz może być dowolnej aplikacji: nie ma tu ani puli
     * ścieżki, ani zawężenia do aktów. Pełny arkusz to {@value ExamFormat#QUESTION_COUNT} pytań; dłuższy zostałby
     * obcięty do tylu, bo tyle ma egzamin i tyle przyjmuje zapis wyniku.
     */
    private ExamStartResponse startSheet(User user, Long sheetId) {
        QuestionSource sheet = sourceRepository.findById(sheetId)
                .filter(QuestionSource::isExamSheet)
                .orElseThrow(() -> new IllegalArgumentException("Nie ma takiego egzaminu MS. Wybierz inny."));
        Set<Long> suspended = new HashSet<>(stateRepository.findSuspendedQuestionIds(user.getId(), ReviewMode.TEST));
        List<Long> drawn = questionRepository.findRefsInSources(List.of(sheet.getId())).stream()
                .map(QuestionRef::getId)
                .filter(id -> !suspended.contains(id))
                .limit(ExamFormat.QUESTION_COUNT)
                .toList();
        if (drawn.isEmpty()) {
            throw new IllegalArgumentException(
                    "Z tego egzaminu MS nie ma teraz pytań, które możesz rozwiązać. Wybierz inny egzamin.");
        }
        List<TestQueueItemResponse> questions = QuestionScopes.loadInOrder(questionRepository, drawn).stream()
                .map(ReviewQueueMapper::toTest)
                .toList();
        return new ExamStartResponse(sheetLabel(sheet), ExamFormat.QUESTION_COUNT, ExamFormat.passPointsFor(questions.size()), questions);
    }

    /**
     * Egzaminy MS do wyboru: arkusze wszystkich aplikacji według lat, z liczbą pytań, jaka będzie na egzaminie (bez pytań
     * zawieszonych w testach). Pomija arkusze bez pytań i aplikacje bez arkuszy. Aplikacja użytkownika jest pierwsza.
     */
    @Transactional(readOnly = true)
    public ExamSheetsResponse sheets(Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        List<QuestionSourceRepository.SourceRow> sheets = sourceRepository.findAllRows().stream()
                .filter(s -> s.getKind() == SourceKind.EXAM_SHEET && s.getTrackId() != null && s.getExamYear() != null)
                .toList();
        if (sheets.isEmpty()) {
            return new ExamSheetsResponse(List.of());
        }

        Set<Long> suspended = new HashSet<>(stateRepository.findSuspendedQuestionIds(user.getId(), ReviewMode.TEST));
        Map<Long, Integer> countBySheet = new HashMap<>();
        for (QuestionRef ref : questionRepository.findRefsInSources(sheets.stream().map(QuestionSourceRepository.SourceRow::getId).toList())) {
            if (!suspended.contains(ref.getId())) {
                countBySheet.merge(ref.getSourceId(), 1, Integer::sum);
            }
        }

        Map<Long, List<ExamSheetsResponse.Sheet>> sheetsByTrack = new HashMap<>();
        for (QuestionSourceRepository.SourceRow sheet : sheets) {
            int count = countBySheet.getOrDefault(sheet.getId(), 0);
            if (count > 0) {
                sheetsByTrack.computeIfAbsent(sheet.getTrackId(), k -> new ArrayList<>()).add(
                        new ExamSheetsResponse.Sheet(sheet.getId(), sheet.getExamYear(), Math.min(count, ExamFormat.QUESTION_COUNT)));
            }
        }

        Long ownTrackId = settingsRepository.findById(user.getId()).map(UserSettings::getTrackId).orElse(null);
        List<ExamSheetsResponse.Application> applications = new ArrayList<>();
        for (Track track : trackRepository.findAllByOrderBySortOrderAscIdAsc()) {
            List<ExamSheetsResponse.Sheet> ofTrack = sheetsByTrack.get(track.getId());
            if (track.getKind() != TrackKind.EXAM || ofTrack == null) {
                continue;
            }
            ofTrack.sort(Comparator.comparingInt(ExamSheetsResponse.Sheet::year).reversed().thenComparing(ExamSheetsResponse.Sheet::id));
            boolean own = track.getId().equals(ownTrackId);
            ExamSheetsResponse.Application application = new ExamSheetsResponse.Application(
                    track.getId(), track.getSlug(), track.getName(), track.getShortName(), own, List.copyOf(ofTrack));
            if (own) {
                applications.add(0, application);
            } else {
                applications.add(application);
            }
        }
        return new ExamSheetsResponse(applications);
    }

    /** Ile pytań da się wylosować z każdego źródła; te same reguły co w {@link #start}. */
    @Transactional(readOnly = true)
    public ExamAvailabilityResponse availability(Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        return new ExamAvailabilityResponse(
                drawableIds(user, SourceMode.ALL).size(),
                drawableIds(user, SourceMode.MINISTRY).size(),
                drawableIds(user, SourceMode.AUTHORED).size());
    }

    /** Numery pytań do wylosowania: cała pula ścieżki (bez zawężenia do aktów) z wybranego źródła, bez zawieszonych w testach. */
    private List<Long> drawableIds(User user, SourceMode mode) {
        Pool pool = poolService.forUser(user.getId(), mode.filter()).unnarrowed();
        Set<Long> suspended = new HashSet<>(stateRepository.findSuspendedQuestionIds(user.getId(), ReviewMode.TEST));
        return new ArrayList<>(questionRepository.findRefsInPool(pool.actIds(), pool.sourceIds())
                .stream()
                .filter(ref -> !suspended.contains(ref.getId()))
                .map(QuestionRef::getId)
                .toList());
    }

    /** Podpis egzaminu MS z wybranego roku w raporcie i historii: „Egzamin MS: Notarialna 2024". */
    static String sheetLabel(QuestionSource sheet) {
        return SHEET_LABEL_PREFIX + sheet.getName();
    }

    /** Podpis egzaminu w raporcie i historii. */
    static String scopeLabel(SourceMode mode) {
        return switch (SourceMode.orAll(mode)) {
            case ALL -> SCOPE_LABEL;
            case MINISTRY -> "Ministerstwo Sprawiedliwości";
            case AUTHORED -> "Pytania autorskie";
        };
    }

    /**
     * Ocenia egzamin, zapisuje podsumowanie podejścia razem z odpowiedziami i zwraca raport ze wszystkimi pytaniami
     * w kolejności egzaminu.
     */
    @Transactional
    public ExamResultResponse submit(ExamSubmitRequest request, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);

        List<ExamSubmitRequest.Answer> answers = request.answers();
        Set<Long> ids = new HashSet<>();
        for (ExamSubmitRequest.Answer answer : answers) {
            if (!ids.add(answer.questionId())) {
                throw new IllegalArgumentException("To samo pytanie zostało przesłane więcej niż raz.");
            }
        }

        Map<Long, Question> found = new HashMap<>();
        for (Question question : questionRepository.findForGrading(ids)) {
            found.put(question.getId(), question);
        }

        // Tylko pytania, które da się ocenić, w kolejności egzaminu; reszta to „wykluczone".
        List<GradedAnswer> graded = new ArrayList<>();
        int excluded = 0;
        for (ExamSubmitRequest.Answer answer : answers) {
            Question question = found.get(answer.questionId());
            if (question == null) {
                excluded++;
            } else {
                graded.add(new GradedAnswer(question, answer.selectedOption()));
            }
        }
        if (graded.isEmpty()) {
            throw new IllegalArgumentException("Żadne z przesłanych pytań nie jest już dostępne, więc nie ma czego oceniać.");
        }

        int total = graded.size();
        int answered = (int) graded.stream().filter(g -> g.selected() != null).count();
        int correct = (int) graded.stream().filter(GradedAnswer::isCorrect).count();
        // Próg ustala serwer (format jest stały), nie klient.
        int passPoints = ExamFormat.passPointsFor(total);
        ExamAttempt attempt = new ExamAttempt();
        attempt.setUser(user);
        attempt.setFinishedAt(LocalDateTime.now(clock));
        attempt.setScopeLabel(request.sheetId() == null
                ? scopeLabel(request.source())
                : sourceRepository.findById(request.sheetId())
                        .filter(QuestionSource::isExamSheet)
                        .map(ExamService::sheetLabel)
                        .orElse(SHEET_LABEL_FALLBACK));
        attempt.setQuestionCount(total);
        attempt.setAnsweredCount(answered);
        attempt.setCorrectCount(correct);
        attempt.setPassPoints(passPoints);
        attempt.setPassThresholdPercent(ExamScoring.thresholdPercent(passPoints, total));
        attempt.setPassed(ExamScoring.passed(correct, total));
        attempt.setTimeLimitMinutes(request.timeLimitMinutes());
        attempt.setDurationSeconds(request.durationSeconds());
        attempt.setTimedOut(Boolean.TRUE.equals(request.timedOut()));
        attempt.setExcludedCount(excluded);
        attempt.setHasAnswers(true);
        attemptRepository.save(attempt);
        answerRepository.saveAll(toAnswerRows(attempt, graded));

        return report(attempt, graded);
    }

    /**
     * Raport podejścia z zapisanych odpowiedzi („Moje testy" → Egzaminy). Treść pytań, poprawne odpowiedzi i ocena
     * poszczególnych pytań pochodzą z AKTUALNYCH pytań (poprawione pytanie pokazuje się w raporcie poprawione), a wynik
     * w nagłówku to wynik z chwili egzaminu. Podejście bez zapisanych odpowiedzi (sprzed poprawki 2 etapu 34), cudze
     * albo nieistniejące to 404.
     */
    @Transactional(readOnly = true)
    public ExamResultResponse attempt(Long attemptId, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        ExamAttempt attempt = attemptRepository.findByIdAndUserId(attemptId, user.getId())
                .filter(ExamAttempt::isHasAnswers)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Nie ma raportu tego egzaminu. Starsze podejścia mają zapisany tylko wynik."));
        List<GradedAnswer> graded = answerRepository.findDetailed(attempt.getId()).stream()
                .map(row -> new GradedAnswer(row.getQuestion(), row.getSelectedOption()))
                .toList();
        return report(attempt, graded);
    }

    /** Jedna ocenione pytanie egzaminu: pytanie i to, co użytkownik wybrał ({@code null} = bez odpowiedzi). */
    private record GradedAnswer(Question question, CorrectOption selected) {
        boolean isCorrect() {
            return selected != null && selected == question.getCorrectOption();
        }
    }

    private static List<ExamAttemptAnswer> toAnswerRows(ExamAttempt attempt, List<GradedAnswer> graded) {
        List<ExamAttemptAnswer> rows = new ArrayList<>();
        for (GradedAnswer answer : graded) {
            ExamAttemptAnswer row = new ExamAttemptAnswer();
            row.setAttempt(attempt);
            row.setQuestion(answer.question());
            row.setPosition(rows.size() + 1);
            row.setSelectedOption(answer.selected());
            rows.add(row);
        }
        return rows;
    }

    /** Raport: nagłówek z podsumowania podejścia, podział na źródła i pytania w kolejności egzaminu. */
    private static ExamResultResponse report(ExamAttempt attempt, List<GradedAnswer> graded) {
        Map<String, int[]> bySource = new LinkedHashMap<>(); // [wszystkie, poprawne]
        List<ExamResultResponse.QuestionResult> questions = new ArrayList<>();
        for (GradedAnswer answer : graded) {
            Question question = answer.question();
            String sourceName = question.getSource().getName();
            int[] counts = bySource.computeIfAbsent(sourceName == null ? "" : sourceName, k -> new int[2]);
            counts[0]++;
            if (answer.isCorrect()) {
                counts[1]++;
            }
            questions.add(new ExamResultResponse.QuestionResult(
                    question.getId(), question.getBody(), question.getOptionA(), question.getOptionB(), question.getOptionC(),
                    answer.selected(), question.getCorrectOption(), answer.isCorrect(),
                    question.getExplanation(), question.getLegalBasis(), question.getValidAsOf(), sourceName,
                    question.getSource().getKind(), question.getSource().getExamYear(),
                    question.getLegalAct().getName(), question.getLegalAct().getShortName(),
                    LawChangeInfo.of(question)));
        }
        List<ExamResultResponse.SourceBreakdown> breakdown = bySource.entrySet().stream()
                .map(e -> new ExamResultResponse.SourceBreakdown(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .sorted(Comparator.comparingInt(ExamResultResponse.SourceBreakdown::total).reversed()
                        .thenComparing(ExamResultResponse.SourceBreakdown::sourceName))
                .toList();
        return new ExamResultResponse(toResponse(attempt), attempt.getExcludedCount(), breakdown, questions);
    }

    /** Jedna strona podejść użytkownika, od najnowszego. */
    @Transactional(readOnly = true)
    public PageResponse<ExamAttemptResponse> history(PageParams params, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        return PageResponse.of(attemptRepository.findPage(user.getId(), PageRequest.of(params.page(), params.size()))
                .map(ExamService::toResponse));
    }

    static ExamAttemptResponse toResponse(ExamAttempt attempt) {
        return new ExamAttemptResponse(
                attempt.getId(), attempt.getFinishedAt(), attempt.getScopeLabel(),
                attempt.getQuestionCount(), attempt.getAnsweredCount(), attempt.getCorrectCount(),
                ExamScoring.scorePercent(attempt.getCorrectCount(), attempt.getQuestionCount()),
                attempt.getPassThresholdPercent(), attempt.getPassPoints(), attempt.isPassed(),
                attempt.getTimeLimitMinutes(), attempt.getDurationSeconds(), attempt.isTimedOut(),
                attempt.isHasAnswers());
    }
}

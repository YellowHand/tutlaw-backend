package pl.gradusiuris.backend.settings;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.gradusiuris.backend.pool.Pool;
import pl.gradusiuris.backend.pool.PoolService;
import pl.gradusiuris.backend.pool.UserPoolActRepository;
import pl.gradusiuris.backend.question.QuestionRef;
import pl.gradusiuris.backend.question.QuestionRepository;
import pl.gradusiuris.backend.review.ReviewLogRepository;
import pl.gradusiuris.backend.review.ReviewMode;
import pl.gradusiuris.backend.review.SchedulingRules;
import pl.gradusiuris.backend.review.UserQuestionStateRepository;
import pl.gradusiuris.backend.settings.dto.ChooseThemeRequest;
import pl.gradusiuris.backend.settings.dto.ChooseTrackRequest;
import pl.gradusiuris.backend.settings.dto.LearningSettingsResponse;
import pl.gradusiuris.backend.settings.dto.UpdateLearningSettingsRequest;
import pl.gradusiuris.backend.track.Track;
import pl.gradusiuris.backend.track.TrackRepository;
import pl.gradusiuris.backend.track.dto.TrackSummary;
import pl.gradusiuris.backend.user.CurrentUserResolver;
import pl.gradusiuris.backend.user.User;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ustawienia nauki użytkownika: odczyt (z wartościami domyślnymi), zapis, wybór ścieżki (aplikacji albo Studenta),
 * wybór wyglądu aplikacji (ciemny/jasny), dzienny limit nowych pytań i plan nowych pytań do egzaminu. Data egzaminu
 * nie jest ustawieniem użytkownika: bierze się z terminu wybranej aplikacji ({@link ExamTerm}), który ustawia
 * administrator.
 */
@Service
@RequiredArgsConstructor
public class LearningSettingsService {

    static final double MIN_RETENTION = 0.70;
    static final double MAX_RETENTION = 0.97;
    private final UserSettingsRepository repository;
    private final LearningDefaults defaults;
    private final CurrentUserResolver currentUserResolver;
    private final QuestionRepository questionRepository;
    private final UserQuestionStateRepository stateRepository;
    private final ExamTermRepository examTerms;
    private final ReviewLogRepository reviewLogs;
    private final TrackRepository trackRepository;
    private final PoolService poolService;
    private final UserPoolActRepository poolActRepository;
    private final Clock clock;

    /** Obowiązujące ustawienia (z domyślnymi tam, gdzie użytkownik niczego nie ustawił). Używane przy każdej odpowiedzi i budowie kolejki. */
    @Transactional(readOnly = true)
    public EffectiveSettings effectiveFor(Long userId) {
        UserSettings stored = repository.findById(userId).orElse(null);
        return resolve(stored, examDateOf(stored), LocalDate.now(clock));
    }

    /** Termin egzaminu aplikacji wybranej przez użytkownika; {@code null}, gdy nie wybrał, to Student albo termin nieogłoszony. */
    private LocalDate examDateOf(UserSettings stored) {
        if (stored == null || stored.getTrackId() == null) {
            return null;
        }
        return examTerms.findById(stored.getTrackId()).map(ExamTerm::getExamDate).orElse(null);
    }

    @Transactional(readOnly = true)
    public LearningSettingsResponse get(Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        UserSettings stored = repository.findById(user.getId()).orElse(null);
        return describe(user.getId(), stored, LocalDate.now(clock));
    }

    /**
     * Wybór ścieżki: jednej z aplikacji albo Studenta. Od tej chwili pulą pytań jest pula tej ścieżki, a egzaminem termin
     * aplikacji (Student nie ma terminu ani planu). Zmiana ścieżki czyści zawężenie do aktów, bo dotyczyło poprzedniej
     * ścieżki. Ponowny wybór tej samej ścieżki niczego nie zmienia.
     */
    @Transactional
    public LearningSettingsResponse chooseTrack(ChooseTrackRequest request, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        Track track = trackRepository.findById(request.trackId())
                .orElseThrow(() -> new IllegalArgumentException("Nie ma takiej ścieżki."));

        UserSettings settings = repository.findById(user.getId()).orElseGet(() -> new UserSettings(user.getId()));
        boolean changed = !track.getId().equals(settings.getTrackId());
        settings.setTrackId(track.getId());
        if (changed) {
            poolActRepository.deleteByUserId(user.getId());
        }
        settings.setUpdatedAt(LocalDateTime.now(clock));
        repository.save(settings);

        return describe(user.getId(), settings, LocalDate.now(clock));
    }

    /** Wybór wyglądu aplikacji. Zapisany na koncie, więc taki sam na każdym urządzeniu. */
    @Transactional
    public LearningSettingsResponse chooseTheme(ChooseThemeRequest request, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        if (request.theme() == null) {
            throw new IllegalArgumentException("Wybierz wygląd: ciemny albo jasny.");
        }

        UserSettings settings = repository.findById(user.getId()).orElseGet(() -> new UserSettings(user.getId()));
        settings.setTheme(request.theme());
        settings.setUpdatedAt(LocalDateTime.now(clock));
        repository.save(settings);

        return describe(user.getId(), settings, LocalDate.now(clock));
    }

    /**
     * Ile nowych pytań testowych dziennie dodać do kolejki. Własny limit użytkownika obowiązuje wprost. Bez własnego
     * limitu jest automatyczny: nie mniej niż domyślny, a gdy plan do egzaminu wymaga więcej, tyle, ile trzeba, ale
     * nie więcej niż {@value SchedulingRules#MAX_AUTO_NEW_PER_DAY}. Bez terminu egzaminu (albo w rezerwie na powtórki)
     * automatyczny limit to po prostu domyślny. Liczenie planu czyta karty użytkownika, więc robimy je tylko wtedy,
     * gdy jest potrzebne.
     */
    @Transactional(readOnly = true)
    public int dailyNewTestQuestionLimit(Long userId) {
        UserSettings stored = repository.findById(userId).orElse(null);
        EffectiveSettings e = resolve(stored, examDateOf(stored), LocalDate.now(clock));
        if (!e.newTestQuestionsAuto() || e.daysToExam() == null || SchedulingRules.planningDays(e.daysToExam()) == 0) {
            return e.newTestQuestionsPerDay();
        }
        NewCounts counts = countNew(userId);
        long required = SchedulingRules.suggestedPerDay(counts.newTests() + counts.startedToday(), e.daysToExam());
        return SchedulingRules.autoNewQuestionsLimit(e.newTestQuestionsPerDay(), required);
    }

    @Transactional
    public LearningSettingsResponse update(UpdateLearningSettingsRequest request, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        LocalDate today = LocalDate.now(clock);
        validate(request);

        UserSettings settings = repository.findById(user.getId()).orElseGet(() -> new UserSettings(user.getId()));
        settings.setNewFlashcardsPerDay(request.newFlashcardsPerDay());
        settings.setNewTestQuestionsPerDay(request.newTestQuestionsPerDay());
        settings.setMaxReviewsPerDay(request.maxReviewsPerDay());
        settings.setDesiredRetention(request.desiredRetention());
        settings.setUpdatedAt(LocalDateTime.now(clock));
        repository.save(settings);

        return describe(user.getId(), settings, today);
    }

    /** Czysta część: składa obowiązujące wartości z zapisanych i domyślnych. */
    EffectiveSettings resolve(UserSettings stored, LocalDate examDate, LocalDate today) {
        int newFlashcards = stored != null && stored.getNewFlashcardsPerDay() != null
                ? stored.getNewFlashcardsPerDay() : defaults.newFlashcardsPerDay();
        boolean newTestsAuto = stored == null || stored.getNewTestQuestionsPerDay() == null;
        int newTests = newTestsAuto ? defaults.newTestQuestionsPerDay() : stored.getNewTestQuestionsPerDay();
        int maxReviews = stored != null && stored.getMaxReviewsPerDay() != null
                ? stored.getMaxReviewsPerDay() : defaults.maxReviewsPerDay();
        double retention = stored != null && stored.getDesiredRetention() != null
                ? stored.getDesiredRetention() : defaults.retention();
        Long trackId = stored != null ? stored.getTrackId() : null;

        Long daysToExam = null;
        if (examDate != null) {
            long days = ChronoUnit.DAYS.between(today, examDate);
            if (days >= 0) {
                daysToExam = days;
            }
        }

        return new EffectiveSettings(
                newFlashcards, newTests, maxReviews,
                retention, SchedulingRules.effectiveRetention(retention, daysToExam),
                trackId, examDate, daysToExam, newTestsAuto);
    }

    private LearningSettingsResponse describe(Long userId, UserSettings stored, LocalDate today) {
        EffectiveSettings e = resolve(stored, examDateOf(stored), today);
        LearningSettingsResponse.Plan plan = e.daysToExam() == null
                ? null : buildPlan(userId, e.daysToExam());

        // Limit w mocy: własny, a bez niego automatyczny, czyli wyliczony z planu (nie mniej niż domyślny, nie więcej niż sufit).
        int newTestsInForce = e.newTestQuestionsAuto()
                ? SchedulingRules.autoNewQuestionsLimit(
                        e.newTestQuestionsPerDay(), plan == null ? 0 : plan.requiredTestQuestionsPerDay())
                : e.newTestQuestionsPerDay();
        Theme theme = stored == null ? Theme.DARK : stored.getTheme();
        TrackSummary track = e.trackId() == null
                ? null : trackRepository.findById(e.trackId()).map(TrackSummary::of).orElse(null);

        return new LearningSettingsResponse(
                e.newFlashcardsPerDay(), newTestsInForce, e.newTestQuestionsAuto(), e.maxReviewsPerDay(), e.desiredRetention(),
                track, e.examDate(), theme, e.daysToExam(), e.examPassed(),
                e.effectiveRetention(), e.retentionBoosted(),
                new LearningSettingsResponse.Defaults(
                        defaults.newFlashcardsPerDay(), defaults.newTestQuestionsPerDay(),
                        defaults.maxReviewsPerDay(), defaults.retention()),
                plan);
    }

    /**
     * Ile nowych pytań testowych zostało do rozpoczęcia i ile dziennie trzeba, żeby zdążyć przed rezerwą na powtórki.
     * „Nowe" to pytania z puli użytkownika (jego ścieżka i zawężenie do aktów), których jeszcze nie ruszył w teście.
     * Fiszek się nie planuje: powstają z pomyłek, więc ich liczby nie da się przewidzieć.
     *
     * <p>Cel dnia dla pytań liczymy od stanu z początku dnia (do „zostało" dodajemy to, co użytkownik zaczął dzisiaj),
     * więc nie maleje w miarę nauki w ciągu dnia i da się go porównać z liczbą pytań zaczętych dzisiaj.
     */
    private LearningSettingsResponse.Plan buildPlan(Long userId, long daysToExam) {
        NewCounts counts = countNew(userId);
        long requiredTests = SchedulingRules.suggestedPerDay(counts.newTests() + counts.startedToday(), daysToExam);

        return new LearningSettingsResponse.Plan(
                counts.newTests(), SchedulingRules.planningDays(daysToExam),
                requiredTests, SchedulingRules.MAX_AUTO_NEW_PER_DAY,
                Math.min(requiredTests, SchedulingRules.MAX_AUTO_NEW_PER_DAY),
                counts.startedToday());
    }

    /** Ile jest nowych (nietkniętych w teście) pytań z puli użytkownika oraz ile pytań zaczął dziś. */
    private NewCounts countNew(Long userId) {
        Pool pool = poolService.forUser(userId);
        List<QuestionRef> inPool = questionRepository.findRefsInPool(pool.actIds(), pool.allSourceIds());
        Set<Long> started = new HashSet<>();
        for (UserQuestionStateRepository.QuestionDue row : stateRepository.findScheduleByUserIdAndMode(userId, ReviewMode.TEST)) {
            started.add(row.getQuestionId());
        }
        long newTests = inPool.stream().filter(ref -> !started.contains(ref.getId())).count();

        LocalDateTime startOfDay = LocalDate.now(clock).atStartOfDay();
        long startedToday = reviewLogs.countFirstReviewsBetween(userId, ReviewMode.TEST, startOfDay, startOfDay.plusDays(1));
        return new NewCounts(newTests, startedToday);
    }

    private record NewCounts(long newTests, long startedToday) {
    }

    private static void validate(UpdateLearningSettingsRequest request) {
        Double retention = request.desiredRetention();
        if (retention != null && (retention.isNaN() || retention < MIN_RETENTION || retention > MAX_RETENTION)) {
            throw new IllegalArgumentException("Docelowa retencja musi mieścić się między 70% a 97%.");
        }
    }
}

package pl.tutlaw.backend.settings;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tutlaw.backend.card.CardRepository;
import pl.tutlaw.backend.card.CardScopes;
import pl.tutlaw.backend.card.QuestionOrigin;
import pl.tutlaw.backend.review.ReviewLogRepository;
import pl.tutlaw.backend.review.ReviewMode;
import pl.tutlaw.backend.review.SchedulingRules;
import pl.tutlaw.backend.review.UserCardStateRepository;
import pl.tutlaw.backend.settings.dto.ChooseExamRequest;
import pl.tutlaw.backend.settings.dto.LearningSettingsResponse;
import pl.tutlaw.backend.settings.dto.UpdateLearningSettingsRequest;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ustawienia nauki użytkownika: odczyt (z wartościami domyślnymi), zapis, wybór aplikacji, do której się przygotowuje,
 * i plan nowych pytań do egzaminu. Data egzaminu nie jest ustawieniem użytkownika: bierze się z terminu wybranej
 * aplikacji ({@link ExamTerm}), który ustawia administrator.
 */
@Service
@RequiredArgsConstructor
public class LearningSettingsService {

    static final double MIN_RETENTION = 0.70;
    static final double MAX_RETENTION = 0.97;
    private final UserSettingsRepository repository;
    private final LearningDefaults defaults;
    private final CurrentUserResolver currentUserResolver;
    private final CardRepository cardRepository;
    private final UserCardStateRepository stateRepository;
    private final ExamTermRepository examTerms;
    private final ReviewLogRepository reviewLogs;
    private final Clock clock;

    /** Obowiązujące ustawienia (z domyślnymi tam, gdzie użytkownik niczego nie ustawił). Używane przy każdej odpowiedzi i budowie kolejki. */
    @Transactional(readOnly = true)
    public EffectiveSettings effectiveFor(Long userId) {
        UserSettings stored = repository.findById(userId).orElse(null);
        return resolve(stored, examDateOf(stored), LocalDate.now(clock));
    }

    /** Termin egzaminu aplikacji wybranej przez użytkownika; {@code null}, gdy nie wybrał albo termin nieogłoszony. */
    private LocalDate examDateOf(UserSettings stored) {
        if (stored == null || stored.getExamOrigin() == null) {
            return null;
        }
        return examTerms.findById(stored.getExamOrigin()).map(ExamTerm::getExamDate).orElse(null);
    }

    @Transactional(readOnly = true)
    public LearningSettingsResponse get(Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        UserSettings stored = repository.findById(user.getId()).orElse(null);
        return describe(user.getId(), stored, LocalDate.now(clock));
    }

    /**
     * Wybór aplikacji, do której użytkownik się przygotowuje. Od tej chwili jego egzaminem jest termin tej aplikacji, a
     * filtr pytań w Testach zostaje ustawiony na nią i na pytania autorskie (jednorazowo: później filtr zmienia się
     * osobno i nie wpływa na wybrany egzamin).
     */
    @Transactional
    public LearningSettingsResponse chooseExam(ChooseExamRequest request, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        QuestionOrigin origin = request.origin();
        if (origin == null || !origin.isExamApplication()) {
            throw new IllegalArgumentException("Wybierz aplikację, do której się przygotowujesz (pytania autorskie nie mają egzaminu).");
        }

        UserSettings settings = repository.findById(user.getId()).orElseGet(() -> new UserSettings(user.getId()));
        settings.setExamOrigin(origin);
        settings.setTestOrigins(OriginFilterService.serialize(EnumSet.of(origin, QuestionOrigin.AUTHORED)));
        settings.setUpdatedAt(LocalDateTime.now(clock));
        repository.save(settings);

        return describe(user.getId(), settings, LocalDate.now(clock));
    }

    @Transactional
    public LearningSettingsResponse update(UpdateLearningSettingsRequest request, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        LocalDate today = LocalDate.now(clock);
        validate(request);

        UserSettings settings = repository.findById(user.getId()).orElseGet(() -> new UserSettings(user.getId()));
        settings.setNewCardsPerDay(request.newCardsPerDay());
        settings.setNewTestQuestionsPerDay(request.newTestQuestionsPerDay());
        settings.setMaxReviewsPerDay(request.maxReviewsPerDay());
        settings.setDesiredRetention(request.desiredRetention());
        settings.setShuffleOptions(request.shuffleOptions() == null || request.shuffleOptions());
        settings.setUpdatedAt(LocalDateTime.now(clock));
        repository.save(settings);

        return describe(user.getId(), settings, today);
    }

    /** Czysta część: składa obowiązujące wartości z zapisanych i domyślnych. */
    EffectiveSettings resolve(UserSettings stored, LocalDate examDate, LocalDate today) {
        int newCards = stored != null && stored.getNewCardsPerDay() != null
                ? stored.getNewCardsPerDay() : defaults.newCardsPerDay();
        int newTests = stored != null && stored.getNewTestQuestionsPerDay() != null
                ? stored.getNewTestQuestionsPerDay() : defaults.newTestQuestionsPerDay();
        int maxReviews = stored != null && stored.getMaxReviewsPerDay() != null
                ? stored.getMaxReviewsPerDay() : defaults.maxReviewsPerDay();
        double retention = stored != null && stored.getDesiredRetention() != null
                ? stored.getDesiredRetention() : defaults.retention();
        QuestionOrigin examOrigin = stored != null ? stored.getExamOrigin() : null;
        boolean shuffle = stored == null || stored.isShuffleOptions();

        Long daysToExam = null;
        if (examDate != null) {
            long days = ChronoUnit.DAYS.between(today, examDate);
            if (days >= 0) {
                daysToExam = days;
            }
        }

        return new EffectiveSettings(
                newCards, newTests, maxReviews,
                retention, SchedulingRules.effectiveRetention(retention, daysToExam),
                examOrigin, examDate, daysToExam, shuffle);
    }

    private LearningSettingsResponse describe(Long userId, UserSettings stored, LocalDate today) {
        EffectiveSettings e = resolve(stored, examDateOf(stored), today);
        LearningSettingsResponse.Plan plan = e.daysToExam() == null
                ? null : buildPlan(userId, e.daysToExam(), OriginFilterService.parse(stored == null ? null : stored.getTestOrigins()));

        return new LearningSettingsResponse(
                e.newCardsPerDay(), e.newTestQuestionsPerDay(), e.maxReviewsPerDay(), e.desiredRetention(),
                e.examOrigin(), e.examDate(), e.shuffleOptions(), e.daysToExam(), e.examPassed(),
                e.effectiveRetention(), e.retentionBoosted(),
                new LearningSettingsResponse.Defaults(
                        defaults.newCardsPerDay(), defaults.newTestQuestionsPerDay(),
                        defaults.maxReviewsPerDay(), defaults.retention()),
                plan);
    }

    /**
     * Ile nowych pytań i fiszek zostało do rozpoczęcia i ile dziennie trzeba, żeby zdążyć przed rezerwą na powtórki.
     * „Nowe" to dostępne dla użytkownika karty, których jeszcze nie ruszył. Pytania testowe i zwykłe fiszki
     * liczymy osobno, bo mają osobne dzienne limity. Pytania liczą się tylko z wybranych pochodzeń (filtr w Testach).
     *
     * <p>Cel dnia dla pytań liczymy od stanu z początku dnia (do „zostało" dodajemy to, co użytkownik zaczął dzisiaj),
     * więc nie maleje w miarę nauki w ciągu dnia i da się go porównać z liczbą pytań zaczętych dzisiaj.
     */
    private LearningSettingsResponse.Plan buildPlan(Long userId, long daysToExam, Set<QuestionOrigin> testOrigins) {
        List<CardRepository.CardRef> readable = CardScopes.readableRefs(cardRepository, null, null, null, userId);
        Set<Long> started = new HashSet<>();
        for (UserCardStateRepository.CardDue row : stateRepository.findCardDueByUserId(userId)) {
            started.add(row.getCardId());
        }

        long newTests = 0;
        long newFlashcards = 0;
        for (CardRepository.CardRef card : readable) {
            if (started.contains(card.getId())) continue;
            if (CardScopes.isTest(card)) {
                // Pytania spoza filtra pochodzenia nie pojawią się w Testach, więc nie liczą się do planu.
                if (CardScopes.matchesOrigins(card, testOrigins)) newTests++;
            } else {
                newFlashcards++;
            }
        }

        LocalDateTime startOfDay = LocalDate.now(clock).atStartOfDay();
        long startedToday = reviewLogs.countFirstReviewsBetween(userId, ReviewMode.TEST, startOfDay, startOfDay.plusDays(1));

        return new LearningSettingsResponse.Plan(
                newTests, newFlashcards, SchedulingRules.planningDays(daysToExam),
                SchedulingRules.suggestedPerDay(newTests + startedToday, daysToExam),
                SchedulingRules.suggestedPerDay(newFlashcards, daysToExam),
                startedToday);
    }

    private static void validate(UpdateLearningSettingsRequest request) {
        Double retention = request.desiredRetention();
        if (retention != null && (retention.isNaN() || retention < MIN_RETENTION || retention > MAX_RETENTION)) {
            throw new IllegalArgumentException("Docelowa retencja musi mieścić się między 70% a 97%.");
        }
    }
}

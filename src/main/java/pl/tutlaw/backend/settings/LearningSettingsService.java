package pl.tutlaw.backend.settings;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tutlaw.backend.card.CardRepository;
import pl.tutlaw.backend.card.CardScopes;
import pl.tutlaw.backend.review.SchedulingRules;
import pl.tutlaw.backend.review.UserCardStateRepository;
import pl.tutlaw.backend.settings.dto.LearningSettingsResponse;
import pl.tutlaw.backend.settings.dto.UpdateLearningSettingsRequest;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Ustawienia nauki użytkownika: odczyt (z wartościami domyślnymi), zapis i plan nowych pytań do egzaminu. */
@Service
@RequiredArgsConstructor
public class LearningSettingsService {

    static final double MIN_RETENTION = 0.70;
    static final double MAX_RETENTION = 0.97;
    static final int MAX_EXAM_DISTANCE_YEARS = 3;

    private final UserSettingsRepository repository;
    private final LearningDefaults defaults;
    private final CurrentUserResolver currentUserResolver;
    private final CardRepository cardRepository;
    private final UserCardStateRepository stateRepository;
    private final Clock clock;

    /** Obowiązujące ustawienia (z domyślnymi tam, gdzie użytkownik niczego nie ustawił). Używane przy każdej odpowiedzi i budowie kolejki. */
    @Transactional(readOnly = true)
    public EffectiveSettings effectiveFor(Long userId) {
        UserSettings stored = repository.findById(userId).orElse(null);
        return resolve(stored, LocalDate.now(clock));
    }

    @Transactional(readOnly = true)
    public LearningSettingsResponse get(Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        UserSettings stored = repository.findById(user.getId()).orElse(null);
        return describe(user.getId(), stored, LocalDate.now(clock));
    }

    @Transactional
    public LearningSettingsResponse update(UpdateLearningSettingsRequest request, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        LocalDate today = LocalDate.now(clock);
        validate(request, today);

        UserSettings settings = repository.findById(user.getId()).orElseGet(() -> new UserSettings(user.getId()));
        settings.setNewCardsPerDay(request.newCardsPerDay());
        settings.setNewTestQuestionsPerDay(request.newTestQuestionsPerDay());
        settings.setMaxReviewsPerDay(request.maxReviewsPerDay());
        settings.setDesiredRetention(request.desiredRetention());
        settings.setExamDate(request.examDate());
        settings.setShuffleOptions(request.shuffleOptions() == null || request.shuffleOptions());
        settings.setUpdatedAt(LocalDateTime.now(clock));
        repository.save(settings);

        return describe(user.getId(), settings, today);
    }

    /** Czysta część: składa obowiązujące wartości z zapisanych i domyślnych. */
    EffectiveSettings resolve(UserSettings stored, LocalDate today) {
        int newCards = stored != null && stored.getNewCardsPerDay() != null
                ? stored.getNewCardsPerDay() : defaults.newCardsPerDay();
        int newTests = stored != null && stored.getNewTestQuestionsPerDay() != null
                ? stored.getNewTestQuestionsPerDay() : defaults.newTestQuestionsPerDay();
        int maxReviews = stored != null && stored.getMaxReviewsPerDay() != null
                ? stored.getMaxReviewsPerDay() : defaults.maxReviewsPerDay();
        double retention = stored != null && stored.getDesiredRetention() != null
                ? stored.getDesiredRetention() : defaults.retention();
        LocalDate examDate = stored != null ? stored.getExamDate() : null;
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
                examDate, daysToExam, shuffle);
    }

    private LearningSettingsResponse describe(Long userId, UserSettings stored, LocalDate today) {
        EffectiveSettings e = resolve(stored, today);
        LearningSettingsResponse.Plan plan = e.daysToExam() == null ? null : buildPlan(userId, e.daysToExam());

        return new LearningSettingsResponse(
                e.newCardsPerDay(), e.newTestQuestionsPerDay(), e.maxReviewsPerDay(), e.desiredRetention(),
                e.examDate(), e.shuffleOptions(), e.daysToExam(), e.examPassed(),
                e.effectiveRetention(), e.retentionBoosted(),
                new LearningSettingsResponse.Defaults(
                        defaults.newCardsPerDay(), defaults.newTestQuestionsPerDay(),
                        defaults.maxReviewsPerDay(), defaults.retention()),
                plan);
    }

    /**
     * Ile nowych pytań i fiszek zostało do rozpoczęcia i ile dziennie trzeba, żeby zdążyć przed egzaminem.
     * „Nowe" to dostępne dla użytkownika karty, których jeszcze nie ruszył. Pytania testowe i zwykłe fiszki
     * liczymy osobno, bo mają osobne dzienne limity.
     */
    private LearningSettingsResponse.Plan buildPlan(Long userId, long daysToExam) {
        List<CardRepository.CardRef> readable = CardScopes.readableRefs(cardRepository, null, null, null, userId);
        Set<Long> started = new HashSet<>();
        for (UserCardStateRepository.CardDue row : stateRepository.findCardDueByUserId(userId)) {
            started.add(row.getCardId());
        }

        long newTests = 0;
        long newFlashcards = 0;
        for (CardRepository.CardRef card : readable) {
            if (started.contains(card.getId())) continue;
            if (CardScopes.isTest(card)) newTests++;
            else newFlashcards++;
        }

        return new LearningSettingsResponse.Plan(
                newTests, newFlashcards, SchedulingRules.planningDays(daysToExam),
                SchedulingRules.suggestedPerDay(newTests, daysToExam),
                SchedulingRules.suggestedPerDay(newFlashcards, daysToExam));
    }

    private static void validate(UpdateLearningSettingsRequest request, LocalDate today) {
        Double retention = request.desiredRetention();
        if (retention != null && (retention.isNaN() || retention < MIN_RETENTION || retention > MAX_RETENTION)) {
            throw new IllegalArgumentException("Docelowa retencja musi mieścić się między 70% a 97%.");
        }
        LocalDate exam = request.examDate();
        if (exam != null) {
            if (exam.isBefore(today)) {
                throw new IllegalArgumentException("Data egzaminu nie może być w przeszłości.");
            }
            if (exam.isAfter(today.plusYears(MAX_EXAM_DISTANCE_YEARS))) {
                throw new IllegalArgumentException("Data egzaminu jest zbyt odległa (najwyżej " + MAX_EXAM_DISTANCE_YEARS + " lata od dziś).");
            }
        }
    }
}

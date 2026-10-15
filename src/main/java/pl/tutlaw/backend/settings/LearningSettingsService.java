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
import pl.tutlaw.backend.settings.dto.ChooseThemeRequest;
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
 * wybór wyglądu aplikacji (ciemny/jasny), dzienny limit nowych pytań i plan nowych pytań do egzaminu. Data egzaminu nie jest ustawieniem użytkownika: bierze się z terminu wybranej
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
     * osobno i nie wpływa na wybrany egzamin). Student ({@code student = true}) wybiera "bez aplikacji": nie ma
     * wtedy terminu egzaminu ani planu, a filtr pytań obejmuje wszystko. W obu przypadkach okno startowe jest zamknięte.
     */
    @Transactional
    public LearningSettingsResponse chooseExam(ChooseExamRequest request, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        QuestionOrigin origin = request.origin();
        boolean student = request.isStudent();
        if (!student && (origin == null || !origin.isExamApplication())) {
            throw new IllegalArgumentException("Wybierz aplikację, do której się przygotowujesz (pytania autorskie nie mają egzaminu).");
        }

        UserSettings settings = repository.findById(user.getId()).orElseGet(() -> new UserSettings(user.getId()));
        if (student) {
            // Student: bez aplikacji, terminu egzaminu i zawężenia pytań (wszystkie pochodzenia).
            settings.setExamOrigin(null);
            settings.setTestOrigins(null);
        } else {
            settings.setExamOrigin(origin);
            settings.setTestOrigins(OriginFilterService.serialize(EnumSet.of(origin, QuestionOrigin.AUTHORED)));
        }
        settings.setStartChoiceMade(true);
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
        NewCounts counts = countNew(userId, OriginFilterService.parse(stored == null ? null : stored.getTestOrigins()));
        long required = SchedulingRules.suggestedPerDay(counts.newTests() + counts.startedToday(), e.daysToExam());
        return SchedulingRules.autoNewQuestionsLimit(e.newTestQuestionsPerDay(), required);
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
        boolean newTestsAuto = stored == null || stored.getNewTestQuestionsPerDay() == null;
        int newTests = newTestsAuto ? defaults.newTestQuestionsPerDay() : stored.getNewTestQuestionsPerDay();
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
                examOrigin, examDate, daysToExam, shuffle, newTestsAuto);
    }

    private LearningSettingsResponse describe(Long userId, UserSettings stored, LocalDate today) {
        EffectiveSettings e = resolve(stored, examDateOf(stored), today);
        LearningSettingsResponse.Plan plan = e.daysToExam() == null
                ? null : buildPlan(userId, e.daysToExam(), OriginFilterService.parse(stored == null ? null : stored.getTestOrigins()));

        // Limit w mocy: własny, a bez niego automatyczny, czyli wyliczony z planu (nie mniej niż domyślny, nie więcej niż sufit).
        int newTestsInForce = e.newTestQuestionsAuto()
                ? SchedulingRules.autoNewQuestionsLimit(
                        e.newTestQuestionsPerDay(), plan == null ? 0 : plan.requiredTestQuestionsPerDay())
                : e.newTestQuestionsPerDay();
        Theme theme = stored == null ? Theme.DARK : stored.getTheme();
        boolean startChoiceMade = stored != null && stored.isStartChoiceMade();

        return new LearningSettingsResponse(
                e.newCardsPerDay(), newTestsInForce, e.newTestQuestionsAuto(), e.maxReviewsPerDay(), e.desiredRetention(),
                e.examOrigin(), e.examDate(), e.shuffleOptions(), theme, startChoiceMade, e.daysToExam(), e.examPassed(),
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
        NewCounts counts = countNew(userId, testOrigins);
        long requiredTests = SchedulingRules.suggestedPerDay(counts.newTests() + counts.startedToday(), daysToExam);

        return new LearningSettingsResponse.Plan(
                counts.newTests(), counts.newFlashcards(), SchedulingRules.planningDays(daysToExam),
                requiredTests, SchedulingRules.MAX_AUTO_NEW_PER_DAY,
                Math.min(requiredTests, SchedulingRules.MAX_AUTO_NEW_PER_DAY),
                SchedulingRules.suggestedPerDay(counts.newFlashcards(), daysToExam),
                counts.startedToday());
    }

    /** Ile jest nowych (nietkniętych) pytań testowych z wybranych pochodzeń i nowych fiszek oraz ile pytań użytkownik zaczął dziś. */
    private NewCounts countNew(Long userId, Set<QuestionOrigin> testOrigins) {
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
        return new NewCounts(newTests, newFlashcards, startedToday);
    }

    private record NewCounts(long newTests, long newFlashcards, long startedToday) {
    }

    private static void validate(UpdateLearningSettingsRequest request) {
        Double retention = request.desiredRetention();
        if (retention != null && (retention.isNaN() || retention < MIN_RETENTION || retention > MAX_RETENTION)) {
            throw new IllegalArgumentException("Docelowa retencja musi mieścić się między 70% a 97%.");
        }
    }
}

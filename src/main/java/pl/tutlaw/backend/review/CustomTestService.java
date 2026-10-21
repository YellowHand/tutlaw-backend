package pl.tutlaw.backend.review;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import pl.tutlaw.backend.course.CourseRepository;
import pl.tutlaw.backend.pool.Pool;
import pl.tutlaw.backend.pool.PoolService;
import pl.tutlaw.backend.pool.SourceMode;
import pl.tutlaw.backend.question.LawChangePolicy;
import pl.tutlaw.backend.question.QuestionRef;
import pl.tutlaw.backend.question.QuestionRepository;
import pl.tutlaw.backend.review.dto.CustomTestCountsResponse;
import pl.tutlaw.backend.review.dto.CustomTestRequest;
import pl.tutlaw.backend.review.dto.CustomTestSelectionResponse;
import pl.tutlaw.backend.review.fsrs.FsrsEngine;
import pl.tutlaw.backend.user.CurrentUserResolver;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * Własny test: użytkownik składa sesję z wybranych dziedzin, aktów prawnych i rozdziałów, wybiera, z jakiego źródła mają
 * być pytania (wszystkie, Ministerstwo Sprawiedliwości, autorskie), które pytania mają w niej być
 * (wszystkie, nowe, błędy, zaległe, zmiany w prawie) i mówi, ile pytań chce (10, 20, 50, 100 albo 150). Serwis tylko LOSUJE numery
 * pytań (zapisuje je jako sesję {@link CustomTestSessionService}); samą sesję otwiera się zwykłą kolejką ćwiczeń
 * ({@link PracticeQueueService}), więc odpowiedzi idą normalną drogą testu: z oceną pamięci, a pomyłki trafiają do
 * „Moich błędów".
 *
 * <p>W puli są wyłącznie pytania z puli użytkownika (akty jego ścieżki i jego zawężenie) z wybranego źródła,
 * niezawieszone w trybie testu. Własny test nie jest ograniczony dziennym limitem nowych pytań (tak jak „Powtórz
 * jeszcze"): użytkownik sam mówi, ile chce.
 */
@Service
@RequiredArgsConstructor
public class CustomTestService {

    /** Liczby pytań do wyboru. Własnej liczby nie ma. */
    public static final List<Integer> COUNT_CHOICES = List.of(10, 20, 50, 100, 150);
    /** Największa możliwa sesja (musi mieścić się w limicie kolejki ćwiczeń, przez którą jest otwierana). */
    static final int MAX_QUESTIONS = COUNT_CHOICES.get(COUNT_CHOICES.size() - 1);

    private final QuestionRepository questionRepository;
    private final UserQuestionStateRepository stateRepository;
    private final MistakesService mistakesService;
    private final CurrentUserResolver currentUserResolver;
    private final FsrsEngine fsrsEngine;
    private final Clock clock;
    private final PoolService poolService;
    private final LawChangePolicy lawChangePolicy;
    private final CourseRepository courseRepository;

    private final Random random = new Random();

    /** Pytania dostępne w zakresie, z podziałem na rodzaje: do podglądu „Dostępnych: N" przy układaniu testu. */
    public CustomTestCountsResponse counts(
            List<Long> fieldIds, List<Long> actIds, List<Long> courseIds, List<Long> sectionIds, SourceMode source,
            Authentication authentication) {
        Long userId = currentUserResolver.resolve(authentication).getId();
        Candidates pool = pool(userId, fieldIds, actIds, courseIds, sectionIds, source);
        LocalDateTime now = LocalDateTime.now(clock);
        return new CustomTestCountsResponse(
                pool.refs().size(),
                candidates(CustomTestKind.NEW, pool, userId, now).size(),
                candidates(CustomTestKind.MISTAKES, pool, userId, now).size(),
                candidates(CustomTestKind.DUE, pool, userId, now).size(),
                candidates(CustomTestKind.LAW_CHANGE, pool, userId, now).size());
    }

    /**
     * Losuje pytania. Dla „błędów" i „zaległych" przy ograniczonej liczbie wchodzą najpilniejsze (najświeższe błędy,
     * najniższa szansa przypomnienia), dla pozostałych losowe. Kolejność w sesji jest zawsze losowa.
     */
    public CustomTestSelectionResponse select(CustomTestRequest request, Authentication authentication) {
        Long userId = currentUserResolver.resolve(authentication).getId();
        CustomTestKind kind = request.kind() == null ? CustomTestKind.ALL : request.kind();
        int requested = request.questionCount();
        if (requested < 1 || requested > MAX_QUESTIONS) {
            throw new IllegalArgumentException("Liczba pytań musi wynosić od 1 do " + MAX_QUESTIONS + ".");
        }

        Candidates pool = pool(userId, request.fieldIds(), request.actIds(), request.courseIds(),
                request.sectionIds(), request.source());
        List<Long> candidates = new ArrayList<>(
                candidates(kind, pool, userId, LocalDateTime.now(clock)));
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException(nothingToDrawMessage(kind, pool));
        }

        int available = candidates.size();
        if (!isAllowedCount(requested, available)) {
            throw new IllegalArgumentException("Wybierz liczbę pytań: 10, 20, 50, 100 albo 150.");
        }
        boolean mostUrgentFirst = kind == CustomTestKind.DUE || kind == CustomTestKind.MISTAKES;
        if (!mostUrgentFirst) {
            Collections.shuffle(candidates, random);
        }
        List<Long> chosen = new ArrayList<>(candidates.subList(0, Math.min(requested, available)));
        if (mostUrgentFirst) {
            Collections.shuffle(chosen, random);
        }
        return new CustomTestSelectionResponse(chosen, available, requested, null);
    }

    // ---------------------------------------------------------------- pula

    /**
     * Pytania zakresu po wszystkich filtrach (źródło, zawieszone) i harmonogram użytkownika w trybie testu.
     * {@code inScope} to liczba pytań zakresu przed wyborem źródła i zawieszeniem: odróżnia „nie ma pytań" od
     * „wybrane źródło wszystko wycięło".
     */
    private record Candidates(List<QuestionRef> refs, int inScope, Map<Long, UserQuestionStateRepository.QuestionDue> schedule) {
    }

    private Candidates pool(
            Long userId, List<Long> fieldIds, List<Long> actIds, List<Long> courseIds, List<Long> sectionIds,
            SourceMode source) {
        Pool pool = poolService.forUser(userId, SourceMode.orAll(source).filter());
        List<QuestionRef> all = questionRepository.findRefsInPool(pool.actIds(), pool.allSourceIds());
        List<Long> fields = ids(fieldIds);
        List<Long> chosenCourses = ids(courseIds);
        List<Long> sections = ids(sectionIds);
        // Przedmiot to akty przypisane przez administratora; akty wybrane wprost i akty przedmiotów to jeden zakres.
        Set<Long> acts = new LinkedHashSet<>(ids(actIds));
        if (!chosenCourses.isEmpty()) {
            acts.addAll(courseRepository.findActIdsByCourseIds(chosenCourses));
        }
        Set<Long> sectionQuestions = sections.isEmpty()
                ? Set.of() : new HashSet<>(questionRepository.findIdsBySectionIds(sections));
        // Cokolwiek wybrano (także przedmiot bez aktów albo rozdział bez pytań) zawęża zakres i nie może go rozszerzyć do
        // całej puli: pusty zakres to „nic", a „cała pula" to brak wyboru.
        boolean scopeChosen = !fields.isEmpty() || !acts.isEmpty() || !chosenCourses.isEmpty() || !sections.isEmpty();
        List<QuestionRef> inScope = scopeChosen ? scoped(all, fields, new ArrayList<>(acts), sectionQuestions) : all;

        Set<Long> allowedSources = new HashSet<>(pool.sourceIds());
        Map<Long, UserQuestionStateRepository.QuestionDue> schedule = new HashMap<>();
        for (UserQuestionStateRepository.QuestionDue row : stateRepository.findScheduleByUserIdAndMode(userId, ReviewMode.TEST)) {
            schedule.put(row.getQuestionId(), row);
        }

        List<QuestionRef> refs = inScope.stream()
                .filter(ref -> allowedSources.contains(ref.getSourceId()))
                .filter(ref -> !isSuspended(schedule.get(ref.getId())))
                .toList();
        return new Candidates(refs, inScope.size(), schedule);
    }

    /**
     * Zakres to suma wybranych dziedzin, aktów i rozdziałów (podanych jako numery ich pytań). Pusty wybór niczego nie
     * wybiera: „cała pula" rozstrzyga wołający, który wie, czy cokolwiek wybrano.
     */
    static List<QuestionRef> scoped(
            List<QuestionRef> refs, List<Long> fieldIds, List<Long> actIds, Set<Long> sectionQuestionIds) {
        Set<Long> fields = new HashSet<>(fieldIds);
        Set<Long> acts = new HashSet<>(actIds);
        return refs.stream()
                .filter(ref -> fields.contains(ref.getLegalFieldId()) || acts.contains(ref.getLegalActId())
                        || sectionQuestionIds.contains(ref.getId()))
                .toList();
    }

    /** Numery pytań wybranego rodzaju. Dla błędów od najświeższej pomyłki, dla zaległych od najpilniejszej. */
    private List<Long> candidates(CustomTestKind kind, Candidates pool, Long userId, LocalDateTime now) {
        if (pool.refs().isEmpty()) {
            return List.of();
        }
        return switch (kind) {
            case ALL -> pool.refs().stream().map(QuestionRef::getId).toList();
            case NEW -> pool.refs().stream()
                    .map(QuestionRef::getId)
                    .filter(id -> !pool.schedule().containsKey(id))
                    .toList();
            case MISTAKES -> {
                Set<Long> inPool = new HashSet<>();
                pool.refs().forEach(ref -> inPool.add(ref.getId()));
                yield mistakesService.recentMistakeQuestionIds(userId).stream().filter(inPool::contains).toList();
            }
            case DUE -> dueByUrgency(pool, now);
            case LAW_CHANGE -> pool.refs().stream()
                    .filter(ref -> lawChangePolicy.isRecent(ref.getLawChangedAt()))
                    .map(QuestionRef::getId)
                    .toList();
        };
    }

    /** Zaległe od tych, które najpewniej już zapomniałeś (jak w kolejce nauki). */
    private List<Long> dueByUrgency(Candidates pool, LocalDateTime now) {
        List<QueueRules.DueItem> due = new ArrayList<>();
        for (QuestionRef ref : pool.refs()) {
            UserQuestionStateRepository.QuestionDue schedule = pool.schedule().get(ref.getId());
            if (schedule == null || schedule.getDueDate().isAfter(now)) {
                continue; // nowe albo jeszcze nie ich czas (zawieszone są już poza pulą)
            }
            double stability = schedule.getStability() == null ? 0 : schedule.getStability();
            double retrievability = fsrsEngine.retrievability(
                    QueueRules.elapsedDays(schedule.getLastReviewAt(), now), stability);
            due.add(new QueueRules.DueItem(ref.getId(), schedule.getDueDate(), retrievability));
        }
        return QueueRules.prioritize(due).stream().map(QueueRules.DueItem::questionId).toList();
    }

    // ---------------------------------------------------------------- pomocnicze

    private static boolean isSuspended(UserQuestionStateRepository.QuestionDue schedule) {
        return schedule != null && Boolean.TRUE.equals(schedule.getSuspended());
    }

    /**
     * Wolno wybrać 10, 20, 50, 100 albo 150 pytań (gdy jest ich mniej, test dostaje wszystkie). Wyjątek: gdy w zakresie
     * jest mniej niż 10 pytań, jedyny wybór to wszystkie z nich, czyli liczba równa liczbie dostępnych.
     */
    static boolean isAllowedCount(int requested, int available) {
        return COUNT_CHOICES.contains(requested)
                || (available < COUNT_CHOICES.get(0) && requested == available);
    }

    /** Numery bez pustych i powtórzeń, w kolejności podania. */
    private static List<Long> ids(List<Long> raw) {
        if (raw == null) {
            return List.of();
        }
        return raw.stream().filter(Objects::nonNull).distinct().toList();
    }

    private static String nothingToDrawMessage(CustomTestKind kind, Candidates pool) {
        if (pool.refs().isEmpty()) {
            return pool.inScope() == 0
                    ? "W wybranym zakresie nie ma teraz pytań, które możesz rozwiązać."
                    : "W wybranym zakresie nie ma pytań z wybranego źródła. Wybierz inne źródło.";
        }
        return switch (kind) {
            case NEW -> "Wszystkie pytania z wybranego zakresu były już rozwiązywane.";
            case MISTAKES -> "W wybranym zakresie nie ma teraz pytań z błędną ostatnią odpowiedzią.";
            case DUE -> "W wybranym zakresie nie ma teraz zaległych powtórek.";
            case LAW_CHANGE -> "W wybranym zakresie nie ma pytań ze zmianą w prawie z ostatniego roku.";
            case ALL -> "W wybranym zakresie nie ma teraz pytań, które możesz rozwiązać.";
        };
    }
}

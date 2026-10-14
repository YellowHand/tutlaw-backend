package pl.tutlaw.backend.review;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import pl.tutlaw.backend.card.CardRepository;
import pl.tutlaw.backend.card.CardScopes;
import pl.tutlaw.backend.card.QuestionOrigin;
import pl.tutlaw.backend.review.dto.CustomTestCountsResponse;
import pl.tutlaw.backend.review.dto.CustomTestRequest;
import pl.tutlaw.backend.review.dto.CustomTestSelectionResponse;
import pl.tutlaw.backend.review.fsrs.FsrsEngine;
import pl.tutlaw.backend.settings.OriginFilterService;
import pl.tutlaw.backend.user.CurrentUserResolver;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * Własny test: użytkownik składa sesję z wybranych rodzajów i aktów prawnych, wybiera, które pytania mają w niej być
 * (wszystkie, nowe, błędy, zaległe), może zawęzić lata egzaminu i mówi, ile pytań chce. Serwis tylko LOSUJE numery
 * pytań; samą sesję otwiera się zwykłą kolejką ćwiczeń ({@link PracticeQueueService}), więc odpowiedzi idą normalną
 * drogą testu: z oceną pamięci, a pomyłki trafiają do „Moich błędów".
 *
 * <p>W puli są wyłącznie pytania testowe dostępne dla użytkownika (pula płaska albo przeczytany rozdział),
 * niezawieszone i z wybranych pochodzeń (filtr pochodzenia w Testach). Własny test nie jest ograniczony dziennym
 * limitem nowych pytań (tak jak „Powtórz jeszcze"): użytkownik sam mówi, ile chce.
 */
@Service
@RequiredArgsConstructor
public class CustomTestService {

    /** Tyle samo co limit kolejki ćwiczeń, przez którą sesja jest potem otwierana. */
    static final int MAX_QUESTIONS = PracticeQueueService.MAX_CARDS;
    static final int MIN_YEAR = 1900;
    static final int MAX_YEAR = 2100;
    /** Numer, którego nie ma w bazie: zamiast pustej listy w {@code IN}. */
    private static final Long NO_SUCH_ID = -1L;

    private final CardRepository cardRepository;
    private final UserCardStateRepository stateRepository;
    private final MistakesService mistakesService;
    private final CurrentUserResolver currentUserResolver;
    private final FsrsEngine fsrsEngine;
    private final Clock clock;
    private final OriginFilterService originFilter;

    private final Random random = new Random();

    /** Pytania dostępne w zakresie, z podziałem na rodzaje: do podglądu „Dostępnych: N" przy układaniu testu. */
    public CustomTestCountsResponse counts(
            List<Long> categoryIds, List<Long> subjectIds, Integer yearFrom, Integer yearTo,
            Authentication authentication) {
        Long userId = currentUserResolver.resolve(authentication).getId();
        Pool pool = pool(userId, categoryIds, subjectIds, yearFrom, yearTo);
        LocalDateTime now = LocalDateTime.now(clock);
        return new CustomTestCountsResponse(
                pool.refs().size(),
                candidates(CustomTestKind.NEW, pool, userId, now).size(),
                candidates(CustomTestKind.MISTAKES, pool, userId, now).size(),
                candidates(CustomTestKind.DUE, pool, userId, now).size());
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

        Pool pool = pool(userId, request.categoryIds(), request.subjectIds(), request.yearFrom(), request.yearTo());
        List<Long> candidates = new ArrayList<>(
                candidates(kind, pool, userId, LocalDateTime.now(clock)));
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException(nothingToDrawMessage(kind, pool));
        }

        int available = candidates.size();
        boolean mostUrgentFirst = kind == CustomTestKind.DUE || kind == CustomTestKind.MISTAKES;
        if (!mostUrgentFirst) {
            Collections.shuffle(candidates, random);
        }
        List<Long> chosen = new ArrayList<>(candidates.subList(0, Math.min(requested, available)));
        if (mostUrgentFirst) {
            Collections.shuffle(chosen, random);
        }
        return new CustomTestSelectionResponse(chosen, available, requested);
    }

    // ---------------------------------------------------------------- pula

    /**
     * Pytania zakresu po wszystkich filtrach (pochodzenie, lata, zawieszone) i harmonogram użytkownika.
     * {@code inScope} to liczba pytań zakresu przed filtrami: odróżnia „nie ma pytań" od „filtr wszystko wyciął".
     */
    private record Pool(List<CardRepository.TestRef> refs, int inScope, Map<Long, UserCardStateRepository.CardDue> schedule) {
    }

    private Pool pool(Long userId, List<Long> categoryIds, List<Long> subjectIds, Integer yearFrom, Integer yearTo) {
        validateYears(yearFrom, yearTo);
        List<Long> categories = ids(categoryIds);
        List<Long> subjects = ids(subjectIds);

        List<CardRepository.TestRef> inScope = categories.isEmpty() && subjects.isEmpty()
                ? cardRepository.findReadableTestRefsForUser(userId)
                : cardRepository.findReadableTestRefsInScope(
                        userId, orNoSuchId(categories), orNoSuchId(subjects));

        Map<Long, UserCardStateRepository.CardDue> schedule = new HashMap<>();
        for (UserCardStateRepository.CardDue row : stateRepository.findCardDueByUserId(userId)) {
            schedule.put(row.getCardId(), row);
        }
        Set<QuestionOrigin> origins = originFilter.activeFor(userId);

        List<CardRepository.TestRef> refs = inScope.stream()
                .filter(ref -> CardScopes.originAllowed(ref.getOrigin(), origins))
                .filter(ref -> inYears(ref.getExamYear(), yearFrom, yearTo))
                .filter(ref -> !isSuspended(schedule.get(ref.getId())))
                .toList();
        return new Pool(refs, inScope.size(), schedule);
    }

    /** Numery pytań wybranego rodzaju. Dla błędów od najświeższej pomyłki, dla zaległych od najpilniejszej. */
    private List<Long> candidates(CustomTestKind kind, Pool pool, Long userId, LocalDateTime now) {
        if (pool.refs().isEmpty()) {
            return List.of();
        }
        return switch (kind) {
            case ALL -> pool.refs().stream().map(CardRepository.TestRef::getId).toList();
            case NEW -> pool.refs().stream()
                    .map(CardRepository.TestRef::getId)
                    .filter(id -> !pool.schedule().containsKey(id))
                    .toList();
            case MISTAKES -> {
                Set<Long> inPool = new HashSet<>();
                pool.refs().forEach(ref -> inPool.add(ref.getId()));
                yield mistakesService.recentMistakeCardIds(userId).stream().filter(inPool::contains).toList();
            }
            case DUE -> dueByUrgency(pool, now);
        };
    }

    /** Zaległe od tych, które najpewniej już zapomniałeś (jak w kolejce nauki). */
    private List<Long> dueByUrgency(Pool pool, LocalDateTime now) {
        List<QueueRules.DueItem> due = new ArrayList<>();
        for (CardRepository.TestRef ref : pool.refs()) {
            UserCardStateRepository.CardDue schedule = pool.schedule().get(ref.getId());
            if (schedule == null || schedule.getDueDate().isAfter(now)) {
                continue; // nowe albo jeszcze nie ich czas (zawieszone są już poza pulą)
            }
            double stability = schedule.getStability() == null ? 0 : schedule.getStability();
            double retrievability = fsrsEngine.retrievability(
                    QueueRules.elapsedDays(schedule.getLastReviewAt(), now), stability);
            due.add(new QueueRules.DueItem(ref.getId(), schedule.getDueDate(), retrievability));
        }
        return QueueRules.prioritize(due).stream().map(QueueRules.DueItem::cardId).toList();
    }

    // ---------------------------------------------------------------- pomocnicze

    private static boolean isSuspended(UserCardStateRepository.CardDue schedule) {
        return schedule != null && Boolean.TRUE.equals(schedule.getSuspended());
    }

    /** Bez zakresu lat przechodzi wszystko; z zakresem tylko pytania z rokiem w tym zakresie (autorskie nie mają roku). */
    static boolean inYears(Integer examYear, Integer yearFrom, Integer yearTo) {
        if (yearFrom == null && yearTo == null) {
            return true;
        }
        if (examYear == null) {
            return false;
        }
        return (yearFrom == null || examYear >= yearFrom) && (yearTo == null || examYear <= yearTo);
    }

    static void validateYears(Integer yearFrom, Integer yearTo) {
        for (Integer year : new Integer[]{yearFrom, yearTo}) {
            if (year != null && (year < MIN_YEAR || year > MAX_YEAR)) {
                throw new IllegalArgumentException("Rok egzaminu musi mieścić się między " + MIN_YEAR + " a " + MAX_YEAR + ".");
            }
        }
        if (yearFrom != null && yearTo != null && yearFrom > yearTo) {
            throw new IllegalArgumentException("Początek zakresu lat nie może być późniejszy niż jego koniec.");
        }
    }

    /** Numery bez pustych i powtórzeń, w kolejności podania. */
    private static List<Long> ids(List<Long> raw) {
        if (raw == null) {
            return List.of();
        }
        return raw.stream().filter(Objects::nonNull).distinct().toList();
    }

    private static List<Long> orNoSuchId(List<Long> ids) {
        return ids.isEmpty() ? List.of(NO_SUCH_ID) : ids;
    }

    private static String nothingToDrawMessage(CustomTestKind kind, Pool pool) {
        if (pool.refs().isEmpty()) {
            return pool.inScope() == 0
                    ? "W wybranym zakresie nie ma teraz pytań testowych, które możesz rozwiązać."
                    : "W wybranym zakresie nie ma pytań z wybranych pochodzeń i lat. "
                            + "Zmień filtr pochodzenia w Testach albo zakres lat.";
        }
        return switch (kind) {
            case NEW -> "Wszystkie pytania z wybranego zakresu były już rozwiązywane.";
            case MISTAKES -> "W wybranym zakresie nie ma teraz pytań z błędną ostatnią odpowiedzią.";
            case DUE -> "W wybranym zakresie nie ma teraz zaległych powtórek.";
            case ALL -> "W wybranym zakresie nie ma teraz pytań testowych, które możesz rozwiązać.";
        };
    }
}

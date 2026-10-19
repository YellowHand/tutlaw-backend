package pl.tutlaw.backend.review;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import pl.tutlaw.backend.pool.Pool;
import pl.tutlaw.backend.pool.PoolService;
import pl.tutlaw.backend.question.Question;
import pl.tutlaw.backend.question.QuestionRepository;
import pl.tutlaw.backend.review.dto.MistakesCountResponse;
import pl.tutlaw.backend.review.dto.TestQueueItemResponse;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * "Powtórz moje błędy": sesja testowa z pytań, w których OSTATNIA odpowiedź użytkownika w teście była błędna
 * (z ostatnich {@value #LOOKBACK_DAYS} dni). Pytanie, na które później odpowiedział dobrze, już się tu nie pojawia.
 * Odpowiedzi w tej sesji idą zwykłą drogą testu (z oceną pamięci i terminami), więc to dodatkowe ćwiczenie,
 * a nie osobny tor nauki. Zawieszone i niedostępne pytania odpadają, a także te spoza puli użytkownika
 * (akty jego ścieżki i filtr źródeł w Testach).
 */
@Service
@RequiredArgsConstructor
public class MistakesService {

    static final int LOOKBACK_DAYS = 30;
    /** Ile pytań maksymalnie w jednej sesji. */
    static final int SESSION_SIZE = 50;
    /** Ile ostatnich odpowiedzi w testach analizujemy i ile pytań sprawdzamy pod kątem dostępności. */
    static final int MAX_ANSWERS_SCANNED = 20_000;
    static final int MAX_MISTAKE_QUESTIONS = 1_000;

    private final ReviewLogRepository reviewLogRepository;
    private final QuestionRepository questionRepository;
    private final CurrentUserResolver currentUserResolver;
    private final Clock clock;
    private final PoolService poolService;

    private final Random random = new Random();

    public MistakesCountResponse count(Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        return new MistakesCountResponse(eligibleMistakes(user).size(), LOOKBACK_DAYS, SESSION_SIZE);
    }

    /** Do {@value #SESSION_SIZE} najświeższych błędów, w losowej kolejności. */
    public List<TestQueueItemResponse> queue(Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        List<Question> session = new ArrayList<>(eligibleMistakes(user).stream().limit(SESSION_SIZE).toList());
        Collections.shuffle(session, random);
        return session.stream().map(ReviewQueueMapper::toTest).toList();
    }

    /**
     * Numery pytań z błędną ostatnią odpowiedzią (z ostatnich {@value #LOOKBACK_DAYS} dni), od najświeższej pomyłki.
     * Bez sprawdzania puli i zawieszenia: korzysta z tego własny test, który robi to sam na swojej puli
     * ({@link CustomTestService}).
     */
    List<Long> recentMistakeQuestionIds(Long userId) {
        return latestMistakeQuestionIds(userId, LocalDateTime.now(clock).minusDays(LOOKBACK_DAYS));
    }

    /** Pytania z błędną ostatnią odpowiedzią, od najświeższej pomyłki, tylko te, które nadal można ćwiczyć. */
    private List<Question> eligibleMistakes(User user) {
        LocalDateTime since = LocalDateTime.now(clock).minusDays(LOOKBACK_DAYS);
        List<Long> mistakeIds = latestMistakeQuestionIds(user.getId(), since);
        if (mistakeIds.isEmpty()) {
            return List.of();
        }

        // Pula z Testów obowiązuje także tutaj: błędy spoza aktów ścieżki i z wyłączonych źródeł nie wchodzą do sesji
        // ani do licznika.
        Pool pool = poolService.forUser(user.getId());
        Set<Long> allowedSources = new HashSet<>(pool.sourceIds());
        Map<Long, Question> practicable = new HashMap<>();
        for (Question question : questionRepository.findPracticeQuestions(mistakeIds, user.getId(), pool.actIds())) {
            if (allowedSources.contains(question.getSource().getId())) {
                practicable.put(question.getId(), question);
            }
        }
        // Kolejność z listy błędów (najświeższy pierwszy); pytania, których nie zwróciło zapytanie, odpadają.
        return mistakeIds.stream().map(practicable::get).filter(question -> question != null).toList();
    }

    /**
     * Pytania, których najnowsza odpowiedź w teście z okresu była błędna, od najświeższej pomyłki.
     * Odpowiedzi przychodzą od najnowszej, więc pierwsze spotkanie pytania rozstrzyga o jego "ostatniej odpowiedzi".
     */
    private List<Long> latestMistakeQuestionIds(Long userId, LocalDateTime since) {
        Set<Long> seen = new HashSet<>();
        Set<Long> mistakes = new LinkedHashSet<>();
        for (ReviewLogRepository.TestAnswerRow row : reviewLogRepository.findTestAnswersSince(
                userId, since, PageRequest.of(0, MAX_ANSWERS_SCANNED))) {
            if (!seen.add(row.getQuestionId())) {
                continue; // starsza odpowiedź na pytanie, którego ostatnią odpowiedź już znamy
            }
            if (Boolean.FALSE.equals(row.getWasCorrect())) {
                mistakes.add(row.getQuestionId());
                if (mistakes.size() >= MAX_MISTAKE_QUESTIONS) {
                    break;
                }
            }
        }
        return new ArrayList<>(mistakes);
    }
}

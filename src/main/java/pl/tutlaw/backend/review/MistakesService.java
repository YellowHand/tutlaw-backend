package pl.tutlaw.backend.review;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import pl.tutlaw.backend.card.Card;
import pl.tutlaw.backend.card.CardRepository;
import pl.tutlaw.backend.card.QuestionOrigin;
import pl.tutlaw.backend.review.dto.MistakesCountResponse;
import pl.tutlaw.backend.review.dto.TestQueueItemResponse;
import pl.tutlaw.backend.settings.OriginFilterService;
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
 * a nie osobny tor nauki. Zawieszone i niedostępne pytania odpadają, a także te spoza wybranych pochodzeń
 * (filtr pochodzenia w Testach).
 */
@Service
@RequiredArgsConstructor
public class MistakesService {

    static final int LOOKBACK_DAYS = 30;
    /** Ile pytań maksymalnie w jednej sesji. */
    static final int SESSION_SIZE = 50;
    /** Ile ostatnich odpowiedzi w testach analizujemy i ile kart sprawdzamy pod kątem dostępności. */
    static final int MAX_ANSWERS_SCANNED = 20_000;
    static final int MAX_MISTAKE_CARDS = 1_000;

    private final ReviewLogRepository reviewLogRepository;
    private final CardRepository cardRepository;
    private final CurrentUserResolver currentUserResolver;
    private final Clock clock;
    private final OriginFilterService originFilter;

    private final Random random = new Random();

    public MistakesCountResponse count(Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        return new MistakesCountResponse(eligibleMistakes(user).size(), LOOKBACK_DAYS, SESSION_SIZE);
    }

    /** Do {@value #SESSION_SIZE} najświeższych błędów, w losowej kolejności. */
    public List<TestQueueItemResponse> queue(Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        List<Card> session = new ArrayList<>(eligibleMistakes(user).stream().limit(SESSION_SIZE).toList());
        Collections.shuffle(session, random);
        return session.stream().map(ReviewQueueMapper::toTestResponse).toList();
    }

    /** Karty z błędną ostatnią odpowiedzią, od najświeższej pomyłki, tylko te, które nadal można ćwiczyć. */
    private List<Card> eligibleMistakes(User user) {
        LocalDateTime since = LocalDateTime.now(clock).minusDays(LOOKBACK_DAYS);
        List<Long> mistakeIds = latestMistakeCardIds(user.getId(), since);
        if (mistakeIds.isEmpty()) {
            return List.of();
        }

        // Filtr pochodzenia z Testów obowiązuje także tutaj: błędy z wyłączonych pochodzeń nie wchodzą do sesji ani do licznika.
        Set<QuestionOrigin> origins = originFilter.activeFor(user.getId());
        Map<Long, Card> practicable = new HashMap<>();
        for (Card card : cardRepository.findPracticeTestCards(mistakeIds, user.getId())) {
            if (card.getOrigin() == null || origins.contains(card.getOrigin())) {
                practicable.put(card.getId(), card);
            }
        }
        // Kolejność z listy błędów (najświeższy pierwszy); karty, których nie zwróciło zapytanie, odpadają.
        return mistakeIds.stream().map(practicable::get).filter(card -> card != null).toList();
    }

    /**
     * Karty, których najnowsza odpowiedź w teście z okresu była błędna, od najświeższej pomyłki.
     * Odpowiedzi przychodzą od najnowszej, więc pierwsze spotkanie karty rozstrzyga o jej "ostatniej odpowiedzi".
     */
    private List<Long> latestMistakeCardIds(Long userId, LocalDateTime since) {
        Set<Long> seen = new HashSet<>();
        Set<Long> mistakes = new LinkedHashSet<>();
        for (ReviewLogRepository.TestAnswerRow row : reviewLogRepository.findTestAnswersSince(
                userId, since, PageRequest.of(0, MAX_ANSWERS_SCANNED))) {
            if (!seen.add(row.getCardId())) {
                continue; // starsza odpowiedź na kartę, której ostatnią odpowiedź już znamy
            }
            if (Boolean.FALSE.equals(row.getWasCorrect())) {
                mistakes.add(row.getCardId());
                if (mistakes.size() >= MAX_MISTAKE_CARDS) {
                    break;
                }
            }
        }
        return new ArrayList<>(mistakes);
    }
}

package pl.tutlaw.backend.exam;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tutlaw.backend.card.Card;
import pl.tutlaw.backend.card.CardRepository;
import pl.tutlaw.backend.card.CardScopes;
import pl.tutlaw.backend.card.QuestionOrigin;
import pl.tutlaw.backend.exam.dto.ExamAttemptResponse;
import pl.tutlaw.backend.exam.dto.ExamResultResponse;
import pl.tutlaw.backend.exam.dto.ExamStartResponse;
import pl.tutlaw.backend.exam.dto.ExamSubmitRequest;
import pl.tutlaw.backend.review.ReviewQueueMapper;
import pl.tutlaw.backend.review.UserCardStateRepository;
import pl.tutlaw.backend.review.dto.TestQueueItemResponse;
import pl.tutlaw.backend.settings.OriginFilterService;
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
 * Egzamin próbny w stałym formacie ({@link ExamFormat}): losowe {@value ExamFormat#QUESTION_COUNT} pytań testowych z
 * całego dostępnego materiału, zaliczenie od {@value ExamFormat#PASS_POINTS} punktów. Zakresu, liczby pytań ani progu
 * nie wybiera użytkownik. Ocena i próg są po stronie serwera, a podsumowanie podejścia jest zapisywane.
 * Egzamin jest odizolowany od nauki: nie zmienia terminów powtórek (FSRS), dziennika odpowiedzi, dziennych limitów
 * ani licznika błędów kart. Po egzaminie użytkownik może przerobić swoje błędy zwykłą sesją testową.
 * Ćwiczenie z wybranego działu to osobna funkcja bez oceny "zdał / nie zdał" (własny test).
 */
@Service
@RequiredArgsConstructor
public class ExamService {

    /** Ile ostatnich podejść zwraca historia. */
    static final int HISTORY_LIMIT = 50;
    /** Nazwa zakresu w raporcie i historii: egzamin zawsze obejmuje cały materiał. */
    static final String SCOPE_LABEL = "Cały materiał";

    private final CardRepository cardRepository;
    private final UserCardStateRepository stateRepository;
    private final ExamAttemptRepository attemptRepository;
    private final CurrentUserResolver currentUserResolver;
    private final Clock clock;
    private final OriginFilterService originFilter;

    private final Random random = new Random();

    /**
     * Losuje {@value ExamFormat#QUESTION_COUNT} pytań z całego materiału: tylko w formacie testowym, z wybranych
     * pochodzeń (filtr pochodzenia w Testach), dostępne dla użytkownika (pula płaska albo przeczytany rozdział)
     * i niezawieszone. Gdy jest ich mniej niż 150, egzamin ma tyle, ile jest, a próg zaliczenia maleje proporcjonalnie
     * (zob. {@link ExamFormat#passPointsFor}). Ocena egzaminu (submit) filtra nie stosuje: ocenia to, co zostało pokazane.
     */
    @Transactional(readOnly = true)
    public ExamStartResponse start(Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);

        Set<Long> suspended = new HashSet<>(stateRepository.findSuspendedCardIds(user.getId()));
        Set<QuestionOrigin> origins = originFilter.activeFor(user.getId());
        // Losujemy na samych numerach; pełne karty ładujemy dopiero dla wylosowanych (jedno zapytanie).
        List<CardRepository.CardRef> inScope = cardRepository.findReadableRefsForUser(user.getId())
                .stream()
                .filter(CardScopes::isTest)
                .filter(ref -> !suspended.contains(ref.getId()))
                .toList();
        List<Long> pool = new ArrayList<>(inScope.stream()
                .filter(ref -> CardScopes.matchesOrigins(ref, origins))
                .map(CardRepository.CardRef::getId)
                .toList());
        if (pool.isEmpty()) {
            // Gdy pytania są, ale wszystkie spoza wybranych pochodzeń, podpowiadamy filtr zamiast mylić brakiem pytań.
            throw new IllegalArgumentException(inScope.isEmpty()
                    ? "Nie ma teraz pytań testowych, które możesz rozwiązać na egzaminie."
                    : "Nie ma pytań z wybranych pochodzeń. Zmień filtr pochodzenia w Testach.");
        }

        Collections.shuffle(pool, random);
        List<Long> drawn = pool.stream().limit(ExamFormat.QUESTION_COUNT).toList();
        List<TestQueueItemResponse> questions = CardScopes.loadInOrder(cardRepository, drawn).stream()
                .map(ReviewQueueMapper::toTestResponse)
                .toList();
        return new ExamStartResponse(SCOPE_LABEL, ExamFormat.QUESTION_COUNT, ExamFormat.passPointsFor(questions.size()), questions);
    }

    /** Ocenia egzamin, zapisuje podsumowanie podejścia i zwraca raport ze wszystkimi pytaniami w kolejności egzaminu. */
    @Transactional
    public ExamResultResponse submit(ExamSubmitRequest request, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);

        List<ExamSubmitRequest.Answer> answers = request.answers();
        Set<Long> ids = new HashSet<>();
        for (ExamSubmitRequest.Answer answer : answers) {
            if (!ids.add(answer.cardId())) {
                throw new IllegalArgumentException("To samo pytanie zostało przesłane więcej niż raz.");
            }
        }

        Map<Long, Card> cards = new HashMap<>();
        for (Card card : cardRepository.findReadableTestCardsByIds(ids, user.getId())) {
            cards.put(card.getId(), card);
        }

        List<ExamResultResponse.QuestionResult> questions = new ArrayList<>();
        Map<String, int[]> bySource = new LinkedHashMap<>(); // [wszystkie, poprawne]
        int answered = 0;
        int correct = 0;
        int excluded = 0;
        for (ExamSubmitRequest.Answer answer : answers) {
            Card card = cards.get(answer.cardId());
            if (card == null) {
                excluded++;
                continue;
            }
            boolean isCorrect = answer.selectedOption() != null && answer.selectedOption() == card.getCorrectOption();
            if (answer.selectedOption() != null) {
                answered++;
            }
            if (isCorrect) {
                correct++;
            }
            String sourceName = card.getSource() != null ? card.getSource().getName() : null;
            int[] counts = bySource.computeIfAbsent(sourceName == null ? "" : sourceName, k -> new int[2]);
            counts[0]++;
            if (isCorrect) {
                counts[1]++;
            }
            questions.add(new ExamResultResponse.QuestionResult(
                    card.getId(), card.getFront(), card.getOptionA(), card.getOptionB(), card.getOptionC(),
                    answer.selectedOption(), card.getCorrectOption(), isCorrect,
                    card.getExplanation(), card.getLegalBasis(), card.getValidAsOf(), sourceName,
                    card.getOrigin(), card.getExamYear()));
        }
        if (questions.isEmpty()) {
            throw new IllegalArgumentException("Żadne z przesłanych pytań nie jest już dostępne, więc nie ma czego oceniać.");
        }

        int total = questions.size();
        // Próg ustala serwer (format jest stały), nie klient.
        int passPoints = ExamFormat.passPointsFor(total);
        ExamAttempt attempt = new ExamAttempt();
        attempt.setUser(user);
        attempt.setFinishedAt(LocalDateTime.now(clock));
        attempt.setScopeLabel(SCOPE_LABEL);
        attempt.setQuestionCount(total);
        attempt.setAnsweredCount(answered);
        attempt.setCorrectCount(correct);
        attempt.setPassPoints(passPoints);
        attempt.setPassThresholdPercent(ExamScoring.thresholdPercent(passPoints, total));
        attempt.setPassed(ExamScoring.passed(correct, total));
        attempt.setTimeLimitMinutes(request.timeLimitMinutes());
        attempt.setDurationSeconds(request.durationSeconds());
        attempt.setTimedOut(Boolean.TRUE.equals(request.timedOut()));
        attemptRepository.save(attempt);

        List<ExamResultResponse.SourceBreakdown> breakdown = bySource.entrySet().stream()
                .map(e -> new ExamResultResponse.SourceBreakdown(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .sorted(Comparator.comparingInt(ExamResultResponse.SourceBreakdown::total).reversed()
                        .thenComparing(ExamResultResponse.SourceBreakdown::sourceName))
                .toList();

        return new ExamResultResponse(toResponse(attempt), excluded, breakdown, questions);
    }

    /** Ostatnie podejścia użytkownika, od najnowszego. */
    @Transactional(readOnly = true)
    public List<ExamAttemptResponse> history(Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        return attemptRepository.findRecent(user.getId(), PageRequest.of(0, HISTORY_LIMIT)).stream()
                .map(ExamService::toResponse)
                .toList();
    }

    static ExamAttemptResponse toResponse(ExamAttempt attempt) {
        return new ExamAttemptResponse(
                attempt.getId(), attempt.getFinishedAt(), attempt.getScopeLabel(),
                attempt.getQuestionCount(), attempt.getAnsweredCount(), attempt.getCorrectCount(),
                ExamScoring.scorePercent(attempt.getCorrectCount(), attempt.getQuestionCount()),
                attempt.getPassThresholdPercent(), attempt.getPassPoints(), attempt.isPassed(),
                attempt.getTimeLimitMinutes(), attempt.getDurationSeconds(), attempt.isTimedOut());
    }
}

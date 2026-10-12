package pl.tutlaw.backend.exam;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tutlaw.backend.card.Card;
import pl.tutlaw.backend.card.CardRepository;
import pl.tutlaw.backend.card.CardScopes;
import pl.tutlaw.backend.category.CategoryRepository;
import pl.tutlaw.backend.common.exception.ResourceNotFoundException;
import pl.tutlaw.backend.exam.dto.ExamAttemptResponse;
import pl.tutlaw.backend.exam.dto.ExamResultResponse;
import pl.tutlaw.backend.exam.dto.ExamStartRequest;
import pl.tutlaw.backend.exam.dto.ExamStartResponse;
import pl.tutlaw.backend.exam.dto.ExamSubmitRequest;
import pl.tutlaw.backend.review.ReviewQueueMapper;
import pl.tutlaw.backend.review.UserCardStateRepository;
import pl.tutlaw.backend.review.dto.TestQueueItemResponse;
import pl.tutlaw.backend.source.SourceRepository;
import pl.tutlaw.backend.subject.SubjectRepository;
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
 * Egzamin próbny: losowy zestaw pytań testowych z wybranego zakresu, ocena po stronie serwera i zapis podsumowania.
 * Egzamin jest odizolowany od nauki: nie zmienia terminów powtórek (FSRS), dziennika odpowiedzi, dziennych limitów
 * ani licznika błędów kart. Po egzaminie użytkownik może przerobić swoje błędy zwykłą sesją testową.
 */
@Service
@RequiredArgsConstructor
public class ExamService {

    static final int MAX_QUESTIONS = 200;
    /** Ile ostatnich podejść zwraca historia. */
    static final int HISTORY_LIMIT = 50;
    private static final int MAX_LABEL_LENGTH = 300;

    private final CardRepository cardRepository;
    private final UserCardStateRepository stateRepository;
    private final ExamAttemptRepository attemptRepository;
    private final CategoryRepository categoryRepository;
    private final SubjectRepository subjectRepository;
    private final SourceRepository sourceRepository;
    private final CurrentUserResolver currentUserResolver;
    private final Clock clock;

    private final Random random = new Random();

    /**
     * Losuje pytania z zakresu: tylko w formacie testowym, dostępne dla użytkownika (pula płaska albo przeczytany
     * rozdział) i niezawieszone. Gdy jest ich mniej niż chciał, dostaje wszystkie.
     */
    @Transactional(readOnly = true)
    public ExamStartResponse start(ExamStartRequest request, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        String label = scopeLabel(request.categoryId(), request.subjectId(), request.sourceId());

        Set<Long> suspended = new HashSet<>(stateRepository.findSuspendedCardIds(user.getId()));
        // Losujemy na samych numerach; pełne karty ładujemy dopiero dla wylosowanych (jedno zapytanie).
        List<Long> pool = new ArrayList<>(
                CardScopes.readableRefs(
                                cardRepository, request.categoryId(), request.subjectId(), request.sourceId(), user.getId())
                        .stream()
                        .filter(CardScopes::isTest)
                        .map(CardRepository.CardRef::getId)
                        .filter(id -> !suspended.contains(id))
                        .toList());
        if (pool.isEmpty()) {
            throw new IllegalArgumentException(
                    "W wybranym zakresie nie ma teraz pytań testowych, które możesz rozwiązać.");
        }

        Collections.shuffle(pool, random);
        List<Long> drawn = pool.stream().limit(request.questionCount()).toList();
        List<TestQueueItemResponse> questions = CardScopes.loadInOrder(cardRepository, drawn).stream()
                .map(ReviewQueueMapper::toTestResponse)
                .toList();
        return new ExamStartResponse(label, request.questionCount(), questions);
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
        String label = scopeLabel(request.categoryId(), request.subjectId(), request.sourceId());

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
                    card.getExplanation(), card.getLegalBasis(), card.getValidAsOf(), sourceName));
        }
        if (questions.isEmpty()) {
            throw new IllegalArgumentException("Żadne z przesłanych pytań nie jest już dostępne, więc nie ma czego oceniać.");
        }

        int total = questions.size();
        ExamAttempt attempt = new ExamAttempt();
        attempt.setUser(user);
        attempt.setFinishedAt(LocalDateTime.now(clock));
        attempt.setScopeLabel(label);
        attempt.setQuestionCount(total);
        attempt.setAnsweredCount(answered);
        attempt.setCorrectCount(correct);
        attempt.setPassThresholdPercent(request.passThresholdPercent());
        attempt.setPassed(ExamScoring.passed(correct, total, request.passThresholdPercent()));
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
                attempt.getPassThresholdPercent(), attempt.isPassed(),
                attempt.getTimeLimitMinutes(), attempt.getDurationSeconds(), attempt.isTimedOut());
    }

    /** Czytelna nazwa zakresu do raportu i historii. Nieistniejący zakres to 404. */
    private String scopeLabel(Long categoryId, Long subjectId, Long sourceId) {
        String label;
        if (sourceId != null) {
            var source = sourceRepository.findById(sourceId).orElseThrow(ExamService::scopeNotFound);
            label = source.getSubject() != null
                    ? source.getSubject().getName() + " → " + source.getName()
                    : source.getName();
        } else if (subjectId != null) {
            label = subjectRepository.findById(subjectId).orElseThrow(ExamService::scopeNotFound).getName();
        } else if (categoryId != null) {
            label = categoryRepository.findById(categoryId).orElseThrow(ExamService::scopeNotFound).getName();
        } else {
            label = "Cały materiał";
        }
        return label.length() > MAX_LABEL_LENGTH ? label.substring(0, MAX_LABEL_LENGTH) : label;
    }

    private static ResourceNotFoundException scopeNotFound() {
        return new ResourceNotFoundException("Nie znaleziono wybranego zakresu egzaminu");
    }
}

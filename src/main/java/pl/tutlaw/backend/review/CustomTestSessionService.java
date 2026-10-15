package pl.tutlaw.backend.review;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tutlaw.backend.card.Card;
import pl.tutlaw.backend.card.CardRepository;
import pl.tutlaw.backend.card.CorrectOption;
import pl.tutlaw.backend.card.QuestionOrigin;
import pl.tutlaw.backend.category.Category;
import pl.tutlaw.backend.category.CategoryRepository;
import pl.tutlaw.backend.common.exception.ResourceNotFoundException;
import pl.tutlaw.backend.review.dto.CustomTestRequest;
import pl.tutlaw.backend.review.dto.CustomTestSelectionResponse;
import pl.tutlaw.backend.review.dto.CustomTestSessionDetailResponse;
import pl.tutlaw.backend.review.dto.CustomTestSessionSummaryResponse;
import pl.tutlaw.backend.settings.OriginFilterService;
import pl.tutlaw.backend.subject.Subject;
import pl.tutlaw.backend.subject.SubjectRepository;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Własne testy jako sesje ("Moje testy"). Test powstaje w chwili ułożenia: zapisujemy konfigurację (zakres, rodzaj
 * pytań, liczba, filtr pochodzenia) i wylosowane pytania. Odpowiedzi dopisują się do niego w trakcie
 * ({@link #recordAnswer}); po odpowiedzi na ostatnie pytanie test jest ukończony. Test, którego nikt nie skończył,
 * zostaje na liście jako nieukończony. Zapisuje się wyłącznie własny test: powtórki, "Popraw błędy" i egzamin mają
 * swoje miejsca (egzamin: historia podejść).
 */
@Service
@RequiredArgsConstructor
public class CustomTestSessionService {

    /** Ile ostatnich testów zwraca lista. */
    static final int LIST_LIMIT = 50;

    private final CustomTestService customTestService;
    private final CustomTestSessionRepository sessionRepository;
    private final CustomTestSessionQuestionRepository questionRepository;
    private final CardRepository cardRepository;
    private final CategoryRepository categoryRepository;
    private final SubjectRepository subjectRepository;
    private final OriginFilterService originFilter;
    private final CurrentUserResolver currentUserResolver;
    private final Clock clock;

    /** Losuje pytania ({@link CustomTestService#select}) i zapisuje test. Odpowiedź zawiera numer zapisanego testu. */
    @Transactional
    public CustomTestSelectionResponse draw(CustomTestRequest request, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        CustomTestSelectionResponse selection = customTestService.select(request, authentication);

        List<Long> categoryIds = distinctIds(request.categoryIds());
        List<Long> subjectIds = distinctIds(request.subjectIds());

        CustomTestSession session = new CustomTestSession();
        session.setUser(user);
        session.setStartedAt(LocalDateTime.now(clock));
        session.setKind(request.kind() == null ? CustomTestKind.ALL : request.kind());
        session.setScopeLabel(CustomTestScopeLabel.describe(scopeNames(categoryIds, subjectIds)));
        session.setCategoryIds(joinIds(categoryIds));
        session.setSubjectIds(joinIds(subjectIds));
        session.setOrigins(serializeOrigins(originFilter.activeFor(user.getId())));
        session.setRequestedCount(selection.requested());
        session.setAvailableCount(selection.available());
        sessionRepository.save(session);

        // Karty wczytujemy po numerach jednym zapytaniem; kolejność pytań w sesji to kolejność z losowania.
        Map<Long, Card> cards = new HashMap<>();
        for (Card card : cardRepository.findAllById(selection.cardIds())) {
            cards.put(card.getId(), card);
        }
        List<CustomTestSessionQuestion> rows = new ArrayList<>();
        int position = 1;
        for (Long cardId : selection.cardIds()) {
            Card card = cards.get(cardId);
            if (card == null) {
                continue;
            }
            CustomTestSessionQuestion row = new CustomTestSessionQuestion();
            row.setSession(session);
            row.setCard(card);
            row.setPosition(position++);
            rows.add(row);
        }
        questionRepository.saveAll(rows);

        return new CustomTestSelectionResponse(
                selection.cardIds(), selection.available(), selection.requested(), session.getId());
    }

    /**
     * Zapisuje odpowiedź w teście. Wołane przy każdej odpowiedzi w sesji; błędny numer testu, test innego
     * użytkownika, pytanie spoza testu i pytanie już zapisane są po cichu pomijane (odpowiedź w nauce i tak się zapisała).
     * Po odpowiedzi na ostatnie pytanie test jest ukończony.
     */
    @Transactional
    public void recordAnswer(
            Long userId, Long sessionId, Long cardId, CorrectOption selected,
            boolean correct, boolean guessed, Double timeSpentSeconds) {
        if (sessionId == null) {
            return;
        }
        CustomTestSession session = sessionRepository.findByIdAndUserId(sessionId, userId).orElse(null);
        if (session == null) {
            return;
        }
        CustomTestSessionQuestion row = questionRepository.findBySessionIdAndCardId(sessionId, cardId).orElse(null);
        if (row == null || row.isAnswered()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        row.setSelectedOption(selected);
        row.setWasCorrect(correct);
        row.setWasGuessed(guessed);
        row.setTimeSpentSeconds(timeSpentSeconds);
        row.setAnsweredAt(now);
        questionRepository.save(row);

        if (!session.isFinished() && questionRepository.countBySessionIdAndAnsweredAtIsNull(sessionId) == 0) {
            session.setFinishedAt(now);
            sessionRepository.save(session);
        }
    }

    /** Ostatnie testy użytkownika, od najnowszego. */
    @Transactional(readOnly = true)
    public List<CustomTestSessionSummaryResponse> list(Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        List<CustomTestSession> sessions = sessionRepository.findRecent(user.getId(), PageRequest.of(0, LIST_LIMIT));
        return summarize(sessions);
    }

    /** Wszystkie testy użytkownika (do eksportu danych). */
    @Transactional(readOnly = true)
    public List<CustomTestSessionSummaryResponse> listAll(Long userId) {
        return summarize(sessionRepository.findAllForUser(userId));
    }

    @Transactional(readOnly = true)
    public CustomTestSessionDetailResponse detail(Long sessionId, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        CustomTestSession session = sessionRepository.findByIdAndUserId(sessionId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Nie znaleziono takiego testu."));
        List<CustomTestSessionQuestion> rows = questionRepository.findDetailed(session.getId());

        List<CustomTestSessionDetailResponse.Question> questions = rows.stream().map(row -> {
            Card card = row.getCard();
            return new CustomTestSessionDetailResponse.Question(
                    row.getPosition(), card.getId(), card.getFront(),
                    card.getOptionA(), card.getOptionB(), card.getOptionC(),
                    card.getCorrectOption(), row.getSelectedOption(), row.getWasCorrect(), row.isWasGuessed(),
                    row.getTimeSpentSeconds(), card.getExplanation(), card.getLegalBasis(), card.getValidAsOf(),
                    card.getSource() != null ? card.getSource().getName() : null,
                    card.getOrigin(), card.getExamYear());
        }).toList();
        return new CustomTestSessionDetailResponse(summaryOf(session, rows), questions);
    }

    @Transactional(readOnly = true)
    public long countFinished(Long userId) {
        return sessionRepository.countByUserIdAndFinishedAtIsNotNull(userId);
    }

    // ---------------------------------------------------------------- pomocnicze

    private List<CustomTestSessionSummaryResponse> summarize(List<CustomTestSession> sessions) {
        if (sessions.isEmpty()) {
            return List.of();
        }
        Map<Long, List<CustomTestSessionQuestion>> bySession = questionRepository
                .findBySessionIds(sessions.stream().map(CustomTestSession::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(q -> q.getSession().getId()));
        return sessions.stream()
                .map(s -> summaryOf(s, bySession.getOrDefault(s.getId(), List.of())))
                .toList();
    }

    static CustomTestSessionSummaryResponse summaryOf(CustomTestSession s, List<CustomTestSessionQuestion> rows) {
        int answered = 0;
        int correct = 0;
        int guessed = 0;
        double time = 0;
        for (CustomTestSessionQuestion row : rows) {
            if (!row.isAnswered()) {
                continue;
            }
            answered++;
            if (Boolean.TRUE.equals(row.getWasCorrect())) {
                correct++;
            }
            if (row.isWasGuessed()) {
                guessed++;
            }
            time += AnswerTimes.capped(row.getTimeSpentSeconds());
        }
        return new CustomTestSessionSummaryResponse(
                s.getId(), s.getStartedAt(), s.getFinishedAt(), s.isFinished(), s.getScopeLabel(), s.getKind(),
                parseIds(s.getCategoryIds()), parseIds(s.getSubjectIds()),
                OriginFilterService.parse(s.getOrigins()),
                s.getRequestedCount(), rows.size(), answered, correct, guessed, time);
    }

    /** Nazwy wybranych rodzajów, potem aktów prawnych (w kolejności wyboru); nieistniejące numery są pomijane. */
    private List<String> scopeNames(List<Long> categoryIds, List<Long> subjectIds) {
        List<String> names = new ArrayList<>();
        if (!categoryIds.isEmpty()) {
            Map<Long, String> byId = categoryRepository.findAllById(categoryIds).stream()
                    .collect(Collectors.toMap(Category::getId, Category::getName));
            categoryIds.stream().map(byId::get).filter(Objects::nonNull).forEach(names::add);
        }
        if (!subjectIds.isEmpty()) {
            Map<Long, String> byId = subjectRepository.findAllById(subjectIds).stream()
                    .collect(Collectors.toMap(Subject::getId, Subject::getName));
            subjectIds.stream().map(byId::get).filter(Objects::nonNull).forEach(names::add);
        }
        // Wybrano coś, czego już nie ma: lepsza ogólna nazwa niż "Cały materiał".
        if (names.isEmpty() && (!categoryIds.isEmpty() || !subjectIds.isEmpty())) {
            names.add("Wybrane działy");
        }
        return names;
    }

    private static List<Long> distinctIds(List<Long> raw) {
        return raw == null ? List.of() : raw.stream().filter(Objects::nonNull).distinct().toList();
    }

    static String joinIds(List<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    static List<Long> parseIds(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String part : stored.split(",")) {
            try {
                ids.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ignored) {
                // uszkodzony wpis: pomijamy
            }
        }
        return ids;
    }

    /** Kody oddzielone przecinkami; wszystkie pochodzenia to null (jak w ustawieniach konta). */
    static String serializeOrigins(Set<QuestionOrigin> origins) {
        if (origins.containsAll(EnumSet.allOf(QuestionOrigin.class))) {
            return null;
        }
        return EnumSet.copyOf(origins).stream().map(Enum::name).collect(Collectors.joining(","));
    }
}

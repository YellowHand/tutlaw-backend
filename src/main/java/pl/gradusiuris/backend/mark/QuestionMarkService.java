package pl.gradusiuris.backend.mark;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.gradusiuris.backend.common.PageParams;
import pl.gradusiuris.backend.common.PageResponse;
import pl.gradusiuris.backend.common.exception.ResourceNotFoundException;
import pl.gradusiuris.backend.mark.dto.BulkMarkRequest;
import pl.gradusiuris.backend.mark.dto.BulkMarkResponse;
import pl.gradusiuris.backend.mark.dto.MarkAnalysisResponse;
import pl.gradusiuris.backend.mark.dto.MarkSummaryResponse;
import pl.gradusiuris.backend.mark.dto.MarkedQuestionResponse;
import pl.gradusiuris.backend.mark.dto.QuestionMarkResponse;
import pl.gradusiuris.backend.question.QuestionRepository;
import pl.gradusiuris.backend.question.QuestionText;
import pl.gradusiuris.backend.user.CurrentUserResolver;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Oznaczenia pytań (pytanie o termin, pułapki) dla administratora: uruchomienie automatu, podsumowanie, przegląd
 * propozycji i zatwierdzanie lub odrzucanie (pojedynczo albo hurtem). Użytkownicy oznaczeń nie widzą; liczą się tylko
 * zatwierdzone, i to one układają testy „Pytania z terminów" i „Pułapki".
 *
 * <p>Zasady: automat tylko PROPONUJE ({@link MarkStatus#SUGGESTED}), a ponowne uruchomienie nigdy nie nadpisuje
 * decyzji (para pytanie-oznaczenie ma najwyżej jeden wiersz, więc zatwierdzone i odrzucone zostają, jakie były).
 * Administrator może też nadać oznaczenie ręcznie (nowy wiersz MANUAL) albo je cofnąć (odrzucenie).
 */
@Service
@RequiredArgsConstructor
public class QuestionMarkService {

    /** Ile pytań automat czyta naraz (treści pytań ładuje się partiami). */
    static final int BATCH_SIZE = 500;
    /** Stany, na które administrator może zmienić oznaczenie. Do propozycji nie ma powrotu. */
    private static final Set<MarkStatus> DECISIONS = EnumSet.of(MarkStatus.CONFIRMED, MarkStatus.REJECTED);

    private final QuestionMarkRepository markRepository;
    private final QuestionRepository questionRepository;
    private final CurrentUserResolver currentUserResolver;
    private final Clock clock;

    /**
     * Przegląda wszystkie pytania i dodaje propozycje tam, gdzie pary (pytanie, oznaczenie) jeszcze nie ma w bazie.
     * Istniejące wiersze, także odrzucone, zostają bez zmian.
     */
    @Transactional
    public MarkAnalysisResponse analyze() {
        Map<Long, Set<QuestionMarkType>> existing = new HashMap<>();
        for (QuestionMarkRepository.MarkKey key : markRepository.findAllKeys()) {
            existing.computeIfAbsent(key.getQuestionId(), id -> EnumSet.noneOf(QuestionMarkType.class)).add(key.getMark());
        }

        Map<QuestionMarkType, Integer> byMark = new EnumMap<>(QuestionMarkType.class);
        for (QuestionMarkType type : QuestionMarkType.values()) {
            byMark.put(type, 0);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        int scanned = 0;
        int suggested = 0;
        long after = 0;
        while (true) {
            List<QuestionText> batch = questionRepository.findTextsAfter(after, PageRequest.of(0, BATCH_SIZE));
            if (batch.isEmpty()) {
                break;
            }
            List<QuestionMark> toSave = new ArrayList<>();
            for (QuestionText question : batch) {
                scanned++;
                after = question.getId();
                Set<QuestionMarkType> already = existing.getOrDefault(question.getId(), Set.of());
                for (QuestionMarkType type : QuestionMarkDetector.detect(
                        question.getBody(), question.getOptionA(), question.getOptionB(), question.getOptionC())) {
                    if (!already.contains(type)) {
                        toSave.add(suggestion(question.getId(), type, now));
                        byMark.merge(type, 1, Integer::sum);
                        suggested++;
                    }
                }
            }
            if (!toSave.isEmpty()) {
                markRepository.saveAll(toSave);
            }
        }
        return new MarkAnalysisResponse(scanned, suggested, byMark);
    }

    private static QuestionMark suggestion(Long questionId, QuestionMarkType type, LocalDateTime now) {
        QuestionMark mark = new QuestionMark();
        mark.setQuestionId(questionId);
        mark.setMark(type);
        mark.setOrigin(MarkOrigin.AUTO);
        mark.setStatus(MarkStatus.SUGGESTED);
        mark.setCreatedAt(now);
        return mark;
    }

    /** Ile oznaczeń każdego rodzaju jest w każdym stanie (także zerowe wiersze). */
    @Transactional(readOnly = true)
    public MarkSummaryResponse summary() {
        Map<QuestionMarkType, long[]> counts = new EnumMap<>(QuestionMarkType.class); // [propozycje, zatwierdzone, odrzucone]
        for (QuestionMarkType type : QuestionMarkType.values()) {
            counts.put(type, new long[3]);
        }
        for (QuestionMarkRepository.MarkCount row : markRepository.countByMarkAndStatus()) {
            long[] cell = counts.get(row.getMark());
            switch (row.getStatus()) {
                case SUGGESTED -> cell[0] = row.getTotal();
                case CONFIRMED -> cell[1] = row.getTotal();
                case REJECTED -> cell[2] = row.getTotal();
            }
        }
        List<MarkSummaryResponse.Row> rows = counts.entrySet().stream()
                .map(e -> new MarkSummaryResponse.Row(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
                .toList();
        return new MarkSummaryResponse(questionRepository.count(), rows);
    }

    /**
     * Oznaczenia w danym stanie z pytaniami, do przejrzenia. Bez wskazanego rodzaju (null) wszystkie rodzaje.
     * Od najmniejszego numeru pytania.
     */
    @Transactional(readOnly = true)
    public PageResponse<MarkedQuestionResponse> list(MarkStatus status, QuestionMarkType mark, PageParams params) {
        Collection<QuestionMarkType> marks = mark == null ? List.of(QuestionMarkType.values()) : List.of(mark);
        Page<QuestionMarkRepository.MarkedQuestion> page = markRepository.findForReview(
                status == null ? MarkStatus.SUGGESTED : status, marks, PageRequest.of(params.page(), params.size()));
        return PageResponse.of(page.map(QuestionMarkService::toResponse));
    }

    /**
     * Hurtowa zmiana stanu: wszystkie oznaczenia w stanie {@code from} (podanych rodzajów albo wszystkich) dostają
     * stan {@code to}. Typowo „zatwierdź wszystkie propozycje".
     */
    @Transactional
    public BulkMarkResponse bulk(BulkMarkRequest request, Authentication authentication) {
        if (!DECISIONS.contains(request.to())) {
            throw new IllegalArgumentException("Stan docelowy to CONFIRMED (zatwierdź) albo REJECTED (odrzuć).");
        }
        if (request.from() == request.to()) {
            throw new IllegalArgumentException("Stan początkowy i docelowy muszą się różnić.");
        }
        Collection<QuestionMarkType> marks = request.marks() == null || request.marks().isEmpty()
                ? List.of(QuestionMarkType.values())
                : request.marks().stream().distinct().toList();
        Long reviewer = currentUserResolver.resolve(authentication).getId();
        int updated = markRepository.changeStatus(request.from(), request.to(), marks, LocalDateTime.now(clock), reviewer);
        return new BulkMarkResponse(updated);
    }

    /**
     * Decyzja o jednym oznaczeniu jednego pytania: CONFIRMED nadaje je (albo zatwierdza propozycję), REJECTED cofa
     * (albo odrzuca propozycję). Gdy pary jeszcze nie było, powstaje wiersz MANUAL; istniejący wiersz zachowuje
     * pochodzenie (kto zaproponował), a zmienia się tylko stan i dane decyzji.
     */
    @Transactional
    public QuestionMarkResponse decide(Long questionId, QuestionMarkType type, MarkStatus status, Authentication authentication) {
        if (!DECISIONS.contains(status)) {
            throw new IllegalArgumentException("Decyzja to CONFIRMED (nadaj) albo REJECTED (cofnij).");
        }
        if (!questionRepository.existsById(questionId)) {
            throw new ResourceNotFoundException("Question not found");
        }
        Long reviewer = currentUserResolver.resolve(authentication).getId();
        LocalDateTime now = LocalDateTime.now(clock);
        QuestionMark mark = markRepository.findByQuestionIdAndMark(questionId, type).orElseGet(() -> {
            QuestionMark created = new QuestionMark();
            created.setQuestionId(questionId);
            created.setMark(type);
            created.setOrigin(MarkOrigin.MANUAL);
            created.setCreatedAt(now);
            return created;
        });
        mark.setStatus(status);
        mark.setReviewedAt(now);
        mark.setReviewedBy(reviewer);
        markRepository.save(mark);
        return new QuestionMarkResponse(mark.getQuestionId(), mark.getMark(), mark.getOrigin(), mark.getStatus());
    }

    private static MarkedQuestionResponse toResponse(QuestionMarkRepository.MarkedQuestion row) {
        return new MarkedQuestionResponse(
                row.getId(), row.getQuestionId(), row.getMark(), row.getOrigin(), row.getStatus(), row.getBody(),
                row.getOptionA(), row.getOptionB(), row.getOptionC(), row.getCorrectOption(), row.getSourceName(),
                row.getActShortName());
    }
}

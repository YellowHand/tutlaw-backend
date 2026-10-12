package pl.tutlaw.backend.card;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tutlaw.backend.card.dto.BulkImportResult;
import pl.tutlaw.backend.card.dto.CardRequest;
import pl.tutlaw.backend.card.dto.CardResponse;
import pl.tutlaw.backend.chapter.Chapter;
import pl.tutlaw.backend.chapter.ChapterRepository;
import pl.tutlaw.backend.common.exception.ConflictException;
import pl.tutlaw.backend.common.exception.ResourceNotFoundException;
import pl.tutlaw.backend.source.Source;
import pl.tutlaw.backend.source.SourceRepository;
import pl.tutlaw.backend.source.SourceType;
import pl.tutlaw.backend.tag.Tag;
import pl.tutlaw.backend.tag.TagRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CardService {

    private final CardRepository cardRepository;
    private final ChapterRepository chapterRepository;
    private final SourceRepository sourceRepository;
    private final TagRepository tagRepository;

    public List<CardResponse> getByChapter(Long chapterId) {
        return cardRepository.findByChapterId(chapterId).stream()
                .map(CardMapper::toResponse).toList();
    }

    public List<CardResponse> getBySource(Long sourceId) {
        return cardRepository.findBySourceId(sourceId).stream().map(CardMapper::toResponse).toList();
    }

    /**
     * Dodaje jedną kartę. Zła treść to 400, a identyczna karta w tym samym źródle to 409.
     */
    @Transactional
    public CardResponse create(CardRequest request) {
        ImportContext context = new ImportContext();
        Card card = buildCard(request, context);

        String key = keyOf(card);
        if (context.existingKeys(card.getSource().getId()).contains(key)) {
            throw new ConflictException("Taka karta już istnieje w tym źródle");
        }

        return CardMapper.toResponse(cardRepository.save(card));
    }

    /**
     * Import zbiorczy. Poprawne, niepowtarzające się wiersze zapisują się w jednej transakcji;
     * błędne i zduplikowane są pominięte i opisane w wyniku (numer wiersza liczony od 1).
     */
    @Transactional
    public BulkImportResult bulkCreate(List<CardRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("Lista kart jest pusta");
        }

        ImportContext context = new ImportContext();
        List<Card> toSave = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int duplicates = 0;

        for (int i = 0; i < requests.size(); i++) {
            String rowLabel = "Wiersz " + (i + 1) + ": ";
            CardRequest request = requests.get(i);
            if (request == null) {
                errors.add(rowLabel + "pusty wiersz");
                continue;
            }
            try {
                Card card = buildCard(request, context);
                Set<String> keys = context.existingKeys(card.getSource().getId());
                // add() zwraca false, gdy klucz już jest: w bazie albo wcześniej w tym samym imporcie.
                if (!keys.add(keyOf(card))) {
                    duplicates++;
                    continue;
                }
                toSave.add(card);
            } catch (IllegalArgumentException | ResourceNotFoundException e) {
                errors.add(rowLabel + e.getMessage());
            }
        }

        cardRepository.saveAll(toSave);
        return new BulkImportResult(toSave.size(), duplicates, errors);
    }

    /** Sprawdza wiersz i składa z niego kartę (jeszcze niezapisaną). */
    private Card buildCard(CardRequest request, ImportContext context) {
        if (request.sourceId() == null) {
            throw new IllegalArgumentException("Brak identyfikatora źródła (sourceId)");
        }
        if (request.type() == null) {
            throw new IllegalArgumentException("Brak typu karty (type)");
        }

        Source source = context.source(request.sourceId());
        Chapter chapter = request.chapterId() == null ? null : context.chapter(request.chapterId());
        if (chapter != null && !chapter.getSource().getId().equals(source.getId())) {
            throw new IllegalArgumentException("Rozdział nie należy do wskazanego źródła");
        }

        List<String> problems = CardContentRules.validate(
                source.getSourceType() == SourceType.TEXTBOOK,
                chapter != null,
                request.front(), request.back(), request.pageNumber(),
                request.optionA(), request.optionB(), request.optionC(), request.correctOption());
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join(" ", problems));
        }

        Card card = new Card();
        card.setSource(source);
        card.setChapter(chapter);
        card.setType(request.type());
        card.setFront(request.front().strip());
        card.setBack(request.back().strip());
        card.setPageNumber(request.pageNumber());
        card.setVerbatimQuote(request.verbatimQuote());
        card.setOptionA(CardContentRules.blankToNull(request.optionA()));
        card.setOptionB(CardContentRules.blankToNull(request.optionB()));
        card.setOptionC(CardContentRules.blankToNull(request.optionC()));
        card.setCorrectOption(request.correctOption());
        card.setExplanation(CardContentRules.blankToNull(request.explanation()));
        card.setTags(context.tags(request.tagIds()));
        return card;
    }

    private static String keyOf(Card card) {
        return CardContentRules.duplicateKey(
                card.getFront(), card.getBack(),
                card.getOptionA(), card.getOptionB(), card.getOptionC(), card.getCorrectOption());
    }

    /**
     * Podręczna pamięć na czas jednej operacji: źródła, rozdziały, tagi i klucze istniejących kart
     * pobieramy z bazy raz, a nie osobno dla każdego wiersza importu.
     */
    private final class ImportContext {
        private final Map<Long, Source> sources = new HashMap<>();
        private final Map<Long, Chapter> chapters = new HashMap<>();
        private final Map<Long, Set<String>> keysBySource = new HashMap<>();
        private Map<Long, Tag> allTags;

        Source source(Long id) {
            Source cached = sources.get(id);
            if (cached != null) return cached;
            Source loaded = sourceRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Nie znaleziono źródła o id " + id));
            sources.put(id, loaded);
            return loaded;
        }

        Chapter chapter(Long id) {
            Chapter cached = chapters.get(id);
            if (cached != null) return cached;
            Chapter loaded = chapterRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Nie znaleziono rozdziału o id " + id));
            chapters.put(id, loaded);
            return loaded;
        }

        Set<Tag> tags(List<Long> ids) {
            Set<Tag> result = new HashSet<>();
            if (ids == null || ids.isEmpty()) return result;
            if (allTags == null) {
                allTags = new HashMap<>();
                for (Tag tag : tagRepository.findAll()) {
                    allTags.put(tag.getId(), tag);
                }
            }
            for (Long id : new LinkedHashSet<>(ids)) {
                Tag tag = allTags.get(id);
                if (tag == null) {
                    throw new ResourceNotFoundException("Nie znaleziono tagu o id " + id);
                }
                result.add(tag);
            }
            return result;
        }

        Set<String> existingKeys(Long sourceId) {
            return keysBySource.computeIfAbsent(sourceId, id -> {
                Set<String> keys = new HashSet<>();
                for (Card existing : cardRepository.findBySourceId(id)) {
                    keys.add(keyOf(existing));
                }
                return keys;
            });
        }
    }
}

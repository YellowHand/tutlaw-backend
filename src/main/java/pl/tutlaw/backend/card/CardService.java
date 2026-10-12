package pl.tutlaw.backend.card;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tutlaw.backend.card.dto.BulkImportResult;
import pl.tutlaw.backend.card.dto.CardRequest;
import pl.tutlaw.backend.card.dto.CardResponse;
import pl.tutlaw.backend.chapter.Chapter;
import pl.tutlaw.backend.chapter.ChapterRepository;
import pl.tutlaw.backend.common.exception.ResourceNotFoundException;
import pl.tutlaw.backend.source.Source;
import pl.tutlaw.backend.source.SourceRepository;
import pl.tutlaw.backend.tag.TagRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

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

    @Transactional
    public CardResponse create(CardRequest request) {
        Source source = sourceRepository.findById(request.sourceId())
                .orElseThrow(() -> new ResourceNotFoundException("Source not found"));

        Chapter chapter = null;
        if (request.chapterId() != null) {
            chapter = chapterRepository.findById(request.chapterId())
                    .orElseThrow(() -> new ResourceNotFoundException("Chapter not found"));

            if (!chapter.getSource().getId().equals(source.getId())) {
                throw new IllegalArgumentException("Chapter does not belong to the specified source");
            }
        }

        Card card = new Card();
        card.setSource(source);
        card.setChapter(chapter);
        card.setType(request.type());
        card.setFront(request.front());
        card.setBack(request.back());
        card.setPageNumber(request.pageNumber());
        card.setVerbatimQuote(request.verbatimQuote());
        card.setOptionA(request.optionA());
        card.setOptionB(request.optionB());
        card.setOptionC(request.optionC());
        card.setCorrectOption(request.correctOption());
        card.setExplanation(request.explanation());

        if (request.tagIds() != null && !request.tagIds().isEmpty()) {
            card.setTags(new HashSet<>(tagRepository.findAllById(request.tagIds())));
        }

        return CardMapper.toResponse(cardRepository.save(card));
    }

    public List<CardResponse> getBySource(Long sourceId) {
        return cardRepository.findBySourceId(sourceId).stream().map(CardMapper::toResponse).toList();
    }

    public BulkImportResult bulkCreate(List<CardRequest> requests) {
        int created = 0;
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < requests.size(); i++) {
            try {
                create(requests.get(i));
                created++;
            } catch (Exception e) {
                errors.add("Wiersz " + (i + 1) + ": " + e.getMessage());
            }
        }

        return new BulkImportResult(created, errors);
    }
}

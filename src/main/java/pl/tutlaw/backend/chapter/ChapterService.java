package pl.tutlaw.backend.chapter;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import pl.tutlaw.backend.chapter.dto.ChapterRequest;
import pl.tutlaw.backend.chapter.dto.ChapterResponse;
import pl.tutlaw.backend.common.exception.ResourceNotFoundException;
import pl.tutlaw.backend.progress.ChapterProgress;
import pl.tutlaw.backend.progress.ChapterProgressRepository;
import pl.tutlaw.backend.source.Source;
import pl.tutlaw.backend.source.SourceRepository;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChapterService {

    private final ChapterRepository chapterRepository;
    private final SourceRepository sourceRepository;
    private final ChapterProgressRepository progressRepository;
    private final CurrentUserResolver currentUserResolver;

    public List<ChapterResponse> getBySource(Long sourceId, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        List<Chapter> chapters = chapterRepository.findBySourceIdOrderByOrderIndexAsc(sourceId);

        List<Long> chapterIds = chapters.stream().map(Chapter::getId).toList();
        Map<Long, Boolean> readByChapterId = progressRepository
                .findByUserIdAndChapterIdIn(user.getId(), chapterIds)
                .stream()
                .collect(Collectors.toMap(p -> p.getChapter().getId(), ChapterProgress::isRead));

        return chapters.stream()
                .map(c -> ChapterMapper.toResponse(c, readByChapterId.getOrDefault(c.getId(), false)))
                .toList();
    }

    public ChapterResponse create(ChapterRequest request) {
        Source source = sourceRepository.findById(request.sourceId())
                .orElseThrow(() -> new ResourceNotFoundException("Source not found"));

        Chapter parent = null;
        if (request.parentChapterId() != null) {
            parent = chapterRepository.findById(request.parentChapterId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent chapter not found"));
        }

        Chapter chapter = new Chapter();
        chapter.setSource(source);
        chapter.setParentChapter(parent);
        chapter.setTitle(request.title());
        chapter.setOrderIndex(request.orderIndex());

        return ChapterMapper.toResponse(chapterRepository.save(chapter), false);
    }
}
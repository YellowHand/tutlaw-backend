package pl.tutlaw.backend.progress;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tutlaw.backend.chapter.Chapter;
import pl.tutlaw.backend.chapter.ChapterRepository;
import pl.tutlaw.backend.common.exception.ResourceNotFoundException;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ChapterProgressService {

    private final ChapterProgressRepository progressRepository;
    private final ChapterRepository chapterRepository;
    private final CurrentUserResolver currentUserResolver;

    @Transactional
    public void setProgress(Long chapterId, boolean isRead, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new ResourceNotFoundException("Chapter not found"));

        ChapterProgress progress = progressRepository.findByUserIdAndChapterId(user.getId(), chapterId)
                .orElseGet(() -> {
                    ChapterProgress p = new ChapterProgress();
                    p.setUser(user);
                    p.setChapter(chapter);
                    return p;
                });

        progress.setRead(isRead);
        progress.setReadAt(isRead ? LocalDateTime.now() : null);
        progressRepository.save(progress);
    }
}

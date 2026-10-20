package pl.tutlaw.backend.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tutlaw.backend.exam.ExamAttemptRepository;
import pl.tutlaw.backend.flashcard.FlashcardAdditionRepository;
import pl.tutlaw.backend.review.CustomTestSessionRepository;
import pl.tutlaw.backend.review.ReviewLogRepository;
import pl.tutlaw.backend.review.ReviewMode;
import pl.tutlaw.backend.review.UserQuestionStateRepository;
import pl.tutlaw.backend.studytime.StudyTimeRepository;
import pl.tutlaw.backend.user.dto.StudyProgressResponse;

/**
 * Zerowanie postępów: użytkownik zaczyna naukę od zera, ale zostaje przy swoim koncie, ścieżce, ustawieniach nauki
 * (limity, retencja), wybranych przedmiotach i zawężeniu puli oraz zgłoszeniach pytań. Znikają stany nauki obu trybów,
 * historia odpowiedzi, pytania dodane ręcznie do fiszek, własne testy, egzaminy próbne i czas nauki (te same dane,
 * co w skrypcie {@code docs/reset-study-progress.sql} dla jednego użytkownika). Operacja jest nieodwracalna, więc
 * wymaga wpisania słowa {@value #CONFIRMATION_WORD}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StudyProgressService {

    /** Słowo, które użytkownik wpisuje, żeby potwierdzić zerowanie. */
    public static final String CONFIRMATION_WORD = "WYZERUJ";

    private final CurrentUserResolver currentUserResolver;
    private final UserQuestionStateRepository stateRepository;
    private final ReviewLogRepository reviewLogRepository;
    private final FlashcardAdditionRepository flashcardAdditionRepository;
    private final CustomTestSessionRepository customTestRepository;
    private final ExamAttemptRepository examAttemptRepository;
    private final StudyTimeRepository studyTimeRepository;

    /** Ile czego zniknie po wyzerowaniu, do pokazania w oknie potwierdzenia. */
    @Transactional(readOnly = true)
    public StudyProgressResponse summary(Authentication authentication) {
        return summaryOf(currentUserResolver.resolve(authentication).getId());
    }

    /**
     * Kasuje postępy zalogowanego użytkownika. Odpowiada tym, co skasowano (liczby sprzed kasowania). Bez wpisania
     * słowa {@value #CONFIRMATION_WORD} niczego nie rusza (IllegalArgumentException, czyli HTTP 400).
     */
    @Transactional
    public StudyProgressResponse reset(String confirm, Authentication authentication) {
        Long userId = currentUserResolver.resolve(authentication).getId();
        if (confirm == null || !CONFIRMATION_WORD.equals(confirm.trim())) {
            throw new IllegalArgumentException("Wpisz słowo " + CONFIRMATION_WORD + ", żeby wyzerować postępy.");
        }
        StudyProgressResponse removed = summaryOf(userId);

        customTestRepository.deleteAllByUser(userId);
        flashcardAdditionRepository.deleteAllByUser(userId);
        reviewLogRepository.deleteAllByUser(userId);
        stateRepository.deleteAllByUser(userId);
        examAttemptRepository.deleteAllByUser(userId);
        studyTimeRepository.deleteAllByUser(userId);

        log.info("Study progress of user {} reset at the user's request: {}", userId, removed);
        return removed;
    }

    private StudyProgressResponse summaryOf(Long userId) {
        return new StudyProgressResponse(
                stateRepository.countByUserIdAndMode(userId, ReviewMode.TEST),
                stateRepository.countByUserIdAndMode(userId, ReviewMode.FLASHCARD),
                reviewLogRepository.countByUserIdAndMode(userId, ReviewMode.TEST)
                        + reviewLogRepository.countByUserIdAndMode(userId, ReviewMode.FLASHCARD),
                flashcardAdditionRepository.countByUserId(userId),
                customTestRepository.countByUserId(userId),
                examAttemptRepository.countByUserId(userId),
                studyTimeRepository.sumAll(userId));
    }
}

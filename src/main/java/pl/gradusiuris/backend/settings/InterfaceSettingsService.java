package pl.gradusiuris.backend.settings;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.gradusiuris.backend.review.ReviewLogRepository;
import pl.gradusiuris.backend.review.ReviewMode;
import pl.gradusiuris.backend.settings.dto.InterfaceSettingsResponse;
import pl.gradusiuris.backend.settings.dto.UpdateInterfaceRequest;
import pl.gradusiuris.backend.user.CurrentUserResolver;
import pl.gradusiuris.backend.user.User;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Ustawienia interfejsu na koncie: stałe ukrycie podpowiedzi o skrótach klawiszowych (osobno testy i fiszki) i licznika
 * czasu przy pytaniu. Podpowiedzi znikają same po {@value #ANSWERS_CAP} odpowiedziach w danym trybie; liczymy je na
 * koncie (z dziennika odpowiedzi), więc nowe urządzenie nie przywraca podpowiedzi komuś, kto już się nauczył skrótów.
 */
@Service
@RequiredArgsConstructor
public class InterfaceSettingsService {

    /** Po tylu odpowiedziach w trybie podpowiedź skrótów znika sama. */
    public static final int ANSWERS_CAP = 10;

    private final UserSettingsRepository repository;
    private final ReviewLogRepository reviewLogs;
    private final CurrentUserResolver currentUserResolver;
    private final Clock clock;

    @Transactional(readOnly = true)
    public InterfaceSettingsResponse get(Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        return describe(user.getId(), repository.findById(user.getId()).orElse(null));
    }

    @Transactional
    public InterfaceSettingsResponse update(UpdateInterfaceRequest request, Authentication authentication) {
        User user = currentUserResolver.resolve(authentication);
        UserSettings settings = repository.findById(user.getId()).orElseGet(() -> new UserSettings(user.getId()));
        if (request.testHintHidden() != null) {
            settings.setHintHiddenTest(request.testHintHidden());
        }
        if (request.flashcardHintHidden() != null) {
            settings.setHintHiddenFlashcard(request.flashcardHintHidden());
        }
        if (request.questionTimerHidden() != null) {
            settings.setQuestionTimerHidden(request.questionTimerHidden());
        }
        settings.setUpdatedAt(LocalDateTime.now(clock));
        repository.save(settings);
        return describe(user.getId(), settings);
    }

    private InterfaceSettingsResponse describe(Long userId, UserSettings stored) {
        return new InterfaceSettingsResponse(
                stored != null && stored.isHintHiddenTest(),
                stored != null && stored.isHintHiddenFlashcard(),
                stored != null && stored.isQuestionTimerHidden(),
                answersUpToCap(userId, ReviewMode.TEST),
                answersUpToCap(userId, ReviewMode.FLASHCARD));
    }

    /** Liczba odpowiedzi w trybie, ale co najwyżej {@link #ANSWERS_CAP}: dalej nikt tego nie sprawdza, więc nie liczymy całej historii. */
    private int answersUpToCap(Long userId, ReviewMode mode) {
        return reviewLogs.findIdsByMode(userId, mode, PageRequest.of(0, ANSWERS_CAP)).size();
    }
}

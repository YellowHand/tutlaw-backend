package pl.gradusiuris.backend.studytime;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.gradusiuris.backend.user.CurrentUserResolver;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Czas nauki: ile czasu użytkownik spędził w aplikacji na nauce (testy, powtórki, własne testy, fiszki i egzamin, także
 * na czytaniu wyjaśnień). Liczy go przeglądarka: tylko gdy karta jest widoczna i użytkownik coś robi, a do serwera
 * wysyła porcje co około pół minuty. To osobna miara od czasu odpowiadania na pytanie.
 */
@Service
@RequiredArgsConstructor
public class StudyTimeService {

    /**
     * Największa pojedyncza porcja. Przeglądarka wysyła co ok. 30 s, a po przerwie w łączności dosyła zaległe, więc
     * zostawiamy zapas; większej porcji nie przyjmujemy (nie da się jednym żądaniem "nauczyć" całego dnia).
     */
    static final int MAX_REPORT_SECONDS = 300;

    /** Okno "ostatnie 7 dni": dziś i sześć poprzednich dni. */
    static final int RECENT_DAYS = 7;

    private final StudyTimeRepository repository;
    private final CurrentUserResolver currentUserResolver;
    private final Clock clock;

    public record Totals(long todaySeconds, long last7DaysSeconds, long totalSeconds) {
    }

    @Transactional
    public void add(int seconds, Authentication authentication) {
        if (seconds < 1 || seconds > MAX_REPORT_SECONDS) {
            throw new IllegalArgumentException("Czas nauki w jednym zgłoszeniu musi wynosić od 1 do " + MAX_REPORT_SECONDS + " s.");
        }
        Long userId = currentUserResolver.resolve(authentication).getId();
        repository.addSeconds(userId, LocalDate.now(clock), seconds);
    }

    @Transactional(readOnly = true)
    public Totals totals(Long userId) {
        LocalDate today = LocalDate.now(clock);
        return new Totals(
                repository.sumSince(userId, today),
                repository.sumSince(userId, today.minusDays(RECENT_DAYS - 1L)),
                repository.sumAll(userId));
    }
}

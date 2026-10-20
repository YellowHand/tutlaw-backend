package pl.tutlaw.backend.question;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tutlaw.backend.review.UserQuestionStateRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Zmiana w prawie wraca do nauki: pytanie, którego zmiana weszła w życie po ostatniej odpowiedzi użytkownika
 * ({@link Question#getLawChangedAt()}), staje się dla niego zaległe w obu trybach (testy i fiszki). Administrator
 * wpisuje daty skryptem SQL, więc przestawienie terminów robi {@link LawChangeJob} przy starcie aplikacji i raz na dobę
 * (zmiana z datą z przyszłości zaczyna działać w swoim dniu). Działanie jest idempotentne, bo dotyka tylko stanów
 * z terminem w przyszłości, a pytanie powtórzone po zmianie już nie wraca.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LawChangeService {

    private final UserQuestionStateRepository stateRepository;
    private final Clock clock;

    /** Przestawia terminy pytań ze zmianą w prawie. Zwraca liczbę przestawionych stanów nauki. */
    @Transactional
    public int requeueChangedQuestions() {
        LocalDateTime now = LocalDateTime.now(clock);
        int changed = stateRepository.requeueAfterLawChange(now, LocalDate.now(clock));
        if (changed > 0) {
            log.info("Law changes: {} learning states returned to the due queue", changed);
        }
        return changed;
    }
}

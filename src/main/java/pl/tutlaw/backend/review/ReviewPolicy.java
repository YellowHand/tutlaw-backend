package pl.tutlaw.backend.review;

import pl.tutlaw.backend.review.fsrs.Rating;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Reguły przełożenia odpowiedzi użytkownika na ocenę i termin kolejnej powtórki.
 * Same funkcje, bez bazy i bez Springa, więc łatwo je testować.
 */
public final class ReviewPolicy {

    private ReviewPolicy() {
    }

    /**
     * Ocena FSRS dla odpowiedzi w teście:
     * <ul>
     *   <li>poprawna i pewna: {@code GOOD} („Znam"). Test nigdy nie daje {@code EASY},
     *       bo przy trzech opcjach nie da się odróżnić „łatwego" pytania od szczęśliwego trafienia,</li>
     *   <li>niepewna (poprawna lub nie) albo błędna: {@code AGAIN} („Nie pamiętam"), pytanie wraca do powtórek.</li>
     * </ul>
     */
    public static Rating testRating(boolean correct, boolean guessed) {
        return correct && !guessed ? Rating.GOOD : Rating.AGAIN;
    }

    /**
     * Termin kolejnej powtórki.
     * <ul>
     *   <li>Odstęp krótszy niż doba (np. „Nie pamiętam" przy nowej karcie, ok. 5 godzin) liczy się od teraz.</li>
     *   <li>Odstęp od doby wzwyż zaokrąglamy do pełnych dni i ustawiamy na początek dnia. Dzięki temu karta
     *       „na jutro" jest do zrobienia od północy, a nie dopiero o tej godzinie, o której uczyłeś się dziś.</li>
     * </ul>
     */
    public static LocalDateTime computeDueDate(LocalDateTime now, double intervalDays) {
        if (intervalDays < 1.0) {
            return now.plusSeconds(Math.round(intervalDays * 86_400));
        }
        long days = Math.round(intervalDays);
        return now.toLocalDate().plusDays(days).atStartOfDay();
    }

    /** Odpowiedzi tej samej karty przychodzące szybciej niż po tym czasie uznajemy za przypadkowe podwójne wysłanie. */
    public static final long DUPLICATE_SUBMIT_WINDOW_MILLIS = 1_000;

    /**
     * Czy to ponowne wysłanie tej samej odpowiedzi (np. podwójne kliknięcie albo automatyczny ponowny strzał
     * po błędzie sieci). Bez tej ochrony druga kopia byłaby policzona jako powtórka "tego samego dnia"
     * i zniekształciłaby stabilność karty oraz statystyki.
     */
    public static boolean isDuplicateSubmit(LocalDateTime lastReviewAt, LocalDateTime now) {
        if (lastReviewAt == null) return false;
        long millis = Duration.between(lastReviewAt, now).toMillis();
        return millis >= 0 && millis < DUPLICATE_SUBMIT_WINDOW_MILLIS;
    }
}

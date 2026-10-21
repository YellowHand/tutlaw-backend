package pl.gradusiuris.backend.review;

import org.junit.jupiter.api.Test;
import pl.gradusiuris.backend.review.fsrs.Rating;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewPolicyTest {

    @Test
    void wrongAnswer_isAgain_evenWhenNotMarkedAsGuess() {
        assertThat(ReviewPolicy.testRating(false, false)).isEqualTo(Rating.AGAIN);
        assertThat(ReviewPolicy.testRating(false, true)).isEqualTo(Rating.AGAIN);
    }

    @Test
    void confidentCorrectAnswer_isGood_neverEasy() {
        assertThat(ReviewPolicy.testRating(true, false)).isEqualTo(Rating.GOOD);
    }

    @Test
    void unsureCorrectAnswer_isAgain_soTheQuestionJoinsTheReviews() {
        assertThat(ReviewPolicy.testRating(true, true)).isEqualTo(Rating.AGAIN);
    }

    @Test
    void shortInterval_isScheduledFromNow() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 12, 14, 30);

        LocalDateTime due = ReviewPolicy.computeDueDate(now, 0.212);

        assertThat(due).isAfter(now.plusHours(5)).isBefore(now.plusHours(6));
    }

    @Test
    void intervalOfOneOrMoreDays_isRoundedAndStartsAtMidnight() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 12, 22, 45);

        assertThat(ReviewPolicy.computeDueDate(now, 1.0)).isEqualTo(LocalDateTime.of(2026, 10, 13, 0, 0));
        assertThat(ReviewPolicy.computeDueDate(now, 1.4)).isEqualTo(LocalDateTime.of(2026, 10, 13, 0, 0));
        assertThat(ReviewPolicy.computeDueDate(now, 2.6)).isEqualTo(LocalDateTime.of(2026, 10, 15, 0, 0));
    }

    @Test
    void cardDueTomorrow_isAlreadyDueTomorrowMorning_notOnlyAtTheHourItWasStudied() {
        LocalDateTime studiedAt = LocalDateTime.of(2026, 10, 12, 22, 45);
        LocalDateTime due = ReviewPolicy.computeDueDate(studiedAt, 1.2);

        LocalDateTime nextMorning = LocalDateTime.of(2026, 10, 13, 8, 0);
        assertThat(due.isAfter(nextMorning)).isFalse();
    }

    @Test
    void secondSubmitWithinAFewHundredMilliseconds_isTreatedAsDuplicate() {
        LocalDateTime last = LocalDateTime.of(2026, 10, 12, 14, 30, 0);

        assertThat(ReviewPolicy.isDuplicateSubmit(last, last.plusNanos(300_000_000))).isTrue();
    }

    @Test
    void reviewAfterMoreThanTheWindow_isNotADuplicate() {
        LocalDateTime last = LocalDateTime.of(2026, 10, 12, 14, 30, 0);

        assertThat(ReviewPolicy.isDuplicateSubmit(last, last.plusSeconds(2))).isFalse();
        assertThat(ReviewPolicy.isDuplicateSubmit(last, last.plusHours(5))).isFalse();
    }

    @Test
    void neverReviewedCard_orClockGoingBackwards_isNotADuplicate() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 12, 14, 30, 0);

        assertThat(ReviewPolicy.isDuplicateSubmit(null, now)).isFalse();
        assertThat(ReviewPolicy.isDuplicateSubmit(now.plusSeconds(5), now)).isFalse();
    }
}

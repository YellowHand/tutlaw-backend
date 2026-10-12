package pl.tutlaw.backend.review;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import pl.tutlaw.backend.card.Card;
import pl.tutlaw.backend.card.CardRepository;
import pl.tutlaw.backend.card.CorrectOption;
import pl.tutlaw.backend.chapter.Chapter;
import pl.tutlaw.backend.review.dto.BacklogResponse;
import pl.tutlaw.backend.review.dto.ReviewQueueItemResponse;
import pl.tutlaw.backend.review.dto.TestQueueItemResponse;
import pl.tutlaw.backend.review.fsrs.FsrsEngine;
import pl.tutlaw.backend.review.fsrs.FsrsWeights;
import pl.tutlaw.backend.testsupport.FakeRepos;
import pl.tutlaw.backend.testsupport.MutableClock;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/** Układanie kolejki powtórek na prawdziwym ReviewQueueService, z repozytoriami "w pamięci". */
class ReviewQueueServiceTest {

    /** 2026-10-12 08:00 UTC to 10:00 w Warszawie. */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 10, 0);

    private static final class Fixture {
        final List<Card> cards = new ArrayList<>();
        final List<UserCardStateRepository.CardDue> schedule = new ArrayList<>();
        long introducedToday = 0;
        long dueReviewsDoneToday = 0;
        ReviewQueueService service;
        final Authentication jan = FakeRepos.authentication("jan@gmail.com");

        Fixture(int dailyLimit) {
            this(dailyLimit, 20);
        }

        Fixture(int dailyLimit, int newPerDay) {
            List<User> users = new ArrayList<>();
            User user = new User();
            user.setId(1L);
            user.setPrivateEmail("jan@gmail.com");
            users.add(user);

            CardRepository cardRepository = FakeRepos.proxy(CardRepository.class, (method, args) -> {
                if (method.startsWith("findReadableCardsForUser")) {
                    return new ArrayList<>(cards);
                }
                throw new UnsupportedOperationException("CardRepository." + method);
            });
            UserCardStateRepository states = FakeRepos.proxy(UserCardStateRepository.class, (method, args) -> {
                if (method.equals("findCardDueByUserId")) {
                    return new ArrayList<>(schedule);
                }
                throw new UnsupportedOperationException("UserCardStateRepository." + method);
            });
            ReviewLogRepository logs = FakeRepos.proxy(ReviewLogRepository.class, (method, args) -> {
                switch (method) {
                    case "countFirstReviewsBetween":
                        return introducedToday;
                    case "countDueReviewsBetween":
                        return dueReviewsDoneToday;
                    default:
                        throw new UnsupportedOperationException("ReviewLogRepository." + method);
                }
            });

            service = new ReviewQueueService(
                    cardRepository, states, logs, new CurrentUserResolver(FakeRepos.users(users)),
                    new FsrsEngine(FsrsWeights.defaults(), 0.9),
                    new MutableClock(Instant.parse("2026-10-12T08:00:00Z"), ZoneId.of("Europe/Warsaw")),
                    new Random(7), newPerDay, 40, dailyLimit);
        }

        Card card(long id, boolean test, int chapterOrder) {
            Card card = new Card();
            card.setId(id);
            card.setFront("P" + id);
            card.setBack("O" + id);
            if (test) {
                card.setOptionA("a");
                card.setOptionB("b");
                card.setOptionC("c");
                card.setCorrectOption(CorrectOption.A);
            }
            Chapter chapter = new Chapter();
            chapter.setOrderIndex(chapterOrder);
            card.setChapter(chapter);
            cards.add(card);
            return card;
        }

        /** Karta z historią: ostatnia powtórka {@code lastDaysAgo} dni temu, termin {@code dueDaysAgo} dni temu. */
        void scheduled(long cardId, double stability, int lastDaysAgo, int dueDaysAgo) {
            LocalDateTime last = NOW.minusDays(lastDaysAgo);
            LocalDateTime due = NOW.minusDays(dueDaysAgo);
            schedule.add(new UserCardStateRepository.CardDue() {
                public Long getCardId() { return cardId; }
                public LocalDateTime getDueDate() { return due; }
                public Double getStability() { return stability; }
                public LocalDateTime getLastReviewAt() { return last; }
            });
        }

        /**
         * Trzy zaległe karty. Szansa przypomnienia zależy od stosunku czasu do stabilności:
         * karta 3 (5 dni przy stabilności 1) jest najbardziej zapomniana, potem 1 (20 dni przy 10), potem 2 (3 dni przy 2).
         * Najstarszy termin ma karta 1, więc kolejność "po terminie" byłaby inna: 1, 3, 2.
         */
        void threeDueCards(boolean test) {
            card(1, test, 1);
            card(2, test, 1);
            card(3, test, 1);
            scheduled(1, 10, 20, 10);
            scheduled(2, 2, 3, 1);
            scheduled(3, 1, 5, 2);
        }

        List<Long> flashQueue() {
            return service.getQueue(null, null, null, null, null, jan).stream().map(ReviewQueueItemResponse::cardId).toList();
        }

        List<Long> testQueue() {
            return service.getTestQueue(null, null, null, null, null, jan).stream().map(TestQueueItemResponse::cardId).toList();
        }
    }

    @Test
    void dueCardsComeInOrderOfLowestRetrievabilityNotOldestDueDate() {
        Fixture f = new Fixture(100);
        f.threeDueCards(false);

        assertThat(f.flashQueue()).containsExactly(3L, 1L, 2L);
    }

    @Test
    void queueIsCappedAtTheDailyLimitAndKeepsTheMostUrgent() {
        Fixture f = new Fixture(2);
        f.threeDueCards(false);

        assertThat(f.flashQueue()).containsExactly(3L, 1L);

        BacklogResponse backlog = f.service.getBacklog(null, null, null, ReviewMode.FLASHCARD, f.jan);
        assertThat(backlog.dueNow()).isEqualTo(3L);
        assertThat(backlog.deferred()).isEqualTo(1L);
        assertThat(backlog.dailyLimit()).isEqualTo(2);
        assertThat(backlog.doneToday()).isEqualTo(0L);
    }

    @Test
    void reviewsAlreadyDoneTodayReduceTheAllowance() {
        Fixture f = new Fixture(2);
        f.threeDueCards(false);
        f.dueReviewsDoneToday = 1;

        assertThat(f.flashQueue()).containsExactly(3L);
        BacklogResponse backlog = f.service.getBacklog(null, null, null, ReviewMode.FLASHCARD, f.jan);
        assertThat(backlog.deferred()).isEqualTo(2L);
        assertThat(backlog.doneToday()).isEqualTo(1L);
    }

    @Test
    void whenTheDailyLimitIsUsedUpNoDueCardsAreOfferedButNewOnesStillAre() {
        Fixture f = new Fixture(2);
        f.threeDueCards(false);
        f.card(9, false, 1);
        f.dueReviewsDoneToday = 2;

        assertThat(f.flashQueue()).containsExactly(9L);
        assertThat(f.service.getBacklog(null, null, null, ReviewMode.FLASHCARD, f.jan).deferred()).isEqualTo(3L);
    }

    @Test
    void extraDueReviewsAddABatchBeyondTheLimit() {
        Fixture f = new Fixture(2);
        f.threeDueCards(false);
        f.dueReviewsDoneToday = 2;

        List<Long> extra = f.service.getQueue(null, null, null, null, 2, f.jan)
                .stream().map(ReviewQueueItemResponse::cardId).toList();

        assertThat(extra).containsExactly(3L, 1L);
    }

    @Test
    void negativeExtraDueReviewsMeansNone() {
        Fixture f = new Fixture(100);
        f.threeDueCards(false);

        assertThat(f.service.getQueue(null, null, null, null, -5, f.jan)).isEmpty();
    }

    @Test
    void cardsNotYetDueAreNotInTheQueue() {
        Fixture f = new Fixture(100);
        f.threeDueCards(false);
        f.card(4, false, 1);
        f.scheduled(4, 5, 1, -3); // termin za 3 dni

        assertThat(f.flashQueue()).containsExactly(3L, 1L, 2L);
        assertThat(f.service.getBacklog(null, null, null, ReviewMode.FLASHCARD, f.jan).dueNow()).isEqualTo(3L);
    }

    @Test
    void newCardsFollowTheDueOnesInChapterOrderUpToTheDailyLimit() {
        Fixture f = new Fixture(100, 2);
        f.threeDueCards(false);
        f.card(30, false, 3);
        f.card(10, false, 1);
        f.card(20, false, 2);

        assertThat(f.flashQueue()).containsExactly(3L, 1L, 2L, 10L, 20L);

        f.introducedToday = 1; // jedna nowa już dziś, zostaje jedno miejsce
        assertThat(f.flashQueue()).containsExactly(3L, 1L, 2L, 10L);
    }

    @Test
    void testQueueHoldsOnlyTestQuestionsAndTheSameUrgentSet() {
        Fixture f = new Fixture(2);
        f.threeDueCards(true);
        f.card(50, false, 1);           // zwykła fiszka, bez opcji ABC
        f.scheduled(50, 1, 30, 20);     // zaległa i bardzo zapomniana, ale to nie pytanie testowe

        List<Long> queue = f.testQueue();

        // Mieszanie zmienia kolejność, ale zestaw to dwa najpilniejsze pytania testowe.
        assertThat(queue).hasSize(2);
        assertThat(queue.containsAll(List.of(3L, 1L))).isTrue();
        assertThat(f.service.getBacklog(null, null, null, ReviewMode.TEST, f.jan).dueNow()).isEqualTo(3L);
        assertThat(f.service.getBacklog(null, null, null, ReviewMode.FLASHCARD, f.jan).dueNow()).isEqualTo(4L);
    }

    @Test
    void availableNewCardsCountsOnlyCardsWithoutHistory() {
        Fixture f = new Fixture(100);
        f.threeDueCards(false);
        f.card(8, false, 1);
        f.card(9, false, 1);

        assertThat(f.service.countAvailableNewCards(null, null, null, f.jan)).isEqualTo(2);
    }
}

package pl.tutlaw.backend.statistics;

import org.junit.jupiter.api.Test;
import pl.tutlaw.backend.card.Card;
import pl.tutlaw.backend.card.CardRepository;
import pl.tutlaw.backend.card.CorrectOption;
import pl.tutlaw.backend.review.ReviewLogRepository;
import pl.tutlaw.backend.review.ReviewMode;
import pl.tutlaw.backend.review.UserCardStateRepository;
import pl.tutlaw.backend.review.fsrs.FsrsEngine;
import pl.tutlaw.backend.review.fsrs.FsrsWeights;
import pl.tutlaw.backend.statistics.dto.OverviewResponse;
import pl.tutlaw.backend.testsupport.CardRefs;
import pl.tutlaw.backend.testsupport.FakeRepos;
import pl.tutlaw.backend.testsupport.MutableClock;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Przegląd nauki w trybach Fiszki / Testy / cały materiał, na prawdziwym StatisticsService z repozytoriami "w pamięci". */
class StatisticsOverviewModeTest {

    private static final class Fixture {
        final List<Card> cards = new ArrayList<>();
        final List<Long> confidentMistakes = new ArrayList<>();
        /** Flagi (withFlashcards, withTests) przekazane do zapytań o stany i powtórki. */
        final List<String> flagsSeen = new ArrayList<>();
        /** Które zapytanie o aktywność poszło: "all" albo nazwa trybu. */
        final List<String> activityLookups = new ArrayList<>();
        final StatisticsService service;
        final org.springframework.security.core.Authentication jan = FakeRepos.authentication("jan@gmail.com");

        Fixture() {
            User user = new User();
            user.setId(1L);
            user.setPrivateEmail("jan@gmail.com");
            CardRepository cardRepository = FakeRepos.proxy(CardRepository.class, (method, args) -> {
                if (method.startsWith("findReadableRefsForUser")) {
                    return CardRefs.of(cards);
                }
                throw new UnsupportedOperationException("CardRepository." + method);
            });
            UserCardStateRepository states = FakeRepos.proxy(UserCardStateRepository.class, (method, args) -> {
                switch (method) {
                    case "countStartedReadable":
                        flagsSeen.add(args[2] + "/" + args[3]);
                        return 0L;
                    case "findDueUntilAnySubject":
                        flagsSeen.add(args[2] + "/" + args[3]);
                        return List.of();
                    default:
                        throw new UnsupportedOperationException("UserCardStateRepository." + method);
                }
            });
            ReviewLogRepository logs = FakeRepos.proxy(ReviewLogRepository.class, (method, args) -> {
                switch (method) {
                    case "countRecalls":
                        flagsSeen.add(args[4] + "/" + args[5]);
                        return 0L;
                    case "findConfidentMistakeCardIds":
                        return new ArrayList<>(confidentMistakes);
                    case "findReviewTimesSince":
                        activityLookups.add("all");
                        return List.of();
                    case "findReviewTimesSinceByMode":
                        activityLookups.add(String.valueOf(args[1]));
                        return List.of();
                    default:
                        throw new UnsupportedOperationException("ReviewLogRepository." + method);
                }
            });
            List<User> users = new ArrayList<>(List.of(user));
            service = new StatisticsService(
                    cardRepository, null, null, states, logs, new CurrentUserResolver(FakeRepos.users(users)),
                    new FsrsEngine(FsrsWeights.defaults(), 0.9), MutableClock.at("2026-10-12T08:00:00Z"));
        }

        Card card(long id, boolean test, Long linkedTestCardId) {
            Card card = new Card();
            card.setId(id);
            if (test) {
                card.setCorrectOption(CorrectOption.A);
            }
            card.setLinkedTestCardId(linkedTestCardId);
            cards.add(card);
            return card;
        }

        Fixture withMaterial() {
            card(1, true, null);      // pytanie testowe
            card(2, true, null);      // pytanie testowe
            card(3, false, null);     // zwykła fiszka
            card(4, false, 1L);       // fiszka powiązana z pytaniem 1
            return this;
        }

        Fixture clearFlags() {
            flagsSeen.clear();
            return this;
        }

        String distinctFlags() {
            return flagsSeen.stream().distinct().collect(Collectors.joining(","));
        }
    }

    @Test
    void flashcardMode_countsOnlyUnlockedFlashcards() {
        Fixture f = new Fixture().withMaterial();

        OverviewResponse overview = f.service.getOverview(null, ReviewMode.FLASHCARD, f.jan);

        assertThat(overview.totalCards()).isEqualTo(1);       // zwykła fiszka; powiązana jest jeszcze ukryta
        assertThat(f.distinctFlags()).isEqualTo("true/false");
    }

    @Test
    void flashcardMode_includesTheLinkedFlashcardAfterAConfidentMistake() {
        Fixture f = new Fixture().withMaterial();
        f.confidentMistakes.add(1L);

        assertThat(f.service.getOverview(null, ReviewMode.FLASHCARD, f.jan).totalCards()).isEqualTo(2);
    }

    @Test
    void testMode_countsOnlyTestQuestions_andIgnoresLinks() {
        Fixture f = new Fixture().withMaterial();

        OverviewResponse overview = f.service.getOverview(null, ReviewMode.TEST, f.jan);

        assertThat(overview.totalCards()).isEqualTo(2);
        assertThat(f.distinctFlags()).isEqualTo("false/true");
    }

    @Test
    void noMode_coversTheWholeMaterial_withLockedFlashcardsStillHidden() {
        Fixture f = new Fixture().withMaterial();

        OverviewResponse overview = f.service.getOverview(null, null, f.jan);

        assertThat(overview.totalCards()).isEqualTo(3);       // 2 pytania testowe + zwykła fiszka
        assertThat(f.distinctFlags()).isEqualTo("true/true");
    }

    @Test
    void activity_withoutMode_countsEverything_andWithModeOnlyThatMode() {
        Fixture f = new Fixture();

        f.service.getActivity(28, null, f.jan);
        f.service.getActivity(28, ReviewMode.FLASHCARD, f.jan);
        f.service.getActivity(28, ReviewMode.TEST, f.jan);

        assertThat(f.activityLookups).containsExactly("all", "FLASHCARD", "TEST");
    }
}

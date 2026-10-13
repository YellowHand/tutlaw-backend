package pl.tutlaw.backend.statistics;

import org.junit.jupiter.api.Test;
import pl.tutlaw.backend.card.CardRepository;
import pl.tutlaw.backend.card.OriginYearCount;
import pl.tutlaw.backend.card.QuestionOrigin;
import pl.tutlaw.backend.review.ReviewLogRepository;
import pl.tutlaw.backend.review.UserCardStateRepository;
import pl.tutlaw.backend.statistics.dto.OriginStatsResponse;
import pl.tutlaw.backend.testsupport.FakeRepos;
import pl.tutlaw.backend.testsupport.MutableClock;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Serwis statystyk według pochodzenia: do zapytań trafiają właściwe parametry i wynik jest złożony z ich odpowiedzi. */
class OriginStatisticsServiceTest {

    private static OriginYearCount count(QuestionOrigin origin, Integer year, long total) {
        return new OriginYearCount() {
            @Override public QuestionOrigin getOrigin() { return origin; }
            @Override public Integer getExamYear() { return year; }
            @Override public Long getTotal() { return total; }
        };
    }

    private static final class Fixture {
        final List<String> calls = new ArrayList<>();
        final List<Object[]> arguments = new ArrayList<>();
        final OriginStatisticsService service;
        final MutableClock clock = MutableClock.at("2026-10-12T08:00:00Z");
        final org.springframework.security.core.Authentication jan = FakeRepos.authentication("jan@gmail.com");

        Fixture() {
            User user = new User();
            user.setId(7L);
            user.setPrivateEmail("jan@gmail.com");

            CardRepository cards = FakeRepos.proxy(CardRepository.class, (method, args) -> {
                if (method.equals("countReadableTestQuestionsByOriginYear")) {
                    calls.add(method);
                    arguments.add(args);
                    return List.of(count(QuestionOrigin.NOTARY, 2021, 12L));
                }
                throw new UnsupportedOperationException("CardRepository." + method);
            });
            ReviewLogRepository logs = FakeRepos.proxy(ReviewLogRepository.class, (method, args) -> {
                switch (method) {
                    case "countSolvedTestQuestionsByOriginYear":
                        calls.add(method);
                        arguments.add(args);
                        return List.of(count(QuestionOrigin.NOTARY, 2021, 5L));
                    case "countTestAnswersByOriginYear":
                        calls.add(method);
                        arguments.add(args);
                        return List.of();
                    default:
                        throw new UnsupportedOperationException("ReviewLogRepository." + method);
                }
            });
            UserCardStateRepository states = FakeRepos.proxy(UserCardStateRepository.class, (method, args) -> {
                if (method.equals("countTestStatesByOriginYear")) {
                    calls.add(method);
                    arguments.add(args);
                    return List.of();
                }
                throw new UnsupportedOperationException("UserCardStateRepository." + method);
            });
            List<User> users = new ArrayList<>(List.of(user));
            service = new OriginStatisticsService(cards, logs, states, new CurrentUserResolver(FakeRepos.users(users)),
                    clock);
        }
    }

    @Test
    void usesCurrentUser_andBuildsResultFromQueryRows() {
        Fixture f = new Fixture();

        OriginStatsResponse response = f.service.getOriginStats(f.jan);

        assertThat(f.calls).contains("countReadableTestQuestionsByOriginYear", "countSolvedTestQuestionsByOriginYear",
                "countTestStatesByOriginYear", "countTestAnswersByOriginYear");
        assertThat(f.arguments).allSatisfy(args -> assertThat(args[0]).isEqualTo(7L));
        var notary = response.origins().stream().filter(e -> e.origin() == QuestionOrigin.NOTARY).findFirst().orElseThrow();
        assertThat(notary.stats().totalQuestions()).isEqualTo(12);
        assertThat(notary.stats().solvedQuestions()).isEqualTo(5);
        assertThat(notary.years()).hasSize(1);
        assertThat(notary.years().get(0).year()).isEqualTo(2021);
    }

    @Test
    void answersAreAskedTwice_forAllTimeAndForTheLast30Days() {
        Fixture f = new Fixture();

        f.service.getOriginStats(f.jan);

        List<LocalDateTime> since = new ArrayList<>();
        for (int i = 0; i < f.calls.size(); i++) {
            if (f.calls.get(i).equals("countTestAnswersByOriginYear")) {
                since.add((LocalDateTime) f.arguments.get(i)[1]);
            }
        }
        assertThat(since).hasSize(2);
        LocalDateTime now = LocalDateTime.now(f.clock);
        LocalDateTime allTime = since.get(0);
        LocalDateTime recent = since.get(1);
        assertThat(allTime.getYear()).isLessThan(2005);
        assertThat(recent).isEqualTo(now.minusDays(30));
    }

    @Test
    void stateQueryGetsNowAndTheSameMatureThresholdAsOverview() {
        Fixture f = new Fixture();

        f.service.getOriginStats(f.jan);

        Object[] args = f.arguments.get(f.calls.indexOf("countTestStatesByOriginYear"));
        assertThat(args[2]).isEqualTo(21.0);
        assertThat(OriginStatisticsService.MATURE_STABILITY_THRESHOLD).isEqualTo(21.0);
        assertThat(args[1]).isInstanceOf(LocalDateTime.class);
    }
}

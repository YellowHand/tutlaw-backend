package pl.gradusiuris.backend.studytime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import pl.gradusiuris.backend.testsupport.FakeRepos;
import pl.gradusiuris.backend.testsupport.MutableClock;
import pl.gradusiuris.backend.user.CurrentUserResolver;
import pl.gradusiuris.backend.user.User;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StudyTimeServiceTest {

    // 2026-10-12 12:00 czasu warszawskiego
    private final MutableClock clock = MutableClock.at("2026-10-12T10:00:00Z");
    private final Authentication authentication = mock(Authentication.class);
    private final CurrentUserResolver resolver = mock(CurrentUserResolver.class);
    /** Kolejne wywołania addSeconds: [użytkownik, dzień, sekundy]. */
    private final List<Object[]> added = new ArrayList<>();
    private StudyTimeService service;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(5L);
        when(resolver.resolve(authentication)).thenReturn(user);
        StudyTimeRepository repository = FakeRepos.proxy(StudyTimeRepository.class, (method, args) -> {
            switch (method) {
                case "addSeconds":
                    added.add(args);
                    return 1;
                case "sumSince":
                    return added.stream()
                            .filter(a -> a[0].equals(args[0]) && !((LocalDate) a[1]).isBefore((LocalDate) args[1]))
                            .mapToLong(a -> (int) a[2]).sum();
                case "sumAll":
                    return added.stream().filter(a -> a[0].equals(args[0])).mapToLong(a -> (int) a[2]).sum();
                default:
                    throw new UnsupportedOperationException("StudyTimeRepository." + method);
            }
        });
        service = new StudyTimeService(repository, resolver, clock);
    }

    @Test
    void addsSecondsToTodayForTheCaller() {
        service.add(30, authentication);

        assertThat(added).hasSize(1);
        assertThat(added.get(0)[0]).isEqualTo(5L);
        assertThat(added.get(0)[1]).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(added.get(0)[2]).isEqualTo(30);
    }

    @Test
    void rejectsZeroNegativeAndOversizedReports() {
        for (int seconds : new int[]{0, -5, 301, 100_000}) {
            assertThatThrownBy(() -> service.add(seconds, authentication))
                    .as(seconds + " s")
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(added).isEmpty();
        service.add(300, authentication);
        service.add(1, authentication);
        assertThat(added).hasSize(2);
    }

    @Test
    void totalsSplitTodayTheLastSevenDaysAndEverything() {
        service.add(60, authentication);                  // 12 paź (dziś)
        clock.advance(Duration.ofDays(-3));
        service.add(120, authentication);                 // 9 paź
        clock.advance(Duration.ofDays(-4));
        service.add(300, authentication);                 // 5 paź: poza ostatnimi 7 dniami (6-12 paź)
        clock.advance(Duration.ofDays(7));                // znowu 12 paź

        StudyTimeService.Totals totals = service.totals(5L);

        assertThat(totals.todaySeconds()).isEqualTo(60);
        assertThat(totals.last7DaysSeconds()).isEqualTo(180);
        assertThat(totals.totalSeconds()).isEqualTo(480);
    }

    @Test
    void sixDaysAgoIsStillInTheLastSevenDaysButSevenDaysAgoIsNot() {
        clock.advance(Duration.ofDays(-6));
        service.add(10, authentication);                  // 6 paź
        clock.advance(Duration.ofDays(-1));
        service.add(20, authentication);                  // 5 paź
        clock.advance(Duration.ofDays(7));

        assertThat(service.totals(5L).last7DaysSeconds()).isEqualTo(10);
    }

    @Test
    void totalsForAUserWithoutTimeAreZero() {
        assertThat(service.totals(99L)).isEqualTo(new StudyTimeService.Totals(0, 0, 0));
    }
}

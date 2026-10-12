package pl.tutlaw.backend.report;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import pl.tutlaw.backend.card.Card;
import pl.tutlaw.backend.card.CardRepository;
import pl.tutlaw.backend.common.exception.ResourceNotFoundException;
import pl.tutlaw.backend.common.exception.TooManyRequestsException;
import pl.tutlaw.backend.common.exception.UnauthorizedException;
import pl.tutlaw.backend.report.dto.CardReportRequest;
import pl.tutlaw.backend.report.dto.CardReportResponse;
import pl.tutlaw.backend.testsupport.FakeRepos;
import pl.tutlaw.backend.testsupport.MutableClock;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Zgłaszanie błędów w pytaniach na prawdziwym CardReportService, z repozytoriami "w pamięci". */
class CardReportServiceTest {

    private static final class Fixture {
        final MutableClock clock = new MutableClock(Instant.parse("2026-10-12T08:00:00Z"), ZoneId.of("Europe/Warsaw"));
        final List<CardReport> reports = new ArrayList<>();
        final Map<Long, Card> cards = new HashMap<>();
        /** Pytania, które użytkownik ma odblokowane (pula płaska albo przeczytany rozdział). */
        final Set<Long> readable = new HashSet<>();
        final CardReportService service;
        final Authentication jan = FakeRepos.authentication("jan@gmail.com");

        Fixture() {
            List<User> users = new ArrayList<>();
            User user = new User();
            user.setId(1L);
            user.setPrivateEmail("jan@gmail.com");
            users.add(user);

            AtomicLong ids = new AtomicLong(1);
            CardReportRepository reportRepository = FakeRepos.proxy(CardReportRepository.class, (method, args) -> {
                switch (method) {
                    case "save": {
                        CardReport r = (CardReport) args[0];
                        if (r.getId() == null) {
                            r.setId(ids.getAndIncrement());
                        }
                        if (!reports.contains(r)) {
                            reports.add(r);
                        }
                        return r;
                    }
                    case "findByUserIdAndCardIdAndStatus":
                        return reports.stream()
                                .filter(r -> r.getUser().getId().equals(args[0])
                                        && r.getCard().getId().equals(args[1])
                                        && r.getStatus() == args[2])
                                .findFirst();
                    case "countByUserIdAndCreatedAtAfter":
                        return reports.stream()
                                .filter(r -> r.getUser().getId().equals(args[0])
                                        && r.getCreatedAt().isAfter((LocalDateTime) args[1]))
                                .count();
                    default:
                        throw new UnsupportedOperationException("CardReportRepository." + method);
                }
            });

            CardRepository cardRepository = FakeRepos.proxy(CardRepository.class, (method, args) -> {
                switch (method) {
                    case "findById":
                        return Optional.ofNullable(cards.get(args[0]));
                    case "countReadableById":
                        return readable.contains(args[0]) ? 1L : 0L;
                    default:
                        throw new UnsupportedOperationException("CardRepository." + method);
                }
            });

            service = new CardReportService(
                    new CurrentUserResolver(FakeRepos.users(users)), cardRepository, reportRepository, clock);
        }

        Card card(long id, boolean readableByUser) {
            Card card = new Card();
            card.setId(id);
            card.setFront("Pytanie " + id);
            cards.put(id, card);
            if (readableByUser) {
                readable.add(id);
            }
            return card;
        }
    }

    private static CardReportRequest request(CardReportReason reason, String comment) {
        return new CardReportRequest(reason, comment);
    }

    @Test
    void savesANewReportWithTrimmedCommentAndStatusNew() {
        Fixture f = new Fixture();
        f.card(10, true);

        CardReportResponse response = f.service.report(10L, request(CardReportReason.OUTDATED, "  Zmiana od 1 stycznia  "), f.jan);

        assertThat(response.updated()).isFalse();
        assertThat(f.reports).hasSize(1);
        CardReport saved = f.reports.get(0);
        assertThat(saved.getStatus()).isEqualTo(CardReportStatus.NEW);
        assertThat(saved.getReason()).isEqualTo(CardReportReason.OUTDATED);
        assertThat(saved.getComment()).isEqualTo("Zmiana od 1 stycznia");
        assertThat(saved.getUser().getId()).isEqualTo(1L);
        assertThat(saved.getCard().getId()).isEqualTo(10L);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void emptyCommentIsStoredAsNull() {
        Fixture f = new Fixture();
        f.card(10, true);

        f.service.report(10L, request(CardReportReason.WRONG_ANSWER, "   "), f.jan);

        assertThat(f.reports.get(0).getComment()).isNull();
    }

    @Test
    void secondReportOfTheSameQuestionUpdatesTheOpenOneInsteadOfAddingAnother() {
        Fixture f = new Fixture();
        f.card(10, true);
        f.service.report(10L, request(CardReportReason.UNCLEAR, "literówka"), f.jan);

        CardReportResponse second = f.service.report(10L, request(CardReportReason.WRONG_ANSWER, "to jednak błąd"), f.jan);

        assertThat(second.updated()).isTrue();
        assertThat(f.reports).hasSize(1);
        assertThat(f.reports.get(0).getReason()).isEqualTo(CardReportReason.WRONG_ANSWER);
        assertThat(f.reports.get(0).getComment()).isEqualTo("to jednak błąd");
    }

    @Test
    void afterTheOwnerResolvedAReportTheUserMayReportTheQuestionAgain() {
        Fixture f = new Fixture();
        f.card(10, true);
        f.service.report(10L, request(CardReportReason.UNCLEAR, "x"), f.jan);
        f.reports.get(0).setStatus(CardReportStatus.RESOLVED);

        CardReportResponse again = f.service.report(10L, request(CardReportReason.UNCLEAR, "nadal niejasne"), f.jan);

        assertThat(again.updated()).isFalse();
        assertThat(f.reports).hasSize(2);
    }

    @Test
    void lockedOrMissingQuestionLooksTheSame() {
        Fixture f = new Fixture();
        f.card(10, false);

        assertThatThrownBy(() -> f.service.report(10L, request(CardReportReason.WRONG_ANSWER, null), f.jan))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> f.service.report(999L, request(CardReportReason.WRONG_ANSWER, null), f.jan))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(f.reports).isEmpty();
    }

    @Test
    void badInputIsRejectedBeforeAnythingIsSaved() {
        Fixture f = new Fixture();
        f.card(10, true);

        assertThatThrownBy(() -> f.service.report(10L, request(null, "x"), f.jan))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> f.service.report(10L, request(CardReportReason.OTHER, " "), f.jan))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Opisz");
        assertThatThrownBy(() -> f.service.report(10L, request(CardReportReason.OTHER, "a".repeat(1001)), f.jan))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1000");
        assertThat(f.reports).isEmpty();
    }

    @Test
    void requiresALoggedInExistingUser() {
        Fixture f = new Fixture();
        f.card(10, true);

        assertThatThrownBy(() -> f.service.report(10L, request(CardReportReason.WRONG_ANSWER, null), null))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> f.service.report(
                10L, request(CardReportReason.WRONG_ANSWER, null), FakeRepos.authentication("nikt@gmail.com")))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void dailyLimitStopsFloodingButStillAllowsCompletingAnExistingReport() {
        Fixture f = new Fixture();
        for (int i = 1; i <= ReportRules.DAILY_LIMIT + 1; i++) {
            f.card(i, true);
        }
        for (int i = 1; i <= ReportRules.DAILY_LIMIT; i++) {
            f.service.report((long) i, request(CardReportReason.WRONG_ANSWER, null), f.jan);
        }

        long extra = ReportRules.DAILY_LIMIT + 1;
        assertThatThrownBy(() -> f.service.report(extra, request(CardReportReason.WRONG_ANSWER, null), f.jan))
                .isInstanceOf(TooManyRequestsException.class);
        // Uzupełnienie już wysłanego zgłoszenia nie jest "nowym" i przechodzi.
        CardReportResponse completed = f.service.report(1L, request(CardReportReason.WRONG_ANSWER, "dopisek"), f.jan);
        assertThat(completed.updated()).isTrue();

        // Po dobie limit się zwalnia.
        f.clock.advance(Duration.ofHours(25));
        CardReportResponse later = f.service.report(extra, request(CardReportReason.WRONG_ANSWER, null), f.jan);
        assertThat(later.updated()).isFalse();
    }
}

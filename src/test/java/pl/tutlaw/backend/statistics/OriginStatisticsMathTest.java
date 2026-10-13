package pl.tutlaw.backend.statistics;

import org.junit.jupiter.api.Test;
import pl.tutlaw.backend.card.OriginYearCount;
import pl.tutlaw.backend.card.QuestionOrigin;
import pl.tutlaw.backend.review.ReviewLogRepository.OriginYearAnswers;
import pl.tutlaw.backend.review.UserCardStateRepository.OriginYearStates;
import pl.tutlaw.backend.statistics.dto.OriginStatsResponse;
import pl.tutlaw.backend.statistics.dto.OriginStatsResponse.Metrics;
import pl.tutlaw.backend.statistics.dto.OriginStatsResponse.OriginEntry;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Składanie statystyk według pochodzenia i roku z wyników zapytań. */
class OriginStatisticsMathTest {

    private static OriginYearCount count(QuestionOrigin origin, Integer year, Long total) {
        return new OriginYearCount() {
            @Override public QuestionOrigin getOrigin() { return origin; }
            @Override public Integer getExamYear() { return year; }
            @Override public Long getTotal() { return total; }
        };
    }

    private static OriginYearStates states(QuestionOrigin origin, Integer year, Long mature, Long due) {
        return new OriginYearStates() {
            @Override public QuestionOrigin getOrigin() { return origin; }
            @Override public Integer getExamYear() { return year; }
            @Override public Long getMature() { return mature; }
            @Override public Long getDue() { return due; }
        };
    }

    private static OriginYearAnswers answers(QuestionOrigin origin, Integer year, Long all, Long correct, Long confident) {
        return new OriginYearAnswers() {
            @Override public QuestionOrigin getOrigin() { return origin; }
            @Override public Integer getExamYear() { return year; }
            @Override public Long getAnswers() { return all; }
            @Override public Long getCorrect() { return correct; }
            @Override public Long getConfidentMistakes() { return confident; }
        };
    }

    private static OriginStatsResponse assemble(
            List<OriginYearCount> totals, List<OriginYearCount> solved, List<OriginYearStates> states,
            List<OriginYearAnswers> all, List<OriginYearAnswers> recent) {
        return OriginStatisticsMath.assemble(totals, solved, states, all, recent);
    }

    private static OriginEntry entry(OriginStatsResponse response, QuestionOrigin origin) {
        return response.origins().stream().filter(e -> e.origin() == origin).findFirst().orElseThrow();
    }

    @Test
    void emptyData_returnsAllSixOriginsInEnumOrder_withZerosAndNoAccuracy() {
        OriginStatsResponse response = assemble(List.of(), List.of(), List.of(), List.of(), List.of());

        assertThat(response.origins()).extracting(OriginEntry::origin).containsExactly(QuestionOrigin.values());
        for (OriginEntry entry : response.origins()) {
            assertThat(entry.years()).isEmpty();
            Metrics m = entry.stats();
            assertThat(m.totalQuestions()).isZero();
            assertThat(m.solvedQuestions()).isZero();
            assertThat(m.answers()).isZero();
            assertThat(m.accuracyRate()).isNull();
            assertThat(m.accuracyLast30Days()).isNull();
        }
    }

    @Test
    void originTotals_sumTheYears_andAccuracyIsComputedFromSums() {
        // 2021: 1 z 1 poprawna (100%), 2022: 1 z 3 (33%). Razem 2 z 4 = 50%, a nie średnia z procentów (67%).
        OriginStatsResponse response = assemble(
                List.of(count(QuestionOrigin.NOTARY, 2021, 10L), count(QuestionOrigin.NOTARY, 2022, 6L)),
                List.of(count(QuestionOrigin.NOTARY, 2021, 4L), count(QuestionOrigin.NOTARY, 2022, 2L)),
                List.of(states(QuestionOrigin.NOTARY, 2021, 3L, 1L), states(QuestionOrigin.NOTARY, 2022, 0L, 2L)),
                List.of(answers(QuestionOrigin.NOTARY, 2021, 1L, 1L, 0L), answers(QuestionOrigin.NOTARY, 2022, 3L, 1L, 2L)),
                List.of(answers(QuestionOrigin.NOTARY, 2021, 1L, 1L, 0L), answers(QuestionOrigin.NOTARY, 2022, 1L, 0L, 1L)));

        Metrics notary = entry(response, QuestionOrigin.NOTARY).stats();
        assertThat(notary.totalQuestions()).isEqualTo(16);
        assertThat(notary.solvedQuestions()).isEqualTo(6);
        assertThat(notary.matureQuestions()).isEqualTo(3);
        assertThat(notary.dueQuestions()).isEqualTo(3);
        assertThat(notary.answers()).isEqualTo(4);
        assertThat(notary.correctAnswers()).isEqualTo(2);
        assertThat(notary.accuracyRate()).isCloseTo(50.0, within(1e-9));
        assertThat(notary.answersLast30Days()).isEqualTo(2);
        assertThat(notary.accuracyLast30Days()).isCloseTo(50.0, within(1e-9));
        assertThat(notary.confidentMistakesLast30Days()).isEqualTo(1);
    }

    @Test
    void years_areListedNewestFirst_eachWithItsOwnNumbers() {
        OriginStatsResponse response = assemble(
                List.of(count(QuestionOrigin.BAILIFF, 2019, 3L), count(QuestionOrigin.BAILIFF, 2023, 5L),
                        count(QuestionOrigin.BAILIFF, 2021, 4L)),
                List.of(count(QuestionOrigin.BAILIFF, 2023, 2L)),
                List.of(), List.of(answers(QuestionOrigin.BAILIFF, 2023, 4L, 3L, 0L)), List.of());

        OriginEntry bailiff = entry(response, QuestionOrigin.BAILIFF);
        assertThat(bailiff.years()).extracting(y -> y.year()).containsExactly(2023, 2021, 2019);
        assertThat(bailiff.years().get(0).stats().totalQuestions()).isEqualTo(5);
        assertThat(bailiff.years().get(0).stats().solvedQuestions()).isEqualTo(2);
        assertThat(bailiff.years().get(0).stats().accuracyRate()).isCloseTo(75.0, within(1e-9));
        assertThat(bailiff.years().get(1).stats().totalQuestions()).isEqualTo(4);
        assertThat(bailiff.years().get(1).stats().solvedQuestions()).isZero();
        assertThat(bailiff.years().get(1).stats().accuracyRate()).isNull();
    }

    @Test
    void authoredQuestions_haveNoYears_butAreCounted() {
        OriginStatsResponse response = assemble(
                List.of(count(QuestionOrigin.AUTHORED, null, 123L)),
                List.of(count(QuestionOrigin.AUTHORED, null, 10L)),
                List.of(states(QuestionOrigin.AUTHORED, null, 2L, 4L)),
                List.of(answers(QuestionOrigin.AUTHORED, null, 12L, 9L, 1L)),
                List.of(answers(QuestionOrigin.AUTHORED, null, 5L, 4L, 1L)));

        OriginEntry authored = entry(response, QuestionOrigin.AUTHORED);
        assertThat(authored.years()).isEmpty();
        assertThat(authored.stats().totalQuestions()).isEqualTo(123);
        assertThat(authored.stats().solvedQuestions()).isEqualTo(10);
        assertThat(authored.stats().matureQuestions()).isEqualTo(2);
        assertThat(authored.stats().dueQuestions()).isEqualTo(4);
        assertThat(authored.stats().accuracyRate()).isCloseTo(75.0, within(1e-9));
        assertThat(authored.stats().accuracyLast30Days()).isCloseTo(80.0, within(1e-9));
    }

    @Test
    void rowsOfGroupsWithoutAvailableQuestions_areIgnored() {
        // Odpowiedzi i stany z roku, w którym nie ma dostępnych pytań (np. wszystkie ukryte), nie mogą się liczyć.
        OriginStatsResponse response = assemble(
                List.of(count(QuestionOrigin.JUDGE_PROSECUTOR, 2022, 8L)),
                List.of(count(QuestionOrigin.JUDGE_PROSECUTOR, 2020, 5L)),
                List.of(states(QuestionOrigin.JUDGE_PROSECUTOR, 2020, 5L, 5L)),
                List.of(answers(QuestionOrigin.JUDGE_PROSECUTOR, 2020, 50L, 40L, 3L)),
                List.of(answers(QuestionOrigin.JUDGE_PROSECUTOR, 2020, 7L, 6L, 1L)));

        OriginEntry entry = entry(response, QuestionOrigin.JUDGE_PROSECUTOR);
        assertThat(entry.years()).extracting(y -> y.year()).containsExactly(2022);
        assertThat(entry.stats().totalQuestions()).isEqualTo(8);
        assertThat(entry.stats().solvedQuestions()).isZero();
        assertThat(entry.stats().matureQuestions()).isZero();
        assertThat(entry.stats().answers()).isZero();
        assertThat(entry.stats().confidentMistakesLast30Days()).isZero();
    }

    @Test
    void recentWindow_isSeparateFromAllTime() {
        OriginStatsResponse response = assemble(
                List.of(count(QuestionOrigin.SUPPLEMENTARY_JUDGE, 2018, 20L)), List.of(), List.of(),
                List.of(answers(QuestionOrigin.SUPPLEMENTARY_JUDGE, 2018, 10L, 7L, 2L)),
                List.of(answers(QuestionOrigin.SUPPLEMENTARY_JUDGE, 2018, 4L, 1L, 2L)));

        Metrics m = entry(response, QuestionOrigin.SUPPLEMENTARY_JUDGE).stats();
        assertThat(m.answers()).isEqualTo(10);
        assertThat(m.correctAnswers()).isEqualTo(7);
        assertThat(m.accuracyRate()).isCloseTo(70.0, within(1e-9));
        assertThat(m.answersLast30Days()).isEqualTo(4);
        assertThat(m.accuracyLast30Days()).isCloseTo(25.0, within(1e-9));
        assertThat(m.confidentMistakesLast30Days()).isEqualTo(2);
    }

    @Test
    void noRecentAnswers_meansNoDataNotZeroPercent() {
        OriginStatsResponse response = assemble(
                List.of(count(QuestionOrigin.ADVOCATE_LEGAL_ADVISER, 2024, 9L)), List.of(), List.of(),
                List.of(answers(QuestionOrigin.ADVOCATE_LEGAL_ADVISER, 2024, 3L, 0L, 3L)), List.of());

        Metrics m = entry(response, QuestionOrigin.ADVOCATE_LEGAL_ADVISER).stats();
        assertThat(m.accuracyRate()).isCloseTo(0.0, within(1e-9));
        assertThat(m.accuracyLast30Days()).isNull();
    }

    @Test
    void nullCounts_areTreatedAsZero() {
        OriginStatsResponse response = assemble(
                List.of(count(QuestionOrigin.NOTARY, 2020, 4L)), List.of(count(QuestionOrigin.NOTARY, 2020, null)),
                List.of(states(QuestionOrigin.NOTARY, 2020, null, null)),
                List.of(answers(QuestionOrigin.NOTARY, 2020, null, null, null)),
                List.of(answers(QuestionOrigin.NOTARY, 2020, 2L, null, null)));

        Metrics m = entry(response, QuestionOrigin.NOTARY).stats();
        assertThat(m.solvedQuestions()).isZero();
        assertThat(m.matureQuestions()).isZero();
        assertThat(m.answers()).isZero();
        assertThat(m.accuracyLast30Days()).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void originsAreIndependent() {
        List<OriginYearCount> totals = new ArrayList<>();
        for (QuestionOrigin origin : QuestionOrigin.values()) {
            totals.add(count(origin, origin.requiresYear() ? 2022 : null, 10L + origin.ordinal()));
        }
        OriginStatsResponse response = assemble(totals, List.of(), List.of(), List.of(), List.of());

        for (QuestionOrigin origin : QuestionOrigin.values()) {
            assertThat(entry(response, origin).stats().totalQuestions()).isEqualTo(10 + origin.ordinal());
        }
    }
}

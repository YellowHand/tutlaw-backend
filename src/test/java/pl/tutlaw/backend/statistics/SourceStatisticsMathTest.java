package pl.tutlaw.backend.statistics;

import org.junit.jupiter.api.Test;
import pl.tutlaw.backend.pool.SourceFilter;
import pl.tutlaw.backend.review.ReviewLogRepository.SourceAnswers;
import pl.tutlaw.backend.review.UserQuestionStateRepository.SourceStates;
import pl.tutlaw.backend.source.SourceKind;
import pl.tutlaw.backend.statistics.SourceStatisticsMath.SourceInfo;
import pl.tutlaw.backend.statistics.dto.SourceStatsResponse;
import pl.tutlaw.backend.statistics.dto.SourceStatsResponse.GroupEntry;
import pl.tutlaw.backend.statistics.dto.SourceStatsResponse.Metrics;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Składanie statystyk według źródła (arkusza / pytań autorskich) z wyników zapytań. */
class SourceStatisticsMathTest {

    private static final long NOT_2021 = 1L;
    private static final long NOT_2022 = 2L;
    private static final long KOM_2020 = 3L;
    private static final long KOM_2023 = 4L;
    private static final long AUTORSKIE = 5L;

    private static final List<SourceInfo> SOURCES = List.of(
            new SourceInfo(NOT_2021, "Egzamin notarialny 2021", SourceKind.EXAM_SHEET, 2021),
            new SourceInfo(NOT_2022, "Egzamin notarialny 2022", SourceKind.EXAM_SHEET, 2022),
            new SourceInfo(KOM_2020, "Egzamin komorniczy 2020", SourceKind.EXAM_SHEET, 2020),
            new SourceInfo(KOM_2023, "Egzamin komorniczy 2023", SourceKind.EXAM_SHEET, 2023),
            new SourceInfo(AUTORSKIE, "Pytania autorskie", SourceKind.AUTHORED, null));

    /** Użytkownik przygotowuje się do notarialnej: jej arkusze to „własne". */
    private static final Set<Long> OWN = Set.of(NOT_2021, NOT_2022);

    private static SourceStates states(long sourceId, Long mature, Long due) {
        return new SourceStates() {
            public Long getSourceId() { return sourceId; }
            public Long getMature() { return mature; }
            public Long getDue() { return due; }
        };
    }

    private static SourceAnswers answers(long sourceId, Long all, Long correct, Long confident) {
        return new SourceAnswers() {
            public Long getSourceId() { return sourceId; }
            public Long getAnswers() { return all; }
            public Long getCorrect() { return correct; }
            public Long getConfidentMistakes() { return confident; }
        };
    }

    private static SourceStatsResponse assemble(
            Map<Long, Long> totals, Map<Long, Long> solved, List<SourceStates> states,
            List<SourceAnswers> all, List<SourceAnswers> recent) {
        return SourceStatisticsMath.assemble(SOURCES, OWN, totals, solved, states, all, recent);
    }

    private static GroupEntry group(SourceStatsResponse response, String code) {
        return response.groups().stream().filter(g -> g.group().equals(code)).findFirst().orElseThrow();
    }

    @Test
    void emptyData_returnsAllThreeGroupsInFixedOrder_withZerosAndNoAccuracy() {
        SourceStatsResponse response = assemble(Map.of(), Map.of(), List.of(), List.of(), List.of());

        assertThat(response.groups()).extracting(GroupEntry::group).containsExactly(
                SourceFilter.OWN_SHEETS, SourceFilter.OTHER_SHEETS, SourceFilter.AUTHORED);
        for (GroupEntry group : response.groups()) {
            assertThat(group.sources()).isEmpty();
            Metrics m = group.stats();
            assertThat(m.totalQuestions()).isZero();
            assertThat(m.solvedQuestions()).isZero();
            assertThat(m.answers()).isZero();
            assertThat(m.accuracyRate()).isNull();
            assertThat(m.accuracyLast30Days()).isNull();
        }
    }

    @Test
    void sourcesAreSortedIntoGroups_ownSheetsOtherSheetsAndAuthored() {
        SourceStatsResponse response = assemble(
                Map.of(NOT_2021, 10L, NOT_2022, 6L, KOM_2020, 5L, KOM_2023, 7L, AUTORSKIE, 3L),
                Map.of(), List.of(), List.of(), List.of());

        assertThat(group(response, SourceFilter.OWN_SHEETS).sources()).extracting(e -> e.id())
                .containsExactly(NOT_2022, NOT_2021);
        assertThat(group(response, SourceFilter.OTHER_SHEETS).sources()).extracting(e -> e.id())
                .containsExactly(KOM_2023, KOM_2020);
        assertThat(group(response, SourceFilter.AUTHORED).sources()).extracting(e -> e.id())
                .containsExactly(AUTORSKIE);
    }

    @Test
    void groupTotals_sumTheSources_andAccuracyIsComputedFromSums() {
        // 2021: 1 z 1 poprawna (100%), 2022: 1 z 3 (33%). Razem 2 z 4 = 50%, a nie średnia z procentów (67%).
        SourceStatsResponse response = assemble(
                Map.of(NOT_2021, 10L, NOT_2022, 6L),
                Map.of(NOT_2021, 4L, NOT_2022, 2L),
                List.of(states(NOT_2021, 3L, 1L), states(NOT_2022, 0L, 2L)),
                List.of(answers(NOT_2021, 1L, 1L, 0L), answers(NOT_2022, 3L, 1L, 2L)),
                List.of(answers(NOT_2021, 1L, 1L, 0L), answers(NOT_2022, 1L, 0L, 1L)));

        Metrics own = group(response, SourceFilter.OWN_SHEETS).stats();
        assertThat(own.totalQuestions()).isEqualTo(16);
        assertThat(own.solvedQuestions()).isEqualTo(6);
        assertThat(own.matureQuestions()).isEqualTo(3);
        assertThat(own.dueQuestions()).isEqualTo(3);
        assertThat(own.answers()).isEqualTo(4);
        assertThat(own.correctAnswers()).isEqualTo(2);
        assertThat(own.accuracyRate()).isCloseTo(50.0, within(1e-9));
        assertThat(own.answersLast30Days()).isEqualTo(2);
        assertThat(own.accuracyLast30Days()).isCloseTo(50.0, within(1e-9));
        assertThat(own.confidentMistakesLast30Days()).isEqualTo(1);
    }

    @Test
    void sheets_areListedNewestYearFirst_eachWithItsOwnNumbers() {
        SourceStatsResponse response = assemble(
                Map.of(KOM_2020, 3L, KOM_2023, 5L), Map.of(KOM_2023, 2L),
                List.of(), List.of(answers(KOM_2023, 4L, 3L, 0L)), List.of());

        List<SourceStatsResponse.SourceEntry> other = group(response, SourceFilter.OTHER_SHEETS).sources();
        assertThat(other).extracting(e -> e.examYear()).containsExactly(2023, 2020);
        assertThat(other.get(0).stats().totalQuestions()).isEqualTo(5);
        assertThat(other.get(0).stats().solvedQuestions()).isEqualTo(2);
        assertThat(other.get(0).stats().accuracyRate()).isCloseTo(75.0, within(1e-9));
        assertThat(other.get(1).stats().solvedQuestions()).isZero();
        assertThat(other.get(1).stats().accuracyRate()).isNull();
    }

    @Test
    void authoredQuestions_haveNoYear_butAreCounted() {
        SourceStatsResponse response = assemble(
                Map.of(AUTORSKIE, 123L), Map.of(AUTORSKIE, 10L),
                List.of(states(AUTORSKIE, 2L, 4L)),
                List.of(answers(AUTORSKIE, 12L, 9L, 1L)),
                List.of(answers(AUTORSKIE, 5L, 4L, 1L)));

        GroupEntry authored = group(response, SourceFilter.AUTHORED);
        assertThat(authored.sources().get(0).examYear()).isNull();
        assertThat(authored.stats().totalQuestions()).isEqualTo(123);
        assertThat(authored.stats().solvedQuestions()).isEqualTo(10);
        assertThat(authored.stats().matureQuestions()).isEqualTo(2);
        assertThat(authored.stats().dueQuestions()).isEqualTo(4);
        assertThat(authored.stats().accuracyRate()).isCloseTo(75.0, within(1e-9));
        assertThat(authored.stats().accuracyLast30Days()).isCloseTo(80.0, within(1e-9));
    }

    @Test
    void rowsOfSourcesWithoutAvailableQuestions_areIgnored() {
        // Odpowiedzi i stany z arkusza, w którym nie ma dostępnych pytań (np. wszystkie ukryte), nie mogą się liczyć.
        SourceStatsResponse response = assemble(
                Map.of(NOT_2022, 8L), Map.of(NOT_2021, 5L),
                List.of(states(NOT_2021, 5L, 5L)),
                List.of(answers(NOT_2021, 50L, 40L, 3L)),
                List.of(answers(NOT_2021, 7L, 6L, 1L)));

        GroupEntry own = group(response, SourceFilter.OWN_SHEETS);
        assertThat(own.sources()).extracting(e -> e.id()).containsExactly(NOT_2022);
        assertThat(own.stats().totalQuestions()).isEqualTo(8);
        assertThat(own.stats().solvedQuestions()).isZero();
        assertThat(own.stats().matureQuestions()).isZero();
        assertThat(own.stats().answers()).isZero();
        assertThat(own.stats().confidentMistakesLast30Days()).isZero();
    }

    @Test
    void recentWindow_isSeparateFromAllTime() {
        SourceStatsResponse response = assemble(
                Map.of(KOM_2020, 20L), Map.of(), List.of(),
                List.of(answers(KOM_2020, 10L, 7L, 2L)),
                List.of(answers(KOM_2020, 4L, 1L, 2L)));

        Metrics m = group(response, SourceFilter.OTHER_SHEETS).stats();
        assertThat(m.answers()).isEqualTo(10);
        assertThat(m.correctAnswers()).isEqualTo(7);
        assertThat(m.accuracyRate()).isCloseTo(70.0, within(1e-9));
        assertThat(m.answersLast30Days()).isEqualTo(4);
        assertThat(m.accuracyLast30Days()).isCloseTo(25.0, within(1e-9));
        assertThat(m.confidentMistakesLast30Days()).isEqualTo(2);
    }

    @Test
    void noRecentAnswers_meansNoDataNotZeroPercent() {
        SourceStatsResponse response = assemble(
                Map.of(NOT_2022, 9L), Map.of(), List.of(),
                List.of(answers(NOT_2022, 3L, 0L, 3L)), List.of());

        Metrics m = group(response, SourceFilter.OWN_SHEETS).stats();
        assertThat(m.accuracyRate()).isCloseTo(0.0, within(1e-9));
        assertThat(m.accuracyLast30Days()).isNull();
    }

    @Test
    void nullCounts_areTreatedAsZero() {
        java.util.Map<Long, Long> solved = new java.util.HashMap<>();
        solved.put(NOT_2022, null);
        SourceStatsResponse response = assemble(
                Map.of(NOT_2022, 4L), solved,
                List.of(states(NOT_2022, null, null)),
                List.of(answers(NOT_2022, null, null, null)),
                List.of(answers(NOT_2022, 2L, null, null)));

        Metrics m = group(response, SourceFilter.OWN_SHEETS).stats();
        assertThat(m.solvedQuestions()).isZero();
        assertThat(m.matureQuestions()).isZero();
        assertThat(m.answers()).isZero();
        assertThat(m.accuracyLast30Days()).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void whenNothingIsOwn_everySheetCountsAsAnotherTracksSheet() {
        // Student nie ma własnych arkuszy: wszystkie są „z innych aplikacji"
        SourceStatsResponse response = SourceStatisticsMath.assemble(
                SOURCES, Set.of(), Map.of(NOT_2021, 1L, KOM_2020, 1L, AUTORSKIE, 1L),
                Map.of(), List.of(), List.of(), List.of());

        assertThat(group(response, SourceFilter.OWN_SHEETS).sources()).isEmpty();
        assertThat(group(response, SourceFilter.OTHER_SHEETS).sources()).hasSize(2);
        assertThat(group(response, SourceFilter.AUTHORED).sources()).hasSize(1);
    }
}

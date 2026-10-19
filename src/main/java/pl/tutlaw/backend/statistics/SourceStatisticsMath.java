package pl.tutlaw.backend.statistics;

import pl.tutlaw.backend.pool.SourceFilter;
import pl.tutlaw.backend.pool.SourceGroups;
import pl.tutlaw.backend.review.ReviewLogRepository.SourceAnswers;
import pl.tutlaw.backend.review.UserQuestionStateRepository.SourceStates;
import pl.tutlaw.backend.source.SourceKind;
import pl.tutlaw.backend.statistics.dto.SourceStatsResponse;
import pl.tutlaw.backend.statistics.dto.SourceStatsResponse.GroupEntry;
import pl.tutlaw.backend.statistics.dto.SourceStatsResponse.Metrics;
import pl.tutlaw.backend.statistics.dto.SourceStatsResponse.SourceEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Składanie statystyk według źródła z wyników zapytań (bez Springa i bazy, więc da się to przetestować samym JUnit-em).
 * Zbiór źródeł w grupie wyznacza liczba dostępnych pytań: wiersze innych zapytań dla źródła, w którym nie ma żadnego
 * dostępnego pytania, są pomijane, żeby liczby w jednym wierszu zawsze pochodziły z tego samego zbioru.
 */
final class SourceStatisticsMath {

    private SourceStatisticsMath() {
    }

    /** Opis źródła potrzebny do ułożenia statystyk. */
    record SourceInfo(Long id, String name, SourceKind kind, Integer examYear) {
    }

    /** Sumy dla jednego źródła albo, po zsumowaniu, dla całej grupy. */
    private static final class Totals {
        long total;
        long solved;
        long mature;
        long due;
        long answers;
        long correct;
        long answersRecent;
        long correctRecent;
        long confidentRecent;

        void add(Totals other) {
            total += other.total;
            solved += other.solved;
            mature += other.mature;
            due += other.due;
            answers += other.answers;
            correct += other.correct;
            answersRecent += other.answersRecent;
            correctRecent += other.correctRecent;
            confidentRecent += other.confidentRecent;
        }

        Metrics toMetrics() {
            return new Metrics(
                    (int) total, (int) solved, (int) mature, (int) due,
                    answers, correct, StatisticsMath.percentage(correct, answers),
                    answersRecent, StatisticsMath.percentage(correctRecent, answersRecent), confidentRecent);
        }
    }

    static SourceStatsResponse assemble(
            List<SourceInfo> sources,
            Set<Long> ownSheetIds,
            Map<Long, Long> totals,
            Map<Long, Long> solved,
            List<SourceStates> states,
            List<SourceAnswers> allAnswers,
            List<SourceAnswers> recentAnswers) {

        Map<Long, Totals> bySource = new HashMap<>();
        for (SourceInfo source : sources) {
            long total = totals.getOrDefault(source.id(), 0L);
            if (total > 0) {
                bySource.computeIfAbsent(source.id(), k -> new Totals()).total = total;
            }
        }
        solved.forEach((id, count) -> {
            Totals t = bySource.get(id);
            if (t != null) t.solved += orZero(count);
        });
        for (SourceStates row : states) {
            Totals t = bySource.get(row.getSourceId());
            if (t != null) {
                t.mature += orZero(row.getMature());
                t.due += orZero(row.getDue());
            }
        }
        for (SourceAnswers row : allAnswers) {
            Totals t = bySource.get(row.getSourceId());
            if (t != null) {
                t.answers += orZero(row.getAnswers());
                t.correct += orZero(row.getCorrect());
            }
        }
        for (SourceAnswers row : recentAnswers) {
            Totals t = bySource.get(row.getSourceId());
            if (t != null) {
                t.answersRecent += orZero(row.getAnswers());
                t.correctRecent += orZero(row.getCorrect());
                t.confidentRecent += orZero(row.getConfidentMistakes());
            }
        }

        Map<String, Totals> byGroup = new LinkedHashMap<>();
        Map<String, List<SourceEntry>> entries = new LinkedHashMap<>();
        for (String group : List.of(SourceFilter.OWN_SHEETS, SourceFilter.OTHER_SHEETS, SourceFilter.AUTHORED)) {
            byGroup.put(group, new Totals());
            entries.put(group, new ArrayList<>());
        }
        sources.stream()
                .sorted(Comparator
                        .comparing((SourceInfo s) -> s.examYear() == null ? 0 : -s.examYear())
                        .thenComparing(SourceInfo::name)
                        .thenComparing(SourceInfo::id))
                .forEach(source -> {
                    Totals totalsOfSource = bySource.get(source.id());
                    if (totalsOfSource == null) {
                        return;
                    }
                    String group = SourceGroups.of(source.kind(), source.id(), ownSheetIds);
                    byGroup.get(group).add(totalsOfSource);
                    entries.get(group).add(new SourceEntry(
                            source.id(), source.name(), source.kind(), source.examYear(), totalsOfSource.toMetrics()));
                });

        List<GroupEntry> groups = new ArrayList<>();
        byGroup.forEach((group, sum) -> groups.add(new GroupEntry(group, sum.toMetrics(), List.copyOf(entries.get(group)))));
        return new SourceStatsResponse(groups);
    }

    private static long orZero(Long value) {
        return value == null ? 0 : value;
    }
}

package pl.tutlaw.backend.statistics;

import pl.tutlaw.backend.card.OriginYearCount;
import pl.tutlaw.backend.card.QuestionOrigin;
import pl.tutlaw.backend.review.ReviewLogRepository.OriginYearAnswers;
import pl.tutlaw.backend.review.UserCardStateRepository.OriginYearStates;
import pl.tutlaw.backend.statistics.dto.OriginStatsResponse;
import pl.tutlaw.backend.statistics.dto.OriginStatsResponse.Metrics;
import pl.tutlaw.backend.statistics.dto.OriginStatsResponse.OriginEntry;
import pl.tutlaw.backend.statistics.dto.OriginStatsResponse.YearEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Składanie statystyk według pochodzenia i roku z wyników zapytań (bez Springa i bazy, więc da się to przetestować
 * samym JUnit-em). Zbiór grup wyznacza liczba dostępnych pytań: wiersze innych zapytań dla grupy, w której
 * nie ma żadnego dostępnego pytania, są pomijane, żeby liczby w jednym wierszu zawsze pochodziły z tego samego zbioru.
 */
final class OriginStatisticsMath {

    private OriginStatisticsMath() {
    }

    private record Key(QuestionOrigin origin, Integer year) {
    }

    /** Sumy dla jednej grupy (pochodzenie + rok) albo, po zsumowaniu, dla całego pochodzenia. */
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

    static OriginStatsResponse assemble(
            List<OriginYearCount> totals,
            List<OriginYearCount> solved,
            List<OriginYearStates> states,
            List<OriginYearAnswers> allAnswers,
            List<OriginYearAnswers> recentAnswers) {

        Map<Key, Totals> groups = new LinkedHashMap<>();
        for (OriginYearCount row : totals) {
            groups.computeIfAbsent(new Key(row.getOrigin(), row.getExamYear()), k -> new Totals()).total
                    += orZero(row.getTotal());
        }
        for (OriginYearCount row : solved) {
            Totals t = groups.get(new Key(row.getOrigin(), row.getExamYear()));
            if (t != null) t.solved += orZero(row.getTotal());
        }
        for (OriginYearStates row : states) {
            Totals t = groups.get(new Key(row.getOrigin(), row.getExamYear()));
            if (t != null) {
                t.mature += orZero(row.getMature());
                t.due += orZero(row.getDue());
            }
        }
        for (OriginYearAnswers row : allAnswers) {
            Totals t = groups.get(new Key(row.getOrigin(), row.getExamYear()));
            if (t != null) {
                t.answers += orZero(row.getAnswers());
                t.correct += orZero(row.getCorrect());
            }
        }
        for (OriginYearAnswers row : recentAnswers) {
            Totals t = groups.get(new Key(row.getOrigin(), row.getExamYear()));
            if (t != null) {
                t.answersRecent += orZero(row.getAnswers());
                t.correctRecent += orZero(row.getCorrect());
                t.confidentRecent += orZero(row.getConfidentMistakes());
            }
        }

        Map<QuestionOrigin, Totals> byOrigin = new EnumMap<>(QuestionOrigin.class);
        Map<QuestionOrigin, List<Map.Entry<Integer, Totals>>> yearsByOrigin = new EnumMap<>(QuestionOrigin.class);
        for (Map.Entry<Key, Totals> entry : groups.entrySet()) {
            QuestionOrigin origin = entry.getKey().origin();
            byOrigin.computeIfAbsent(origin, o -> new Totals()).add(entry.getValue());
            if (entry.getKey().year() != null) {
                yearsByOrigin.computeIfAbsent(origin, o -> new ArrayList<>())
                        .add(Map.entry(entry.getKey().year(), entry.getValue()));
            }
        }

        List<OriginEntry> origins = new ArrayList<>();
        for (QuestionOrigin origin : QuestionOrigin.values()) {
            Totals overall = byOrigin.getOrDefault(origin, new Totals());
            List<YearEntry> years = yearsByOrigin.getOrDefault(origin, List.of()).stream()
                    .sorted(Map.Entry.<Integer, Totals>comparingByKey(Comparator.reverseOrder()))
                    .map(e -> new YearEntry(e.getKey(), e.getValue().toMetrics()))
                    .toList();
            origins.add(new OriginEntry(origin, overall.toMetrics(), years));
        }
        return new OriginStatsResponse(origins);
    }

    private static long orZero(Long value) {
        return value == null ? 0 : value;
    }
}

package pl.gradusiuris.backend.admin;

import pl.gradusiuris.backend.common.GroupCount;
import pl.gradusiuris.backend.question.QuestionRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Drobne narzędzia składania odpowiedzi panelu: liczniki z zapytań grupujących zamieniane na mapy. */
final class AdminMaps {

    private AdminMaps() {
    }

    static Map<Long, Long> counts(List<GroupCount> rows) {
        Map<Long, Long> result = new HashMap<>();
        for (GroupCount row : rows) {
            result.put(row.getGroupId(), row.getTotal());
        }
        return result;
    }

    static Map<Long, QuestionRepository.GroupTotals> totals(List<QuestionRepository.GroupTotals> rows) {
        Map<Long, QuestionRepository.GroupTotals> result = new HashMap<>();
        for (QuestionRepository.GroupTotals row : rows) {
            result.put(row.getGroupId(), row);
        }
        return result;
    }

    static long total(Map<Long, QuestionRepository.GroupTotals> totals, Long id) {
        QuestionRepository.GroupTotals row = totals.get(id);
        return row == null || row.getTotal() == null ? 0 : row.getTotal();
    }

    static long hidden(Map<Long, QuestionRepository.GroupTotals> totals, Long id) {
        QuestionRepository.GroupTotals row = totals.get(id);
        return row == null || row.getHiddenTotal() == null ? 0 : row.getHiddenTotal();
    }
}

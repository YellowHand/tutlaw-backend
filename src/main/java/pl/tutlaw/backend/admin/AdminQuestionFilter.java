package pl.tutlaw.backend.admin;

import pl.tutlaw.backend.mark.MarkStatus;
import pl.tutlaw.backend.mark.QuestionMarkType;

/**
 * Filtry listy pytań w panelu. Pusty filtr (null albo false) to brak ograniczenia. {@code mark} zawęża do pytań z
 * oznaczeniem w stanie {@code markStatus} (domyślnie zatwierdzonym).
 */
record AdminQuestionFilter(
        String query,
        Long actId,
        Long sectionId,
        Long sourceId,
        Boolean hidden,
        boolean withoutSection,
        boolean reported,
        QuestionMarkType mark,
        MarkStatus markStatus
) {
}

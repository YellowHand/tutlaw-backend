package pl.tutlaw.backend.report;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportRulesTest {

    @Test
    void reasonIsRequired() {
        assertThat(ReportRules.problem(null, "coś")).isNotNull();
    }

    @Test
    void wrongAnswerNeedsNoComment() {
        assertThat(ReportRules.problem(CardReportReason.WRONG_ANSWER, null)).isNull();
        assertThat(ReportRules.problem(CardReportReason.OUTDATED, "   ")).isNull();
        assertThat(ReportRules.problem(CardReportReason.UNCLEAR, "")).isNull();
    }

    @Test
    void otherRequiresADescription() {
        assertThat(ReportRules.problem(CardReportReason.OTHER, null)).isNotNull();
        assertThat(ReportRules.problem(CardReportReason.OTHER, "   ")).isNotNull();
        assertThat(ReportRules.problem(CardReportReason.OTHER, "Brakuje przecinka")).isNull();
    }

    @Test
    void commentIsLimitedTo1000Characters() {
        assertThat(ReportRules.problem(CardReportReason.OTHER, "a".repeat(1000))).isNull();
        assertThat(ReportRules.problem(CardReportReason.OTHER, "a".repeat(1001))).isNotNull();
        assertThat(ReportRules.problem(CardReportReason.OTHER, "  " + "a".repeat(1000) + "  ")).isNull();
    }

    @Test
    void normalizeCommentTrimsAndTurnsBlankIntoNull() {
        assertThat(ReportRules.normalizeComment("  tekst  ")).isEqualTo("tekst");
        assertThat(ReportRules.normalizeComment("   ")).isNull();
        assertThat(ReportRules.normalizeComment(null)).isNull();
    }
}

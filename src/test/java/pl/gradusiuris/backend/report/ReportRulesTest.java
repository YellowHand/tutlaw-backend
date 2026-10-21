package pl.gradusiuris.backend.report;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportRulesTest {

    @Test
    void reasonIsRequired() {
        assertThat(ReportRules.problem(null, "coś")).isNotNull();
    }

    @Test
    void wrongAnswerNeedsNoComment() {
        assertThat(ReportRules.problem(QuestionReportReason.WRONG_ANSWER, null)).isNull();
        assertThat(ReportRules.problem(QuestionReportReason.OUTDATED, "   ")).isNull();
        assertThat(ReportRules.problem(QuestionReportReason.UNCLEAR, "")).isNull();
    }

    @Test
    void otherRequiresADescription() {
        assertThat(ReportRules.problem(QuestionReportReason.OTHER, null)).isNotNull();
        assertThat(ReportRules.problem(QuestionReportReason.OTHER, "   ")).isNotNull();
        assertThat(ReportRules.problem(QuestionReportReason.OTHER, "Brakuje przecinka")).isNull();
    }

    @Test
    void commentIsLimitedTo1000Characters() {
        assertThat(ReportRules.problem(QuestionReportReason.OTHER, "a".repeat(1000))).isNull();
        assertThat(ReportRules.problem(QuestionReportReason.OTHER, "a".repeat(1001))).isNotNull();
        assertThat(ReportRules.problem(QuestionReportReason.OTHER, "  " + "a".repeat(1000) + "  ")).isNull();
    }

    @Test
    void normalizeCommentTrimsAndTurnsBlankIntoNull() {
        assertThat(ReportRules.normalizeComment("  tekst  ")).isEqualTo("tekst");
        assertThat(ReportRules.normalizeComment("   ")).isNull();
        assertThat(ReportRules.normalizeComment(null)).isNull();
    }
}

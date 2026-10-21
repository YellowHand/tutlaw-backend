package pl.tutlaw.backend.admin;

import org.junit.jupiter.api.Test;
import pl.tutlaw.backend.question.CorrectOption;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Reguły treści pytania (ręczne dodawanie i import). */
class QuestionRulesTest {

    private static QuestionDraft ok() {
        return QuestionRules.draft("  Treść?  ", " A ", "B", "C", CorrectOption.B, "  ", " art. 1 ", null, null, null);
    }

    @Test
    void draft_trimsTextAndTurnsEmptyOptionalFieldsIntoNull() {
        QuestionDraft draft = ok();

        assertThat(draft.body()).isEqualTo("Treść?");
        assertThat(draft.optionA()).isEqualTo("A");
        assertThat(draft.explanation()).isNull();
        assertThat(draft.legalBasis()).isEqualTo("art. 1");
        assertThat(draft.correctOption()).isEqualTo(CorrectOption.B);
    }

    @Test
    void draft_requiresTheBody_theThreeOptions_andTheCorrectOne() {
        assertThatThrownBy(() -> QuestionRules.draft(" ", "a", "b", "c", CorrectOption.A, null, null, null, null, null))
                .hasMessageContaining("treść");
        assertThatThrownBy(() -> QuestionRules.draft("P", null, "b", "c", CorrectOption.A, null, null, null, null, null))
                .hasMessageContaining("odpowiedź A");
        assertThatThrownBy(() -> QuestionRules.draft("P", "a", "", "c", CorrectOption.A, null, null, null, null, null))
                .hasMessageContaining("odpowiedź B");
        assertThatThrownBy(() -> QuestionRules.draft("P", "a", "b", "c", null, null, null, null, null, null))
                .hasMessageContaining("poprawną");
    }

    @Test
    void draft_refusesTwoIdenticalOptions_ignoringCaseAndSpacing() {
        assertThatThrownBy(() -> QuestionRules.draft("P", "Tak  samo", "tak samo", "inaczej", CorrectOption.A, null, null, null, null, null))
                .hasMessageContaining("muszą się różnić");
    }

    @Test
    void draft_limitsTheLegalBasisAndTheLawChangeNote_andTheNoteNeedsADate() {
        assertThatThrownBy(() -> QuestionRules.draft("P", "a", "b", "c", CorrectOption.A, null, "x".repeat(501), null, null, null))
                .hasMessageContaining("500");
        assertThatThrownBy(() -> QuestionRules.draft("P", "a", "b", "c", CorrectOption.A, null, null, null, LocalDate.now(), "y".repeat(501)))
                .hasMessageContaining("500");
        assertThatThrownBy(() -> QuestionRules.draft("P", "a", "b", "c", CorrectOption.A, null, null, null, null, "nowelizacja"))
                .hasMessageContaining("wymaga daty");
        QuestionDraft withNote = QuestionRules.draft("P", "a", "b", "c", CorrectOption.A, null, "x".repeat(500), null, LocalDate.of(2026, 1, 1), "nowelizacja");
        assertThat(withNote.lawChangeNote()).isEqualTo("nowelizacja");
    }

    @Test
    void duplicateKey_ignoresSpacingAndCase_butNotWords() {
        assertThat(QuestionRules.duplicateKey("  Kto  jest\nwłaścicielem? ")).isEqualTo(QuestionRules.duplicateKey("kto jest właścicielem?"));
        assertThat(QuestionRules.duplicateKey("Kto jest właścicielem?")).isNotEqualTo(QuestionRules.duplicateKey("Kto jest posiadaczem?"));
    }
}

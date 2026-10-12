package pl.tutlaw.backend.card;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CardContentRulesTest {

    private static List<String> validateTest(String a, String b, String c, CorrectOption correct) {
        return CardContentRules.validate(false, false, "Pytanie?", "Odpowiedź", null, a, b, c, correct);
    }

    @Test
    void plainFlashcard_isValid() {
        List<String> problems = CardContentRules.validate(
                false, false, "Czym jest posiadanie?", "Władztwo nad rzeczą", 12, null, null, null, null);

        assertThat(problems).isEmpty();
    }

    @Test
    void blankFrontOrBack_isRejected() {
        List<String> problems = CardContentRules.validate(false, false, "  ", "", null, null, null, null, null);

        assertThat(problems).hasSize(2);
    }

    @Test
    void negativePageNumber_isRejected() {
        List<String> problems = CardContentRules.validate(false, false, "P", "O", -1, null, null, null, null);

        assertThat(problems).hasSize(1);
    }

    @Test
    void textbookCardWithoutChapter_isRejected_becauseItWouldNeverBeShown() {
        List<String> problems = CardContentRules.validate(true, false, "P", "O", null, null, null, null, null);

        assertThat(problems).hasSize(1);
        assertThat(problems.get(0)).contains("rozdziału");
    }

    @Test
    void textbookCardWithChapter_isValid() {
        assertThat(CardContentRules.validate(true, true, "P", "O", null, null, null, null, null)).isEmpty();
    }

    @Test
    void completeTestQuestion_isValid() {
        assertThat(validateTest("jedna", "dwie", "trzy", CorrectOption.B)).isEmpty();
    }

    @Test
    void testQuestionWithoutCorrectOption_isRejected() {
        List<String> problems = validateTest("jedna", "dwie", "trzy", null);

        assertThat(problems).hasSize(1);
        assertThat(problems.get(0)).contains("poprawnej");
    }

    @Test
    void correctOptionWithoutAllThreeAnswers_isRejected() {
        List<String> problems = validateTest("jedna", "dwie", " ", CorrectOption.A);

        assertThat(problems).hasSize(1);
        assertThat(problems.get(0)).contains("trzech");
    }

    @Test
    void onlyOneOptionFilledIn_isRejectedAsHalfFinishedTestQuestion() {
        assertThat(validateTest("jedna", null, null, null)).isNotEmpty();
    }

    @Test
    void identicalAnswers_areRejected_evenWhenTheyDifferOnlyByCaseAndSpaces() {
        List<String> problems = validateTest("Dwa lata", "dwa   lata.", "trzy lata", CorrectOption.A);

        assertThat(problems).hasSize(1);
        assertThat(problems.get(0)).contains("różnić");
    }

    @Test
    void normalize_ignoresCaseSpacesAndTrailingPunctuation() {
        assertThat(CardContentRules.normalize("  Art. 5   K.C.;  ")).isEqualTo("art. 5 k.c");
        assertThat(CardContentRules.normalize(null)).isEqualTo("");
    }

    @Test
    void duplicateKey_isTheSameForCosmeticDifferences() {
        String k1 = CardContentRules.duplicateKey("Czym jest posiadanie?", "Władztwo nad rzeczą.", null, null, null, null);
        String k2 = CardContentRules.duplicateKey("  czym  jest posiadanie? ", "władztwo nad rzeczą", null, null, null, null);

        assertThat(k1).isEqualTo(k2);
    }

    @Test
    void duplicateKey_differsWhenTheAnswerDiffers_forFlashcards() {
        String k1 = CardContentRules.duplicateKey("Termin", "7 dni", null, null, null, null);
        String k2 = CardContentRules.duplicateKey("Termin", "14 dni", null, null, null, null);

        assertThat(k1).isNotEqualTo(k2);
    }

    @Test
    void duplicateKey_forTestQuestions_ignoresBackButUsesOptionsAndCorrectOption() {
        String base = CardContentRules.duplicateKey("Termin?", "opis 1", "7 dni", "14 dni", "30 dni", CorrectOption.A);
        String otherBack = CardContentRules.duplicateKey("Termin?", "opis 2", "7 dni", "14 dni", "30 dni", CorrectOption.A);
        String otherCorrect = CardContentRules.duplicateKey("Termin?", "opis 1", "7 dni", "14 dni", "30 dni", CorrectOption.B);
        String otherOptions = CardContentRules.duplicateKey("Termin?", "opis 1", "7 dni", "14 dni", "60 dni", CorrectOption.A);

        assertThat(otherBack).isEqualTo(base);
        assertThat(otherCorrect).isNotEqualTo(base);
        assertThat(otherOptions).isNotEqualTo(base);
    }

    @Test
    void flashcardAndTestWithTheSameFront_areNotDuplicates() {
        String flashcard = CardContentRules.duplicateKey("Termin?", "7 dni", null, null, null, null);
        String test = CardContentRules.duplicateKey("Termin?", "7 dni", "7 dni", "14 dni", "30 dni", CorrectOption.A);

        assertThat(flashcard).isNotEqualTo(test);
    }

    @Test
    void blankToNull_trimsAndTurnsBlankIntoNull() {
        assertThat(CardContentRules.blankToNull("  tekst ")).isEqualTo("tekst");
        assertThat(CardContentRules.blankToNull("   ")).isNull();
        assertThat(CardContentRules.blankToNull(null)).isNull();
    }

    @Test
    void legalBasis_isOptionalAndLimitedTo500Characters() {
        assertThat(CardContentRules.legalBasisProblem(null)).isNull();
        assertThat(CardContentRules.legalBasisProblem("   ")).isNull();
        assertThat(CardContentRules.legalBasisProblem("art. 415 k.c.")).isNull();
        assertThat(CardContentRules.legalBasisProblem("a".repeat(500))).isNull();
        assertThat(CardContentRules.legalBasisProblem("a".repeat(501))).isNotNull();
        // Spacje na brzegach nie liczą się do limitu, tak jak przy zapisie (strip).
        assertThat(CardContentRules.legalBasisProblem("  " + "a".repeat(500) + "  ")).isNull();
    }
}

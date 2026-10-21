package pl.gradusiuris.backend.mark;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Reguły automatu proponującego oznaczenia pytań: każda reguła ma przypadek pozytywny i negatywny. */
class QuestionMarkDetectorTest {

    private static Set<QuestionMarkType> detect(String body, String a, String b, String c) {
        return QuestionMarkDetector.detect(body, a, b, c);
    }

    // ---------------------------------------------------------------- TIME_LIMIT

    @Test
    void timeLimit_isProposedForAQuestionAboutATermWithAnswersInDays() {
        Set<QuestionMarkType> marks = detect("W jakim terminie można wnieść apelację od wyroku?",
                "7 dni", "14 dni", "30 dni");

        assertThat(marks).contains(QuestionMarkType.TIME_LIMIT);
    }

    @Test
    void timeLimit_isProposedForATermKeywordEvenWithoutNumbers() {
        assertThat(detect("Roszczenie z tytułu rękojmi przedawnia się z upływem:",
                "okresu wskazanego w gwarancji", "okresu wskazanego w ustawie", "okresu uzgodnionego przez strony"))
                .contains(QuestionMarkType.TIME_LIMIT);
        assertThat(detect("Termin zawity to termin:", "a", "b", "c")).contains(QuestionMarkType.TIME_LIMIT);
        assertThat(detect("Wniosek można złożyć najpóźniej przed:", "a", "b", "c")).contains(QuestionMarkType.TIME_LIMIT);
    }

    @Test
    void timeLimit_recognisesANumberWrittenAsAWord() {
        assertThat(detect("Skargę należy wnieść w odpowiedzi na wezwanie do czternastu dni od doręczenia.", "a", "b", "c"))
                .contains(QuestionMarkType.TIME_LIMIT);
        assertThat(detect("Zażalenie wnosi się do sądu.", "w ciągu siedmiu dni", "bez terminu", "po roku"))
                .contains(QuestionMarkType.TIME_LIMIT);
    }

    @Test
    void timeLimit_isNotProposedForAPenaltyInYears() {
        // „do 5 lat pozbawienia wolności" to kara, a nie termin (lata i miesiące liczą się tylko ze słowem o terminie)
        Set<QuestionMarkType> marks = detect("Za to przestępstwo grozi kara pozbawienia wolności:",
                "do 2 lat", "do 5 lat", "do 8 lat");

        assertThat(marks).doesNotContain(QuestionMarkType.TIME_LIMIT);
        assertThat(marks).contains(QuestionMarkType.TRAP_NUMBERS); // ale liczby się różnią, więc to pułapka liczbowa
    }

    @Test
    void timeLimit_countsYearsAndMonthsWhenTheQuestionAsksAboutATerm() {
        assertThat(detect("W jakim terminie przedawnia się roszczenie?", "2 lata", "3 lata", "6 lat"))
                .contains(QuestionMarkType.TIME_LIMIT);
    }

    @Test
    void timeLimit_isNotProposedForWorkingHoursOrAges_butIsForAHourDeadline() {
        assertThat(detect("Pracownikowi przysługuje w każdej dobie nieprzerwany odpoczynek trwający co najmniej:",
                "8 godzin", "11 godzin", "13 godzin")).doesNotContain(QuestionMarkType.TIME_LIMIT);
        assertThat(detect("Zatrzymanego należy przekazać sądowi w ciągu:", "24 godzin", "48 godzin", "72 godzin"))
                .contains(QuestionMarkType.TIME_LIMIT);
    }

    @Test
    void timeLimit_ignoresWordsThatOnlyContainTheKeyword() {
        assertThat(detect("Który terminal obsługuje płatności kartą?", "a", "b", "c"))
                .doesNotContain(QuestionMarkType.TIME_LIMIT);
        assertThat(detect("Co oznacza terminologia prawnicza?", "a", "b", "c")).doesNotContain(QuestionMarkType.TIME_LIMIT);
    }

    @Test
    void timeLimit_isNotProposedForAQuestionWithoutTerms() {
        assertThat(detect("Kto jest stroną umowy sprzedaży?", "kupujący", "sprzedawca", "obaj")).isEmpty();
    }

    // ---------------------------------------------------------------- TRAP_NEGATION

    @Test
    void negation_isProposedForAClearNegationInTheQuestion() {
        for (String body : List.of(
                "Które z poniższych stwierdzeń jest nieprawidłowe?",
                "Które z poniższych zdań jest nieprawdziwe?",
                "Które z poniższych zdań nie jest prawdziwe?",
                "Wskaż odpowiedź niepoprawną:",
                "Zaznacz stwierdzenie błędne.",
                "Wskaż zdanie fałszywe:",
                "Sąd może odrzucić pozew z wyjątkiem sytuacji, gdy:",
                "Dowody nie są dopuszczalne, a środek niedopuszczalny to:",
                "Które z poniższych stwierdzeń dotyczących rękojmi nie ma zastosowania?")) {
            assertThat(detect(body, "a", "b", "c")).as(body).contains(QuestionMarkType.TRAP_NEGATION);
        }
    }

    @Test
    void negation_countsErroneousAndFalseOnlyInAnInstruction() {
        // „fałszywe zeznanie" i „błędne oświadczenie woli" to pojęcia prawne, a nie polecenie „wskaż błędne"
        assertThat(detect("Fałszywe zeznanie (art. 233 § 1 KK) popełnia ten, kto:", "a", "b", "c"))
                .doesNotContain(QuestionMarkType.TRAP_NEGATION);
        assertThat(detect("Oświadczenie woli złożone pod wpływem błędu istotnego:", "a", "b", "c"))
                .doesNotContain(QuestionMarkType.TRAP_NEGATION);
        assertThat(detect("Wskaż zdanie fałszywe:", "a", "b", "c")).contains(QuestionMarkType.TRAP_NEGATION);
        assertThat(detect("Które z poniższych stwierdzeń jest błędne?", "a", "b", "c")).contains(QuestionMarkType.TRAP_NEGATION);
    }

    @Test
    void negation_isNotProposedWithoutANegationInTheQuestion() {
        assertThat(detect("Które z poniższych stwierdzeń jest prawidłowe?", "a", "b", "c"))
                .doesNotContain(QuestionMarkType.TRAP_NEGATION);
        assertThat(detect("Który z poniższych organów orzeka niezależnie od sądu?", "a", "b", "c"))
                .doesNotContain(QuestionMarkType.TRAP_NEGATION);
    }

    @Test
    void negation_inTheAnswersAloneDoesNotCount() {
        assertThat(detect("Kto może wnieść skargę kasacyjną?", "Nie może nikt", "Strona", "Prokurator"))
                .doesNotContain(QuestionMarkType.TRAP_NEGATION);
    }

    // ---------------------------------------------------------------- TRAP_ABSOLUTE

    @Test
    void absolute_isProposedWhenOneOrTwoAnswersHaveAnAbsoluteWord() {
        assertThat(detect("Umowa przenosi własność:", "zawsze z chwilą zawarcia", "z chwilą wydania", "po wpisie do rejestru"))
                .contains(QuestionMarkType.TRAP_ABSOLUTE);
        assertThat(detect("Sąd rozstrzyga:", "wyłącznie na rozprawie", "także na posiedzeniu niejawnym", "nigdy bez wniosku"))
                .contains(QuestionMarkType.TRAP_ABSOLUTE);
        for (String word : List.of("jedynie", "bezwarunkowo", "w każdym przypadku", "w żadnym przypadku", "nigdy")) {
            assertThat(detect("Pytanie", "odpowiedź " + word + " obowiązuje", "inna odpowiedź", "jeszcze inna"))
                    .as(word).contains(QuestionMarkType.TRAP_ABSOLUTE);
        }
    }

    @Test
    void absolute_isNotProposedWhenEveryAnswerHasOneOrNoneHas() {
        assertThat(detect("Pytanie", "zawsze A", "zawsze B", "nigdy C")).doesNotContain(QuestionMarkType.TRAP_ABSOLUTE);
        assertThat(detect("Pytanie", "A", "B", "C")).doesNotContain(QuestionMarkType.TRAP_ABSOLUTE);
    }

    @Test
    void absolute_ignoresTheLegalTermOfAbsoluteNullity() {
        // „nieważność bezwzględna" to pojęcie prawne, a nie słowo, które robi z odpowiedzi pułapkę
        assertThat(detect("Umowa sprzeczna z ustawą jest:", "bezwzględnie nieważna", "ważna", "wzruszalna"))
                .doesNotContain(QuestionMarkType.TRAP_ABSOLUTE);
    }

    @Test
    void absolute_inTheQuestionAloneDoesNotCount() {
        assertThat(detect("Sąd zawsze orzeka:", "A", "B", "C")).doesNotContain(QuestionMarkType.TRAP_ABSOLUTE);
    }

    @Test
    void absolute_doesNotMatchWordsThatOnlyContainTheKeyword() {
        assertThat(detect("Pytanie", "zawsze", "niezawsze", "czasem")).contains(QuestionMarkType.TRAP_ABSOLUTE);
        assertThat(detect("Pytanie", "niezawsze", "czasem", "rzadko")).doesNotContain(QuestionMarkType.TRAP_ABSOLUTE);
    }

    // ---------------------------------------------------------------- TRAP_NUMBERS

    @Test
    void numbers_isProposedForTheSameUnitWithDifferentNumbers() {
        assertThat(detect("Pytanie", "7 dni", "14 dni", "30 dni")).contains(QuestionMarkType.TRAP_NUMBERS);
        assertThat(detect("Pytanie", "5%", "10%", "15%")).contains(QuestionMarkType.TRAP_NUMBERS);
        assertThat(detect("Pytanie", "1000 zł", "2000 złotych", "1000 zł")).contains(QuestionMarkType.TRAP_NUMBERS);
        assertThat(detect("Pytanie", "3 miesiące", "6 miesięcy", "inna odpowiedź")).contains(QuestionMarkType.TRAP_NUMBERS);
    }

    @Test
    void numbers_recognisesNumbersWrittenAsWords() {
        assertThat(detect("Pytanie", "siedem dni", "czternaście dni", "trzydzieści dni"))
                .contains(QuestionMarkType.TRAP_NUMBERS);
        assertThat(detect("Pytanie", "7 dni", "czternastu dni", "inny termin")).contains(QuestionMarkType.TRAP_NUMBERS);
    }

    @Test
    void numbers_isNotProposedWhenTheNumbersAreTheSame() {
        assertThat(detect("Pytanie", "14 dni", "14 dni", "14 dni")).doesNotContain(QuestionMarkType.TRAP_NUMBERS);
        assertThat(detect("Pytanie", "siedem dni", "7 dni", "7 dni")).doesNotContain(QuestionMarkType.TRAP_NUMBERS);
    }

    @Test
    void numbers_isNotProposedForDifferentUnitsOrNumbersWithoutAUnit() {
        assertThat(detect("Pytanie", "7 dni", "2 miesiące", "3 lata")).doesNotContain(QuestionMarkType.TRAP_NUMBERS);
        assertThat(detect("Pytanie", "art. 415 k.c.", "art. 416 k.c.", "art. 417 k.c.")).doesNotContain(QuestionMarkType.TRAP_NUMBERS);
        assertThat(detect("Pytanie", "14 dni", "bez terminu", "nie dotyczy")).doesNotContain(QuestionMarkType.TRAP_NUMBERS);
    }

    @Test
    void numbers_comparesDecimalsByValue() {
        assertThat(detect("Pytanie", "2,5%", "2.50%", "2,5 procent")).doesNotContain(QuestionMarkType.TRAP_NUMBERS);
        assertThat(detect("Pytanie", "2,5%", "3,5%", "inna")).contains(QuestionMarkType.TRAP_NUMBERS);
    }

    // ---------------------------------------------------------------- TRAP_CONJUNCTION

    @Test
    void conjunction_isProposedWhenTwoAnswersDifferOnlyByTheConjunction() {
        assertThat(detect("Pytanie",
                "Sąd wydaje wyrok i uzasadnienie", "Sąd wydaje wyrok lub uzasadnienie", "Sąd wydaje postanowienie"))
                .contains(QuestionMarkType.TRAP_CONJUNCTION);
        assertThat(detect("Pytanie", "wniosek oraz załączniki", "wniosek albo załączniki", "sam wniosek"))
                .contains(QuestionMarkType.TRAP_CONJUNCTION);
    }

    @Test
    void conjunction_isProposedForVeryAlikeAnswersWithTheConjunctionSwapped() {
        assertThat(detect("Pytanie",
                "Sąd może zawiesić postępowanie i wyznaczyć nowy termin rozprawy",
                "Sąd może zawiesić postępowanie lub wyznaczyć termin rozprawy",
                "Sąd nie może zawiesić postępowania"))
                .contains(QuestionMarkType.TRAP_CONJUNCTION);
    }

    @Test
    void conjunction_isNotProposedWhenAllAnswersUseTheSameConjunction() {
        assertThat(detect("Pytanie", "wniosek i załączniki", "pozew i odpowiedź", "skarga i zażalenie"))
                .doesNotContain(QuestionMarkType.TRAP_CONJUNCTION);
        assertThat(detect("Pytanie", "A lub B", "C lub D", "E lub F")).doesNotContain(QuestionMarkType.TRAP_CONJUNCTION);
    }

    @Test
    void conjunction_isNotProposedForUnrelatedAnswers() {
        assertThat(detect("Pytanie",
                "Skarga jest wnoszona do sądu i podlega opłacie",
                "Wniosek składa się do prokuratora lub komendanta policji",
                "Zażalenie przysługuje stronie"))
                .doesNotContain(QuestionMarkType.TRAP_CONJUNCTION);
    }

    // ---------------------------------------------------------------- całość

    @Test
    void detect_canProposeSeveralMarksForOneQuestion() {
        Set<QuestionMarkType> marks = detect("W jakim terminie, z wyjątkiem spraw pilnych, wnosi się zażalenie?",
                "zawsze 7 dni", "14 dni", "30 dni");

        assertThat(marks).containsExactlyInAnyOrder(QuestionMarkType.TIME_LIMIT, QuestionMarkType.TRAP_NEGATION,
                QuestionMarkType.TRAP_ABSOLUTE, QuestionMarkType.TRAP_NUMBERS);
    }

    @Test
    void detect_neverProposesTheOutdatedLawMark_andHandlesMissingTexts() {
        assertThat(detect(null, null, null, null)).isEmpty();
        assertThat(detect("", "", "", "")).isEmpty();
        for (String text : List.of("W jakim terminie?", "Które jest nieprawidłowe?", "zawsze", "7 dni")) {
            assertThat(detect(text, text, text, text)).doesNotContain(QuestionMarkType.TRAP_OUTDATED_LAW);
        }
    }

    @Test
    void detect_isNotAffectedByCaseOrExtraWhitespace() {
        assertThat(detect("W JAKIM  TERMINIE\nnależy złożyć wniosek?", "7  DNI", "14 DNI", "30 dni"))
                .contains(QuestionMarkType.TIME_LIMIT, QuestionMarkType.TRAP_NUMBERS);
    }

    // ---------------------------------------------------------------- liczby z jednostką (parser)

    @Test
    void quantities_parsesNumbersWithUnitsInDifferentFormsAndCases() {
        assertThat(QuestionMarkDetector.quantities("w ciągu 14 dni od doręczenia"))
                .containsExactly(new QuestionMarkDetector.Quantity("14", QuestionMarkDetector.Unit.DAYS));
        assertThat(QuestionMarkDetector.quantities("przez dwa tygodnie"))
                .containsExactly(new QuestionMarkDetector.Quantity("2", QuestionMarkDetector.Unit.WEEKS));
        assertThat(QuestionMarkDetector.quantities("stawka 2,5% i 100 zł"))
                .containsExactly(new QuestionMarkDetector.Quantity("2.5", QuestionMarkDetector.Unit.PERCENT),
                        new QuestionMarkDetector.Quantity("100", QuestionMarkDetector.Unit.ZLOTY));
        assertThat(QuestionMarkDetector.quantities("w 30 dniach, po 6 miesiącach i 3 latach"))
                .extracting(QuestionMarkDetector.Quantity::unit)
                .containsExactly(QuestionMarkDetector.Unit.DAYS, QuestionMarkDetector.Unit.MONTHS,
                        QuestionMarkDetector.Unit.YEARS);
    }

    @Test
    void quantities_ignoresNumbersWithoutAUnitAndWordsThatMerelyStartLikeOne() {
        assertThat(QuestionMarkDetector.quantities("art. 415 § 2 k.c.")).isEmpty();
        assertThat(QuestionMarkDetector.quantities("14 dniówek")).isEmpty();
        assertThat(QuestionMarkDetector.quantities("dwutygodniowy termin")).isEmpty();
    }
}

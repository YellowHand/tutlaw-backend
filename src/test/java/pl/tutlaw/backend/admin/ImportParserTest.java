package pl.tutlaw.backend.admin;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Odczyt plików importu (CSV, JSON) bez bazy i Springa. */
class ImportParserTest {

    private static final String HEADER = "akt;zrodlo;tresc;a;b;c;poprawna";

    // ------------------------------------------------------------------------------------------------------- CSV

    @Test
    void csv_readsSemicolonSeparatedRows_withRowNumbersLikeInASpreadsheet() {
        ParsedImport parsed = ImportParser.parseCsv(HEADER + "\nkc;autorskie;Pytanie 1;x;y;z;A\nkc;autorskie;Pytanie 2;x;y;z;b\n");

        assertThat(parsed.rows()).extracting(ImportRow::number).containsExactly(2, 3);
        assertThat(parsed.rows().get(0).get(ImportField.BODY)).isEqualTo("Pytanie 1");
        assertThat(parsed.rows().get(1).get(ImportField.CORRECT)).isEqualTo("b");
        assertThat(parsed.rows().get(0).get(ImportField.ACT)).isEqualTo("kc");
        assertThat(parsed.ignoredColumns()).isEmpty();
    }

    @Test
    void csv_detectsCommaAndTabSeparators_andIgnoresTheByteOrderMark() {
        String comma = "﻿tresc,a,b,c,poprawna\nP,x,y,z,C\n";
        String tab = "tresc\ta\tb\tc\tpoprawna\nP\tx\ty\tz\tC\n";

        assertThat(ImportParser.parseCsv(comma).rows().get(0).get(ImportField.CORRECT)).isEqualTo("C");
        assertThat(ImportParser.parseCsv(tab).rows().get(0).get(ImportField.OPTION_B)).isEqualTo("y");
    }

    @Test
    void csv_keepsSeparatorsQuotesAndNewLinesInsideQuotedFields() {
        String csv = HEADER + "\nkc;a;\"Treść; z \"\"cytatem\"\"\nw drugiej linii\";x;y;z;A\n";

        ParsedImport parsed = ImportParser.parseCsv(csv);

        assertThat(parsed.rows()).hasSize(1);
        assertThat(parsed.rows().get(0).get(ImportField.BODY)).isEqualTo("Treść; z \"cytatem\"\nw drugiej linii");
    }

    @Test
    void csv_handlesWindowsLineEndings_blankLines_andEmptyCellsAsMissingValues() {
        String csv = HEADER + ";wyjasnienie\r\n\r\nkc;a;P;x;y;z;A;\r\n";

        ParsedImport parsed = ImportParser.parseCsv(csv);

        assertThat(parsed.rows()).hasSize(1);
        assertThat(parsed.rows().get(0).number()).isEqualTo(3);
        assertThat(parsed.rows().get(0).get(ImportField.EXPLANATION)).isNull();
    }

    @Test
    void csv_recognisesPolishAndEnglishHeaders_andReportsUnknownOnes() {
        String csv = "Akt prawny;Źródło;Treść pytania;Odpowiedź A;Odpowiedź B;Odpowiedź C;Odpowiedź poprawna;Podstawa prawna;Rozdział;Zmiana w prawie;Opis zmiany;Kolor\n"
                + "kc;a;P;x;y;z;A;art. 1;I;2026-01-01;nowelizacja;zielony\n";

        ParsedImport parsed = ImportParser.parseCsv(csv);

        ImportRow row = parsed.rows().get(0);
        assertThat(row.get(ImportField.LEGAL_BASIS)).isEqualTo("art. 1");
        assertThat(row.get(ImportField.SECTION)).isEqualTo("I");
        assertThat(row.get(ImportField.LAW_CHANGED_AT)).isEqualTo("2026-01-01");
        assertThat(row.get(ImportField.LAW_CHANGE_NOTE)).isEqualTo("nowelizacja");
        assertThat(parsed.ignoredColumns()).containsExactly("Kolor");
        assertThat(ImportParser.parseCsv("body,optionA,optionB,optionC,correctOption\nP,x,y,z,B\n").rows()).hasSize(1);
    }

    @Test
    void csv_refusesAFileWithoutTheRequiredColumns_aRepeatedColumn_oraBrokenQuote() {
        assertThatThrownBy(() -> ImportParser.parseCsv("tresc;a;b\nP;x;y\n"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("C").hasMessageContaining("poprawna");
        assertThatThrownBy(() -> ImportParser.parseCsv("tresc;a;a;b;c;poprawna\n"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("więcej niż raz");
        assertThatThrownBy(() -> ImportParser.parseCsv(HEADER + "\nkc;a;\"niezamknięte;x;y;z;A\n"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cudzysłów");
        assertThatThrownBy(() -> ImportParser.parseCsv("  \n\n"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("pusty");
    }

    @Test
    void csv_marksARowWithTooManyFieldsAsAProblem_insteadOfSilentlyCuttingIt() {
        ParsedImport parsed = ImportParser.parseCsv(HEADER + "\nkc;a;P;x;y;z;A;nadmiarowe\n");

        assertThat(parsed.rows().get(0).problem()).contains("więcej pól");
    }

    @Test
    void csv_refusesMoreThanTheRowLimit() {
        String rows = IntStream.range(0, ImportParser.MAX_ROWS + 1).mapToObj(i -> "kc;a;P" + i + ";x;y;z;A")
                .collect(Collectors.joining("\n"));

        assertThatThrownBy(() -> ImportParser.parseCsv(HEADER + "\n" + rows))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("najwyżej");
    }

    // ------------------------------------------------------------------------------------------------------ JSON

    @Test
    void json_readsAListOfObjects_withNumbersAndBooleansAsText() {
        ParsedImport parsed = ImportParser.parseJson("""
                [{"akt":"kc","zrodlo":"a","tresc":"P1","a":"x","b":"y","c":"z","poprawna":"A","podstawa":"art. 5"},
                 {"act":"kc","source":"a","body":"P2","optionA":"1","optionB":2,"optionC":true,"correct":"C","legalBasis":"art. 6"}]
                """);

        assertThat(parsed.rows()).extracting(ImportRow::number).containsExactly(1, 2);
        assertThat(parsed.rows().get(1).get(ImportField.OPTION_B)).isEqualTo("2");
        assertThat(parsed.rows().get(1).get(ImportField.OPTION_C)).isEqualTo("true");
        assertThat(parsed.rows().get(1).get(ImportField.LEGAL_BASIS)).isEqualTo("art. 6");
    }

    @Test
    void json_readsAnObjectWithDefaultsAndAQuestionList_andOptionsGivenAsAList() {
        ParsedImport parsed = ImportParser.parseJson("""
                {"akt":"kodeks-cywilny","zrodlo":"Notarialna 2025","wersja":3,
                 "questions":[{"tresc":"P","options":["x","y","z"],"poprawna":"B"}]}
                """);

        assertThat(parsed.defaults().get(ImportField.ACT)).isEqualTo("kodeks-cywilny");
        assertThat(parsed.defaults().get(ImportField.SOURCE)).isEqualTo("Notarialna 2025");
        assertThat(parsed.ignoredColumns()).containsExactly("wersja");
        ImportRow row = parsed.rows().get(0);
        assertThat(row.get(ImportField.OPTION_A)).isEqualTo("x");
        assertThat(row.get(ImportField.OPTION_C)).isEqualTo("z");
        assertThat(row.problem()).isNull();
    }

    @Test
    void json_turnsAMalformedItemIntoARowProblem_notAFailureOfTheWholeFile() {
        ParsedImport parsed = ImportParser.parseJson("""
                [{"tresc":"P","options":["x","y"],"poprawna":"A"}, "to nie obiekt", {"tresc":["lista"],"poprawna":"A"}, {"tresc":"P","a":"x","b":"y","c":"z","poprawna":"A"}]
                """);

        assertThat(parsed.rows().get(0).problem()).contains("trzy odpowiedzi");
        assertThat(parsed.rows().get(1).problem()).contains("nie jest obiektem");
        assertThat(parsed.rows().get(2).problem()).contains("nieprawidłowy typ");
        assertThat(parsed.rows().get(3).problem()).isNull();
    }

    @Test
    void json_refusesBrokenStructure_withThePlaceOfTheError() {
        assertThatThrownBy(() -> ImportParser.parseJson("[{\"tresc\": }]"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("wiersz 1");
        assertThatThrownBy(() -> ImportParser.parseJson("{\"akt\":\"kc\"}"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("listy pytań");
        assertThatThrownBy(() -> ImportParser.parseJson("\"tekst\""))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("listę pytań");
        assertThatThrownBy(() -> ImportParser.parseJson("{\"questions\":{}}"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("listą");
    }

    // -------------------------------------------------------------------------------------------- MiniJson

    @Test
    @SuppressWarnings("unchecked")
    void miniJson_parsesEscapesUnicodeNestingAndLiterals() {
        Object value = MiniJson.parse("{\"a\":\"l1\\nl2 \\\"q\\\" \\u0105 \\\\\",\"b\":[1,-2.5e3,true,false,null,{}],\"c\":{\"d\":[]}}");

        assertThat(value).isInstanceOf(java.util.Map.class);
        java.util.Map<?, ?> map = (java.util.Map<?, ?>) value;
        assertThat(map.get("a")).isEqualTo("l1\nl2 \"q\" ą \\");
        assertThat((List<Object>) map.get("b")).containsExactly("1", "-2.5e3", true, false, null, new java.util.LinkedHashMap<>());
    }

    @Test
    void miniJson_rejectsTrailingTextUnclosedStringsControlCharactersAndDeepNesting() {
        assertThatThrownBy(() -> MiniJson.parse("[1] x")).hasMessageContaining("jeszcze tekst");
        assertThatThrownBy(() -> MiniJson.parse("\"abc")).hasMessageContaining("nie został zamknięty");
        assertThatThrownBy(() -> MiniJson.parse("\"a\nb\"")).hasMessageContaining("znak sterujący");
        assertThatThrownBy(() -> MiniJson.parse("[".repeat(50))).hasMessageContaining("zbyt głęboko");
        assertThatThrownBy(() -> MiniJson.parse("{\"a\" 1}")).hasMessageContaining(":");
        assertThatThrownBy(() -> MiniJson.parse("[1,]")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MiniJson.parse("")).hasMessageContaining("urwały");
        assertThatThrownBy(() -> MiniJson.parse("-")).hasMessageContaining("liczba");
    }
}

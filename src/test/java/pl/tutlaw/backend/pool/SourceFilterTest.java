package pl.tutlaw.backend.pool;

import org.junit.jupiter.api.Test;
import pl.tutlaw.backend.source.SourceKind;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SourceFilterTest {

    @Test
    void allGroups_isTheDefault_andIsStoredAsNull() {
        assertThat(SourceFilter.ALL.isAll()).isTrue();
        assertThat(SourceFilter.ALL.serialize()).isNull();
        assertThat(SourceFilter.parse(null)).isEqualTo(SourceFilter.ALL);
        assertThat(SourceFilter.parse("  ")).isEqualTo(SourceFilter.ALL);
    }

    @Test
    void serialize_listsTheChosenGroups_inAFixedOrder() {
        assertThat(new SourceFilter(true, false, false).serialize()).isEqualTo("OWN_SHEETS");
        assertThat(new SourceFilter(false, true, false).serialize()).isEqualTo("OTHER_SHEETS");
        assertThat(new SourceFilter(false, false, true).serialize()).isEqualTo("AUTHORED");
        assertThat(new SourceFilter(true, false, true).serialize()).isEqualTo("OWN_SHEETS,AUTHORED");
        assertThat(new SourceFilter(false, true, true).serialize()).isEqualTo("OTHER_SHEETS,AUTHORED");
    }

    @Test
    void parse_isTheInverseOfSerialize_forEveryNonEmptyCombination() {
        for (int mask = 1; mask < 8; mask++) {
            SourceFilter filter = new SourceFilter((mask & 1) != 0, (mask & 2) != 0, (mask & 4) != 0);
            assertThat(SourceFilter.parse(filter.serialize())).isEqualTo(filter);
        }
    }

    @Test
    void parse_skipsUnknownCodes_andToleratesSpaces() {
        assertThat(SourceFilter.parse(" AUTHORED , NOTARY ")).isEqualTo(new SourceFilter(false, false, true));
    }

    @Test
    void parse_neverHidesTheWholeMaterial() {
        // zapis bez ani jednego znanego kodu (stary, uszkodzony) daje wszystkie grupy
        assertThat(SourceFilter.parse("NOTARY,BAILIFF")).isEqualTo(SourceFilter.ALL);
        assertThat(new SourceFilter(false, false, false).isNone()).isTrue();
        assertThat(SourceFilter.parse(new SourceFilter(false, false, false).serialize())).isEqualTo(SourceFilter.ALL);
    }

    @Test
    void sourceGroups_authoredIsItsOwnGroup_andSheetsSplitByTheTrackOwnership() {
        Set<Long> own = Set.of(1L);

        assertThat(SourceGroups.of(SourceKind.AUTHORED, 3L, own)).isEqualTo(SourceFilter.AUTHORED);
        assertThat(SourceGroups.of(SourceKind.EXAM_SHEET, 1L, own)).isEqualTo(SourceFilter.OWN_SHEETS);
        assertThat(SourceGroups.of(SourceKind.EXAM_SHEET, 2L, own)).isEqualTo(SourceFilter.OTHER_SHEETS);
        // użytkownik bez ścieżki albo Student nie ma własnych arkuszy: każdy arkusz jest „cudzy"
        assertThat(SourceGroups.of(SourceKind.EXAM_SHEET, 1L, Set.of())).isEqualTo(SourceFilter.OTHER_SHEETS);
    }
}

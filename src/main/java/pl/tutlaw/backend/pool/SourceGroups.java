package pl.tutlaw.backend.pool;

import pl.tutlaw.backend.source.SourceKind;

import java.util.Set;

/** Do której z trzech grup filtra źródeł należy źródło: własne arkusze, arkusze innych ścieżek albo pytania autorskie. */
public final class SourceGroups {

    private SourceGroups() {
    }

    public static String of(SourceKind kind, Long sourceId, Set<Long> ownSheetIds) {
        if (kind == SourceKind.AUTHORED) {
            return SourceFilter.AUTHORED;
        }
        return ownSheetIds.contains(sourceId) ? SourceFilter.OWN_SHEETS : SourceFilter.OTHER_SHEETS;
    }
}

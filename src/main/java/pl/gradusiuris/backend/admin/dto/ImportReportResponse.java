package pl.gradusiuris.backend.admin.dto;

import java.util.List;

/**
 * Raport z importu. {@code saved} jest prawdą tylko po prawdziwym zapisie ({@code dryRun=false}, bez błędów).
 * {@code created} to liczba zapisanych pytań, a {@code newSheets} nazwy arkuszy założonych (przy podglądzie: które zostałyby
 * założone). {@code rows} ma najwyżej 500 pozycji (błędy mają pierwszeństwo), a {@code rowsTruncated} mówi, że lista jest
 * skrócona; liczniki dotyczą całego pliku.
 */
public record ImportReportResponse(
        boolean dryRun,
        boolean saved,
        int total,
        int ok,
        int duplicates,
        int errors,
        int created,
        List<String> newSheets,
        List<String> ignoredColumns,
        List<ImportRowResult> rows,
        boolean rowsTruncated
) {
}

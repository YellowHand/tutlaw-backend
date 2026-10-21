package pl.gradusiuris.backend.mark.dto;

import pl.gradusiuris.backend.mark.QuestionMarkType;

import java.util.List;

/**
 * Ile jest oznaczeń każdego rodzaju w każdym stanie.
 *
 * @param questions wszystkie pytania w bazie (do porównania z liczbą oznaczonych)
 * @param marks     jeden wiersz na rodzaj oznaczenia (także z zerami)
 */
public record MarkSummaryResponse(long questions, List<Row> marks) {

    /**
     * @param suggested propozycje automatu czekające na przejrzenie
     * @param confirmed zatwierdzone: tylko one liczą się w testach
     * @param rejected  odrzucone: automat ich nie proponuje ponownie
     */
    public record Row(QuestionMarkType mark, long suggested, long confirmed, long rejected) {
    }
}

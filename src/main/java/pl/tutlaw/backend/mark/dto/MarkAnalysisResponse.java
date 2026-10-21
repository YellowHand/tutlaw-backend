package pl.tutlaw.backend.mark.dto;

import pl.tutlaw.backend.mark.QuestionMarkType;

import java.util.Map;

/**
 * Wynik uruchomienia automatu.
 *
 * @param questionsScanned  ile pytań przejrzał automat
 * @param suggested         ile nowych propozycji dodał (pary pytanie-oznaczenie, których jeszcze nie było w bazie)
 * @param suggestedByMark   nowe propozycje według rodzaju (każdy rodzaj, także 0)
 */
public record MarkAnalysisResponse(int questionsScanned, int suggested, Map<QuestionMarkType, Integer> suggestedByMark) {
}

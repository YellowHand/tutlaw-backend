package pl.tutlaw.backend.review;

import pl.tutlaw.backend.question.Question;
import pl.tutlaw.backend.review.dto.FlashcardQueueItemResponse;
import pl.tutlaw.backend.review.dto.TestQueueItemResponse;

public class ReviewQueueMapper {

    /**
     * Fiszka pytania: przód to treść pytania, tył to tekst poprawnej odpowiedzi.
     * {@code lapses} to liczba dotychczasowych błędów w fiszce (0 dla nowej).
     */
    public static FlashcardQueueItemResponse toFlashcard(Question question, int lapses, boolean leech) {
        return new FlashcardQueueItemResponse(
                question.getId(),
                question.getBody(),
                question.correctOptionText(),
                question.getExplanation(),
                question.getLegalBasis(),
                question.getValidAsOf(),
                question.getLegalAct().getName(),
                question.getLegalAct().getShortName(),
                question.getSource().getName(),
                question.getSource().getKind(),
                question.getSource().getExamYear(),
                lapses,
                leech
        );
    }

    public static TestQueueItemResponse toTest(Question question) {
        return new TestQueueItemResponse(
                question.getId(),
                question.getBody(),
                question.getOptionA(),
                question.getOptionB(),
                question.getOptionC(),
                question.getLegalAct().getName(),
                question.getLegalAct().getShortName(),
                question.getSource().getName(),
                question.getSource().getKind(),
                question.getSource().getExamYear()
        );
    }
}

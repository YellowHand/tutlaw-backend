package pl.tutlaw.backend.review;

import pl.tutlaw.backend.card.Card;
import pl.tutlaw.backend.review.dto.ReviewQueueItemResponse;
import pl.tutlaw.backend.review.dto.TestQueueItemResponse;

public class ReviewQueueMapper {

    public static ReviewQueueItemResponse toResponse(Card card) {
        return new ReviewQueueItemResponse(
                card.getId(),
                card.getFront(),
                card.getBack(),
                card.getPageNumber(),
                card.isVerbatimQuote(),
                card.getSource() != null ? card.getSource().getName() : null,
                card.getLegalBasis(),
                card.getValidAsOf()
        );
    }

    public static TestQueueItemResponse toTestResponse(Card card) {
        return new TestQueueItemResponse(
                card.getId(),
                card.getFront(),
                card.getOptionA(),
                card.getOptionB(),
                card.getOptionC(),
                card.getSource() != null ? card.getSource().getName() : null
        );
    }
}
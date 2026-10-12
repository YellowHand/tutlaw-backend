package pl.tutlaw.backend.review.fsrs;

public enum Rating {
    AGAIN(1), HARD(2), GOOD(3), EASY(4);

    private final int value;

    Rating(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}

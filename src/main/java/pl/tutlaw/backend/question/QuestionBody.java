package pl.tutlaw.backend.question;

/** Odchudzony widok pytania do wykrywania powtórzeń: numer, źródło i sama treść. */
public interface QuestionBody {

    Long getId();

    Long getSourceId();

    String getBody();
}

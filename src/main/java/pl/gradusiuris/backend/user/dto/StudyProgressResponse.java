package pl.gradusiuris.backend.user.dto;

/**
 * Co składa się na postępy użytkownika, czyli to, co zniknie po ich wyzerowaniu (i co znikło, gdy to odpowiedź na
 * wyzerowanie). Ścieżka, ustawienia nauki, wybrane przedmioty i zawężenie puli oraz zgłoszenia pytań nie należą do
 * postępów i zostają.
 *
 * @param testQuestions  pytania w nauce w trybie testu (stany FSRS)
 * @param flashcards     pytania w nauce w trybie fiszek
 * @param answers        zapisane odpowiedzi (historia we wszystkich trybach)
 * @param manualFlashcards pytania dodane do fiszek ręcznie
 * @param customTests    własne testy z listy „Moje testy"
 * @param exams          podejścia do egzaminu próbnego
 * @param studyTimeSeconds łączny czas nauki
 */
public record StudyProgressResponse(
        long testQuestions,
        long flashcards,
        long answers,
        long manualFlashcards,
        long customTests,
        long exams,
        long studyTimeSeconds
) {
}

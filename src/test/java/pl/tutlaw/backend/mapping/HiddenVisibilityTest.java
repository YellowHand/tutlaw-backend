package pl.tutlaw.backend.mapping;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import pl.tutlaw.backend.course.CourseRepository;
import pl.tutlaw.backend.legal.LegalActRepository;
import pl.tutlaw.backend.source.QuestionSourceRepository;
import pl.tutlaw.backend.track.TrackRepository;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ukryty akt i ukryty arkusz nie istnieją dla użytkowników: pula (PoolService) składa się z tych zapytań, więc każde z nich
 * musi pomijać ukryte. Widok administratora ma osobne zapytania, które ukrytych nie pomijają (zob. nazwy poniżej).
 */
class HiddenVisibilityTest {

    private static String jpql(Class<?> repository, String name) {
        for (Method method : repository.getDeclaredMethods()) {
            if (method.getName().equals(name) && method.isAnnotationPresent(Query.class)) {
                return method.getAnnotation(Query.class).value().replaceAll("\\s+", " ");
            }
        }
        throw new AssertionError("brak zapytania " + repository.getSimpleName() + "." + name);
    }

    @Test
    void everyQueryThatFeedsThePoolSkipsHiddenActsAndSheets() {
        List<String> problems = new ArrayList<>();
        check(problems, LegalActRepository.class, "findAllIds", "a.hidden = false");
        check(problems, LegalActRepository.class, "findIdsByFieldIds", "a.hidden = false");
        check(problems, TrackRepository.class, "findActIds", "a.hidden = false");
        check(problems, CourseRepository.class, "findCourseActs", "a.hidden = false");
        check(problems, CourseRepository.class, "findActIdsByCourseIds", "a.hidden = false");
        check(problems, QuestionSourceRepository.class, "findAllRows", "s.hidden = false");
        check(problems, QuestionSourceRepository.class, "findIdsByKind", "s.hidden = false");
        check(problems, QuestionSourceRepository.class, "findIdsByTrackId", "s.hidden = false");
        assertThat(problems).isEmpty();
    }

    @Test
    void theAdminViewsDoNotHideAnything() {
        assertThat(jpql(CourseRepository.class, "findAllCourseActs")).doesNotContain("hidden");
        assertThat(jpql(TrackRepository.class, "findAllTrackActs")).doesNotContain("hidden");
        assertThat(jpql(QuestionSourceRepository.class, "findAllSheetsWithTrack")).doesNotContain("hidden");
        assertThat(jpql(QuestionSourceRepository.class, "countSheetsByTrack")).doesNotContain("hidden");
    }

    private static void check(List<String> problems, Class<?> repository, String method, String required) {
        if (!jpql(repository, method).contains(required)) {
            problems.add(repository.getSimpleName() + "." + method + " nie zawiera warunku \"" + required + "\"");
        }
    }
}

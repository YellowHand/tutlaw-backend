package pl.gradusiuris.backend.mapping;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Zapytania w repozytoriach bez uruchamiania Springa i bazy. Spring Data i Hibernate wykryłyby te błędy dopiero
 * przy starcie aplikacji; tu padają w {@code mvn test}.
 */
class RepositoryQueryTest {

    private final Map<String, Class<?>> entities = MappingSupport.entitiesByJpqlName();

    private static List<Method> queryMethods() {
        List<Method> methods = new ArrayList<>();
        for (Class<?> repository : MappingSupport.repositories()) {
            for (Method method : repository.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Query.class)) methods.add(method);
            }
        }
        return methods;
    }

    @Test
    void theRepositoriesAreFound_soTheChecksBelowAreNotVacuous() {
        assertThat(MappingSupport.repositories()).hasSizeGreaterThanOrEqualTo(20);
        assertThat(queryMethods()).hasSizeGreaterThanOrEqualTo(60);
    }

    @Test
    void noQueryHasGluedKeywordsOrUnbalancedParentheses() {
        List<String> problems = new ArrayList<>();
        for (Method method : queryMethods()) {
            problems.addAll(QueryChecks.glue(method.getDeclaringClass().getSimpleName() + "." + method.getName(),
                    method.getAnnotation(Query.class).value()));
        }
        assertThat(problems).isEmpty();
    }

    @Test
    void queryParametersMatchTheirParamAnnotations() {
        List<String> problems = new ArrayList<>();
        for (Method method : queryMethods()) {
            problems.addAll(QueryChecks.params(method.getDeclaringClass().getSimpleName() + "." + method.getName(),
                    method, method.getAnnotation(Query.class).value()));
        }
        assertThat(problems).isEmpty();
    }

    @Test
    void jpqlPathsPointAtExistingEntitiesAndFields() {
        List<String> problems = new ArrayList<>();
        for (Method method : queryMethods()) {
            Query query = method.getAnnotation(Query.class);
            if (query.nativeQuery()) continue;
            problems.addAll(QueryChecks.paths(method.getDeclaringClass().getSimpleName() + "." + method.getName(),
                    query.value(), entities));
        }
        assertThat(problems).isEmpty();
    }

    @Test
    void everyProjectionGetterHasAMatchingSelectAlias() {
        List<String> problems = new ArrayList<>();
        for (Method method : queryMethods()) {
            Query query = method.getAnnotation(Query.class);
            if (query.nativeQuery()) continue;
            problems.addAll(QueryChecks.projection(method.getDeclaringClass().getSimpleName() + "." + method.getName(),
                    method, query.value()));
        }
        assertThat(problems).isEmpty();
    }

    @Test
    void derivedMethodsUseExistingProperties() {
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (Class<?> repository : MappingSupport.repositories()) {
            Class<?> entity = MappingSupport.entityOf(repository);
            for (Method method : repository.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Query.class)) continue;
                List<String> found = QueryChecks.derived(repository.getSimpleName() + "." + method.getName(), entity, method);
                problems.addAll(found);
                if (method.getName().matches("^(find|read|get|query|search|stream|count|exists|delete|remove)\\w*?By[A-Z]\\w*$")) checked++;
            }
        }
        assertThat(problems).isEmpty();
        assertThat(checked).isGreaterThanOrEqualTo(10);
    }

    // ---------------------------------------------------------------- sprawdzenia naprawdę łapią błędy

    @Test
    void theGlueCheckSpotsKeywordsGluedToTheirNeighbours() {
        assertThat(QueryChecks.glue("t", "SELECT q FROM Question q WHEREq.hidden = false")).isNotEmpty();
        assertThat(QueryChecks.glue("t", "SELECT q FROM Question q WHERE q.id IN :ids)GROUP BY q.id")).isNotEmpty();
        assertThat(QueryChecks.glue("t", "SELECT q FROM Question q WHERE q.hidden = falseAND q.id = 1")).isNotEmpty();
        assertThat(QueryChecks.glue("t", "SELECT q FROM Question q WHERE (q.id IN :ids\nAND q.hidden = false)")).isEmpty();
        // słowa w literałach tekstowych nie liczą się
        assertThat(QueryChecks.glue("t", "SELECT q FROM Question q WHERE q.body = 'WHEREx FROMy'")).isEmpty();
    }

    @Test
    void theParamCheckSpotsMissingAndUnusedParams() throws NoSuchMethodException {
        Method needsParam = Sample.class.getMethod("a", Long.class);
        Method twoParams = Sample.class.getMethod("b", Long.class, Long.class);
        Method named = Sample.class.getMethod("c", Long.class);

        assertThat(QueryChecks.params("t", needsParam, "SELECT 1 WHERE x = :id")).isNotEmpty();      // brak @Param
        assertThat(QueryChecks.params("t", named, "SELECT 1 WHERE x = :other")).hasSize(2);          // użyty :other i nieużyty id
        assertThat(QueryChecks.params("t", named, "SELECT 1 WHERE x = :id")).isEmpty();
        assertThat(QueryChecks.params("t", twoParams, "SELECT 1 WHERE x = :first AND y = :second")).isEmpty();
        assertThat(QueryChecks.params("t", named, "SELECT 1 WHERE x = :id AND d = '10:30' AND z = y::text")).isEmpty();
    }

    @Test
    void thePathCheckSpotsUnknownFieldsEntitiesAndRelations() {
        assertThat(QueryChecks.paths("t", "SELECT q FROM Question q WHERE q.hidden = false", entities)).isEmpty();
        assertThat(QueryChecks.paths("t", "SELECT q FROM Question q WHERE q.nonexistent = false", entities)).isNotEmpty();
        assertThat(QueryChecks.paths("t", "SELECT q FROM Question q WHERE q.legalAct.legalField.id = 1", entities)).isEmpty();
        assertThat(QueryChecks.paths("t", "SELECT q FROM Question q WHERE q.legalAct.nope.id = 1", entities)).isNotEmpty();
        assertThat(QueryChecks.paths("t", "SELECT q FROM Questions q", entities)).isNotEmpty();
        assertThat(QueryChecks.paths("t", "SELECT q FROM Question q JOIN q.source s WHERE s.kind = 'AUTHORED'", entities)).isEmpty();
        assertThat(QueryChecks.paths("t", "SELECT q FROM Question q JOIN q.sources s", entities)).isNotEmpty();
        assertThat(QueryChecks.paths("t", "SELECT q FROM Question q JOIN FETCH q.legalAct a JOIN FETCH a.legalField WHERE a.slug = 'x'", entities)).isEmpty();
        assertThat(QueryChecks.paths("t", "SELECT a.userId FROM FlashcardAddition a, Question q WHERE q.id = a.questionId AND q.bodyy IS NULL", entities)).isNotEmpty();
        // wartości w literałach tekstowych nie są ścieżkami
        assertThat(QueryChecks.paths("t", "SELECT q FROM Question q WHERE q.body = 'q.nonexistent'", entities)).isEmpty();
    }

    @Test
    void theProjectionCheckSpotsAGetterWithoutAnAlias() throws NoSuchMethodException {
        Method method = Sample.class.getMethod("projected");

        assertThat(QueryChecks.projection("t", method, "SELECT q.id AS id, q.body AS body FROM Question q")).isEmpty();
        assertThat(QueryChecks.projection("t", method, "SELECT q.id AS id, q.body AS bodyText FROM Question q")).hasSize(1);
    }

    @Test
    void theDerivedCheckSpotsUnknownProperties() throws NoSuchMethodException {
        Class<?> entity = pl.gradusiuris.backend.flashcard.FlashcardAddition.class;

        assertThat(QueryChecks.derived("t", entity, Sample.class.getMethod("findByUserIdAndQuestionId", Long.class, Long.class))).isEmpty();
        assertThat(QueryChecks.derived("t", entity, Sample.class.getMethod("findByUserIdAndQuestionNumber", Long.class, Long.class))).hasSize(1);
        assertThat(QueryChecks.derived("t", entity, Sample.class.getMethod("findAllByOrderByAddedAtDesc"))).isEmpty();
        assertThat(QueryChecks.derived("t", entity, Sample.class.getMethod("findAllByOrderByAddedWhenDesc"))).hasSize(1);
        assertThat(QueryChecks.derived("t", entity, Sample.class.getMethod("existsByUserIdIn", java.util.Collection.class))).isEmpty();
    }

    /** Metody-wzorce do sprawdzenia samych sprawdzeń. */
    @SuppressWarnings("unused")
    interface Sample {
        interface Row {
            Long getId();

            String getBody();
        }

        void a(Long id);

        void b(@org.springframework.data.repository.query.Param("first") Long first,
               @org.springframework.data.repository.query.Param("second") Long second);

        void c(@org.springframework.data.repository.query.Param("id") Long id);

        List<Row> projected();

        void findByUserIdAndQuestionId(Long userId, Long questionId);

        void findByUserIdAndQuestionNumber(Long userId, Long number);

        void findAllByOrderByAddedAtDesc();

        void findAllByOrderByAddedWhenDesc();

        boolean existsByUserIdIn(java.util.Collection<Long> userIds);
    }
}

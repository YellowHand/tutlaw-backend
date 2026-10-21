package pl.gradusiuris.backend.question;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Wspólne kroki kolejek, planu do egzaminu i losowania egzaminu: zawężenie listy pytań do zakresu (źródło, akt prawny,
 * dziedzina albo cała pula) oraz wczytanie samych wybranych pytań w zadanej kolejności. Dzięki temu cała pula
 * użytkownika nigdy nie jest ładowana z treścią pytań.
 */
public final class QuestionScopes {

    private QuestionScopes() {
    }

    /**
     * Pytania z listy mieszczące się w zakresie. Zakres od najwęższego: źródło (arkusz), akt prawny, dziedzina;
     * bez żadnego cała lista. Wskazanych zakresów nie łączymy: obowiązuje najwęższy podany.
     */
    public static List<QuestionRef> inScope(List<QuestionRef> refs, Long fieldId, Long actId, Long sourceId) {
        if (sourceId != null) {
            return refs.stream().filter(r -> sourceId.equals(r.getSourceId())).toList();
        }
        if (actId != null) {
            return refs.stream().filter(r -> actId.equals(r.getLegalActId())).toList();
        }
        if (fieldId != null) {
            return refs.stream().filter(r -> fieldId.equals(r.getLegalFieldId())).toList();
        }
        return refs;
    }

    /**
     * Czy fiszka pytania jest dla użytkownika odblokowana: pytanie ma fiszkę dopiero wtedy, gdy użytkownik pomylił je
     * w teście bez zaznaczenia „Nie jestem pewien" albo dodał je do fiszek ręcznie
     * ({@code unlockedQuestionIds} to numery takich pytań).
     */
    public static boolean isUnlocked(QuestionRef ref, Set<Long> unlockedQuestionIds) {
        return !Boolean.TRUE.equals(ref.getFlashcardHidden()) && unlockedQuestionIds.contains(ref.getId());
    }

    /**
     * Wczytuje pełne pytania (z aktem prawnym, dziedziną i źródłem) i zwraca je w kolejności {@code ids}. Pytanie,
     * które w międzyczasie zniknęło z bazy, jest po cichu pomijane. Jedno zapytanie niezależnie od liczby pytań.
     */
    public static List<Question> loadInOrder(QuestionRepository questionRepository, List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Question> byId = new HashMap<>();
        for (Question question : questionRepository.findAllWithRelationsByIdIn(ids)) {
            byId.put(question.getId(), question);
        }
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }
}

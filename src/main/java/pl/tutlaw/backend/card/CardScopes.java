package pl.tutlaw.backend.card;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Wspólne kroki kolejek, planu do egzaminu i losowania egzaminu: zakres kart (źródło, przedmiot, kategoria albo
 * cały materiał) w odchudzonej postaci oraz wczytanie samych wybranych kart w zadanej kolejności.
 * Dzięki temu cała pula użytkownika nigdy nie jest ładowana z treścią pytań.
 */
public final class CardScopes {

    private CardScopes() {
    }

    /**
     * Dostępne dla użytkownika karty zakresu (pula płaska albo przeczytany rozdział), bez treści.
     * Zakres od najwęższego: źródło, przedmiot, kategoria (rodzaj), a bez żadnego cały materiał użytkownika.
     */
    public static List<CardRepository.CardRef> readableRefs(
            CardRepository cardRepository, Long categoryId, Long subjectId, Long sourceId, Long userId) {
        if (sourceId != null) {
            return cardRepository.findReadableRefsForUserAndSource(sourceId, userId);
        }
        if (subjectId != null) {
            return cardRepository.findReadableRefsForUserAndSubject(subjectId, userId);
        }
        if (categoryId != null) {
            return cardRepository.findReadableRefsForUserAndCategory(categoryId, userId);
        }
        return cardRepository.findReadableRefsForUser(userId);
    }

    /** Czy karta ma format testowy (3 odpowiedzi, jedna poprawna). */
    public static boolean isTest(CardRepository.CardRef ref) {
        return ref.getCorrectOption() != null;
    }

    /**
     * Wczytuje pełne karty (ze źródłem) i zwraca je w kolejności {@code ids}. Karta, która w międzyczasie zniknęła
     * z bazy, jest po cichu pomijana. Jedno zapytanie niezależnie od liczby kart.
     */
    public static List<Card> loadInOrder(CardRepository cardRepository, List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Card> byId = new HashMap<>();
        for (Card card : cardRepository.findAllWithSourceByIdIn(ids)) {
            byId.put(card.getId(), card);
        }
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }
}

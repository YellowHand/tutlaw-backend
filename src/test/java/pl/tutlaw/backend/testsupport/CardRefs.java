package pl.tutlaw.backend.testsupport;

import pl.tutlaw.backend.card.Card;
import pl.tutlaw.backend.card.CardRepository;
import pl.tutlaw.backend.card.CorrectOption;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Odchudzone karty i "ładowanie po numerach" do testów serwisów, które nie ładują już całej puli. */
public final class CardRefs {

    private CardRefs() {
    }

    public static CardRepository.CardRef of(long id, Integer chapterOrder, CorrectOption correctOption) {
        return new CardRepository.CardRef() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public Integer getChapterOrder() {
                return chapterOrder;
            }

            @Override
            public CorrectOption getCorrectOption() {
                return correctOption;
            }
        };
    }

    /** Odchudzony widok karty: numer, kolejność rozdziału (null bez rozdziału) i poprawna opcja (null dla fiszki). */
    public static CardRepository.CardRef of(Card card) {
        Integer chapterOrder = card.getChapter() == null ? null : card.getChapter().getOrderIndex();
        return of(card.getId(), chapterOrder, card.getCorrectOption());
    }

    public static List<CardRepository.CardRef> of(List<Card> cards) {
        return cards.stream().map(CardRefs::of).collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Zachowuje się jak zapytanie o karty po numerach: zwraca istniejące karty z {@code ids}, ale w ODWROTNEJ
     * kolejności niż poproszono (baza nie gwarantuje kolejności), żeby testy pilnowały, że serwis układa ją sam.
     */
    public static List<Card> byIds(Collection<Card> all, Collection<Long> ids) {
        Set<Long> wanted = Set.copyOf(ids);
        List<Card> found = all.stream().filter(card -> wanted.contains(card.getId())).collect(Collectors.toList());
        List<Card> inRequestOrder = new ArrayList<>();
        for (Long id : ids) {
            found.stream().filter(card -> card.getId().equals(id)).findFirst().ifPresent(inRequestOrder::add);
        }
        java.util.Collections.reverse(inRequestOrder);
        return inRequestOrder;
    }
}

package pl.tutlaw.backend.testsupport;

import pl.tutlaw.backend.legal.LegalAct;
import pl.tutlaw.backend.legal.LegalField;
import pl.tutlaw.backend.question.CorrectOption;
import pl.tutlaw.backend.question.Question;
import pl.tutlaw.backend.question.QuestionRef;
import pl.tutlaw.backend.source.QuestionSource;
import pl.tutlaw.backend.source.SourceKind;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pytania, odchudzone widoki pytań i „ładowanie po numerach" do testów serwisów, które nie ładują już całej puli.
 * Źródła w testach: 1 = własny arkusz, 2 = arkusz innej ścieżki, 3 = pytania autorskie (zob. {@link Pools}).
 */
public final class QuestionRefs {

    public static final long OWN_SHEET = 1L;
    public static final long OTHER_SHEET = 2L;
    public static final long AUTHORED = 3L;

    private QuestionRefs() {
    }

    public static LegalField field(long id) {
        LegalField field = new LegalField();
        field.setId(id);
        field.setName("Dziedzina " + id);
        field.setSlug("dziedzina-" + id);
        return field;
    }

    public static LegalAct act(long id, long fieldId) {
        LegalAct act = new LegalAct();
        act.setId(id);
        act.setLegalField(field(fieldId));
        act.setName("Akt " + id);
        act.setShortName("A" + id);
        act.setSlug("akt-" + id);
        return act;
    }

    public static QuestionSource source(long id) {
        QuestionSource source = new QuestionSource();
        source.setId(id);
        source.setSlug("zrodlo-" + id);
        if (id == AUTHORED) {
            source.setKind(SourceKind.AUTHORED);
            source.setName("Pytania autorskie");
        } else {
            source.setKind(SourceKind.EXAM_SHEET);
            source.setName("Arkusz " + id);
            source.setExamYear(2020 + (int) id);
        }
        return source;
    }

    /** Pytanie ABC (poprawna odpowiedź A) w podanym akcie, dziedzinie i źródle. */
    public static Question question(long id, long actId, long fieldId, long sourceId) {
        Question question = new Question();
        question.setId(id);
        question.setLegalAct(act(actId, fieldId));
        question.setSource(source(sourceId));
        question.setBody("P" + id);
        question.setOptionA("a");
        question.setOptionB("b");
        question.setOptionC("c");
        question.setCorrectOption(CorrectOption.A);
        return question;
    }

    /** Pytanie w akcie 1, dziedzinie 1 i własnym arkuszu. */
    public static Question question(long id) {
        return question(id, 1, 1, OWN_SHEET);
    }

    public static QuestionRef refOf(Question question) {
        long actId = question.getLegalAct().getId();
        long fieldId = question.getLegalAct().getLegalField().getId();
        long sourceId = question.getSource().getId();
        boolean flashcardHidden = question.isFlashcardHidden();
        LocalDate lawChangedAt = question.getLawChangedAt();
        return new QuestionRef() {
            @Override
            public Long getId() {
                return question.getId();
            }

            @Override
            public Long getLegalActId() {
                return actId;
            }

            @Override
            public Long getLegalFieldId() {
                return fieldId;
            }

            @Override
            public Long getSourceId() {
                return sourceId;
            }

            @Override
            public Boolean getFlashcardHidden() {
                return flashcardHidden;
            }

            @Override
            public LocalDate getLawChangedAt() {
                return lawChangedAt;
            }
        };
    }

    /**
     * Zachowuje się jak zapytanie o pytania puli: nieukryte, z aktu z {@code actIds} i ze źródła z {@code sourceIds}.
     */
    public static List<QuestionRef> inPool(Collection<Question> all, Collection<Long> actIds, Collection<Long> sourceIds) {
        return all.stream()
                .filter(q -> !q.isHidden())
                .filter(q -> actIds.contains(q.getLegalAct().getId()))
                .filter(q -> sourceIds.contains(q.getSource().getId()))
                .map(QuestionRefs::refOf)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Zachowuje się jak zapytanie o pytania po numerach: zwraca istniejące pytania z {@code ids}, ale w ODWROTNEJ
     * kolejności niż poproszono (baza nie gwarantuje kolejności), żeby testy pilnowały, że serwis układa ją sam.
     */
    public static List<Question> byIds(Collection<Question> all, Collection<Long> ids) {
        Set<Long> wanted = Set.copyOf(ids);
        List<Question> found = all.stream().filter(q -> wanted.contains(q.getId())).collect(Collectors.toList());
        List<Question> inRequestOrder = new ArrayList<>();
        for (Long id : ids) {
            found.stream().filter(q -> q.getId().equals(id)).findFirst().ifPresent(inRequestOrder::add);
        }
        java.util.Collections.reverse(inRequestOrder);
        return inRequestOrder;
    }
}

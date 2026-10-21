package pl.tutlaw.backend.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tutlaw.backend.admin.dto.AdminSectionResponse;
import pl.tutlaw.backend.common.exception.ConflictException;
import pl.tutlaw.backend.common.exception.ResourceNotFoundException;
import pl.tutlaw.backend.legal.ActSection;
import pl.tutlaw.backend.legal.ActSectionRepository;
import pl.tutlaw.backend.legal.LegalAct;
import pl.tutlaw.backend.legal.LegalActRepository;
import pl.tutlaw.backend.question.QuestionRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rozdziały aktów w panelu administratora: lista, dodawanie (pojedynczo albo wklejoną listą), zmiana nazwy, kolejność
 * i usuwanie (tylko rozdziału bez pytań). Nazwa zawiera numer rozdziału, tak jak ma się pokazać użytkownikowi w kreatorze
 * testu. Przypisanie pytań do rozdziałów jest w skryptach SQL (katalog baza/) i w edycji pytań.
 */
@Service
@RequiredArgsConstructor
public class AdminSectionService {

    private final ActSectionRepository sectionRepository;
    private final LegalActRepository actRepository;
    private final QuestionRepository questionRepository;

    @Transactional(readOnly = true)
    public List<AdminSectionResponse> list(Long actId) {
        requireAct(actId);
        return describe(actId);
    }

    @Transactional
    public List<AdminSectionResponse> create(Long actId, String rawName) {
        LegalAct act = requireAct(actId);
        String name = clean(rawName);
        List<ActSection> existing = sectionRepository.findByActOrdered(actId);
        requireFreeName(existing, null, name);
        sectionRepository.save(newSection(act, name, nextOrder(existing)));
        return describe(actId);
    }

    /**
     * Dodaje wiele rozdziałów naraz na koniec listy, w podanej kolejności. Puste pozycje i nazwy, które akt już ma
     * (albo które powtarzają się na liście), są pomijane.
     */
    @Transactional
    public List<AdminSectionResponse> createMany(Long actId, List<String> rawNames) {
        LegalAct act = requireAct(actId);
        List<ActSection> existing = sectionRepository.findByActOrdered(actId);
        Set<String> taken = new HashSet<>();
        for (ActSection section : existing) {
            taken.add(Slugs.fold(section.getName()));
        }
        int order = nextOrder(existing);
        List<ActSection> created = new ArrayList<>();
        for (String raw : rawNames) {
            String name = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
            if (name.isEmpty() || name.length() > 300 || !taken.add(Slugs.fold(name))) {
                continue;
            }
            created.add(newSection(act, name, order));
            order += AdminTrackService.SORT_STEP;
        }
        if (created.isEmpty()) {
            throw new IllegalArgumentException("Nie ma nic do dodania: wszystkie pozycje są puste albo akt już ma takie rozdziały.");
        }
        sectionRepository.saveAll(created);
        return describe(actId);
    }

    @Transactional
    public List<AdminSectionResponse> rename(Long sectionId, String rawName) {
        ActSection section = find(sectionId);
        Long actId = section.getLegalAct().getId();
        String name = clean(rawName);
        requireFreeName(sectionRepository.findByActOrdered(actId), sectionId, name);
        section.setName(name);
        sectionRepository.save(section);
        return describe(actId);
    }

    /** Ustawia kolejność rozdziałów aktu: {@code ids} to numery wszystkich jego rozdziałów w żądanej kolejności. */
    @Transactional
    public List<AdminSectionResponse> reorder(Long actId, List<Long> ids) {
        requireAct(actId);
        List<ActSection> existing = sectionRepository.findByActOrdered(actId);
        Set<Long> have = new HashSet<>();
        for (ActSection section : existing) {
            have.add(section.getId());
        }
        if (ids.size() != existing.size() || !have.equals(new HashSet<>(ids))) {
            throw new IllegalArgumentException("Lista rozdziałów jest nieaktualna. Odśwież stronę i spróbuj ponownie.");
        }
        Map<Long, ActSection> byId = new java.util.HashMap<>();
        for (ActSection section : existing) {
            byId.put(section.getId(), section);
        }
        int order = AdminTrackService.SORT_STEP;
        for (Long id : ids) {
            byId.get(id).setSortOrder(order);
            order += AdminTrackService.SORT_STEP;
        }
        sectionRepository.saveAll(existing);
        return describe(actId);
    }

    /** Usuwa rozdział bez pytań. Rozdział z pytaniami (nawet ukrytymi) zostaje: zdjęcie z nich przypisania byłoby nieodwracalne. */
    @Transactional
    public List<AdminSectionResponse> delete(Long sectionId) {
        ActSection section = find(sectionId);
        Long actId = section.getLegalAct().getId();
        long questions = questionRepository.countInSection(sectionId);
        if (questions > 0) {
            throw new ConflictException("Rozdział ma pytania (" + questions + "), więc nie można go usunąć.");
        }
        sectionRepository.delete(section);
        return describe(actId);
    }

    private List<AdminSectionResponse> describe(Long actId) {
        Map<Long, QuestionRepository.GroupTotals> totals = AdminMaps.totals(questionRepository.totalsBySection());
        List<AdminSectionResponse> result = new ArrayList<>();
        for (ActSection section : sectionRepository.findByActOrdered(actId)) {
            result.add(new AdminSectionResponse(section.getId(), actId, section.getName(), section.getSortOrder(),
                    AdminMaps.total(totals, section.getId()), AdminMaps.hidden(totals, section.getId())));
        }
        return result;
    }

    private static ActSection newSection(LegalAct act, String name, int order) {
        ActSection section = new ActSection();
        section.setLegalAct(act);
        section.setName(name);
        section.setSortOrder(order);
        return section;
    }

    private static int nextOrder(List<ActSection> existing) {
        return existing.stream().mapToInt(ActSection::getSortOrder).max().orElse(0) + AdminTrackService.SORT_STEP;
    }

    private LegalAct requireAct(Long actId) {
        return actRepository.findById(actId).orElseThrow(() -> new ResourceNotFoundException("Nie ma takiego aktu."));
    }

    private ActSection find(Long id) {
        return sectionRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Nie ma takiego rozdziału."));
    }

    private static void requireFreeName(List<ActSection> existing, Long selfId, String name) {
        for (ActSection other : existing) {
            if (!other.getId().equals(selfId) && Slugs.fold(other.getName()).equals(Slugs.fold(name))) {
                throw new ConflictException("Ten akt ma już rozdział o takiej nazwie.");
            }
        }
    }

    private static String clean(String text) {
        String trimmed = text == null ? "" : text.trim().replaceAll("\\s+", " ");
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Nazwa rozdziału nie może być pusta.");
        }
        return trimmed;
    }
}

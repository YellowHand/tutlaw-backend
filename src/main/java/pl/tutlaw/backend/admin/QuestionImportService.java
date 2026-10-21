package pl.tutlaw.backend.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tutlaw.backend.admin.dto.ImportFormat;
import pl.tutlaw.backend.admin.dto.ImportQuestionsRequest;
import pl.tutlaw.backend.admin.dto.ImportReportResponse;
import pl.tutlaw.backend.admin.dto.ImportRowResult;
import pl.tutlaw.backend.admin.dto.ImportRowStatus;
import pl.tutlaw.backend.legal.ActSection;
import pl.tutlaw.backend.legal.ActSectionRepository;
import pl.tutlaw.backend.legal.LegalAct;
import pl.tutlaw.backend.legal.LegalActRepository;
import pl.tutlaw.backend.question.CorrectOption;
import pl.tutlaw.backend.question.Question;
import pl.tutlaw.backend.question.QuestionBody;
import pl.tutlaw.backend.question.QuestionRepository;
import pl.tutlaw.backend.source.QuestionSource;
import pl.tutlaw.backend.source.QuestionSourceRepository;
import pl.tutlaw.backend.source.SourceKind;
import pl.tutlaw.backend.track.Track;
import pl.tutlaw.backend.track.TrackKind;
import pl.tutlaw.backend.track.TrackRepository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Import pytań z pliku CSV albo JSON. Zastępuje skrypty SQL z katalogu baza/ i trzyma się tych samych zasad: akt
 * wskazuje się adresem lub nazwą, źródło adresem, nazwą albo zapisem „Aplikacja rok” (brakujący arkusz zostaje założony),
 * rozdział nazwą z danego aktu (nieznana nazwa to błąd, żeby literówka nie zostawiła pytania bez rozdziału), a pytanie
 * o tej samej treści w tym samym źródle jest pomijane. Podgląd ({@code dryRun}) niczego nie zapisuje. Zapis jest
 * wszystko albo nic: gdy choć jeden wiersz ma błąd, nie powstaje nic, a raport wskazuje wszystkie błędy naraz.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QuestionImportService {

    static final int PREVIEW_LENGTH = 100;
    static final int MAX_REPORT_ROWS = 500;
    private static final int SLUG_MAX = 100;
    private static final Pattern SHEET_REFERENCE = Pattern.compile("^(.+?)[\\s\\-_/]*(\\d{4})$");
    private static final DateTimeFormatter POLISH_DATE = DateTimeFormatter.ofPattern("d.M.yyyy");

    private final QuestionRepository questionRepository;
    private final LegalActRepository actRepository;
    private final ActSectionRepository sectionRepository;
    private final QuestionSourceRepository sourceRepository;
    private final TrackRepository trackRepository;

    @Transactional
    public ImportReportResponse run(ImportQuestionsRequest request) {
        boolean dryRun = !Boolean.FALSE.equals(request.dryRun());
        ParsedImport parsed = request.format() == ImportFormat.JSON
                ? ImportParser.parseJson(request.content())
                : ImportParser.parseCsv(request.content());
        Lookup lookup = new Lookup();
        String defaultAct = firstNonNull(QuestionRules.clean(request.defaultAct()), parsed.defaults().get(ImportField.ACT));
        String defaultSource = firstNonNull(QuestionRules.clean(request.defaultSource()), parsed.defaults().get(ImportField.SOURCE));

        List<Planned> planned = new ArrayList<>();
        for (ImportRow row : parsed.rows()) {
            planned.add(plan(row, defaultAct, defaultSource, lookup));
        }
        markDuplicates(planned);

        List<ImportRowResult> results = new ArrayList<>();
        int ok = 0;
        int duplicates = 0;
        int errors = 0;
        Map<String, PendingSheet> newSheets = new LinkedHashMap<>();
        for (Planned p : planned) {
            results.add(new ImportRowResult(p.row.number(), p.status, p.message, preview(p.row.get(ImportField.BODY))));
            switch (p.status) {
                case OK -> {
                    ok++;
                    if (p.source.pending != null) {
                        newSheets.putIfAbsent(p.source.key(), p.source.pending);
                    }
                }
                case DUPLICATE -> duplicates++;
                case ERROR -> errors++;
            }
        }

        int created = 0;
        boolean saved = false;
        if (!dryRun && errors == 0 && ok > 0) {
            created = save(planned, newSheets);
            saved = true;
            log.info("Question import: {} questions saved, {} duplicates skipped, {} new sheets", created, duplicates, newSheets.size());
        }
        List<String> sheetNames = newSheets.values().stream().map(s -> AdminSheetService.derivedName(s.track, s.year)).toList();
        boolean truncated = results.size() > MAX_REPORT_ROWS;
        return new ImportReportResponse(dryRun, saved, planned.size(), ok, duplicates, errors, created, sheetNames,
                parsed.ignoredColumns(), truncated ? shorten(results) : results, truncated);
    }

    // ------------------------------------------------------------------------------------------------ planowanie

    private Planned plan(ImportRow row, String defaultAct, String defaultSource, Lookup lookup) {
        if (row.problem() != null) {
            return Planned.error(row, row.problem());
        }
        try {
            String actRef = firstNonNull(row.get(ImportField.ACT), defaultAct);
            if (actRef == null) {
                throw new IllegalArgumentException("Podaj akt prawny (kolumna „akt” albo akt domyślny).");
            }
            LegalAct act = lookup.act(actRef);
            String sourceRef = firstNonNull(row.get(ImportField.SOURCE), defaultSource);
            if (sourceRef == null) {
                throw new IllegalArgumentException("Podaj źródło pytania (kolumna „zrodlo” albo źródło domyślne).");
            }
            SourceChoice source = lookup.source(sourceRef);
            ActSection section = row.get(ImportField.SECTION) == null ? null : lookup.section(act, row.get(ImportField.SECTION));
            QuestionDraft draft = QuestionRules.draft(
                    row.get(ImportField.BODY), row.get(ImportField.OPTION_A), row.get(ImportField.OPTION_B),
                    row.get(ImportField.OPTION_C), correct(row.get(ImportField.CORRECT)),
                    row.get(ImportField.EXPLANATION), row.get(ImportField.LEGAL_BASIS),
                    date(row.get(ImportField.VALID_AS_OF), "obowiązuje od"),
                    date(row.get(ImportField.LAW_CHANGED_AT), "zmiana w prawie"),
                    row.get(ImportField.LAW_CHANGE_NOTE));
            return Planned.ok(row, act, section, source, draft);
        } catch (IllegalArgumentException e) {
            return Planned.error(row, e.getMessage());
        }
    }

    /** Powtórzenia: pytanie już istniejące w źródle albo wcześniej w pliku (pierwsze zostaje, następne są pomijane). */
    private void markDuplicates(List<Planned> planned) {
        Set<Long> sourceIds = new HashSet<>();
        for (Planned p : planned) {
            if (p.status == ImportRowStatus.OK && p.source.existing != null) {
                sourceIds.add(p.source.existing.getId());
            }
        }
        Map<String, Long> stored = new HashMap<>();
        if (!sourceIds.isEmpty()) {
            for (QuestionBody body : questionRepository.findBodiesInSources(sourceIds)) {
                stored.putIfAbsent(body.getSourceId() + "|" + QuestionRules.duplicateKey(body.getBody()), body.getId());
            }
        }
        Map<String, Integer> seen = new HashMap<>();
        for (Planned p : planned) {
            if (p.status != ImportRowStatus.OK) {
                continue;
            }
            String same = QuestionRules.duplicateKey(p.draft.body());
            String key = p.source.key() + "|" + same;
            Long storedId = p.source.existing == null ? null : stored.get(p.source.existing.getId() + "|" + same);
            if (storedId != null) {
                p.duplicate("Takie pytanie jest już w tym źródle (numer " + storedId + "). Pominięto.");
            } else if (seen.containsKey(key)) {
                p.duplicate("Powtórzone w pliku (taka sama treść jak w wierszu " + seen.get(key) + "). Pominięto.");
            } else {
                seen.put(key, p.row.number());
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------- zapis

    private int save(List<Planned> planned, Map<String, PendingSheet> newSheets) {
        Map<String, QuestionSource> created = new HashMap<>();
        for (Map.Entry<String, PendingSheet> entry : newSheets.entrySet()) {
            PendingSheet pending = entry.getValue();
            QuestionSource sheet = new QuestionSource();
            sheet.setKind(SourceKind.EXAM_SHEET);
            sheet.setTrack(pending.track);
            sheet.setExamYear(pending.year);
            sheet.setName(AdminSheetService.derivedName(pending.track, pending.year));
            sheet.setSlug(Slugs.unique(AdminSheetService.derivedSlug(pending.track, pending.year), SLUG_MAX,
                    slug -> sourceRepository.countBySlugValue(slug) > 0));
            sheet.setHidden(false);
            created.put(entry.getKey(), sourceRepository.save(sheet));
        }
        List<Question> questions = new ArrayList<>();
        for (Planned p : planned) {
            if (p.status != ImportRowStatus.OK) {
                continue;
            }
            Question q = new Question();
            q.setLegalAct(p.act);
            q.setSection(p.section);
            q.setSource(p.source.existing != null ? p.source.existing : created.get(p.source.key()));
            q.setBody(p.draft.body());
            q.setOptionA(p.draft.optionA());
            q.setOptionB(p.draft.optionB());
            q.setOptionC(p.draft.optionC());
            q.setCorrectOption(p.draft.correctOption());
            q.setExplanation(p.draft.explanation());
            q.setLegalBasis(p.draft.legalBasis());
            q.setValidAsOf(p.draft.validAsOf());
            q.setLawChangedAt(p.draft.lawChangedAt());
            q.setLawChangeNote(p.draft.lawChangeNote());
            questions.add(q);
        }
        questionRepository.saveAll(questions);
        return questions.size();
    }

    // ------------------------------------------------------------------------------------------- pola i raport

    private static CorrectOption correct(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Wskaż poprawną odpowiedź (A, B albo C).");
        }
        String letter = value.strip().toUpperCase(java.util.Locale.ROOT).replaceAll("[).:]+$", "");
        return switch (letter) {
            case "A" -> CorrectOption.A;
            case "B" -> CorrectOption.B;
            case "C" -> CorrectOption.C;
            default -> throw new IllegalArgumentException("Poprawna odpowiedź musi być A, B albo C (wpisano „" + value + "”).");
        };
    }

    private static LocalDate date(String value, String field) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value.strip());
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDate.parse(value.strip(), POLISH_DATE);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Nieprawidłowa data „" + value + "” w polu „" + field + "” (użyj RRRR-MM-DD albo D.M.RRRR).");
            }
        }
    }

    private static String preview(String body) {
        if (body == null) {
            return "";
        }
        String flat = body.replaceAll("\\s+", " ");
        return flat.codePointCount(0, flat.length()) <= PREVIEW_LENGTH ? flat
                : flat.substring(0, flat.offsetByCodePoints(0, PREVIEW_LENGTH)) + "…";
    }

    /** Skraca listę do {@link #MAX_REPORT_ROWS}: najpierw wszystkie błędy (do 200), potem pozostałe od początku pliku. */
    private static List<ImportRowResult> shorten(List<ImportRowResult> all) {
        List<ImportRowResult> keep = new ArrayList<>();
        int errorRoom = 200;
        for (ImportRowResult r : all) {
            if (r.status() == ImportRowStatus.ERROR && errorRoom-- > 0) {
                keep.add(r);
            }
        }
        for (ImportRowResult r : all) {
            if (keep.size() >= MAX_REPORT_ROWS) {
                break;
            }
            if (!keep.contains(r)) {
                keep.add(r);
            }
        }
        keep.sort(Comparator.comparingInt(ImportRowResult::row));
        return keep;
    }

    private static String firstNonNull(String first, String second) {
        return first != null ? first : second;
    }

    /** Nazwa do porównań: małe litery, bez polskich znaków, pojedyncze odstępy. */
    private static String norm(String text) {
        return Slugs.fold(QuestionRules.duplicateKey(text));
    }

    // ------------------------------------------------------------------------------------------ rozpoznawanie

    /** Akt, źródło i rozdział po adresie lub nazwie; dane z bazy ładowane raz, przy pierwszym użyciu. */
    private final class Lookup {

        private List<LegalAct> acts;
        private List<QuestionSource> sources;
        private List<Track> tracks;
        private final Map<Long, Map<String, ActSection>> sections = new HashMap<>();
        private final Map<String, SourceChoice> sourceCache = new HashMap<>();
        private final Map<String, PendingSheet> pendingSheets = new HashMap<>();

        LegalAct act(String ref) {
            if (acts == null) {
                acts = actRepository.findAll();
            }
            String key = norm(ref);
            List<LegalAct> found = acts.stream().filter(a -> a.getSlug().equalsIgnoreCase(ref.strip())).toList();
            if (found.isEmpty()) {
                found = acts.stream().filter(a -> norm(a.getName()).equals(key) || norm(a.getShortName()).equals(key)).toList();
            }
            if (found.size() > 1) {
                throw new IllegalArgumentException("Nazwa aktu „" + ref + "” pasuje do kilku aktów. Wpisz jego adres (slug).");
            }
            if (found.isEmpty()) {
                throw new IllegalArgumentException("Nie ma aktu prawnego „" + ref + "”. Użyj adresu (slug) albo nazwy z zakładki Akty prawne.");
            }
            return found.get(0);
        }

        ActSection section(LegalAct act, String name) {
            Map<String, ActSection> byName = sections.computeIfAbsent(act.getId(), id -> {
                Map<String, ActSection> map = new HashMap<>();
                for (ActSection s : sectionRepository.findByActOrdered(id)) {
                    map.putIfAbsent(norm(s.getName()), s);
                }
                return map;
            });
            ActSection found = byName.get(norm(name));
            if (found == null) {
                throw new IllegalArgumentException("Akt „" + act.getShortName() + "” nie ma rozdziału „" + name
                        + "”. Nazwa musi być taka jak w zakładce rozdziałów aktu.");
            }
            return found;
        }

        SourceChoice source(String ref) {
            SourceChoice cached = sourceCache.get(ref);
            if (cached != null) {
                return cached;
            }
            SourceChoice choice = resolveSource(ref);
            sourceCache.put(ref, choice);
            return choice;
        }

        private SourceChoice resolveSource(String ref) {
            if (sources == null) {
                sources = sourceRepository.findAll();
            }
            String key = norm(ref);
            List<QuestionSource> found = sources.stream().filter(s -> s.getSlug().equalsIgnoreCase(ref.strip())).toList();
            if (found.isEmpty()) {
                found = sources.stream().filter(s -> norm(s.getName()).equals(key)).toList();
            }
            if (found.size() > 1) {
                throw new IllegalArgumentException("Nazwa źródła „" + ref + "” pasuje do kilku źródeł. Wpisz jego adres (slug).");
            }
            if (found.size() == 1) {
                return SourceChoice.of(found.get(0));
            }
            Matcher m = SHEET_REFERENCE.matcher(ref.strip());
            if (m.matches()) {
                SourceChoice sheet = sheetOfApplication(m.group(1).strip(), Integer.parseInt(m.group(2)));
                if (sheet != null) {
                    return sheet;
                }
            }
            throw new IllegalArgumentException("Nie ma źródła „" + ref + "”. Wpisz adres albo nazwę arkusza (np. „Notarialna 2025”) "
                    + "albo „autorskie”.");
        }

        /** Arkusz aplikacji z danego roku: istniejący albo (gdy aplikacja jest, a arkusza nie) do założenia przy zapisie. */
        private SourceChoice sheetOfApplication(String prefix, int year) {
            if (year < 1990 || year > 2100) {
                return null;
            }
            if (tracks == null) {
                tracks = trackRepository.findAllByOrderBySortOrderAscIdAsc();
            }
            String key = norm(prefix);
            List<Track> matching = tracks.stream().filter(t -> t.getKind() == TrackKind.EXAM
                    && (norm(t.getSlug()).equals(key) || norm(t.getShortName()).equals(key) || norm(t.getName()).equals(key))).toList();
            if (matching.size() != 1) {
                if (matching.size() > 1) {
                    throw new IllegalArgumentException("Nazwa aplikacji „" + prefix + "” pasuje do kilku aplikacji. Wpisz adres arkusza.");
                }
                return null;
            }
            Track track = matching.get(0);
            for (QuestionSource s : sources) {
                if (s.isExamSheet() && s.getTrack().getId().equals(track.getId()) && year == s.getExamYear()) {
                    return SourceChoice.of(s);
                }
            }
            PendingSheet pending = pendingSheets.computeIfAbsent(track.getId() + ":" + year, k -> new PendingSheet(track, year));
            return SourceChoice.pending(pending);
        }
    }

    // ---------------------------------------------------------------------------------------------- typy pomocnicze

    private record PendingSheet(Track track, int year) {
    }

    /** Źródło wybrane dla wiersza: istniejące albo arkusz do założenia. */
    private static final class SourceChoice {
        final QuestionSource existing;
        final PendingSheet pending;

        private SourceChoice(QuestionSource existing, PendingSheet pending) {
            this.existing = existing;
            this.pending = pending;
        }

        static SourceChoice of(QuestionSource source) {
            return new SourceChoice(source, null);
        }

        static SourceChoice pending(PendingSheet sheet) {
            return new SourceChoice(null, sheet);
        }

        /** Klucz źródła do wykrywania powtórzeń w pliku. */
        String key() {
            return existing != null ? "s" + existing.getId() : "p" + pending.track.getId() + ":" + pending.year;
        }
    }

    /** Wiersz po sprawdzeniu: gotowe pytanie albo błąd/powtórzenie z opisem. */
    private static final class Planned {
        final ImportRow row;
        ImportRowStatus status;
        String message;
        LegalAct act;
        ActSection section;
        SourceChoice source;
        QuestionDraft draft;

        private Planned(ImportRow row) {
            this.row = row;
        }

        static Planned ok(ImportRow row, LegalAct act, ActSection section, SourceChoice source, QuestionDraft draft) {
            Planned p = new Planned(row);
            p.status = ImportRowStatus.OK;
            p.act = act;
            p.section = section;
            p.source = source;
            p.draft = draft;
            return p;
        }

        static Planned error(ImportRow row, String message) {
            Planned p = new Planned(row);
            p.status = ImportRowStatus.ERROR;
            p.message = message;
            return p;
        }

        void duplicate(String message) {
            this.status = ImportRowStatus.DUPLICATE;
            this.message = message;
        }
    }
}

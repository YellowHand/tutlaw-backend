package pl.gradusiuris.backend.mark;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import pl.gradusiuris.backend.common.PageParams;
import pl.gradusiuris.backend.common.PageResponse;
import pl.gradusiuris.backend.common.exception.ResourceNotFoundException;
import pl.gradusiuris.backend.mark.dto.BulkMarkRequest;
import pl.gradusiuris.backend.mark.dto.BulkMarkResponse;
import pl.gradusiuris.backend.mark.dto.MarkAnalysisResponse;
import pl.gradusiuris.backend.mark.dto.MarkSummaryResponse;
import pl.gradusiuris.backend.mark.dto.MarkedQuestionResponse;
import pl.gradusiuris.backend.mark.dto.QuestionMarkResponse;
import pl.gradusiuris.backend.question.CorrectOption;
import pl.gradusiuris.backend.question.QuestionRepository;
import pl.gradusiuris.backend.question.QuestionText;
import pl.gradusiuris.backend.testsupport.FakeRepos;
import pl.gradusiuris.backend.testsupport.MutableClock;
import pl.gradusiuris.backend.user.CurrentUserResolver;
import pl.gradusiuris.backend.user.User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Oznaczenia pytań na prawdziwym QuestionMarkService, z repozytoriami „w pamięci". */
class QuestionMarkServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 12, 0);

    private static final String TERM_BODY = "W jakim terminie wnosi się apelację?";
    private static final String PLAIN_BODY = "Kto jest stroną umowy sprzedaży?";

    private static final class Text implements QuestionText {
        private final long id;
        private final String body;
        private final String a;
        private final String b;
        private final String c;

        Text(long id, String body, String a, String b, String c) {
            this.id = id;
            this.body = body;
            this.a = a;
            this.b = b;
            this.c = c;
        }

        @Override public Long getId() { return id; }
        @Override public String getBody() { return body; }
        @Override public String getOptionA() { return a; }
        @Override public String getOptionB() { return b; }
        @Override public String getOptionC() { return c; }
    }

    private static final class Row implements QuestionMarkRepository.MarkedQuestion {
        private final QuestionMark mark;

        Row(QuestionMark mark) {
            this.mark = mark;
        }

        @Override public Long getId() { return mark.getId(); }
        @Override public Long getQuestionId() { return mark.getQuestionId(); }
        @Override public QuestionMarkType getMark() { return mark.getMark(); }
        @Override public MarkOrigin getOrigin() { return mark.getOrigin(); }
        @Override public MarkStatus getStatus() { return mark.getStatus(); }
        @Override public String getBody() { return "Treść " + mark.getQuestionId(); }
        @Override public String getOptionA() { return "A"; }
        @Override public String getOptionB() { return "B"; }
        @Override public String getOptionC() { return "C"; }
        @Override public CorrectOption getCorrectOption() { return CorrectOption.A; }
        @Override public String getSourceName() { return "Egzamin 2024"; }
        @Override public String getActShortName() { return "k.c."; }
    }

    private static final class Fixture {
        final MutableClock clock = MutableClock.at("2026-10-12T10:00:00Z");
        final Map<Long, Text> questions = new TreeMap<>();
        final List<QuestionMark> marks = new ArrayList<>();
        final List<Integer> batchSizes = new ArrayList<>();
        final Authentication admin = FakeRepos.authentication("admin@gmail.com");
        final QuestionMarkService service;

        Fixture() {
            List<User> users = new ArrayList<>();
            User user = new User();
            user.setId(9L);
            user.setPrivateEmail("admin@gmail.com");
            users.add(user);

            AtomicLong ids = new AtomicLong(1);
            QuestionMarkRepository markRepository = FakeRepos.proxy(QuestionMarkRepository.class, (method, args) -> {
                switch (method) {
                    case "findAllKeys":
                        return marks.stream().map(m -> (QuestionMarkRepository.MarkKey) new QuestionMarkRepository.MarkKey() {
                            @Override public Long getQuestionId() { return m.getQuestionId(); }
                            @Override public QuestionMarkType getMark() { return m.getMark(); }
                        }).toList();
                    case "saveAll": {
                        List<QuestionMark> saved = new ArrayList<>();
                        for (Object o : (Iterable<?>) args[0]) {
                            QuestionMark m = (QuestionMark) o;
                            if (m.getId() == null) m.setId(ids.getAndIncrement());
                            marks.add(m);
                            saved.add(m);
                        }
                        return saved;
                    }
                    case "save": {
                        QuestionMark m = (QuestionMark) args[0];
                        if (m.getId() == null) m.setId(ids.getAndIncrement());
                        if (!marks.contains(m)) marks.add(m);
                        return m;
                    }
                    case "findByQuestionIdAndMark":
                        return marks.stream()
                                .filter(m -> m.getQuestionId().equals(args[0]) && m.getMark() == args[1]).findFirst();
                    case "countByMarkAndStatus": {
                        List<QuestionMarkRepository.MarkCount> counts = new ArrayList<>();
                        for (QuestionMarkType type : QuestionMarkType.values()) {
                            for (MarkStatus status : MarkStatus.values()) {
                                long total = marks.stream().filter(m -> m.getMark() == type && m.getStatus() == status).count();
                                if (total > 0) {
                                    counts.add(new QuestionMarkRepository.MarkCount() {
                                        @Override public QuestionMarkType getMark() { return type; }
                                        @Override public MarkStatus getStatus() { return status; }
                                        @Override public Long getTotal() { return total; }
                                    });
                                }
                            }
                        }
                        return counts;
                    }
                    case "changeStatus": {
                        int changed = 0;
                        for (QuestionMark m : marks) {
                            if (m.getStatus() == args[0] && ((Collection<?>) args[2]).contains(m.getMark())) {
                                m.setStatus((MarkStatus) args[1]);
                                m.setReviewedAt((LocalDateTime) args[3]);
                                m.setReviewedBy((Long) args[4]);
                                changed++;
                            }
                        }
                        return changed;
                    }
                    case "findForReview": {
                        List<QuestionMarkRepository.MarkedQuestion> rows = marks.stream()
                                .filter(m -> m.getStatus() == args[0] && ((Collection<?>) args[1]).contains(m.getMark()))
                                .sorted(Comparator.comparing(QuestionMark::getQuestionId).thenComparing(QuestionMark::getId))
                                .map(m -> (QuestionMarkRepository.MarkedQuestion) new Row(m))
                                .toList();
                        Pageable pageable = (Pageable) args[2];
                        int from = Math.min(rows.size(), pageable.getPageNumber() * pageable.getPageSize());
                        int to = Math.min(rows.size(), from + pageable.getPageSize());
                        return new PageImpl<>(rows.subList(from, to), pageable, rows.size());
                    }
                    default:
                        throw new UnsupportedOperationException("QuestionMarkRepository." + method);
                }
            });

            QuestionRepository questionRepository = FakeRepos.proxy(QuestionRepository.class, (method, args) -> {
                switch (method) {
                    case "findTextsAfter": {
                        Pageable pageable = (Pageable) args[1];
                        batchSizes.add(pageable.getPageSize());
                        return questions.values().stream()
                                .filter(q -> q.getId() > (Long) args[0])
                                .limit(pageable.getPageSize())
                                .map(q -> (QuestionText) q)
                                .toList();
                    }
                    case "count":
                        return (long) questions.size();
                    case "existsById":
                        return questions.containsKey(args[0]);
                    default:
                        throw new UnsupportedOperationException("QuestionRepository." + method);
                }
            });

            service = new QuestionMarkService(
                    markRepository, questionRepository, new CurrentUserResolver(FakeRepos.users(users)), clock);
        }

        void question(long id, String body, String a, String b, String c) {
            questions.put(id, new Text(id, body, a, b, c));
        }

        void termQuestion(long id) {
            question(id, TERM_BODY, "7 dni", "14 dni", "30 dni");
        }

        void plainQuestion(long id) {
            question(id, PLAIN_BODY, "kupujący", "sprzedawca", "obaj");
        }

        QuestionMark mark(long questionId, QuestionMarkType type, MarkOrigin origin, MarkStatus status) {
            QuestionMark m = new QuestionMark();
            m.setId((long) marks.size() + 1000);
            m.setQuestionId(questionId);
            m.setMark(type);
            m.setOrigin(origin);
            m.setStatus(status);
            m.setCreatedAt(NOW.minusDays(3));
            if (status != MarkStatus.SUGGESTED) {
                m.setReviewedAt(NOW.minusDays(2));
                m.setReviewedBy(1L);
            }
            marks.add(m);
            return m;
        }

        QuestionMark find(long questionId, QuestionMarkType type) {
            return marks.stream().filter(m -> m.getQuestionId() == questionId && m.getMark() == type).findFirst().orElseThrow();
        }
    }

    // ---------------------------------------------------------------- analiza

    @Test
    void analyze_addsSuggestionsForTheDetectedMarks_andNothingElse() {
        Fixture f = new Fixture();
        f.termQuestion(1);
        f.plainQuestion(2);

        MarkAnalysisResponse result = f.service.analyze();

        assertThat(result.questionsScanned()).isEqualTo(2);
        assertThat(f.marks).extracting(QuestionMark::getQuestionId).containsOnly(1L);
        assertThat(f.marks).extracting(QuestionMark::getMark)
                .containsExactlyInAnyOrder(QuestionMarkType.TIME_LIMIT, QuestionMarkType.TRAP_NUMBERS);
        assertThat(f.marks).allSatisfy(m -> {
            assertThat(m.getOrigin()).isEqualTo(MarkOrigin.AUTO);
            assertThat(m.getStatus()).isEqualTo(MarkStatus.SUGGESTED); // automat niczego nie zatwierdza
            assertThat(m.getReviewedAt()).isNull();
            assertThat(m.getCreatedAt()).isEqualTo(NOW);
        });
        assertThat(result.suggested()).isEqualTo(2);
        assertThat(result.suggestedByMark().get(QuestionMarkType.TIME_LIMIT)).isEqualTo(1);
        assertThat(result.suggestedByMark().get(QuestionMarkType.TRAP_NUMBERS)).isEqualTo(1);
        assertThat(result.suggestedByMark().get(QuestionMarkType.TRAP_NEGATION)).isZero();
        assertThat(result.suggestedByMark()).containsKeys(QuestionMarkType.values());
    }

    @Test
    void analyze_neverTouchesExistingDecisions_andRunningItAgainAddsNothing() {
        Fixture f = new Fixture();
        f.termQuestion(1);
        // administrator odrzucił termin i zatwierdził liczby (jedno ręcznie, drugie jako propozycję automatu)
        QuestionMark rejected = f.mark(1, QuestionMarkType.TIME_LIMIT, MarkOrigin.AUTO, MarkStatus.REJECTED);
        QuestionMark confirmed = f.mark(1, QuestionMarkType.TRAP_NUMBERS, MarkOrigin.MANUAL, MarkStatus.CONFIRMED);

        MarkAnalysisResponse first = f.service.analyze();
        MarkAnalysisResponse second = f.service.analyze();

        assertThat(first.suggested()).isZero();
        assertThat(second.suggested()).isZero();
        assertThat(f.marks).containsExactly(rejected, confirmed);
        assertThat(rejected.getStatus()).isEqualTo(MarkStatus.REJECTED);
        assertThat(confirmed.getStatus()).isEqualTo(MarkStatus.CONFIRMED);
    }

    @Test
    void analyze_secondRunAfterNewQuestionsAddsOnlyTheNewOnes() {
        Fixture f = new Fixture();
        f.termQuestion(1);
        f.service.analyze();
        int before = f.marks.size();
        f.termQuestion(2);

        MarkAnalysisResponse again = f.service.analyze();

        assertThat(again.suggested()).isEqualTo(2);
        assertThat(f.marks).hasSize(before + 2);
        assertThat(f.marks.stream().filter(m -> m.getQuestionId() == 1)).hasSize(before);
    }

    @Test
    void analyze_readsQuestionsInBatches_andVisitsEveryQuestionOnce() {
        Fixture f = new Fixture();
        for (long id = 1; id <= 1_200; id++) {
            f.termQuestion(id);
        }

        MarkAnalysisResponse result = f.service.analyze();

        assertThat(result.questionsScanned()).isEqualTo(1_200);
        assertThat(f.batchSizes).containsOnly(QuestionMarkService.BATCH_SIZE);
        assertThat(f.batchSizes).hasSize(4); // 500 + 500 + 200 i pusta partia kończąca
        assertThat(f.marks).hasSize(2_400);
    }

    @Test
    void analyze_withNoQuestionsDoesNothing() {
        Fixture f = new Fixture();

        MarkAnalysisResponse result = f.service.analyze();

        assertThat(result.questionsScanned()).isZero();
        assertThat(result.suggested()).isZero();
        assertThat(f.marks).isEmpty();
    }

    // ---------------------------------------------------------------- podsumowanie

    @Test
    void summary_countsEveryMarkInEveryStatus_andIncludesZeroRows() {
        Fixture f = new Fixture();
        f.plainQuestion(1);
        f.plainQuestion(2);
        f.mark(1, QuestionMarkType.TIME_LIMIT, MarkOrigin.AUTO, MarkStatus.SUGGESTED);
        f.mark(2, QuestionMarkType.TIME_LIMIT, MarkOrigin.AUTO, MarkStatus.SUGGESTED);
        f.mark(1, QuestionMarkType.TRAP_NEGATION, MarkOrigin.MANUAL, MarkStatus.CONFIRMED);
        f.mark(2, QuestionMarkType.TRAP_NEGATION, MarkOrigin.AUTO, MarkStatus.REJECTED);

        MarkSummaryResponse summary = f.service.summary();

        assertThat(summary.questions()).isEqualTo(2);
        assertThat(summary.marks()).extracting(MarkSummaryResponse.Row::mark).containsExactly(QuestionMarkType.values());
        MarkSummaryResponse.Row time = summary.marks().get(0);
        assertThat(time.suggested()).isEqualTo(2);
        assertThat(time.confirmed()).isZero();
        MarkSummaryResponse.Row negation = summary.marks().get(1);
        assertThat(negation.confirmed()).isEqualTo(1);
        assertThat(negation.rejected()).isEqualTo(1);
        assertThat(summary.marks().get(5).suggested()).isZero(); // TRAP_OUTDATED_LAW: wiersz z zerami
    }

    // ---------------------------------------------------------------- przegląd

    @Test
    void list_defaultsToSuggestionsAndFiltersByMark_inPages() {
        Fixture f = new Fixture();
        f.mark(3, QuestionMarkType.TIME_LIMIT, MarkOrigin.AUTO, MarkStatus.SUGGESTED);
        f.mark(1, QuestionMarkType.TIME_LIMIT, MarkOrigin.AUTO, MarkStatus.SUGGESTED);
        f.mark(2, QuestionMarkType.TRAP_NEGATION, MarkOrigin.AUTO, MarkStatus.SUGGESTED);
        f.mark(4, QuestionMarkType.TIME_LIMIT, MarkOrigin.AUTO, MarkStatus.CONFIRMED);

        PageResponse<MarkedQuestionResponse> all = f.service.list(null, null, PageParams.of(0, 2));
        PageResponse<MarkedQuestionResponse> rest = f.service.list(null, null, PageParams.of(1, 2));
        PageResponse<MarkedQuestionResponse> onlyTime = f.service.list(MarkStatus.SUGGESTED, QuestionMarkType.TIME_LIMIT, PageParams.of(0, 10));
        PageResponse<MarkedQuestionResponse> confirmed = f.service.list(MarkStatus.CONFIRMED, null, PageParams.of(0, 10));

        assertThat(all.items()).extracting(MarkedQuestionResponse::questionId).containsExactly(1L, 2L);
        assertThat(all.totalElements()).isEqualTo(3);
        assertThat(all.totalPages()).isEqualTo(2);
        assertThat(rest.items()).extracting(MarkedQuestionResponse::questionId).containsExactly(3L);
        assertThat(onlyTime.items()).extracting(MarkedQuestionResponse::questionId).containsExactly(1L, 3L);
        assertThat(confirmed.items()).extracting(MarkedQuestionResponse::questionId).containsExactly(4L);
        assertThat(all.items().get(0).body()).isEqualTo("Treść 1");
        assertThat(all.items().get(0).correctOption()).isEqualTo(CorrectOption.A);
    }

    // ---------------------------------------------------------------- hurtowo

    @Test
    void bulk_confirmsAllSuggestions_andRecordsWhoAndWhen() {
        Fixture f = new Fixture();
        QuestionMark one = f.mark(1, QuestionMarkType.TIME_LIMIT, MarkOrigin.AUTO, MarkStatus.SUGGESTED);
        QuestionMark two = f.mark(2, QuestionMarkType.TRAP_NEGATION, MarkOrigin.AUTO, MarkStatus.SUGGESTED);
        QuestionMark rejected = f.mark(3, QuestionMarkType.TIME_LIMIT, MarkOrigin.AUTO, MarkStatus.REJECTED);

        BulkMarkResponse result = f.service.bulk(
                new BulkMarkRequest(MarkStatus.SUGGESTED, MarkStatus.CONFIRMED, null), f.admin);

        assertThat(result.updated()).isEqualTo(2);
        assertThat(one.getStatus()).isEqualTo(MarkStatus.CONFIRMED);
        assertThat(two.getStatus()).isEqualTo(MarkStatus.CONFIRMED);
        assertThat(one.getReviewedAt()).isEqualTo(NOW);
        assertThat(one.getReviewedBy()).isEqualTo(9L);
        assertThat(rejected.getStatus()).isEqualTo(MarkStatus.REJECTED); // odrzucone zostają odrzucone
    }

    @Test
    void bulk_canBeLimitedToSomeMarks() {
        Fixture f = new Fixture();
        QuestionMark time = f.mark(1, QuestionMarkType.TIME_LIMIT, MarkOrigin.AUTO, MarkStatus.SUGGESTED);
        QuestionMark negation = f.mark(1, QuestionMarkType.TRAP_NEGATION, MarkOrigin.AUTO, MarkStatus.SUGGESTED);

        BulkMarkResponse result = f.service.bulk(new BulkMarkRequest(
                MarkStatus.SUGGESTED, MarkStatus.REJECTED, List.of(QuestionMarkType.TRAP_NEGATION, QuestionMarkType.TRAP_NEGATION)),
                f.admin);

        assertThat(result.updated()).isEqualTo(1);
        assertThat(negation.getStatus()).isEqualTo(MarkStatus.REJECTED);
        assertThat(time.getStatus()).isEqualTo(MarkStatus.SUGGESTED);
    }

    @Test
    void bulk_refusesToGoBackToSuggestionsOrToChangeNothing() {
        Fixture f = new Fixture();
        f.mark(1, QuestionMarkType.TIME_LIMIT, MarkOrigin.AUTO, MarkStatus.CONFIRMED);

        assertThatThrownBy(() -> f.service.bulk(new BulkMarkRequest(MarkStatus.CONFIRMED, MarkStatus.SUGGESTED, null), f.admin))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> f.service.bulk(new BulkMarkRequest(MarkStatus.CONFIRMED, MarkStatus.CONFIRMED, null), f.admin))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(f.find(1, QuestionMarkType.TIME_LIMIT).getStatus()).isEqualTo(MarkStatus.CONFIRMED);
    }

    // ---------------------------------------------------------------- pojedyncza decyzja

    @Test
    void decide_confirmingAQuestionWithoutAMarkCreatesAManualOne() {
        Fixture f = new Fixture();
        f.plainQuestion(1);

        QuestionMarkResponse result = f.service.decide(1L, QuestionMarkType.TRAP_OUTDATED_LAW, MarkStatus.CONFIRMED, f.admin);

        assertThat(result.origin()).isEqualTo(MarkOrigin.MANUAL);
        assertThat(result.status()).isEqualTo(MarkStatus.CONFIRMED);
        QuestionMark saved = f.find(1, QuestionMarkType.TRAP_OUTDATED_LAW);
        assertThat(saved.getOrigin()).isEqualTo(MarkOrigin.MANUAL);
        assertThat(saved.getReviewedAt()).isEqualTo(NOW);
        assertThat(saved.getReviewedBy()).isEqualTo(9L);
    }

    @Test
    void decide_confirmingASuggestionKeepsItsOrigin() {
        Fixture f = new Fixture();
        f.plainQuestion(1);
        f.mark(1, QuestionMarkType.TIME_LIMIT, MarkOrigin.AUTO, MarkStatus.SUGGESTED);

        QuestionMarkResponse result = f.service.decide(1L, QuestionMarkType.TIME_LIMIT, MarkStatus.CONFIRMED, f.admin);

        assertThat(result.origin()).isEqualTo(MarkOrigin.AUTO);
        assertThat(result.status()).isEqualTo(MarkStatus.CONFIRMED);
        assertThat(f.marks).hasSize(1);
    }

    @Test
    void decide_rejectingTakesAMarkBack_andAutomatDoesNotProposeItAgain() {
        Fixture f = new Fixture();
        f.termQuestion(1);
        f.mark(1, QuestionMarkType.TIME_LIMIT, MarkOrigin.MANUAL, MarkStatus.CONFIRMED);

        f.service.decide(1L, QuestionMarkType.TIME_LIMIT, MarkStatus.REJECTED, f.admin);
        f.service.analyze();

        assertThat(f.find(1, QuestionMarkType.TIME_LIMIT).getStatus()).isEqualTo(MarkStatus.REJECTED);
        assertThat(f.marks.stream().filter(m -> m.getMark() == QuestionMarkType.TIME_LIMIT)).hasSize(1);
    }

    @Test
    void decide_refusesToTurnAMarkIntoASuggestion_andOfAMissingQuestion() {
        Fixture f = new Fixture();
        f.plainQuestion(1);

        assertThatThrownBy(() -> f.service.decide(1L, QuestionMarkType.TIME_LIMIT, MarkStatus.SUGGESTED, f.admin))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> f.service.decide(99L, QuestionMarkType.TIME_LIMIT, MarkStatus.CONFIRMED, f.admin))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(f.marks).isEmpty();
    }
}

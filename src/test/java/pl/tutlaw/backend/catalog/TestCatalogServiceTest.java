package pl.tutlaw.backend.catalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import pl.tutlaw.backend.catalog.dto.TestCatalogResponse;
import pl.tutlaw.backend.common.GroupCount;
import pl.tutlaw.backend.legal.LegalAct;
import pl.tutlaw.backend.legal.LegalActRepository;
import pl.tutlaw.backend.legal.LegalField;
import pl.tutlaw.backend.legal.LegalFieldRepository;
import pl.tutlaw.backend.pool.Pool;
import pl.tutlaw.backend.pool.SourceFilter;
import pl.tutlaw.backend.question.QuestionRepository;
import pl.tutlaw.backend.review.ReviewLogRepository;
import pl.tutlaw.backend.source.QuestionSourceRepository;
import pl.tutlaw.backend.source.SourceKind;
import pl.tutlaw.backend.testsupport.Pools;
import pl.tutlaw.backend.testsupport.QuestionRefs;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Katalog Testów liczy pytania wyłącznie z aktów puli użytkownika i z wybranych przez niego źródeł. */
@ExtendWith(MockitoExtension.class)
class TestCatalogServiceTest {

    @Mock private LegalFieldRepository fieldRepository;
    @Mock private LegalActRepository actRepository;
    @Mock private QuestionSourceRepository sourceRepository;
    @Mock private QuestionRepository questionRepository;
    @Mock private ReviewLogRepository reviewLogRepository;
    @Mock private CurrentUserResolver currentUserResolver;
    private final Authentication authentication = mock(Authentication.class);

    private Pool pool = Pools.all();
    private TestCatalogService service;

    @BeforeEach
    void setUp() {
        service = new TestCatalogService(fieldRepository, actRepository, sourceRepository, questionRepository,
                reviewLogRepository, currentUserResolver, Pools.holding(() -> pool));
        User user = new User();
        user.setId(5L);
        when(currentUserResolver.resolve(authentication)).thenReturn(user);
    }

    private static GroupCount count(long groupId, long total) {
        return new GroupCount() {
            public Long getGroupId() { return groupId; }
            public Long getTotal() { return total; }
        };
    }

    private static QuestionSourceRepository.SourceRow sourceRow(long id, String name, SourceKind kind, Integer year) {
        return new QuestionSourceRepository.SourceRow() {
            public Long getId() { return id; }
            public String getSlug() { return "zrodlo-" + id; }
            public SourceKind getKind() { return kind; }
            public String getName() { return name; }
            public Long getTrackId() { return null; }
            public Integer getExamYear() { return year; }
        };
    }

    @Test
    void getCatalog_asksEveryCounterForThePoolActsAndAllowedSourcesOnly() {
        pool = Pools.of(new SourceFilter(true, false, true), 1, 2);
        when(fieldRepository.findAll()).thenReturn(List.of());
        when(actRepository.findAllWithFieldByIdIn(pool.actIds())).thenReturn(List.of());
        when(sourceRepository.findAllRows()).thenReturn(List.of());
        when(questionRepository.countByAct(pool.actIds(), pool.sourceIds())).thenReturn(List.of());
        when(questionRepository.countBySource(pool.actIds(), pool.sourceIds())).thenReturn(List.of());
        when(reviewLogRepository.countSolvedTestQuestionsByAct(5L, pool.actIds(), pool.sourceIds())).thenReturn(List.of());
        when(reviewLogRepository.countSolvedTestQuestionsBySource(5L, pool.actIds(), pool.sourceIds())).thenReturn(List.of());

        service.getCatalog(authentication);

        assertThat(pool.sourceIds()).containsExactlyInAnyOrder(QuestionRefs.OWN_SHEET, QuestionRefs.AUTHORED);
        verify(questionRepository).countByAct(pool.actIds(), pool.sourceIds());
        verify(questionRepository).countBySource(pool.actIds(), pool.sourceIds());
        verify(reviewLogRepository).countSolvedTestQuestionsByAct(5L, pool.actIds(), pool.sourceIds());
        verify(reviewLogRepository).countSolvedTestQuestionsBySource(5L, pool.actIds(), pool.sourceIds());
    }

    @Test
    void getCatalog_putsTheCountsIntoTheTree_andHidesSourcesTheFilterTurnedOff() {
        pool = Pools.of(new SourceFilter(true, false, false), 10);
        LegalAct act = QuestionRefs.act(10, 2);
        act.setName("Kodeks karny");
        LegalField field = act.getLegalField();
        field.setName("Prawo karne i proces karny");
        when(fieldRepository.findAll()).thenReturn(List.of(field));
        when(actRepository.findAllWithFieldByIdIn(pool.actIds())).thenReturn(List.of(act));
        when(sourceRepository.findAllRows()).thenReturn(List.of(
                sourceRow(QuestionRefs.OWN_SHEET, "Egzamin notarialny 2023", SourceKind.EXAM_SHEET, 2023),
                sourceRow(QuestionRefs.OTHER_SHEET, "Egzamin komorniczy 2022", SourceKind.EXAM_SHEET, 2022),
                sourceRow(QuestionRefs.AUTHORED, "Pytania autorskie", SourceKind.AUTHORED, null)));
        when(questionRepository.countByAct(any(), any())).thenReturn(List.of(count(10, 12)));
        when(questionRepository.countBySource(any(), any())).thenReturn(List.of(count(QuestionRefs.OWN_SHEET, 12)));
        when(reviewLogRepository.countSolvedTestQuestionsByAct(eq(5L), any(), any())).thenReturn(List.of(count(10, 4)));
        when(reviewLogRepository.countSolvedTestQuestionsBySource(eq(5L), any(), any()))
                .thenReturn(List.of(count(QuestionRefs.OWN_SHEET, 4)));

        TestCatalogResponse response = service.getCatalog(authentication);

        TestCatalogResponse.FieldNode node = response.fields().get(0);
        assertThat(node.questionCount()).isEqualTo(12);
        assertThat(node.solvedCount()).isEqualTo(4);
        assertThat(node.acts()).extracting(TestCatalogResponse.ActNode::name).containsExactly("Kodeks karny");
        // filtr zostawił tylko własne arkusze: tylko one są na liście źródeł
        assertThat(response.sources()).extracting(TestCatalogResponse.SourceNode::id).containsExactly(QuestionRefs.OWN_SHEET);
        assertThat(response.sources().get(0).group()).isEqualTo(SourceFilter.OWN_SHEETS);
    }

    @Test
    void getCatalog_reportsTheNarrowingOfThePool() {
        pool = Pools.narrowedTo(Pools.all(), 1);
        when(fieldRepository.findAll()).thenReturn(List.of());
        when(actRepository.findAllWithFieldByIdIn(any())).thenReturn(List.of());
        when(sourceRepository.findAllRows()).thenReturn(List.of());
        when(questionRepository.countByAct(any(), any())).thenReturn(List.of());
        when(questionRepository.countBySource(any(), any())).thenReturn(List.of());
        when(reviewLogRepository.countSolvedTestQuestionsByAct(eq(5L), any(), any())).thenReturn(List.of());
        when(reviewLogRepository.countSolvedTestQuestionsBySource(eq(5L), any(), any())).thenReturn(List.of());

        assertThat(service.getCatalog(authentication).narrowed()).isTrue();
        verify(actRepository).findAllWithFieldByIdIn(pool.actIds());
    }
}

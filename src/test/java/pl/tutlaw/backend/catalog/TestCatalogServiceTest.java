package pl.tutlaw.backend.catalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import pl.tutlaw.backend.catalog.dto.TestCatalogResponse;
import pl.tutlaw.backend.common.GroupCount;
import pl.tutlaw.backend.course.CourseRepository;
import pl.tutlaw.backend.legal.ActSectionRepository;
import pl.tutlaw.backend.legal.LegalAct;
import pl.tutlaw.backend.legal.LegalActRepository;
import pl.tutlaw.backend.legal.LegalField;
import pl.tutlaw.backend.legal.LegalFieldRepository;
import pl.tutlaw.backend.pool.Pool;
import pl.tutlaw.backend.pool.SourceMode;
import pl.tutlaw.backend.question.QuestionRepository;
import pl.tutlaw.backend.review.ReviewLogRepository;
import pl.tutlaw.backend.testsupport.Pools;
import pl.tutlaw.backend.testsupport.QuestionRefs;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Katalog Testów liczy pytania wyłącznie z aktów puli użytkownika i z wybranego źródła. */
@ExtendWith(MockitoExtension.class)
class TestCatalogServiceTest {

    @Mock private LegalFieldRepository fieldRepository;
    @Mock private LegalActRepository actRepository;
    @Mock private ActSectionRepository sectionRepository;
    @Mock private QuestionRepository questionRepository;
    @Mock private ReviewLogRepository reviewLogRepository;
    @Mock private CurrentUserResolver currentUserResolver;
    @Mock private CourseRepository courseRepository;
    private final Authentication authentication = mock(Authentication.class);

    private Pool pool = Pools.all();
    private TestCatalogService service;

    @BeforeEach
    void setUp() {
        service = new TestCatalogService(fieldRepository, actRepository, sectionRepository, questionRepository,
                reviewLogRepository, currentUserResolver, Pools.holding(() -> pool), courseRepository);
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

    private static ActSectionRepository.SectionRow section(long id, long actId, String name, int sortOrder) {
        return new ActSectionRepository.SectionRow() {
            public Long getId() { return id; }
            public Long getLegalActId() { return actId; }
            public String getName() { return name; }
            public Integer getSortOrder() { return sortOrder; }
        };
    }

    private void nothingInTheDatabase() {
        when(fieldRepository.findAll()).thenReturn(List.of());
        when(actRepository.findAllWithFieldByIdIn(any())).thenReturn(List.of());
        when(sectionRepository.findRowsByActIds(any())).thenReturn(List.of());
        when(questionRepository.countByAct(any(), any())).thenReturn(List.of());
        when(questionRepository.countBySection(any(), any())).thenReturn(List.of());
        when(reviewLogRepository.countSolvedTestQuestionsByAct(eq(5L), any(), any())).thenReturn(List.of());
    }

    @Test
    void getCatalog_asksEveryCounterForThePoolActsAndTheChosenSourceOnly() {
        Pool chosen = Pools.withFilter(pool, SourceMode.MINISTRY.filter());
        nothingInTheDatabase();

        service.getCatalog(SourceMode.MINISTRY, authentication);

        assertThat(chosen.sourceIds()).containsExactlyInAnyOrder(QuestionRefs.OWN_SHEET, QuestionRefs.OTHER_SHEET);
        verify(questionRepository).countByAct(chosen.actIds(), chosen.sourceIds());
        verify(questionRepository).countBySection(chosen.actIds(), chosen.sourceIds());
        verify(reviewLogRepository).countSolvedTestQuestionsByAct(5L, chosen.actIds(), chosen.sourceIds());
        verify(sectionRepository).findRowsByActIds(chosen.actIds());
    }

    @Test
    void getCatalog_withoutASourceCountsEverySource() {
        nothingInTheDatabase();

        service.getCatalog(null, authentication);

        verify(questionRepository).countByAct(pool.actIds(), pool.sourceIds());
        assertThat(pool.sourceIds()).containsExactlyInAnyOrder(
                QuestionRefs.OWN_SHEET, QuestionRefs.OTHER_SHEET, QuestionRefs.AUTHORED);
    }

    @Test
    void getCatalog_authoredCountsOnlyTheAuthoredSource() {
        nothingInTheDatabase();

        service.getCatalog(SourceMode.AUTHORED, authentication);

        verify(questionRepository).countByAct(pool.actIds(), List.of(QuestionRefs.AUTHORED));
        verify(questionRepository).countBySection(pool.actIds(), List.of(QuestionRefs.AUTHORED));
    }

    @Test
    void getCatalog_putsTheCountsAndTheSectionsIntoTheTree() {
        pool = Pools.of(pl.tutlaw.backend.pool.SourceFilter.ALL, 10);
        LegalAct act = QuestionRefs.act(10, 2);
        act.setName("Kodeks karny");
        LegalField field = act.getLegalField();
        field.setName("Prawo karne i proces karny");
        when(fieldRepository.findAll()).thenReturn(List.of(field));
        when(actRepository.findAllWithFieldByIdIn(pool.actIds())).thenReturn(List.of(act));
        when(sectionRepository.findRowsByActIds(pool.actIds())).thenReturn(List.of(
                section(200, 10, "XIX. Przeciwko życiu i zdrowiu", 190),
                section(201, 10, "I. Zasady odpowiedzialności karnej", 10),
                section(202, 10, "II. Formy popełnienia przestępstwa", 20)));
        when(questionRepository.countByAct(any(), any())).thenReturn(List.of(count(10, 12)));
        when(questionRepository.countBySection(any(), any())).thenReturn(List.of(count(200, 7), count(201, 3)));
        when(reviewLogRepository.countSolvedTestQuestionsByAct(eq(5L), any(), any())).thenReturn(List.of(count(10, 4)));

        TestCatalogResponse response = service.getCatalog(null, authentication);

        TestCatalogResponse.FieldNode node = response.fields().get(0);
        assertThat(node.questionCount()).isEqualTo(12);
        assertThat(node.solvedCount()).isEqualTo(4);
        assertThat(node.acts()).extracting(TestCatalogResponse.ActNode::name).containsExactly("Kodeks karny");
        // rozdział II nie ma pytań, więc go nie ma; reszta w kolejności rozdziałów, nie w kolejności z bazy
        assertThat(node.acts().get(0).sections()).extracting(TestCatalogResponse.SectionNode::name)
                .containsExactly("I. Zasady odpowiedzialności karnej", "XIX. Przeciwko życiu i zdrowiu");
        assertThat(node.acts().get(0).sections()).extracting(TestCatalogResponse.SectionNode::questionCount)
                .containsExactly(3, 7);
    }

    @Test
    void getCatalog_reportsTheNarrowingOfThePool() {
        pool = Pools.narrowedTo(Pools.all(), 1);
        nothingInTheDatabase();

        assertThat(service.getCatalog(null, authentication).narrowed()).isTrue();
        verify(actRepository).findAllWithFieldByIdIn(pool.actIds());
    }
}

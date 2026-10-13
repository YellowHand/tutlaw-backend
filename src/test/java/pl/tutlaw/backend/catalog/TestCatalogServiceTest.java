package pl.tutlaw.backend.catalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import pl.tutlaw.backend.card.CardRepository;
import pl.tutlaw.backend.card.QuestionOrigin;
import pl.tutlaw.backend.category.Category;
import pl.tutlaw.backend.category.CategoryRepository;
import pl.tutlaw.backend.common.GroupCount;
import pl.tutlaw.backend.catalog.dto.TestCatalogResponse;
import pl.tutlaw.backend.review.ReviewLogRepository;
import pl.tutlaw.backend.settings.OriginFilterService;
import pl.tutlaw.backend.source.Source;
import pl.tutlaw.backend.source.SourceRepository;
import pl.tutlaw.backend.source.SourceType;
import pl.tutlaw.backend.subject.Subject;
import pl.tutlaw.backend.subject.SubjectRepository;
import pl.tutlaw.backend.user.CurrentUserResolver;
import pl.tutlaw.backend.user.User;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Katalog Testów liczy pytania wyłącznie z pochodzeń wybranych przez użytkownika. */
@ExtendWith(MockitoExtension.class)
class TestCatalogServiceTest {

    @Mock private CategoryRepository categoryRepository;
    @Mock private SubjectRepository subjectRepository;
    @Mock private SourceRepository sourceRepository;
    @Mock private CardRepository cardRepository;
    @Mock private ReviewLogRepository reviewLogRepository;
    @Mock private CurrentUserResolver currentUserResolver;
    @Mock private OriginFilterService originFilter;
    private final Authentication authentication = mock(Authentication.class);

    private TestCatalogService service;

    @BeforeEach
    void setUp() {
        service = new TestCatalogService(categoryRepository, subjectRepository, sourceRepository, cardRepository,
                reviewLogRepository, currentUserResolver, originFilter);
        User user = new User();
        user.setId(5L);
        when(currentUserResolver.resolve(authentication)).thenReturn(user);
    }

    private static GroupCount count(long sourceId, long total) {
        return new GroupCount() {
            public Long getGroupId() { return sourceId; }
            public Long getTotal() { return total; }
        };
    }

    @Test
    void getCatalog_asksEveryCounterForTheSelectedOriginsOnly() {
        Set<QuestionOrigin> chosen = EnumSet.of(QuestionOrigin.NOTARY, QuestionOrigin.AUTHORED);
        when(originFilter.activeFor(5L)).thenReturn(chosen);
        when(categoryRepository.findAll()).thenReturn(List.of());
        when(subjectRepository.findAll()).thenReturn(List.of());
        when(sourceRepository.findAll()).thenReturn(List.of());
        when(cardRepository.countTestQuestionsBySourceInOrigins(chosen)).thenReturn(List.of());
        when(cardRepository.countReadableTestQuestionsBySourceInOrigins(5L, chosen)).thenReturn(List.of());
        when(reviewLogRepository.countSolvedTestQuestionsBySourceInOrigins(5L, chosen)).thenReturn(List.of());

        service.getCatalog(authentication);

        verify(cardRepository).countTestQuestionsBySourceInOrigins(chosen);
        verify(cardRepository).countReadableTestQuestionsBySourceInOrigins(5L, chosen);
        verify(reviewLogRepository).countSolvedTestQuestionsBySourceInOrigins(5L, chosen);
        // zapytania bez filtra służą statystykom i tu nie mogą być użyte
        verify(cardRepository, never()).countTestQuestionsBySource();
        verify(cardRepository, never()).countReadableTestQuestionsBySource(any());
        verify(reviewLogRepository, never()).countSolvedTestQuestionsBySource(any());
    }

    @Test
    void getCatalog_putsTheFilteredCountsIntoTheTree() {
        Set<QuestionOrigin> chosen = EnumSet.of(QuestionOrigin.NOTARY);
        when(originFilter.activeFor(5L)).thenReturn(chosen);
        Category category = new Category();
        category.setId(1L);
        category.setName("Prawo karne");
        category.setSlug("karne");
        category.setItemsLabel("aktów prawnych");
        Subject subject = new Subject();
        subject.setId(2L);
        subject.setName("Kodeks karny");
        subject.setSlug("kk");
        subject.setCategory(category);
        Source source = new Source();
        source.setId(3L);
        source.setName("Zestaw");
        source.setSourceType(SourceType.FLAT_POOL);
        source.setSubject(subject);
        when(categoryRepository.findAll()).thenReturn(List.of(category));
        when(subjectRepository.findAll()).thenReturn(List.of(subject));
        when(sourceRepository.findAll()).thenReturn(List.of(source));
        when(cardRepository.countTestQuestionsBySourceInOrigins(chosen)).thenReturn(List.of(count(3, 12)));
        when(cardRepository.countReadableTestQuestionsBySourceInOrigins(5L, chosen)).thenReturn(List.of(count(3, 12)));
        when(reviewLogRepository.countSolvedTestQuestionsBySourceInOrigins(5L, chosen)).thenReturn(List.of(count(3, 4)));

        TestCatalogResponse response = service.getCatalog(authentication);

        TestCatalogResponse.CategoryNode node = response.categories().get(0);
        assertThat(node.questionCount()).isEqualTo(12);
        assertThat(node.solvedCount()).isEqualTo(4);
        assertThat(node.lockedCount()).isZero();
    }
}

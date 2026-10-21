package pl.tutlaw.backend.common;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PageParamsTest {

    @Test
    void missingValuesGiveTheFirstPageOfTheDefaultSize() {
        PageParams params = PageParams.of(null, null);

        assertThat(params.page()).isZero();
        assertThat(params.size()).isEqualTo(PageParams.DEFAULT_SIZE);
    }

    @Test
    void sensibleValuesAreKept() {
        PageParams params = PageParams.of(3, 25);

        assertThat(params.page()).isEqualTo(3);
        assertThat(params.size()).isEqualTo(25);
    }

    @Test
    void outOfRangeValuesAreClampedInsteadOfFailing() {
        assertThat(PageParams.of(-4, 0)).isEqualTo(new PageParams(0, 1));
        assertThat(PageParams.of(2, -10)).isEqualTo(new PageParams(2, 1));
        assertThat(PageParams.of(0, 1_000_000)).isEqualTo(new PageParams(0, PageParams.MAX_SIZE));
        assertThat(PageParams.of(0, PageParams.MAX_SIZE)).isEqualTo(new PageParams(0, PageParams.MAX_SIZE));
    }

    @Test
    void historyListsDefaultToTwentyAndNeverExceedAHundred() {
        assertThat(PageParams.ofList(null, null)).isEqualTo(new PageParams(0, PageParams.LIST_DEFAULT_SIZE));
        assertThat(PageParams.LIST_DEFAULT_SIZE).isEqualTo(20);
        assertThat(PageParams.ofList(2, 50)).isEqualTo(new PageParams(2, 50));
        assertThat(PageParams.ofList(0, 1_000)).isEqualTo(new PageParams(0, PageParams.LIST_MAX_SIZE));
        assertThat(PageParams.LIST_MAX_SIZE).isEqualTo(100);
        assertThat(PageParams.ofList(-1, 0)).isEqualTo(new PageParams(0, 1));
    }

    @Test
    @SuppressWarnings("unchecked")
    void pageResponseCarriesTheItemsAndTheTotals() {
        Page<String> page = mock(Page.class);
        when(page.getContent()).thenReturn(List.of("a", "b"));
        when(page.getNumber()).thenReturn(2);
        when(page.getSize()).thenReturn(2);
        when(page.getTotalElements()).thenReturn(7L);
        when(page.getTotalPages()).thenReturn(4);

        PageResponse<String> response = PageResponse.of(page);

        assertThat(response.items()).containsExactly("a", "b");
        assertThat(response.page()).isEqualTo(2);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(7L);
        assertThat(response.totalPages()).isEqualTo(4);
    }
}

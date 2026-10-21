package pl.gradusiuris.backend.common;

import org.springframework.data.domain.Page;

import java.util.List;

/** Jedna strona wyników: elementy, numer strony (od 0), rozmiar, łączna liczba elementów i stron. */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}

package pl.tutlaw.backend.category;

import pl.tutlaw.backend.category.dto.CategoryResponse;

public class CategoryMapper {
    public static CategoryResponse toResponse(Category category, int subjectCount) {
        return new CategoryResponse(
                category.getId(), category.getName(), category.getSlug(),
                category.getItemsLabel(), category.getSortOrder(), subjectCount
        );
    }
}

package pl.tutlaw.backend.category;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.tutlaw.backend.category.dto.CategoryRequest;
import pl.tutlaw.backend.category.dto.CategoryResponse;
import pl.tutlaw.backend.common.GroupCount;
import pl.tutlaw.backend.common.exception.ConflictException;
import pl.tutlaw.backend.subject.SubjectRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final SubjectRepository subjectRepository;

    /** Kategorie w kolejności wyświetlania, każda z liczbą przedmiotów/aktów prawnych. */
    public List<CategoryResponse> getAll() {
        Map<Long, Long> subjectCounts = new HashMap<>();
        for (GroupCount row : subjectRepository.countByCategory()) {
            subjectCounts.put(row.getGroupId(), row.getTotal());
        }
        return categoryRepository.findAllByOrderBySortOrderAscNameAsc().stream()
                .map(c -> CategoryMapper.toResponse(c, subjectCounts.getOrDefault(c.getId(), 0L).intValue()))
                .toList();
    }

    public CategoryResponse create(CategoryRequest request) {
        if (categoryRepository.existsBySlug(request.slug())) {
            throw new ConflictException("Kategoria o takim slugu już istnieje");
        }
        Category category = new Category();
        category.setName(request.name());
        category.setSlug(request.slug());
        if (request.itemsLabel() != null && !request.itemsLabel().isBlank()) {
            category.setItemsLabel(request.itemsLabel().strip());
        }
        if (request.sortOrder() != null) {
            if (request.sortOrder() < 0) {
                throw new IllegalArgumentException("Kolejność (sortOrder) nie może być ujemna");
            }
            category.setSortOrder(request.sortOrder());
        }
        return CategoryMapper.toResponse(categoryRepository.save(category), 0);
    }
}

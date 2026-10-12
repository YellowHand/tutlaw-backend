package pl.tutlaw.backend.category;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.tutlaw.backend.category.dto.CategoryRequest;
import pl.tutlaw.backend.category.dto.CategoryResponse;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<CategoryResponse> getAll() {
        return categoryRepository.findAll().stream().map(CategoryMapper::toResponse).toList();
    }

    public CategoryResponse create(CategoryRequest request) {
        Category category = new Category();
        category.setName(request.name());
        category.setSlug(request.slug());
        return CategoryMapper.toResponse(categoryRepository.save(category));
    }
}
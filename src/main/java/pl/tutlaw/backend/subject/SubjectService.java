package pl.tutlaw.backend.subject;


import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.tutlaw.backend.category.Category;
import pl.tutlaw.backend.category.CategoryRepository;
import pl.tutlaw.backend.common.PolishSort;
import pl.tutlaw.backend.common.exception.ResourceNotFoundException;
import pl.tutlaw.backend.subject.dto.SubjectRequest;
import pl.tutlaw.backend.subject.dto.SubjectResponse;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SubjectService {

    private final SubjectRepository subjectRepository;
    private final CategoryRepository categoryRepository;

    public List<SubjectResponse> getAll() {
        return subjectRepository.findAll().stream().map(SubjectMapper::toResponse).toList();
    }

    public List<SubjectResponse> getByCategory(Long categoryId) {
        return subjectRepository.findByCategoryId(categoryId).stream()
                .sorted(Comparator.comparing(Subject::getName, PolishSort.byName()))
                .map(SubjectMapper::toResponse)
                .toList();
    }

    public SubjectResponse create(SubjectRequest request) {
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        if (subjectRepository.existsBySlug(request.slug())) {
            throw new IllegalArgumentException("Subject with this slug already exists");
        }

        Subject subject = new Subject();
        subject.setCategory(category);
        subject.setName(request.name());
        subject.setSlug(request.slug());
        return SubjectMapper.toResponse(subjectRepository.save(subject));
    }
}
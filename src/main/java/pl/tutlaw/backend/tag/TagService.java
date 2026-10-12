package pl.tutlaw.backend.tag;


import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.tutlaw.backend.tag.dto.TagRequest;
import pl.tutlaw.backend.tag.dto.TagResponse;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;

    public List<TagResponse> getAll() {
        return tagRepository.findAll().stream().map(TagMapper::toResponse).toList();
    }

    public TagResponse create(TagRequest request) {
        if (tagRepository.existsByName(request.name())) {
            throw new IllegalArgumentException("Subject with this slug already exists");
        }
        Tag tag = new Tag();
        tag.setName(request.name());
        return TagMapper.toResponse(tagRepository.save(tag));
    }
}
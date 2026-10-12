package pl.tutlaw.backend.source;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.tutlaw.backend.common.exception.ResourceNotFoundException;
import pl.tutlaw.backend.source.dto.SourceRequest;
import pl.tutlaw.backend.source.dto.SourceResponse;
import pl.tutlaw.backend.subject.Subject;
import pl.tutlaw.backend.subject.SubjectRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SourceService {

    private final SourceRepository sourceRepository;
    private final SubjectRepository subjectRepository;

    public List<SourceResponse> getBySubject(Long subjectId) {
        return sourceRepository.findBySubjectId(subjectId).stream()
                .map(SourceMapper::toResponse)
                .toList();
    }

    public SourceResponse create(SourceRequest request) {
        Subject subject = subjectRepository.findById(request.subjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Subject not found"));

        Source source = new Source();
        source.setSubject(subject);
        source.setName(request.name());
        source.setAuthor(request.author());
        source.setEdition(request.edition());
        source.setSourceType(request.sourceType());

        return SourceMapper.toResponse(sourceRepository.save(source));
    }
}
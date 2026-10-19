package pl.tutlaw.backend.question;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.tutlaw.backend.common.exception.ResourceNotFoundException;
import pl.tutlaw.backend.question.dto.QuestionVisibilityResponse;

/**
 * Administracyjne ukrywanie pytań. Treść pytań wczytuje się skryptami SQL (katalog baza/), więc serwis nie tworzy
 * ani nie edytuje pytań. Ukryte pytanie nie pojawia się nikomu (testy, fiszki, liczniki, egzamin), a historia
 * odpowiedzi zostaje; ukryta fiszka zostawia pytanie w testach.
 */
@Service
@RequiredArgsConstructor
public class QuestionService {

    private final QuestionRepository questionRepository;

    @Transactional
    public QuestionVisibilityResponse setHidden(Long id, boolean hidden) {
        Question question = find(id);
        question.setHidden(hidden);
        return toResponse(questionRepository.save(question));
    }

    @Transactional
    public QuestionVisibilityResponse setFlashcardHidden(Long id, boolean hidden) {
        Question question = find(id);
        question.setFlashcardHidden(hidden);
        return toResponse(questionRepository.save(question));
    }

    private Question find(Long id) {
        return questionRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Question not found"));
    }

    private static QuestionVisibilityResponse toResponse(Question question) {
        return new QuestionVisibilityResponse(question.getId(), question.isHidden(), question.isFlashcardHidden());
    }
}

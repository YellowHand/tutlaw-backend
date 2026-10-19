package pl.tutlaw.backend.exam;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.tutlaw.backend.exam.dto.ExamAttemptResponse;
import pl.tutlaw.backend.exam.dto.ExamResultResponse;
import pl.tutlaw.backend.exam.dto.ExamStartResponse;
import pl.tutlaw.backend.exam.dto.ExamSubmitRequest;

import java.util.List;

/** Egzamin próbny. Wszystko dotyczy tylko zalogowanego użytkownika. */
@RestController
@RequestMapping("/api/exams")
@RequiredArgsConstructor
public class ExamController {

    private final ExamService examService;

    /**
     * Losuje pytania egzaminu (bez poprawnych odpowiedzi). Format jest stały (150 pytań z całej puli), więc
     * żądanie nie ma treści. Niczego nie zapisuje.
     */
    @PostMapping("/start")
    public ExamStartResponse start(Authentication authentication) {
        return examService.start(authentication);
    }

    /** Ocenia zakończony egzamin, zapisuje podsumowanie i zwraca raport. */
    @PostMapping("/submit")
    public ExamResultResponse submit(@Valid @RequestBody ExamSubmitRequest request, Authentication authentication) {
        return examService.submit(request, authentication);
    }

    /** Ostatnie podejścia, od najnowszego. */
    @GetMapping("/history")
    public List<ExamAttemptResponse> history(Authentication authentication) {
        return examService.history(authentication);
    }
}

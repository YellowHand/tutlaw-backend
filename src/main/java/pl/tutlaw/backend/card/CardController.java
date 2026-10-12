package pl.tutlaw.backend.card;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pl.tutlaw.backend.card.dto.BulkCardImportRequest;
import pl.tutlaw.backend.card.dto.BulkImportResult;
import pl.tutlaw.backend.card.dto.CardRequest;
import pl.tutlaw.backend.card.dto.CardResponse;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CardController {

    private final CardService cardService;

    // Odpowiedzi zawierają poprawną opcję i wyjaśnienie, więc ten widok jest tylko dla administratora
    // (uczeń dostaje karty wyłącznie przez kolejkę powtórek, która pilnuje, co ma już odblokowane).
    @GetMapping("/api/chapters/{chapterId}/cards")
    @PreAuthorize("hasRole('ADMIN')")
    public List<CardResponse> getByChapter(@PathVariable Long chapterId) {
        return cardService.getByChapter(chapterId);
    }

    @GetMapping("/api/sources/{sourceId}/cards")
    @PreAuthorize("hasRole('ADMIN')")
    public List<CardResponse> getBySource(@PathVariable Long sourceId) {
        return cardService.getBySource(sourceId);
    }

    @PostMapping("/api/cards")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CardResponse> create(@Valid @RequestBody CardRequest request) {
        return ResponseEntity.ok(cardService.create(request));
    }

    @PostMapping("/api/cards/bulk")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BulkImportResult> bulkCreate(@RequestBody BulkCardImportRequest request) {
        return ResponseEntity.ok(cardService.bulkCreate(request.cards()));
    }
}

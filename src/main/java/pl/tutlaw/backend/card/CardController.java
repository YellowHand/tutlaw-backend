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
import pl.tutlaw.backend.common.PageResponse;

@RestController
@RequiredArgsConstructor
public class CardController {

    private final CardService cardService;

    // Odpowiedzi zawierają poprawną opcję i wyjaśnienie, więc ten widok jest tylko dla administratora
    // (uczeń dostaje karty wyłącznie przez kolejkę powtórek, która pilnuje, co ma już odblokowane).
    @GetMapping("/api/chapters/{chapterId}/cards")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<CardResponse> getByChapter(
            @PathVariable Long chapterId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return cardService.getByChapter(chapterId, page, size);
    }

    @GetMapping("/api/sources/{sourceId}/cards")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<CardResponse> getBySource(
            @PathVariable Long sourceId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return cardService.getBySource(sourceId, page, size);
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

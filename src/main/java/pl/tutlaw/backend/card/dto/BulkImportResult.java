package pl.tutlaw.backend.card.dto;

import java.util.List;

public record BulkImportResult(int created, List<String> errors) {
}
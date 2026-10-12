package pl.tutlaw.backend.card.dto;

import java.util.List;

public record BulkCardImportRequest(List<CardRequest> cards) {
}
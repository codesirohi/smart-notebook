package org.sirohi.smartnotebook.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record QueryRequest(
        @NotBlank String question,
        Optional<Integer> topK,
        Optional<List<UUID>> documentIds) {
}

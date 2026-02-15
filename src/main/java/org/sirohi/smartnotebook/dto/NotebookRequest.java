package org.sirohi.smartnotebook.dto;

import jakarta.validation.constraints.NotBlank;

public record NotebookRequest(
        @NotBlank String name,
        String description) {
}

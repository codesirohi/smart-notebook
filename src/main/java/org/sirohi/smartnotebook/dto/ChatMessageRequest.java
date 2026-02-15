package org.sirohi.smartnotebook.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatMessageRequest(
        @NotBlank String content) {
}

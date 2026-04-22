package com.isp.zoho.notifier.model.dto;

import jakarta.validation.constraints.NotBlank;

public record WebhookRequest(
    @NotBlank String zohoAccountId,
    @NotBlank String action,
    String message,
    @NotBlank String requestId
) {}

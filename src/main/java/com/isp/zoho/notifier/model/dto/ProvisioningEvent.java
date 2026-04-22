package com.isp.zoho.notifier.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProvisioningEvent(
    @NotBlank String requestId,
    @NotNull String action,
    @NotNull String status,
    @NotBlank String zohoAccountId,
    String message,
    String correlationId,
    String processedAt
) {

  public boolean isSuccess() {
    return "SUCCESS".equalsIgnoreCase(status);
  }
}

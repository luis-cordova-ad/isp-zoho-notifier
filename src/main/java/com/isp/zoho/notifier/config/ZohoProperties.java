package com.isp.zoho.notifier.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "zoho")
public record ZohoProperties(
    @NotBlank String clientId,
    @NotBlank String clientSecret,
    String refreshToken,
    @NotBlank String apiBaseUrl,
    @NotBlank String authUrl,
    @Positive int retryMaxAttempts,
    @Positive long retryInitialWaitMs,
    boolean webhookEnabled
) {

  public ZohoProperties {
    retryMaxAttempts = retryMaxAttempts > 0 ? retryMaxAttempts : 3;
    retryInitialWaitMs = retryInitialWaitMs > 0 ? retryInitialWaitMs : 2000L;
  }
}

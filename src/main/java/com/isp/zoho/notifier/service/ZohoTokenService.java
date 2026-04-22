package com.isp.zoho.notifier.service;

import com.isp.zoho.notifier.config.ZohoProperties;
import com.isp.zoho.notifier.model.dto.ZohoTokenResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Service
public class ZohoTokenService {

  private static final Logger log = LoggerFactory.getLogger(ZohoTokenService.class);

  private final ZohoProperties zohoProperties;
  private final RestClient zohoAuthRestClient;
  private final Counter tokenRefreshCounter;

  private record CachedToken(String accessToken, Instant expiresAt) {
    boolean isValid() {
      return Instant.now().isBefore(expiresAt);
    }
  }

  private final AtomicReference<CachedToken> tokenCache = new AtomicReference<>();

  public ZohoTokenService(
      ZohoProperties zohoProperties,
      RestClient zohoAuthRestClient,
      MeterRegistry meterRegistry) {
    this.zohoProperties = zohoProperties;
    this.zohoAuthRestClient = zohoAuthRestClient;
    this.tokenRefreshCounter = Counter.builder("zoho.notifier.token.refresh.count")
        .description("Number of Zoho OAuth2 token refreshes")
        .register(meterRegistry);
  }

  public synchronized String getAccessToken() {
    CachedToken cached = tokenCache.get();
    if (cached != null && cached.isValid()) {
      return cached.accessToken();
    }
    return refreshToken();
  }

  private String refreshToken() {
    log.info("Refreshing Zoho OAuth2 access token");
    tokenRefreshCounter.increment();

    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    params.add("grant_type", "refresh_token");
    params.add("client_id", zohoProperties.clientId());
    params.add("client_secret", zohoProperties.clientSecret());
    params.add("refresh_token", zohoProperties.refreshToken());

    ZohoTokenResponse response = zohoAuthRestClient.post()
        .uri("/oauth/v2/token")
        .body(params)
        .retrieve()
        .body(ZohoTokenResponse.class);

    if (response == null || response.accessToken() == null) {
      throw new IllegalStateException("Failed to obtain Zoho access token: null response");
    }

    long ttlSeconds = Math.max(0, response.expiresIn() - 60);
    Instant expiresAt = Instant.now().plusSeconds(ttlSeconds);
    tokenCache.set(new CachedToken(response.accessToken(), expiresAt));

    log.info("Zoho access token refreshed, expires in {}s", ttlSeconds);
    return response.accessToken();
  }
}

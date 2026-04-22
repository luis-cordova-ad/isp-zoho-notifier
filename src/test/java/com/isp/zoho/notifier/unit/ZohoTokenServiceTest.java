package com.isp.zoho.notifier.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.isp.zoho.notifier.config.ZohoProperties;
import com.isp.zoho.notifier.model.dto.ZohoTokenResponse;
import com.isp.zoho.notifier.service.ZohoTokenService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

class ZohoTokenServiceTest {

  private MockWebServer mockWebServer;
  private ZohoTokenService tokenService;

  @BeforeEach
  void setUp() throws IOException {
    mockWebServer = new MockWebServer();
    mockWebServer.start();

    ZohoProperties props = new ZohoProperties(
        "client-id", "client-secret", "refresh-token",
        "http://" + mockWebServer.getHostName() + ":" + mockWebServer.getPort(),
        "http://" + mockWebServer.getHostName() + ":" + mockWebServer.getPort(),
        3, 2000L, false
    );

    RestClient authClient = RestClient.builder()
        .baseUrl("http://" + mockWebServer.getHostName() + ":" + mockWebServer.getPort())
        .build();

    tokenService = new ZohoTokenService(props, authClient, new SimpleMeterRegistry());
  }

  @AfterEach
  void tearDown() throws IOException {
    mockWebServer.shutdown();
  }

  @Test
  void getAccessToken_shouldReturnTokenFromResponse() {
    mockWebServer.enqueue(new MockResponse()
        .setBody("{\"access_token\":\"test-token\",\"expires_in\":3600,\"token_type\":\"Bearer\"}")
        .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

    String token = tokenService.getAccessToken();

    assertThat(token).isEqualTo("test-token");
  }

  @Test
  void getAccessToken_shouldCacheTokenOnSubsequentCalls() {
    mockWebServer.enqueue(new MockResponse()
        .setBody("{\"access_token\":\"cached-token\",\"expires_in\":3600,\"token_type\":\"Bearer\"}")
        .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

    String first = tokenService.getAccessToken();
    String second = tokenService.getAccessToken();

    assertThat(first).isEqualTo("cached-token");
    assertThat(second).isEqualTo("cached-token");
    assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
  }

  @Test
  void getAccessToken_shouldThrowWhenResponseIsEmpty() {
    mockWebServer.enqueue(new MockResponse()
        .setResponseCode(400)
        .setBody("{\"error\":\"invalid_client\"}")
        .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

    assertThatThrownBy(() -> tokenService.getAccessToken())
        .isInstanceOf(Exception.class);
  }
}

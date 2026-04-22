package com.isp.zoho.notifier.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.isp.zoho.notifier.config.ZohoProperties;
import com.isp.zoho.notifier.exception.ZohoClientException;
import com.isp.zoho.notifier.model.dto.ProvisioningEvent;
import com.isp.zoho.notifier.model.entity.ZohoNotificationSent;
import com.isp.zoho.notifier.model.entity.ZohoNotificationSent.NotificationStatus;
import com.isp.zoho.notifier.repository.ZohoNotificationSentRepository;
import com.isp.zoho.notifier.service.ZohoNotificationService;
import com.isp.zoho.notifier.service.ZohoTokenService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class ZohoNotificationServiceTest {

  @Mock
  private ZohoNotificationSentRepository repository;

  @Mock
  private ZohoTokenService tokenService;

  @Mock
  private RestClient zohoRestClient;

  private MeterRegistry meterRegistry;
  private ZohoNotificationService service;

  private static final ZohoProperties PROPERTIES = new ZohoProperties(
      "client-id", "client-secret", "refresh-token",
      "https://api.zoho.com", "https://accounts.zoho.com",
      3, 2000L, false
  );

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    service = new ZohoNotificationService(
        repository, tokenService, zohoRestClient, PROPERTIES, meterRegistry);
  }

  @Test
  void processEvent_shouldSkipNonSuccessEvent() {
    ProvisioningEvent event = new ProvisioningEvent(
        "req-1", "ACTIVATE", "FAILURE", "account-1",
        "error", "corr-1", null);

    service.processEvent(event);

    verify(repository, never()).existsByRequestIdAndStatus(anyString(), any());
    assertThat(meterRegistry.counter("zoho.notifier.events.received").count()).isEqualTo(1.0);
  }

  @Test
  void processEvent_shouldSkipAlreadySentEvent() {
    ProvisioningEvent event = new ProvisioningEvent(
        "req-1", "ACTIVATE", "SUCCESS", "account-1",
        "message", "corr-1", null);

    when(repository.existsByRequestIdAndStatus("req-1", NotificationStatus.SENT))
        .thenReturn(true);

    service.processEvent(event);

    verify(zohoRestClient, never()).post();
  }

  @Test
  void processEvent_shouldIncrementEventsReceivedCounter() {
    ProvisioningEvent event = new ProvisioningEvent(
        "req-2", "SUSPEND", "FAILURE", "account-2",
        null, null, null);

    service.processEvent(event);

    assertThat(meterRegistry.counter("zoho.notifier.events.received").count()).isEqualTo(1.0);
  }

  @Test
  void provisioningEvent_isSuccess_shouldReturnTrueForSuccess() {
    ProvisioningEvent event = new ProvisioningEvent(
        "req-1", "ACTIVATE", "SUCCESS", "account-1",
        null, null, null);

    assertThat(event.isSuccess()).isTrue();
  }

  @Test
  void provisioningEvent_isSuccess_shouldReturnFalseForFailure() {
    ProvisioningEvent event = new ProvisioningEvent(
        "req-1", "ACTIVATE", "FAILURE", "account-1",
        null, null, null);

    assertThat(event.isSuccess()).isFalse();
  }

  @Test
  void zohoClientException_is4xx_shouldReturnTrueFor400Status() {
    ZohoClientException ex = new ZohoClientException("Bad request", 400);
    assertThat(ex.is4xx()).isTrue();
    assertThat(ex.is5xx()).isFalse();
  }

  @Test
  void zohoClientException_is5xx_shouldReturnTrueFor500Status() {
    ZohoClientException ex = new ZohoClientException("Server error", 500);
    assertThat(ex.is5xx()).isTrue();
    assertThat(ex.is4xx()).isFalse();
  }

  @Test
  void zohoNotificationSent_builder_shouldCreateEntityCorrectly() {
    ZohoNotificationSent entity = ZohoNotificationSent.builder()
        .requestId("req-1")
        .zohoAccountId("account-1")
        .action("ACTIVATE")
        .status(NotificationStatus.SENT)
        .sentAt(Instant.now())
        .build();

    assertThat(entity.getRequestId()).isEqualTo("req-1");
    assertThat(entity.getStatus()).isEqualTo(NotificationStatus.SENT);
  }
}

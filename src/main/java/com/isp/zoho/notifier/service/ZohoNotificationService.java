package com.isp.zoho.notifier.service;

import com.isp.zoho.notifier.config.ZohoProperties;
import com.isp.zoho.notifier.exception.ZohoClientException;
import com.isp.zoho.notifier.model.dto.ProvisioningEvent;
import com.isp.zoho.notifier.model.dto.ZohoNoteRequest;
import com.isp.zoho.notifier.model.entity.ZohoNotificationSent;
import com.isp.zoho.notifier.model.entity.ZohoNotificationSent.NotificationStatus;
import com.isp.zoho.notifier.repository.ZohoNotificationSentRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.Map;
import net.logstash.logback.argument.StructuredArguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
@EnableRetry
public class ZohoNotificationService {

  private static final Logger log = LoggerFactory.getLogger(ZohoNotificationService.class);
  private static final Logger audit = LoggerFactory.getLogger("AUDIT");

  private final ZohoNotificationSentRepository repository;
  private final ZohoTokenService tokenService;
  private final RestClient zohoRestClient;
  private final ZohoProperties zohoProperties;
  private final MeterRegistry meterRegistry;

  private final Counter eventsReceivedCounter;
  private final Counter dlqCounter;

  public ZohoNotificationService(
      ZohoNotificationSentRepository repository,
      ZohoTokenService tokenService,
      RestClient zohoRestClient,
      ZohoProperties zohoProperties,
      MeterRegistry meterRegistry) {
    this.repository = repository;
    this.tokenService = tokenService;
    this.zohoRestClient = zohoRestClient;
    this.zohoProperties = zohoProperties;
    this.meterRegistry = meterRegistry;

    this.eventsReceivedCounter = Counter.builder("zoho.notifier.events.received")
        .description("Total provisioning events received")
        .register(meterRegistry);
    this.dlqCounter = Counter.builder("zoho.notifier.dlq.count")
        .description("Events sent to DLQ")
        .register(meterRegistry);
  }

  public void processEvent(ProvisioningEvent event) {
    eventsReceivedCounter.increment();

    if (!event.isSuccess()) {
      log.info("Skipping non-SUCCESS event [requestId={}, status={}]",
          event.requestId(), event.status());
      return;
    }

    if (isAlreadySent(event.requestId())) {
      log.info("Idempotence check: notification already sent [requestId={}]", event.requestId());
      return;
    }

    sendToZohoCrm(event);
  }

  private boolean isAlreadySent(String requestId) {
    return repository.existsByRequestIdAndStatus(requestId, NotificationStatus.SENT);
  }

  @Retryable(
      retryFor = {ZohoClientException.class, RestClientException.class},
      noRetryFor = {ZohoClientException.class},
      maxAttempts = 3,
      backoff = @Backoff(delay = 2000, multiplier = 5, maxDelay = 30000)
  )
  public void sendToZohoCrm(ProvisioningEvent event) {
    Timer.Sample timerSample = Timer.start(meterRegistry);

    try {
      String token = tokenService.getAccessToken();
      ZohoNoteRequest noteRequest = new ZohoNoteRequest(
          "ISP Provisioning — " + event.action(),
          event.message() != null ? event.message() : event.action()
      );

      Map<String, Object> responseBody = zohoRestClient.post()
          .uri("/crm/v2/Accounts/{accountId}/Notes", event.zohoAccountId())
          .header("Authorization", "Zoho-oauthtoken " + token)
          .body(Map.of("data", new Object[]{noteRequest}))
          .retrieve()
          .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
            throw new ZohoClientException(
                "Zoho CRM 4xx error for account " + event.zohoAccountId(),
                res.getStatusCode().value()
            );
          })
          .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
            throw new ZohoClientException(
                "Zoho CRM 5xx error for account " + event.zohoAccountId(),
                res.getStatusCode().value()
            );
          })
          .body(Map.class);

      String noteId = extractNoteId(responseBody);
      persistSuccess(event, noteId);
      recordSentMetric(event.action(), "success");
      logAudit(event, noteId, null);

    } catch (ZohoClientException e) {
      if (e.is4xx()) {
        persistFailure(event, e.getMessage());
        recordFailedMetric(event.action(), "client_error_" + e.getStatusCode());
        logAudit(event, null, e.getMessage());
        throw e;
      }
      persistFailure(event, e.getMessage());
      recordFailedMetric(event.action(), "server_error");
      throw e;
    } finally {
      timerSample.stop(Timer.builder("zoho.notifier.api.duration")
          .tag("action", event.action() != null ? event.action() : "unknown")
          .register(meterRegistry));
    }
  }

  @Recover
  public void recoverFromRetryExhaustion(RestClientException ex, ProvisioningEvent event) {
    log.error("Retry exhausted for requestId={}, sending to DLQ: {}",
        event.requestId(), ex.getMessage());
    dlqCounter.increment();
    persistFailure(event, "Retry exhausted: " + ex.getMessage());
    throw new RuntimeException("Sending to DLQ after retry exhaustion", ex);
  }

  @Recover
  public void recoverFrom4xx(ZohoClientException ex, ProvisioningEvent event) {
    if (ex.is4xx()) {
      log.warn("Permanent 4xx error for requestId={}, sending to DLQ immediately: {}",
          event.requestId(), ex.getMessage());
      dlqCounter.increment();
      throw new RuntimeException("4xx error — no retry, sending to DLQ", ex);
    }
    log.error("Retry exhausted (5xx) for requestId={}, sending to DLQ: {}",
        event.requestId(), ex.getMessage());
    dlqCounter.increment();
    throw new RuntimeException("5xx retry exhausted — sending to DLQ", ex);
  }

  private void persistSuccess(ProvisioningEvent event, String noteId) {
    ZohoNotificationSent entity = ZohoNotificationSent.builder()
        .requestId(event.requestId())
        .zohoAccountId(event.zohoAccountId())
        .action(event.action())
        .zohoNoteId(noteId)
        .status(NotificationStatus.SENT)
        .sentAt(Instant.now())
        .build();
    repository.save(entity);
  }

  private void persistFailure(ProvisioningEvent event, String errorMessage) {
    if (!repository.existsById(event.requestId())) {
      ZohoNotificationSent entity = ZohoNotificationSent.builder()
          .requestId(event.requestId())
          .zohoAccountId(event.zohoAccountId())
          .action(event.action())
          .status(NotificationStatus.FAILED)
          .sentAt(Instant.now())
          .errorMessage(errorMessage)
          .build();
      repository.save(entity);
    }
  }

  private String extractNoteId(Map<String, Object> responseBody) {
    if (responseBody == null) {
      return null;
    }
    Object data = responseBody.get("data");
    if (data instanceof Iterable<?> items) {
      for (Object item : items) {
        if (item instanceof Map<?, ?> itemMap) {
          Object details = itemMap.get("details");
          if (details instanceof Map<?, ?> detailsMap) {
            Object id = detailsMap.get("id");
            return id != null ? id.toString() : null;
          }
        }
      }
    }
    return null;
  }

  private void recordSentMetric(String action, String status) {
    Counter.builder("zoho.notifier.notifications.sent")
        .tag("action", action != null ? action : "unknown")
        .tag("status", status)
        .register(meterRegistry)
        .increment();
  }

  private void recordFailedMetric(String action, String reason) {
    Counter.builder("zoho.notifier.notifications.failed")
        .tag("action", action != null ? action : "unknown")
        .tag("reason", reason)
        .register(meterRegistry)
        .increment();
  }

  private void logAudit(ProvisioningEvent event, String noteId, String error) {
    MDC.put("audit", "true");
    try {
      audit.info("{}", StructuredArguments.entries(Map.of(
          "event", "zoho.notification." + (error == null ? "sent" : "failed"),
          "requestId", event.requestId(),
          "zohoAccountId", event.zohoAccountId(),
          "action", event.action() != null ? event.action() : "",
          "zohoNoteId", noteId != null ? noteId : "",
          "error", error != null ? error : ""
      )));
    } finally {
      MDC.remove("audit");
    }
  }
}

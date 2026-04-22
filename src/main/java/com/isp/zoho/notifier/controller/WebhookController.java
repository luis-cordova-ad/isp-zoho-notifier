package com.isp.zoho.notifier.controller;

import com.isp.zoho.notifier.model.dto.ProvisioningEvent;
import com.isp.zoho.notifier.model.dto.WebhookRequest;
import com.isp.zoho.notifier.service.ZohoNotificationService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notify")
@ConditionalOnProperty(name = "zoho.webhook-enabled", havingValue = "true")
@PreAuthorize("hasAuthority('SCOPE_isp:zoho:notify')")
public class WebhookController {

  private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

  private final ZohoNotificationService notificationService;

  public WebhookController(ZohoNotificationService notificationService) {
    this.notificationService = notificationService;
  }

  @PostMapping("/webhook")
  public ResponseEntity<Void> receiveWebhook(@Valid @RequestBody WebhookRequest request) {
    log.info("Webhook received [requestId={}, zohoAccountId={}]",
        request.requestId(), request.zohoAccountId());

    ProvisioningEvent event = new ProvisioningEvent(
        request.requestId(),
        request.action(),
        "SUCCESS",
        request.zohoAccountId(),
        request.message(),
        UUID.randomUUID().toString(),
        null
    );

    notificationService.processEvent(event);
    return ResponseEntity.accepted().build();
  }
}

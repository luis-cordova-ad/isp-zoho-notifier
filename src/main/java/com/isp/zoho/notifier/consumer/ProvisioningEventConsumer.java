package com.isp.zoho.notifier.consumer;

import com.isp.zoho.notifier.config.RabbitMqConfig;
import com.isp.zoho.notifier.exception.ZohoClientException;
import com.isp.zoho.notifier.model.dto.ProvisioningEvent;
import com.isp.zoho.notifier.service.ZohoNotificationService;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class ProvisioningEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(ProvisioningEventConsumer.class);

  private final ZohoNotificationService notificationService;

  public ProvisioningEventConsumer(ZohoNotificationService notificationService) {
    this.notificationService = notificationService;
  }

  @RabbitListener(
      queues = RabbitMqConfig.PROVISIONING_EVENTS_QUEUE,
      containerFactory = "rabbitListenerContainerFactory",
      ackMode = "MANUAL"
  )
  public void consume(
      ProvisioningEvent event,
      Channel channel,
      @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

    if (event.correlationId() != null) {
      MDC.put("correlationId", event.correlationId());
    }

    try {
      log.info("Received provisioning event [requestId={}, action={}, status={}]",
          event.requestId(), event.action(), event.status());

      notificationService.processEvent(event);

      channel.basicAck(deliveryTag, false);
      log.info("ACK sent for [requestId={}]", event.requestId());

    } catch (ZohoClientException e) {
      if (e.is4xx()) {
        log.warn("4xx Zoho error for [requestId={}] — ACK + DLQ, no retry: {}",
            event.requestId(), e.getMessage());
        channel.basicAck(deliveryTag, false);
      } else {
        log.error("5xx/transient error for [requestId={}] — NACK for retry: {}",
            event.requestId(), e.getMessage());
        channel.basicNack(deliveryTag, false, false);
      }
    } catch (Exception e) {
      log.error("Unexpected error processing [requestId={}] — NACK to DLQ: {}",
          event.requestId(), e.getMessage(), e);
      channel.basicNack(deliveryTag, false, false);
    } finally {
      MDC.remove("correlationId");
    }
  }
}

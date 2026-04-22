package com.isp.zoho.notifier.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

  public static final String PROVISIONING_EVENTS_QUEUE = "provisioning.events";
  public static final String ZOHO_NOTIFIER_DLQ = "zoho.notifier.dlq";
  public static final String ZOHO_NOTIFIER_DLX = "zoho.notifier.dlx";

  @Bean
  Jackson2JsonMessageConverter messageConverter() {
    return new Jackson2JsonMessageConverter();
  }

  @Bean
  RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
      Jackson2JsonMessageConverter messageConverter) {
    RabbitTemplate template = new RabbitTemplate(connectionFactory);
    template.setMessageConverter(messageConverter);
    return template;
  }

  @Bean
  Queue provisioningEventsQueue() {
    return QueueBuilder.durable(PROVISIONING_EVENTS_QUEUE)
        .quorum()
        .deadLetterExchange(ZOHO_NOTIFIER_DLX)
        .build();
  }

  @Bean
  Queue zohoNotifierDlq() {
    return QueueBuilder.durable(ZOHO_NOTIFIER_DLQ)
        .quorum()
        .build();
  }

  @Bean
  DirectExchange zohoNotifierDlx() {
    return new DirectExchange(ZOHO_NOTIFIER_DLX);
  }

  @Bean
  Binding dlqBinding(Queue zohoNotifierDlq, DirectExchange zohoNotifierDlx) {
    return BindingBuilder.bind(zohoNotifierDlq)
        .to(zohoNotifierDlx)
        .with(PROVISIONING_EVENTS_QUEUE);
  }

  @Bean
  SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
      ConnectionFactory connectionFactory,
      Jackson2JsonMessageConverter messageConverter) {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    factory.setMessageConverter(messageConverter);
    factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
    factory.setPrefetchCount(10);
    return factory;
  }
}

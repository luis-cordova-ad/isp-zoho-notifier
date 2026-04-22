package com.isp.zoho.notifier.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.isp.zoho.notifier.config.RabbitMqConfig;
import com.isp.zoho.notifier.model.dto.ProvisioningEvent;
import com.isp.zoho.notifier.model.entity.ZohoNotificationSent.NotificationStatus;
import com.isp.zoho.notifier.repository.ZohoNotificationSentRepository;
import com.isp.zoho.notifier.service.ZohoTokenService;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ProvisioningEventConsumerTest {

  @Container
  static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:11.4")
      .withDatabaseName("freeradius_db")
      .withUsername("isp_zoho_notifier")
      .withPassword("test-only-password");

  @Container
  static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management")
      .withUser("guest", "guest");

  static WireMockServer wireMockServer;

  @Autowired
  RabbitTemplate rabbitTemplate;

  @Autowired
  ZohoNotificationSentRepository repository;

  @MockBean
  ZohoTokenService tokenService;

  @BeforeAll
  static void startWireMock() {
    wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    wireMockServer.start();
    WireMock.configureFor("localhost", wireMockServer.port());
  }

  @AfterAll
  static void stopWireMock() {
    wireMockServer.stop();
  }

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.hikari.data-source-properties.serverName",
        mariadb::getHost);
    registry.add("spring.datasource.hikari.data-source-properties.port",
        () -> mariadb.getMappedPort(3306).toString());
    registry.add("spring.datasource.hikari.data-source-properties.databaseName",
        mariadb::getDatabaseName);
    registry.add("spring.datasource.hikari.data-source-properties.user",
        mariadb::getUsername);
    registry.add("spring.datasource.hikari.data-source-properties.password",
        mariadb::getPassword);
    registry.add("spring.rabbitmq.host", rabbitmq::getHost);
    registry.add("spring.rabbitmq.port", () -> rabbitmq.getMappedPort(5672).toString());
    registry.add("zoho.api-base-url",
        () -> "http://localhost:" + wireMockServer.port());
    registry.add("zoho.auth-url",
        () -> "http://localhost:" + wireMockServer.port());
  }

  @Test
  void shouldSkipFailureEvents() {
    String requestId = UUID.randomUUID().toString();
    ProvisioningEvent event = new ProvisioningEvent(
        requestId, "ACTIVATE", "FAILURE", "account-1",
        "error msg", UUID.randomUUID().toString(), null);

    rabbitTemplate.convertAndSend(RabbitMqConfig.PROVISIONING_EVENTS_QUEUE, event);

    await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
      assertThat(repository.existsById(requestId)).isFalse();
    });
  }

  @Test
  void shouldProcessSuccessEventAndPersistIdempotence() {
    String requestId = UUID.randomUUID().toString();
    String accountId = "account-" + UUID.randomUUID();

    when(tokenService.getAccessToken()).thenReturn("mock-token");

    wireMockServer.stubFor(post(urlPathMatching("/crm/v2/Accounts/.*/Notes"))
        .willReturn(aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"data\":[{\"details\":{\"id\":\"note-123\"},\"status\":\"success\"}]}")));

    ProvisioningEvent event = new ProvisioningEvent(
        requestId, "ACTIVATE", "SUCCESS", accountId,
        "Activation complete", UUID.randomUUID().toString(), null);

    rabbitTemplate.convertAndSend(RabbitMqConfig.PROVISIONING_EVENTS_QUEUE, event);

    await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
      assertThat(repository.existsByRequestIdAndStatus(requestId, NotificationStatus.SENT))
          .isTrue();
    });
  }
}

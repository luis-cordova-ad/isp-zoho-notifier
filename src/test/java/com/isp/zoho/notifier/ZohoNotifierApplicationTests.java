package com.isp.zoho.notifier;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ZohoNotifierApplicationTests {

  @Container
  static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:11.4")
      .withDatabaseName("freeradius_db")
      .withUsername("isp_zoho_notifier")
      .withPassword("test-only-password");

  @Container
  static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management")
      .withUser("guest", "guest");

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
  }

  @Test
  void contextLoads() {
    // Verifies Spring context starts successfully
  }
}

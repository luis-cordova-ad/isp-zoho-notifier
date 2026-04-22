package com.isp.zoho.notifier.config;

import java.time.Duration;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

  @Bean
  RestClient zohoRestClient(RestClient.Builder builder, ZohoProperties zohoProperties) {
    return builder
        .baseUrl(zohoProperties.apiBaseUrl())
        .build();
  }

  @Bean
  RestClient zohoAuthRestClient(RestClient.Builder builder, ZohoProperties zohoProperties) {
    return builder
        .baseUrl(zohoProperties.authUrl())
        .build();
  }

  @Bean
  RestClientCustomizer restClientCustomizer() {
    return restClientBuilder -> {
      SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
      factory.setConnectTimeout(Duration.ofSeconds(10));
      factory.setReadTimeout(Duration.ofSeconds(30));
      restClientBuilder.requestFactory(factory);
    };
  }
}

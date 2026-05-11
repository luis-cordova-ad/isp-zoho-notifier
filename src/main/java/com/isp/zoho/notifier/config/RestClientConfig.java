package com.isp.zoho.notifier.config;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

  @Bean
  RestClient zohoRestClient(RestClient.Builder builder, ZohoProperties zohoProperties) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(10));
    factory.setReadTimeout(Duration.ofSeconds(30));
    return builder
        .baseUrl(zohoProperties.apiBaseUrl())
        .requestFactory(factory)
        .build();
  }

  @Bean
  RestClient zohoAuthRestClient(RestClient.Builder builder, ZohoProperties zohoProperties) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(10));
    factory.setReadTimeout(Duration.ofSeconds(30));
    return builder
        .baseUrl(zohoProperties.authUrl())
        .requestFactory(factory)
        .build();
  }
}
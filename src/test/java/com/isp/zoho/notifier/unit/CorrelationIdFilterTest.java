package com.isp.zoho.notifier.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.isp.zoho.notifier.filter.CorrelationIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class CorrelationIdFilterTest {

  private CorrelationIdFilter filter;

  @BeforeEach
  void setUp() {
    filter = new CorrelationIdFilter();
  }

  @Test
  void shouldGenerateCorrelationIdWhenHeaderAbsent() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (req, res) -> {
      assertThat(MDC.get("correlationId")).isNotNull().isNotBlank();
    });

    String correlationIdHeader = response.getHeader("X-Correlation-ID");
    assertThat(correlationIdHeader).isNotNull().isNotBlank();
  }

  @Test
  void shouldPropagateExistingCorrelationId() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Correlation-ID", "test-correlation-id");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (req, res) -> {
      assertThat(MDC.get("correlationId")).isEqualTo("test-correlation-id");
    });

    assertThat(response.getHeader("X-Correlation-ID")).isEqualTo("test-correlation-id");
  }

  @Test
  void shouldCleanMdcAfterRequest() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (req, res) -> {});

    assertThat(MDC.get("correlationId")).isNull();
  }

  @Test
  void shouldGenerateNewCorrelationIdWhenHeaderIsBlank() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Correlation-ID", "   ");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (req, res) -> {
      String correlationId = MDC.get("correlationId");
      assertThat(correlationId).isNotBlank();
      assertThat(correlationId).isNotEqualTo("   ");
    });
  }
}

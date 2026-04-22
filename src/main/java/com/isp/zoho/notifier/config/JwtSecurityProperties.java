package com.isp.zoho.notifier.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "isp.security.jwt")
public record JwtSecurityProperties(
    @NotBlank String claimRoles,
    @NotBlank String claimScope
) {

  public JwtSecurityProperties {
    claimRoles = claimRoles != null ? claimRoles : "realm_access.roles";
    claimScope = claimScope != null ? claimScope : "scope";
  }
}

package com.isp.zoho.notifier.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

@Configuration
public class JwtGrantedAuthoritiesConfig {

  @Bean
  JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter scopesConverter = new JwtGrantedAuthoritiesConverter();
    scopesConverter.setAuthorityPrefix("SCOPE_");
    scopesConverter.setAuthoritiesClaimName("scope");

    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(jwt ->
        Stream.concat(
            scopesConverter.convert(jwt).stream(),
            extractRealmRoles(jwt).stream()
        ).toList()
    );
    return converter;
  }

  private Collection<GrantedAuthority> extractRealmRoles(Jwt jwt) {
    Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
    if (realmAccess == null || !realmAccess.containsKey("roles")) {
      return List.of();
    }
    Object rolesObj = realmAccess.get("roles");
    if (!(rolesObj instanceof List<?> roleList)) {
      return List.of();
    }
    List<GrantedAuthority> authorities = new ArrayList<>();
    for (Object role : roleList) {
      if (role instanceof String roleName) {
        authorities.add(new SimpleGrantedAuthority("ROLE_" + roleName));
      }
    }
    return authorities;
  }
}

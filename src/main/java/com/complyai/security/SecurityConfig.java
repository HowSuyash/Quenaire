/*
 * Copyright (c) 2026 ComplyAI. All rights reserved.
 *
 * This file is proprietary and confidential. It is part of an unpublished work
 * owned by ComplyAI and is protected by copyright and trade secret law.
 *
 * No licence, express or implied, is granted. No part of this file may be
 * copied, modified, distributed, disclosed, or used to create derivative works
 * without the prior written permission of ComplyAI.
 *
 * Unauthorised copying or distribution of this file, via any medium, is
 * strictly prohibited.
 */
package com.complyai.security;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final TenantContextFilter tenantContextFilter;

  public SecurityConfig(
      JwtAuthenticationFilter jwtAuthenticationFilter, TenantContextFilter tenantContextFilter) {
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    this.tenantContextFilter = tenantContextFilter;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/actuator/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**",
                        "/api-docs/**",
                        // Actuator and springdoc are served under /api to match the
                        // documented URLs now that the context-path is gone.
                        "/api/actuator/**",
                        "/api/swagger-ui/**",
                        "/api/swagger-ui.html",
                        "/api/v3/api-docs/**",
                        "/api/v1/auth/**",
                        "/api/v1/public/**",
                        "/")
                    .permitAll()
                    // Unauthenticated build/version banner.
                    .requestMatchers(HttpMethod.GET, "/api")
                    .permitAll()
                    // Login pages theme themselves before anyone authenticates.
                    .requestMatchers(HttpMethod.GET, "/api/vanta/whitelabel/resolve")
                    .permitAll()
                    // Cross-tenant operator endpoints: platform staff only. A tenant
                    // OWNER must not reach these.
                    .requestMatchers("/api/platform/**")
                    .hasRole("PLATFORM_ADMIN")
                    // Trust Center is deliberately public — it is the prospect-facing page.
                    .requestMatchers(HttpMethod.GET, "/api/vanta/trust/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/vanta/trust/*/leads")
                    .permitAll()
                    // Vendors follow a signed intake link and have no account.
                    .requestMatchers(HttpMethod.GET, "/api/vanta/intake/links/*/status")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/vanta/intake/submit/*")
                    .permitAll()
                    // Stripe calls this server-to-server with no bearer token; the
                    // request is authenticated by its webhook signature instead.
                    .requestMatchers(HttpMethod.POST, "/api/billing/webhook")
                    .permitAll()
                    .requestMatchers("/api/v1/tenants/**")
                    .hasAnyRole("OWNER", "ADMIN")
                    .requestMatchers("/api/v1/users/**")
                    .hasAnyRole("OWNER", "ADMIN")
                    .requestMatchers("/api/billing/**")
                    .hasAnyRole("OWNER", "ADMIN")
                    .requestMatchers("/api/v1/**")
                    .authenticated()
                    // Everything else under /api requires a token. This previously fell
                    // through to permitAll(), leaving /api/vanta/** open to anonymous
                    // callers whenever a controller forgot its @PreAuthorize.
                    .requestMatchers("/api/**")
                    .authenticated()
                    .anyRequest()
                    .permitAll())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
        .addFilterBefore(tenantContextFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(jwtAuthenticationFilter, TenantContextFilter.class);

    return http.build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    // Patterns, not setAllowedOrigins: Spring rejects the literal "*" origin
    // whenever allowCredentials is true, which failed every browser request.
    configuration.setAllowedOriginPatterns(
        List.of(System.getenv().getOrDefault("CORS_ALLOWED_ORIGINS", "*").split(",")));
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("*"));
    configuration.setExposedHeaders(List.of("X-Total-Count", "X-Page-Count"));
    configuration.setAllowCredentials(true);
    configuration.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  @Bean
  public org.springframework.security.oauth2.server.resource.authentication
          .JwtAuthenticationConverter
      jwtAuthenticationConverter() {
    var converter =
        new org.springframework.security.oauth2.server.resource.authentication
            .JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
    return converter;
  }
}

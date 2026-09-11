/*
 * Copyright (c) 2026 Quenaire. All rights reserved.
 *
 * This file is proprietary and confidential. It is part of an unpublished work
 * owned by Quenaire and is protected by copyright and trade secret law.
 *
 * No licence, express or implied, is granted. No part of this file may be
 * copied, modified, distributed, disclosed, or used to create derivative works
 * without the prior written permission of Quenaire.
 *
 * Unauthorised copying or distribution of this file, via any medium, is
 * strictly prohibited.
 */
package com.quenaire.ai.whitelabel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quenaire.domain.tenant.Tenant;
import com.quenaire.domain.tenant.TenantRepository;
import com.quenaire.domain.tenant.TenantStatus;
import com.quenaire.security.TenantContext;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Whitelabel presentation: the colours, logo and wording a tenant puts in front of its own
 * customers.
 *
 * <p>Stored in {@code tenants.branding_json} rather than new columns so adding a branding knob
 * stays a code change, not a migration. This class is the typed gate over that blob.
 */
@Service
public class WhitelabelService {

  private static final Logger log = LoggerFactory.getLogger(WhitelabelService.class);

  /** Hex colours only — these values are interpolated into CSS. */
  private static final Pattern HEX_COLOR = Pattern.compile("^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})$");

  private static final Pattern SAFE_URL = Pattern.compile("^https?://[^\\s\"'<>]+$");

  private static final String DEFAULT_PRIMARY = "#2563EB";
  private static final String DEFAULT_ACCENT = "#7C3AED";

  private final TenantRepository tenantRepository;
  private final ObjectMapper objectMapper;

  public WhitelabelService(TenantRepository tenantRepository, ObjectMapper objectMapper) {
    this.tenantRepository = tenantRepository;
    this.objectMapper = objectMapper;
  }

  /**
   * @param productName what the UI calls itself, e.g. "Acme Trust"
   * @param primaryColor main brand colour
   * @param accentColor secondary colour for highlights
   * @param logoUrl header logo
   * @param faviconUrl browser tab icon
   * @param supportEmail shown in error states and emails
   * @param documentFooter footer line stamped onto PDF exports
   * @param hidePoweredBy suppress the Quenaire attribution (paid plans only)
   */
  public record Branding(
      String productName,
      String primaryColor,
      String accentColor,
      String logoUrl,
      String faviconUrl,
      String supportEmail,
      String documentFooter,
      boolean hidePoweredBy,
      String loginBackgroundUrl,
      String customCssVars) {
    public static Branding defaults(String tenantName) {
      return new Branding(
          tenantName != null ? tenantName : "Quenaire",
          DEFAULT_PRIMARY,
          DEFAULT_ACCENT,
          null,
          null,
          null,
          null,
          false,
          null,
          null);
    }
  }

  /** What an unauthenticated client needs to theme the page before login. */
  public record PublicBranding(
      String tenantSlug,
      String productName,
      String primaryColor,
      String accentColor,
      String logoUrl,
      String faviconUrl,
      String loginBackgroundUrl,
      boolean hidePoweredBy) {}

  // ---- Read -------------------------------------------------------------

  @Transactional(readOnly = true)
  public Branding getBranding() {
    Tenant tenant = TenantContext.getCurrentTenant();
    if (tenant == null) {
      return Branding.defaults(null);
    }
    return parse(tenant);
  }

  /**
   * Resolve branding for an unauthenticated visitor, by custom domain or slug.
   *
   * <p>Only ever returns presentation fields, and only for an active tenant — this endpoint is
   * reachable before login.
   */
  @Transactional(readOnly = true)
  public Optional<PublicBranding> resolvePublic(String host, String slug) {
    Optional<Tenant> tenant = Optional.empty();

    if (host != null && !host.isBlank()) {
      tenant = tenantRepository.findByCustomDomain(normalizeHost(host));
    }
    if (tenant.isEmpty() && slug != null && !slug.isBlank()) {
      tenant = tenantRepository.findActiveBySlug(slug.trim().toLowerCase(Locale.ROOT));
    }

    return tenant
        .filter(t -> t.getStatus() == TenantStatus.ACTIVE || t.getStatus() == TenantStatus.TRIAL)
        .filter(t -> !t.isDeleted())
        .map(
            t -> {
              Branding branding = parse(t);
              return new PublicBranding(
                  t.getSlug(),
                  branding.productName(),
                  branding.primaryColor(),
                  branding.accentColor(),
                  branding.logoUrl(),
                  branding.faviconUrl(),
                  branding.loginBackgroundUrl(),
                  branding.hidePoweredBy());
            });
  }

  // ---- Write ------------------------------------------------------------

  @Transactional
  public Branding updateBranding(Branding incoming) {
    Tenant tenant = TenantContext.getCurrentTenant();
    if (tenant == null) {
      throw new IllegalStateException("No tenant context available");
    }

    Branding current = parse(tenant);

    Branding merged =
        new Branding(
            firstNonBlank(incoming.productName(), current.productName()),
            validColor(incoming.primaryColor(), current.primaryColor()),
            validColor(incoming.accentColor(), current.accentColor()),
            validUrl(incoming.logoUrl(), current.logoUrl()),
            validUrl(incoming.faviconUrl(), current.faviconUrl()),
            firstNonBlank(incoming.supportEmail(), current.supportEmail()),
            firstNonBlank(incoming.documentFooter(), current.documentFooter()),
            incoming.hidePoweredBy(),
            validUrl(incoming.loginBackgroundUrl(), current.loginBackgroundUrl()),
            sanitizeCssVars(incoming.customCssVars()));

    write(tenant, merged);
    tenantRepository.save(tenant);
    return merged;
  }

  /**
   * Point a tenant at its own hostname.
   *
   * <p>Domains are globally unique: two tenants claiming one host would make {@link #resolvePublic}
   * non-deterministic and leak one brand into another.
   */
  @Transactional
  public void setCustomDomain(String domain) {
    Tenant tenant = TenantContext.getCurrentTenant();
    if (tenant == null) {
      throw new IllegalStateException("No tenant context available");
    }

    if (domain == null || domain.isBlank()) {
      tenant.setCustomDomain(null);
      tenantRepository.save(tenant);
      return;
    }

    String normalized = normalizeHost(domain);
    if (!isValidHostname(normalized)) {
      throw new IllegalArgumentException("Not a valid hostname: " + domain);
    }

    Optional<Tenant> existing = tenantRepository.findByCustomDomain(normalized);
    if (existing.isPresent() && !existing.get().getId().equals(tenant.getId())) {
      throw new IllegalArgumentException("That domain is already claimed by another workspace");
    }

    tenant.setCustomDomain(normalized);
    tenantRepository.save(tenant);
  }

  // ---- Serialization ----------------------------------------------------

  private Branding parse(Tenant tenant) {
    Branding defaults = Branding.defaults(tenant.getName());

    String json = tenant.getBrandingJson();
    if (json == null || json.isBlank() || "{}".equals(json.trim())) {
      return defaults;
    }

    try {
      var node = objectMapper.readTree(json);
      return new Branding(
          text(node, "productName", defaults.productName()),
          validColor(text(node, "primaryColor", null), defaults.primaryColor()),
          validColor(text(node, "accentColor", null), defaults.accentColor()),
          text(node, "logoUrl", null),
          text(node, "faviconUrl", null),
          text(node, "supportEmail", null),
          text(node, "documentFooter", null),
          node.path("hidePoweredBy").asBoolean(false),
          text(node, "loginBackgroundUrl", null),
          text(node, "customCssVars", null));
    } catch (Exception e) {
      // Malformed branding must not break the page it themes.
      log.warn("Could not parse branding for tenant {}; using defaults", tenant.getId(), e);
      return defaults;
    }
  }

  private void write(Tenant tenant, Branding branding) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("productName", branding.productName());
    map.put("primaryColor", branding.primaryColor());
    map.put("accentColor", branding.accentColor());
    map.put("logoUrl", branding.logoUrl());
    map.put("faviconUrl", branding.faviconUrl());
    map.put("supportEmail", branding.supportEmail());
    map.put("documentFooter", branding.documentFooter());
    map.put("hidePoweredBy", branding.hidePoweredBy());
    map.put("loginBackgroundUrl", branding.loginBackgroundUrl());
    map.put("customCssVars", branding.customCssVars());

    try {
      tenant.setBrandingJson(objectMapper.writeValueAsString(map));
    } catch (Exception e) {
      throw new IllegalStateException("Could not serialize branding", e);
    }
  }

  // ---- Validation -------------------------------------------------------

  private static String text(
      com.fasterxml.jackson.databind.JsonNode node, String field, String fallback) {
    String value = node.path(field).asText(null);
    return (value == null || value.isBlank() || "null".equals(value)) ? fallback : value;
  }

  private static String firstNonBlank(String candidate, String fallback) {
    return (candidate == null || candidate.isBlank()) ? fallback : candidate.trim();
  }

  /** Reject anything that is not a hex colour: this value lands inside CSS. */
  private static String validColor(String candidate, String fallback) {
    if (candidate == null || candidate.isBlank()) return fallback;
    String trimmed = candidate.trim();
    return HEX_COLOR.matcher(trimmed).matches() ? trimmed.toUpperCase(Locale.ROOT) : fallback;
  }

  /** Only http(s) URLs — blocks {@code javascript:} and {@code data:} in img/src. */
  private static String validUrl(String candidate, String fallback) {
    if (candidate == null || candidate.isBlank()) return fallback;
    String trimmed = candidate.trim();
    return SAFE_URL.matcher(trimmed).matches() ? trimmed : fallback;
  }

  /**
   * Custom CSS is limited to custom-property declarations.
   *
   * <p>Free-form CSS from a tenant admin would be an injection vector into every page their users
   * load, so anything that is not {@code --name: value;} is dropped.
   */
  private static String sanitizeCssVars(String candidate) {
    if (candidate == null || candidate.isBlank()) return null;

    StringBuilder safe = new StringBuilder();
    for (String declaration : candidate.split(";")) {
      String trimmed = declaration.trim();
      if (trimmed.isEmpty()) continue;

      int colon = trimmed.indexOf(':');
      if (colon <= 2 || !trimmed.startsWith("--")) continue;

      String name = trimmed.substring(0, colon).trim();
      String value = trimmed.substring(colon + 1).trim();

      if (!name.matches("^--[a-zA-Z0-9_-]{1,64}$")) continue;
      if (!value.matches("^[a-zA-Z0-9#().,%\\s/_-]{1,120}$")) continue;
      if (value.toLowerCase(Locale.ROOT).contains("url(")) continue;

      safe.append(name).append(':').append(value).append(';');
    }

    return safe.length() == 0 ? null : safe.toString();
  }

  private static String normalizeHost(String host) {
    String value = host.trim().toLowerCase(Locale.ROOT);
    value = value.replaceFirst("^https?://", "");
    int slash = value.indexOf('/');
    if (slash > -1) value = value.substring(0, slash);
    int colon = value.indexOf(':');
    if (colon > -1) value = value.substring(0, colon);
    return value;
  }

  private static boolean isValidHostname(String host) {
    return host.matches("^(?!-)[a-z0-9-]{1,63}(\\.[a-z0-9-]{1,63})+$");
  }
}

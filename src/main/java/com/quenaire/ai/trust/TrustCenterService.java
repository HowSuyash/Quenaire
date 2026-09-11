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
package com.quenaire.ai.trust;

import com.quenaire.domain.tenant.Tenant;
import com.quenaire.domain.trust.TrustCenterItem;
import com.quenaire.domain.trust.TrustCenterItemRepository;
import com.quenaire.domain.trust.TrustCenterLead;
import com.quenaire.domain.trust.TrustCenterLeadRepository;
import com.quenaire.domain.trust.TrustCenterProfile;
import com.quenaire.domain.trust.TrustCenterProfileRepository;
import com.quenaire.integration.slack.SlackIntegrationService;
import com.quenaire.security.TenantContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public Trust Center: what a prospect sees at /trust/{slug}, plus the lead capture behind its call
 * to action.
 */
@Service
public class TrustCenterService {

  private static final Logger log = LoggerFactory.getLogger(TrustCenterService.class);

  private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s.]+\\.[^@\\s]{2,}$");
  private static final Pattern SLUG_SAFE = Pattern.compile("[^a-z0-9-]");

  /** Submissions allowed from one source before we treat it as abuse. */
  private static final int LEAD_RATE_LIMIT = 5;

  private static final Duration LEAD_RATE_WINDOW = Duration.ofHours(1);

  private final TrustCenterProfileRepository profileRepository;
  private final TrustCenterItemRepository itemRepository;
  private final TrustCenterLeadRepository leadRepository;
  private final SlackIntegrationService slackIntegrationService;

  public TrustCenterService(
      TrustCenterProfileRepository profileRepository,
      TrustCenterItemRepository itemRepository,
      TrustCenterLeadRepository leadRepository,
      SlackIntegrationService slackIntegrationService) {
    this.profileRepository = profileRepository;
    this.itemRepository = itemRepository;
    this.leadRepository = leadRepository;
    this.slackIntegrationService = slackIntegrationService;
  }

  // ---- Public read model ------------------------------------------------

  public record PublicItem(
      UUID id,
      String kind,
      String title,
      String body,
      String icon,
      String issuer,
      LocalDate validFrom,
      LocalDate validUntil,
      boolean expired,
      String accessLevel,
      String externalUrl) {}

  public record PublicCta(boolean enabled, String heading, String body, String buttonLabel) {}

  public record PublicProfile(
      String slug,
      String companyName,
      String headline,
      String summary,
      String logoUrl,
      String primaryColor,
      String contactEmail,
      String securityPageUrl,
      String statusPageUrl,
      OffsetDateTime publishedAt,
      List<PublicItem> certifications,
      List<PublicItem> documents,
      List<PublicItem> controls,
      List<PublicItem> faqs,
      PublicCta cta) {}

  /**
   * Render a published profile. Unpublished or unknown slugs come back empty — the controller must
   * not distinguish the two, or the endpoint becomes a tenant-existence oracle.
   */
  @Transactional(readOnly = true)
  public Optional<PublicProfile> getPublicProfile(String slug) {
    if (slug == null || slug.isBlank()) {
      return Optional.empty();
    }

    return profileRepository
        .findPublishedBySlug(normalizeSlug(slug))
        .map(
            profile -> {
              List<TrustCenterItem> items =
                  itemRepository.findByProfileIdAndDeletedAtIsNullOrderByDisplayOrderAsc(
                      profile.getId());

              return new PublicProfile(
                  profile.getSlug(),
                  profile.getCompanyName(),
                  profile.getHeadline(),
                  profile.getSummary(),
                  profile.getLogoUrl(),
                  profile.getPrimaryColor(),
                  profile.getContactEmail(),
                  profile.getSecurityPageUrl(),
                  profile.getStatusPageUrl(),
                  profile.getPublishedAt(),
                  publicItems(items, TrustCenterItem.Kind.CERTIFICATION),
                  publicItems(items, TrustCenterItem.Kind.DOCUMENT),
                  publicItems(items, TrustCenterItem.Kind.CONTROL),
                  publicItems(items, TrustCenterItem.Kind.FAQ),
                  new PublicCta(
                      Boolean.TRUE.equals(profile.getCtaEnabled()),
                      profile.getCtaHeading(),
                      profile.getCtaBody(),
                      profile.getCtaButtonLabel()));
            });
  }

  private List<PublicItem> publicItems(List<TrustCenterItem> items, TrustCenterItem.Kind kind) {
    LocalDate today = LocalDate.now();
    return items.stream()
        .filter(item -> item.getKind() == kind)
        .map(
            item ->
                new PublicItem(
                    item.getId(),
                    item.getKind().name(),
                    item.getTitle(),
                    item.getBody(),
                    item.getIcon(),
                    item.getIssuer(),
                    item.getValidFrom(),
                    item.getValidUntil(),
                    item.getValidUntil() != null && item.getValidUntil().isBefore(today),
                    item.getAccessLevel().name(),
                    // A request-only row must never hand out its link.
                    item.getAccessLevel() == TrustCenterItem.Access.PUBLIC
                        ? item.getExternalUrl()
                        : null))
        .toList();
  }

  // ---- Lead capture -----------------------------------------------------

  public record LeadSubmission(
      String name,
      String email,
      String company,
      String roleTitle,
      String message,
      UUID requestedItemId,
      String sourceUrl) {}

  public record LeadResult(boolean success, String message) {}

  /**
   * Record a request for security material. Runs unauthenticated, so it validates hard and
   * rate-limits per source address.
   */
  @Transactional
  public LeadResult submitLead(String slug, LeadSubmission submission, String remoteAddress) {
    if (submission == null) {
      return new LeadResult(false, "Missing request body");
    }
    if (isBlank(submission.name())) {
      return new LeadResult(false, "Name is required");
    }
    if (isBlank(submission.email()) || !EMAIL.matcher(submission.email().trim()).matches()) {
      return new LeadResult(false, "A valid email address is required");
    }

    Optional<TrustCenterProfile> found = profileRepository.findPublishedBySlug(normalizeSlug(slug));
    if (found.isEmpty()) {
      return new LeadResult(false, "Trust Center not found");
    }

    TrustCenterProfile profile = found.get();
    if (!Boolean.TRUE.equals(profile.getCtaEnabled())) {
      return new LeadResult(false, "This Trust Center is not accepting requests");
    }

    String ipHash = hashSource(remoteAddress);
    long recent =
        leadRepository.countRecentBySource(
            profile.getId(), ipHash, Instant.now().minus(LEAD_RATE_WINDOW));
    if (recent >= LEAD_RATE_LIMIT) {
      log.warn("Rate-limiting Trust Center leads for profile {}", profile.getId());
      return new LeadResult(false, "Too many requests. Please try again later.");
    }

    TrustCenterLead lead = new TrustCenterLead();
    lead.setProfileId(profile.getId());
    lead.setName(trimTo(submission.name(), 255));
    lead.setEmail(trimTo(submission.email().trim().toLowerCase(Locale.ROOT), 320));
    lead.setCompany(trimTo(submission.company(), 255));
    lead.setRoleTitle(trimTo(submission.roleTitle(), 255));
    lead.setMessage(trimTo(submission.message(), 4000));
    lead.setRequestedItemId(submission.requestedItemId());
    lead.setSourceUrl(trimTo(submission.sourceUrl(), 1000));
    lead.setIpHash(ipHash);
    lead.setStatus(TrustCenterLead.Status.NEW);
    lead.setTenant(profile.getTenant());

    leadRepository.save(lead);
    notifyOfLead(profile, lead);

    return new LeadResult(true, "Thanks — our security team will be in touch shortly.");
  }

  private void notifyOfLead(TrustCenterProfile profile, TrustCenterLead lead) {
    String channel = profile.getLeadNotifySlackChannel();
    if (channel == null || channel.isBlank()) {
      return;
    }

    try {
      slackIntegrationService.sendMessage(
          channel,
          String.format(
              "New Trust Center request%n*%s* (%s)%nCompany: %s%n%s",
              lead.getName(),
              lead.getEmail(),
              lead.getCompany() != null ? lead.getCompany() : "—",
              lead.getMessage() != null ? lead.getMessage() : ""));
    } catch (Exception e) {
      // The lead is already saved; a failed ping must not lose it.
      log.warn("Failed to notify Slack of Trust Center lead {}", lead.getId(), e);
    }
  }

  // ---- Tenant-side administration ---------------------------------------

  @Transactional(readOnly = true)
  public Optional<TrustCenterProfile> getMyProfile() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return tenantId == null
        ? Optional.empty()
        : profileRepository.findByTenantIdAndDeletedAtIsNull(tenantId);
  }

  @Transactional
  public TrustCenterProfile upsertProfile(TrustCenterProfile incoming) {
    Tenant tenant = TenantContext.getCurrentTenant();
    if (tenant == null) {
      throw new IllegalStateException("No tenant context available");
    }

    TrustCenterProfile profile =
        profileRepository
            .findByTenantIdAndDeletedAtIsNull(tenant.getId())
            .orElseGet(
                () -> {
                  TrustCenterProfile created = new TrustCenterProfile();
                  created.setTenant(tenant);
                  return created;
                });

    String slug =
        normalizeSlug(
            incoming.getSlug() != null && !incoming.getSlug().isBlank()
                ? incoming.getSlug()
                : tenant.getSlug());

    // Slugs are the public URL, so a collision would hand traffic to the wrong tenant.
    if (!slug.equals(profile.getSlug()) && profileRepository.existsBySlugAndDeletedAtIsNull(slug)) {
      throw new IllegalArgumentException("That Trust Center address is already taken");
    }

    profile.setSlug(slug);
    profile.setCompanyName(
        incoming.getCompanyName() != null ? incoming.getCompanyName() : tenant.getName());
    profile.setHeadline(incoming.getHeadline());
    profile.setSummary(incoming.getSummary());
    profile.setLogoUrl(incoming.getLogoUrl());
    profile.setPrimaryColor(incoming.getPrimaryColor());
    profile.setContactEmail(incoming.getContactEmail());
    profile.setSecurityPageUrl(incoming.getSecurityPageUrl());
    profile.setStatusPageUrl(incoming.getStatusPageUrl());
    profile.setLeadNotifySlackChannel(incoming.getLeadNotifySlackChannel());

    if (incoming.getCtaEnabled() != null) profile.setCtaEnabled(incoming.getCtaEnabled());
    if (incoming.getCtaHeading() != null) profile.setCtaHeading(incoming.getCtaHeading());
    if (incoming.getCtaBody() != null) profile.setCtaBody(incoming.getCtaBody());
    if (incoming.getCtaButtonLabel() != null)
      profile.setCtaButtonLabel(incoming.getCtaButtonLabel());

    return profileRepository.save(profile);
  }

  @Transactional
  public TrustCenterProfile setPublished(boolean published) {
    TrustCenterProfile profile =
        getMyProfile()
            .orElseThrow(() -> new IllegalStateException("Create a Trust Center profile first"));

    profile.setPublished(published);
    profile.setPublishedAt(published ? OffsetDateTime.now() : null);
    return profileRepository.save(profile);
  }

  @Transactional
  public TrustCenterItem addItem(TrustCenterItem item) {
    TrustCenterProfile profile =
        getMyProfile()
            .orElseThrow(() -> new IllegalStateException("Create a Trust Center profile first"));

    item.setProfile(profile);
    if (item.getDisplayOrder() == null) {
      item.setDisplayOrder(
          itemRepository
              .findByProfileIdAndDeletedAtIsNullOrderByDisplayOrderAsc(profile.getId())
              .size());
    }
    return itemRepository.save(item);
  }

  @Transactional
  public void removeItem(UUID itemId) {
    TrustCenterProfile profile =
        getMyProfile().orElseThrow(() -> new IllegalStateException("No Trust Center profile"));

    itemRepository
        .findById(itemId)
        .filter(item -> profile.getId().equals(item.getProfileId()))
        .ifPresent(
            item -> {
              item.softDelete();
              itemRepository.save(item);
            });
  }

  @Transactional(readOnly = true)
  public Page<TrustCenterLead> listLeads(Pageable pageable) {
    return leadRepository.findByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(
        TenantContext.getCurrentTenantId(), pageable);
  }

  @Transactional
  public void updateLeadStatus(UUID leadId, TrustCenterLead.Status status) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    leadRepository
        .findById(leadId)
        .filter(lead -> tenantId != null && tenantId.equals(lead.getTenantId()))
        .ifPresent(
            lead -> {
              lead.setStatus(status);
              leadRepository.save(lead);
            });
  }

  @Transactional(readOnly = true)
  public Map<String, Long> getLeadCounts() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    Map<String, Long> counts = new LinkedHashMap<>();
    for (TrustCenterLead.Status status : TrustCenterLead.Status.values()) {
      counts.put(
          status.name(),
          leadRepository.countByTenantIdAndStatusAndDeletedAtIsNull(tenantId, status));
    }
    return counts;
  }

  // ---- Helpers ----------------------------------------------------------

  static String normalizeSlug(String raw) {
    if (raw == null) return "";
    String lower = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '-');
    return SLUG_SAFE.matcher(lower).replaceAll("");
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

  private static String trimTo(String value, int max) {
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
  }

  /** Hash rather than store the caller's address — we only need equality. */
  private static String hashSource(String remoteAddress) {
    String source = remoteAddress != null ? remoteAddress : "unknown";
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(source.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(hash.length * 2);
      for (byte b : hash) {
        hex.append(Character.forDigit((b >> 4) & 0xF, 16));
        hex.append(Character.forDigit(b & 0xF, 16));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 is mandatory in every JRE; treat absence as unhashable.
      return Integer.toHexString(source.hashCode());
    }
  }
}

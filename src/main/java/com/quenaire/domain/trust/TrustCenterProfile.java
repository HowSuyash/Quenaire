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
package com.quenaire.domain.trust;

import com.quenaire.domain.common.BaseEntity;
import com.quenaire.domain.tenant.Tenant;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The public face of a tenant's security posture — one profile per tenant, served unauthenticated
 * at /trust/{slug}.
 *
 * <p>Nothing here is derived automatically: an owner explicitly publishes each fact, so an
 * unpublished profile leaks nothing.
 */
@Entity
@Table(
    name = "trust_center_profiles",
    indexes = {
      @Index(name = "idx_trust_profile_tenant", columnList = "tenant_id"),
      @Index(name = "idx_trust_profile_slug", columnList = "slug")
    },
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_trust_profile_slug",
          columnNames = {"slug"}),
      @UniqueConstraint(
          name = "uq_trust_profile_tenant",
          columnNames = {"tenant_id"})
    })
public class TrustCenterProfile extends BaseEntity {

  /** Public URL segment, e.g. /trust/acme-security. */
  @Column(name = "slug", length = 120, nullable = false)
  private String slug;

  @Column(name = "published", nullable = false)
  private Boolean published = false;

  @Column(name = "company_name", length = 255, nullable = false)
  private String companyName;

  @Column(name = "headline", length = 500)
  private String headline;

  @Column(name = "summary", columnDefinition = "TEXT")
  private String summary;

  @Column(name = "logo_url", length = 1000)
  private String logoUrl;

  @Column(name = "primary_color", length = 16)
  private String primaryColor;

  @Column(name = "contact_email", length = 320)
  private String contactEmail;

  @Column(name = "security_page_url", length = 1000)
  private String securityPageUrl;

  @Column(name = "status_page_url", length = 1000)
  private String statusPageUrl;

  // ---- Lead generation CTA ----------------------------------------------

  @Column(name = "cta_enabled", nullable = false)
  private Boolean ctaEnabled = true;

  @Column(name = "cta_heading", length = 255)
  private String ctaHeading = "Need our full security package?";

  @Column(name = "cta_body", columnDefinition = "TEXT")
  private String ctaBody =
      "Request access and our security team will share the documents covered by an NDA.";

  @Column(name = "cta_button_label", length = 120)
  private String ctaButtonLabel = "Request access";

  /** Slack channel notified when a lead comes in; null disables notification. */
  @Column(name = "lead_notify_slack_channel", length = 120)
  private String leadNotifySlackChannel;

  @OneToMany(
      mappedBy = "profile",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @OrderBy("displayOrder ASC")
  private List<TrustCenterItem> items = new ArrayList<>();

  @Column(name = "published_at")
  private OffsetDateTime publishedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tenant_id", nullable = false, insertable = false, updatable = false)
  private Tenant tenant;

  // This field writes tenant_id; the association above only reads it, so setTenantId persists.
  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  public String getSlug() {
    return slug;
  }

  public void setSlug(String slug) {
    this.slug = slug;
  }

  public Boolean getPublished() {
    return published;
  }

  public void setPublished(Boolean published) {
    this.published = published;
  }

  public String getCompanyName() {
    return companyName;
  }

  public void setCompanyName(String companyName) {
    this.companyName = companyName;
  }

  public String getHeadline() {
    return headline;
  }

  public void setHeadline(String headline) {
    this.headline = headline;
  }

  public String getSummary() {
    return summary;
  }

  public void setSummary(String summary) {
    this.summary = summary;
  }

  public String getLogoUrl() {
    return logoUrl;
  }

  public void setLogoUrl(String logoUrl) {
    this.logoUrl = logoUrl;
  }

  public String getPrimaryColor() {
    return primaryColor;
  }

  public void setPrimaryColor(String primaryColor) {
    this.primaryColor = primaryColor;
  }

  public String getContactEmail() {
    return contactEmail;
  }

  public void setContactEmail(String contactEmail) {
    this.contactEmail = contactEmail;
  }

  public String getSecurityPageUrl() {
    return securityPageUrl;
  }

  public void setSecurityPageUrl(String securityPageUrl) {
    this.securityPageUrl = securityPageUrl;
  }

  public String getStatusPageUrl() {
    return statusPageUrl;
  }

  public void setStatusPageUrl(String statusPageUrl) {
    this.statusPageUrl = statusPageUrl;
  }

  public Boolean getCtaEnabled() {
    return ctaEnabled;
  }

  public void setCtaEnabled(Boolean ctaEnabled) {
    this.ctaEnabled = ctaEnabled;
  }

  public String getCtaHeading() {
    return ctaHeading;
  }

  public void setCtaHeading(String ctaHeading) {
    this.ctaHeading = ctaHeading;
  }

  public String getCtaBody() {
    return ctaBody;
  }

  public void setCtaBody(String ctaBody) {
    this.ctaBody = ctaBody;
  }

  public String getCtaButtonLabel() {
    return ctaButtonLabel;
  }

  public void setCtaButtonLabel(String ctaButtonLabel) {
    this.ctaButtonLabel = ctaButtonLabel;
  }

  public String getLeadNotifySlackChannel() {
    return leadNotifySlackChannel;
  }

  public void setLeadNotifySlackChannel(String leadNotifySlackChannel) {
    this.leadNotifySlackChannel = leadNotifySlackChannel;
  }

  public List<TrustCenterItem> getItems() {
    return items;
  }

  public void setItems(List<TrustCenterItem> items) {
    this.items = items;
  }

  public OffsetDateTime getPublishedAt() {
    return publishedAt;
  }

  public void setPublishedAt(OffsetDateTime publishedAt) {
    this.publishedAt = publishedAt;
  }

  public Tenant getTenant() {
    return tenant;
  }

  public void setTenant(Tenant tenant) {
    this.tenant = tenant;
    this.tenantId = tenant != null ? tenant.getId() : null;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public void setTenantId(UUID tenantId) {
    this.tenantId = tenantId;
  }
}

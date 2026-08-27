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
package com.complyai.ai.program;

import com.complyai.domain.program.ControlMapping.Framework;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The control catalogue used to map observed signals onto framework controls.
 *
 * <p>Deliberately a curated subset rather than the full text of every standard: these are the
 * controls security questionnaires actually ask about, each with the keywords that identify a
 * matching signal. Adding a control here is the single place a new mapping is introduced.
 */
public final class ControlFrameworkCatalog {

  private ControlFrameworkCatalog() {}

  /**
   * @param keywords lowercase terms; a signal matches when its text contains any
   */
  public record Control(
      Framework framework, String id, String title, String category, List<String> keywords) {}

  private static final List<Control> CONTROLS =
      List.of(
          // ---- SOC 2 Trust Services Criteria ----
          new Control(
              Framework.SOC2,
              "CC6.1",
              "Logical access security controls",
              "Access Control",
              List.of(
                  "mfa",
                  "multi-factor",
                  "iam",
                  "password",
                  "access control",
                  "sso",
                  "least privilege")),
          new Control(
              Framework.SOC2,
              "CC6.6",
              "Encryption of data in transit",
              "Encryption",
              List.of("tls", "https", "in transit", "ssl", "certificate")),
          new Control(
              Framework.SOC2,
              "CC6.7",
              "Encryption of data at rest",
              "Encryption",
              List.of("encrypt", "kms", "at rest", "aes")),
          new Control(
              Framework.SOC2,
              "CC7.1",
              "Vulnerability detection and monitoring",
              "Vulnerability Management",
              List.of("vulnerability", "dependabot", "code scanning", "scan", "cve", "patch")),
          new Control(
              Framework.SOC2,
              "CC7.2",
              "Security event monitoring",
              "Logging & Monitoring",
              List.of("cloudtrail", "log", "monitor", "audit trail", "siem", "security hub")),
          new Control(
              Framework.SOC2,
              "CC7.3",
              "Security incident evaluation",
              "Incident Response",
              List.of("incident", "response", "runbook", "escalation")),
          new Control(
              Framework.SOC2,
              "CC8.1",
              "Change management",
              "Secure Development",
              List.of(
                  "branch protection",
                  "code review",
                  "pull request",
                  "change management",
                  "approval")),
          new Control(
              Framework.SOC2,
              "A1.2",
              "Backup and recovery",
              "Backup & Recovery",
              List.of(
                  "backup",
                  "snapshot",
                  "restore",
                  "rpo",
                  "rto",
                  "disaster recovery",
                  "continuity")),
          new Control(
              Framework.SOC2,
              "CC9.2",
              "Vendor and third-party risk",
              "Vendor Risk",
              List.of("vendor", "third-party", "supplier", "sub-processor")),

          // ---- ISO/IEC 27001:2022 Annex A ----
          new Control(
              Framework.ISO_27001,
              "A.5.15",
              "Access control",
              "Access Control",
              List.of("access control", "iam", "least privilege", "rbac", "permission")),
          new Control(
              Framework.ISO_27001,
              "A.5.17",
              "Authentication information",
              "Access Control",
              List.of("mfa", "multi-factor", "password", "credential", "authentication")),
          new Control(
              Framework.ISO_27001,
              "A.5.23",
              "Cloud services security",
              "Cloud Security",
              List.of("aws", "cloud", "vpc", "security group", "s3")),
          new Control(
              Framework.ISO_27001,
              "A.5.24",
              "Incident management planning",
              "Incident Response",
              List.of("incident", "response plan", "runbook")),
          new Control(
              Framework.ISO_27001,
              "A.5.30",
              "ICT readiness for continuity",
              "Business Continuity",
              List.of("continuity", "disaster recovery", "failover", "rto", "rpo")),
          new Control(
              Framework.ISO_27001,
              "A.8.8",
              "Management of technical vulnerabilities",
              "Vulnerability Management",
              List.of("vulnerability", "patch", "cve", "dependabot", "scan")),
          new Control(
              Framework.ISO_27001,
              "A.8.9",
              "Configuration management",
              "Cloud Security",
              List.of("config rule", "baseline", "configuration", "hardening")),
          new Control(
              Framework.ISO_27001,
              "A.8.15",
              "Logging",
              "Logging & Monitoring",
              List.of("log", "cloudtrail", "audit trail")),
          new Control(
              Framework.ISO_27001,
              "A.8.24",
              "Use of cryptography",
              "Encryption",
              List.of("encrypt", "kms", "tls", "aes", "crypto", "key management")),
          new Control(
              Framework.ISO_27001,
              "A.8.25",
              "Secure development lifecycle",
              "Secure Development",
              List.of("sdlc", "code review", "branch protection", "secure development", "sast")),
          new Control(
              Framework.ISO_27001,
              "A.8.28",
              "Secure coding",
              "Secure Development",
              List.of("code scanning", "sast", "secret scanning", "static analysis")),

          // ---- NIST CSF 2.0 ----
          new Control(
              Framework.NIST_CSF,
              "PR.AA-01",
              "Identities and credentials managed",
              "Access Control",
              List.of("iam", "mfa", "credential", "identity")),
          new Control(
              Framework.NIST_CSF,
              "PR.DS-01",
              "Data-at-rest protected",
              "Encryption",
              List.of("encrypt", "at rest", "kms")),
          new Control(
              Framework.NIST_CSF,
              "PR.DS-02",
              "Data-in-transit protected",
              "Encryption",
              List.of("tls", "in transit", "https")),
          new Control(
              Framework.NIST_CSF,
              "DE.CM-01",
              "Networks and services monitored",
              "Logging & Monitoring",
              List.of("monitor", "log", "security hub", "detection")),
          new Control(
              Framework.NIST_CSF,
              "ID.RA-01",
              "Vulnerabilities identified",
              "Vulnerability Management",
              List.of("vulnerability", "scan", "cve", "finding")),
          new Control(
              Framework.NIST_CSF,
              "RS.MA-01",
              "Incident response executed",
              "Incident Response",
              List.of("incident", "response")),

          // ---- PCI DSS v4 ----
          new Control(
              Framework.PCI_DSS,
              "3.5",
              "Protect stored account data",
              "Encryption",
              List.of("encrypt", "at rest", "kms", "aes")),
          new Control(
              Framework.PCI_DSS,
              "4.2",
              "Protect data in transmission",
              "Encryption",
              List.of("tls", "in transit", "https")),
          new Control(
              Framework.PCI_DSS,
              "6.3",
              "Address security vulnerabilities",
              "Vulnerability Management",
              List.of("vulnerability", "patch", "cve", "scan")),
          new Control(
              Framework.PCI_DSS,
              "8.4",
              "Multi-factor authentication",
              "Access Control",
              List.of("mfa", "multi-factor")),
          new Control(
              Framework.PCI_DSS,
              "10.2",
              "Audit logs implemented",
              "Logging & Monitoring",
              List.of("log", "audit trail", "cloudtrail")),

          // ---- CIS Controls v8 ----
          new Control(
              Framework.CIS,
              "3",
              "Data protection",
              "Encryption",
              List.of("encrypt", "kms", "data protection")),
          new Control(
              Framework.CIS,
              "5",
              "Account management",
              "Access Control",
              List.of("iam", "account", "credential", "mfa")),
          new Control(
              Framework.CIS,
              "7",
              "Continuous vulnerability management",
              "Vulnerability Management",
              List.of("vulnerability", "scan", "patch")),
          new Control(
              Framework.CIS,
              "8",
              "Audit log management",
              "Logging & Monitoring",
              List.of("log", "audit", "cloudtrail")),
          new Control(
              Framework.CIS,
              "11",
              "Data recovery",
              "Backup & Recovery",
              List.of("backup", "restore", "recovery", "snapshot")),
          new Control(
              Framework.CIS,
              "16",
              "Application software security",
              "Secure Development",
              List.of("code scanning", "sast", "secure development", "code review")));

  public static List<Control> all() {
    return CONTROLS;
  }

  public static List<Control> forFramework(Framework framework) {
    return CONTROLS.stream().filter(c -> c.framework() == framework).toList();
  }

  public static Optional<Control> find(Framework framework, String controlId) {
    return CONTROLS.stream()
        .filter(c -> c.framework() == framework && c.id().equalsIgnoreCase(controlId))
        .findFirst();
  }

  public static Map<Framework, Long> controlCounts() {
    return CONTROLS.stream()
        .collect(
            java.util.stream.Collectors.groupingBy(
                Control::framework, java.util.stream.Collectors.counting()));
  }

  /**
   * Score how well a piece of signal text matches a control, 0-100.
   *
   * <p>Score rises with the number of distinct keywords hit, and gets a bump when the signal's own
   * category already agrees — a category match is much stronger evidence than an incidental word.
   */
  public static int score(Control control, String signalText, String signalCategory) {
    if (signalText == null) return 0;
    String haystack = signalText.toLowerCase(Locale.ROOT);

    long hits = control.keywords().stream().filter(haystack::contains).count();
    if (hits == 0) return 0;

    int base = (int) Math.min(70, 40 + (hits - 1) * 15);
    boolean categoryAgrees =
        signalCategory != null && signalCategory.equalsIgnoreCase(control.category());

    return Math.min(100, categoryAgrees ? base + 25 : base);
  }
}

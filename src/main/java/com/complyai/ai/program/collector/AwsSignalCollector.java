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
package com.complyai.ai.program.collector;

import com.complyai.ai.program.ProgramSignalCollector;
import com.complyai.domain.program.ProgramSignal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.config.ConfigClient;
import software.amazon.awssdk.services.config.model.ComplianceType;
import software.amazon.awssdk.services.config.model.ConfigRule;
import software.amazon.awssdk.services.config.model.DescribeComplianceByConfigRuleResponse;
import software.amazon.awssdk.services.config.model.DescribeConfigRulesResponse;
import software.amazon.awssdk.services.securityhub.SecurityHubClient;
import software.amazon.awssdk.services.securityhub.model.GetFindingsRequest;
import software.amazon.awssdk.services.securityhub.model.GetFindingsResponse;

/**
 * Reads AWS posture from Config rules and Security Hub findings.
 *
 * <p>Config rules answer "is this control switched on"; Security Hub answers "what is currently
 * failing". Both are read-only calls.
 */
@Component
public class AwsSignalCollector implements ProgramSignalCollector {

  private static final Logger log = LoggerFactory.getLogger(AwsSignalCollector.class);

  /** Security Hub can return thousands of findings; only the worst matter here. */
  private static final int MAX_FINDINGS = 100;

  private final String region;
  private final String accessKey;
  private final String secretKey;
  private final boolean enabled;

  public AwsSignalCollector(
      @Value("${aws.region:us-east-1}") String region,
      @Value("${aws.access-key:}") String accessKey,
      @Value("${aws.secret-key:}") String secretKey,
      @Value("${aws.program-awareness.enabled:false}") boolean enabled) {
    this.region = region;
    this.accessKey = accessKey;
    this.secretKey = secretKey;
    this.enabled = enabled;
  }

  @Override
  public ProgramSignal.Source source() {
    return ProgramSignal.Source.AWS;
  }

  @Override
  public boolean isConfigured() {
    // Explicit keys are optional: on EC2/EKS the default provider chain
    // supplies an instance role, which is the preferred deployment.
    return enabled;
  }

  @Override
  public List<ProgramSignal> collect() {
    if (!isConfigured()) {
      return List.of();
    }

    List<ProgramSignal> signals = new ArrayList<>();
    signals.addAll(collectConfigRules());
    signals.addAll(collectSecurityHubFindings());
    return signals;
  }

  private List<ProgramSignal> collectConfigRules() {
    List<ProgramSignal> signals = new ArrayList<>();

    try (ConfigClient client =
        ConfigClient.builder()
            .region(Region.of(region))
            .credentialsProvider(credentials())
            .build()) {

      DescribeConfigRulesResponse rules = client.describeConfigRules();
      DescribeComplianceByConfigRuleResponse compliance = client.describeComplianceByConfigRule();

      Map<String, ComplianceType> byRule = new java.util.HashMap<>();
      compliance
          .complianceByConfigRules()
          .forEach(c -> byRule.put(c.configRuleName(), c.compliance().complianceType()));

      for (ConfigRule rule : rules.configRules()) {
        ComplianceType compliant = byRule.get(rule.configRuleName());

        ProgramSignal signal = new ProgramSignal();
        signal.setSource(ProgramSignal.Source.AWS);
        signal.setExternalId("config-rule:" + rule.configRuleArn());
        signal.setTitle(humanize(rule.configRuleName()));
        signal.setDetail(
            rule.description() != null
                ? rule.description()
                : "AWS Config rule " + rule.configRuleName());
        signal.setStatus(mapComplianceType(compliant));
        signal.setCategory(categorizeRule(rule.configRuleName()));
        signal.setResourceType("AWS::Config::ConfigRule");
        signal.setObservedAt(OffsetDateTime.now());
        signals.add(signal);
      }
    } catch (Exception e) {
      // Missing permissions or a disabled service must not fail the refresh.
      log.warn("Could not read AWS Config rules: {}", e.getMessage());
    }

    return signals;
  }

  private List<ProgramSignal> collectSecurityHubFindings() {
    List<ProgramSignal> signals = new ArrayList<>();

    try (SecurityHubClient client =
        SecurityHubClient.builder()
            .region(Region.of(region))
            .credentialsProvider(credentials())
            .build()) {

      GetFindingsResponse findings =
          client.getFindings(GetFindingsRequest.builder().maxResults(MAX_FINDINGS).build());

      findings
          .findings()
          .forEach(
              finding -> {
                ProgramSignal signal = new ProgramSignal();
                signal.setSource(ProgramSignal.Source.AWS);
                signal.setExternalId("securityhub:" + finding.id());
                signal.setTitle(finding.title());
                signal.setDetail(finding.description());
                signal.setStatus(
                    mapSeverity(
                        finding.severity() != null ? finding.severity().labelAsString() : null));
                signal.setCategory("Security Hub");
                signal.setResourceType(
                    finding.resources().isEmpty() ? "AWS" : finding.resources().get(0).type());
                signal.setSourceUrl(finding.sourceUrl());
                signal.setObservedAt(OffsetDateTime.now());
                signals.add(signal);
              });
    } catch (Exception e) {
      log.warn("Could not read AWS Security Hub findings: {}", e.getMessage());
    }

    return signals;
  }

  private software.amazon.awssdk.auth.credentials.AwsCredentialsProvider credentials() {
    if (accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank()) {
      return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    }
    return DefaultCredentialsProvider.create();
  }

  private static ProgramSignal.Status mapComplianceType(ComplianceType type) {
    if (type == null) return ProgramSignal.Status.INFORMATIONAL;
    return switch (type) {
      case COMPLIANT -> ProgramSignal.Status.PASSING;
      case NON_COMPLIANT -> ProgramSignal.Status.FAILING;
      case INSUFFICIENT_DATA -> ProgramSignal.Status.WARNING;
      default -> ProgramSignal.Status.INFORMATIONAL;
    };
  }

  private static ProgramSignal.Status mapSeverity(String label) {
    if (label == null) return ProgramSignal.Status.INFORMATIONAL;
    return switch (label.toUpperCase()) {
      case "CRITICAL", "HIGH" -> ProgramSignal.Status.FAILING;
      case "MEDIUM", "LOW" -> ProgramSignal.Status.WARNING;
      default -> ProgramSignal.Status.INFORMATIONAL;
    };
  }

  /** AWS rule names are SCREAMING_SNAKE or kebab; questionnaires are prose. */
  private static String humanize(String ruleName) {
    if (ruleName == null) return "AWS Config rule";
    String spaced = ruleName.replace('-', ' ').replace('_', ' ').trim();
    return spaced.isEmpty()
        ? ruleName
        : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
  }

  private static String categorizeRule(String ruleName) {
    if (ruleName == null) return "Cloud Security";
    String lower = ruleName.toLowerCase();
    if (lower.contains("encrypt") || lower.contains("kms")) return "Encryption";
    if (lower.contains("mfa") || lower.contains("iam") || lower.contains("password"))
      return "Access Control";
    if (lower.contains("log") || lower.contains("cloudtrail") || lower.contains("audit"))
      return "Logging & Monitoring";
    if (lower.contains("backup") || lower.contains("snapshot")) return "Backup & Recovery";
    if (lower.contains("public") || lower.contains("sg") || lower.contains("vpc"))
      return "Network Security";
    return "Cloud Security";
  }
}

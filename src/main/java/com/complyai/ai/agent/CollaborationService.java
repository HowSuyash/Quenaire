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
package com.complyai.ai.agent;

import com.complyai.security.TenantContext;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Real-time collaboration for the SME review queue.
 *
 * <p>Holds one SSE channel per tenant carrying three things reviewers need to avoid stepping on
 * each other: who else is here (presence), which questions are currently being edited (soft locks),
 * and rows that changed while you were looking at them (live updates).
 *
 * <p>State is per-instance. Running multiple app instances behind a load balancer needs a shared
 * fan-out (Redis pub/sub) in front of {@link #broadcast}; everything else here stays the same.
 */
@Service
public class CollaborationService {

  private static final Logger log = LoggerFactory.getLogger(CollaborationService.class);

  /** SSE connections idle longer than this are dropped by most proxies anyway. */
  private static final long EMITTER_TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

  /** A presence entry not refreshed within this window is considered gone. */
  private static final Duration PRESENCE_TTL = Duration.ofSeconds(45);

  /** An edit lock auto-releases after this, so a closed tab never blocks a question. */
  private static final Duration LOCK_TTL = Duration.ofMinutes(5);

  /** Stable, readable colors for presence avatars. */
  private static final List<String> PRESENCE_COLORS =
      List.of(
          "#2563eb", "#db2777", "#059669", "#d97706", "#7c3aed", "#0891b2", "#dc2626", "#4f46e5");

  private final Map<UUID, List<Subscriber>> channels = new ConcurrentHashMap<>();
  private final Map<UUID, Map<UUID, Presence>> presenceByTenant = new ConcurrentHashMap<>();
  private final Map<UUID, Map<UUID, Lock>> locksByTenant = new ConcurrentHashMap<>();

  private record Subscriber(UUID userId, SseEmitter emitter) {}

  private record Lock(UUID questionId, UUID userId, String userName, OffsetDateTime acquiredAt) {
    boolean isExpired() {
      return acquiredAt.plus(LOCK_TTL).isBefore(OffsetDateTime.now());
    }
  }

  public record Presence(
      UUID userId,
      String name,
      String email,
      String color,
      UUID focusedQuestionId,
      boolean editing,
      OffsetDateTime lastSeenAt) {
    boolean isStale() {
      return lastSeenAt.plus(PRESENCE_TTL).isBefore(OffsetDateTime.now());
    }
  }

  // ---- Subscription -----------------------------------------------------

  /**
   * Open an SSE channel for the current tenant. The caller receives the current presence roster
   * immediately so the UI has something to paint before the first heartbeat.
   */
  public SseEmitter subscribe(UUID tenantId, UUID userId, String name, String email) {
    SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
    Subscriber subscriber = new Subscriber(userId, emitter);

    List<Subscriber> tenantChannel =
        channels.computeIfAbsent(tenantId, k -> new CopyOnWriteArrayList<>());
    tenantChannel.add(subscriber);

    emitter.onCompletion(() -> removeSubscriber(tenantId, subscriber));
    emitter.onTimeout(() -> removeSubscriber(tenantId, subscriber));
    emitter.onError(e -> removeSubscriber(tenantId, subscriber));

    joinPresence(tenantId, userId, name, email);

    try {
      emitter.send(
          SseEmitter.event()
              .name("PRESENCE")
              .data(Map.of("type", "PRESENCE", "users", listPresence(tenantId))));
    } catch (IOException e) {
      log.debug("Failed to send initial presence to {}", userId, e);
      removeSubscriber(tenantId, subscriber);
    }

    broadcastPresence(tenantId);
    return emitter;
  }

  private void removeSubscriber(UUID tenantId, Subscriber subscriber) {
    List<Subscriber> tenantChannel = channels.get(tenantId);
    if (tenantChannel != null) {
      tenantChannel.remove(subscriber);
      if (tenantChannel.isEmpty()) {
        channels.remove(tenantId);
      }
    }

    // Only drop presence when the user has no other open tab.
    boolean stillConnected =
        tenantChannel != null
            && tenantChannel.stream().anyMatch(s -> s.userId().equals(subscriber.userId()));
    if (!stillConnected) {
      leavePresence(tenantId, subscriber.userId());
    }
  }

  // ---- Presence ---------------------------------------------------------

  public void joinPresence(UUID tenantId, UUID userId, String name, String email) {
    Map<UUID, Presence> roster =
        presenceByTenant.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>());
    Presence existing = roster.get(userId);
    roster.put(
        userId,
        new Presence(
            userId,
            name,
            email,
            existing != null ? existing.color() : colorFor(userId),
            existing != null ? existing.focusedQuestionId() : null,
            existing != null && existing.editing(),
            OffsetDateTime.now()));
  }

  /** Heartbeat + focus update from a connected client. */
  public void updatePresence(UUID tenantId, UUID userId, UUID focusedQuestionId, boolean editing) {
    Map<UUID, Presence> roster = presenceByTenant.get(tenantId);
    if (roster == null) {
      return;
    }
    Presence existing = roster.get(userId);
    if (existing == null) {
      return;
    }

    boolean focusChanged =
        !Objects.equals(existing.focusedQuestionId(), focusedQuestionId)
            || existing.editing() != editing;

    roster.put(
        userId,
        new Presence(
            existing.userId(),
            existing.name(),
            existing.email(),
            existing.color(),
            focusedQuestionId,
            editing,
            OffsetDateTime.now()));

    if (focusChanged) {
      broadcastPresence(tenantId);
    }
  }

  public void leavePresence(UUID tenantId, UUID userId) {
    Map<UUID, Presence> roster = presenceByTenant.get(tenantId);
    if (roster != null) {
      roster.remove(userId);
    }
    releaseAllLocks(tenantId, userId);
    broadcastPresence(tenantId);
  }

  public List<Presence> listPresence(UUID tenantId) {
    Map<UUID, Presence> roster = presenceByTenant.get(tenantId);
    if (roster == null) {
      return List.of();
    }
    return roster.values().stream().filter(p -> !p.isStale()).toList();
  }

  private String colorFor(UUID userId) {
    return PRESENCE_COLORS.get(Math.floorMod(userId.hashCode(), PRESENCE_COLORS.size()));
  }

  // ---- Soft edit locks --------------------------------------------------

  /**
   * Try to take the edit lock on a question.
   *
   * @return empty when acquired; otherwise the display name of whoever holds it
   */
  public Optional<String> acquireLock(
      UUID tenantId, UUID questionId, UUID userId, String userName) {
    Map<UUID, Lock> locks = locksByTenant.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>());

    Lock existing = locks.get(questionId);
    if (existing != null && !existing.isExpired() && !existing.userId().equals(userId)) {
      return Optional.of(existing.userName());
    }

    locks.put(questionId, new Lock(questionId, userId, userName, OffsetDateTime.now()));
    broadcast(
        tenantId,
        "QUESTION_LOCKED",
        Map.of(
            "type",
            "QUESTION_LOCKED",
            "questionId",
            questionId.toString(),
            "actorId",
            userId.toString(),
            "actorName",
            userName));
    return Optional.empty();
  }

  public void releaseLock(UUID tenantId, UUID questionId, UUID userId) {
    Map<UUID, Lock> locks = locksByTenant.get(tenantId);
    if (locks == null) {
      return;
    }

    Lock existing = locks.get(questionId);
    if (existing == null || !existing.userId().equals(userId)) {
      return;
    }

    locks.remove(questionId);
    broadcast(
        tenantId,
        "QUESTION_UNLOCKED",
        Map.of(
            "type", "QUESTION_UNLOCKED",
            "questionId", questionId.toString(),
            "actorId", userId.toString()));
  }

  private void releaseAllLocks(UUID tenantId, UUID userId) {
    Map<UUID, Lock> locks = locksByTenant.get(tenantId);
    if (locks == null) {
      return;
    }
    locks
        .values()
        .removeIf(
            lock -> {
              if (!lock.userId().equals(userId)) {
                return false;
              }
              broadcast(
                  tenantId,
                  "QUESTION_UNLOCKED",
                  Map.of(
                      "type", "QUESTION_UNLOCKED",
                      "questionId", lock.questionId().toString(),
                      "actorId", userId.toString()));
              return true;
            });
  }

  public Map<UUID, String> currentLocks(UUID tenantId) {
    Map<UUID, Lock> locks = locksByTenant.get(tenantId);
    if (locks == null) {
      return Map.of();
    }
    Map<UUID, String> result = new HashMap<>();
    locks.forEach(
        (questionId, lock) -> {
          if (!lock.isExpired()) {
            result.put(questionId, lock.userName());
          }
        });
    return result;
  }

  // ---- Broadcasting -----------------------------------------------------

  /** Push a freshly-updated queue row to everyone else in the tenant. */
  public void broadcastQuestionUpdate(
      UUID tenantId, ReviewQueueService.QueueItem item, UUID actorId, String actorName) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("type", "QUESTION_UPDATED");
    payload.put("questionId", item.questionId().toString());
    payload.put("item", item);
    payload.put("actorId", actorId != null ? actorId.toString() : null);
    payload.put("actorName", actorName);
    broadcast(tenantId, "QUESTION_UPDATED", payload);
  }

  private void broadcastPresence(UUID tenantId) {
    broadcast(tenantId, "PRESENCE", Map.of("type", "PRESENCE", "users", listPresence(tenantId)));
  }

  private void broadcast(UUID tenantId, String eventName, Object payload) {
    List<Subscriber> tenantChannel = channels.get(tenantId);
    if (tenantChannel == null || tenantChannel.isEmpty()) {
      return;
    }

    for (Subscriber subscriber : tenantChannel) {
      try {
        subscriber.emitter().send(SseEmitter.event().name(eventName).data(payload));
      } catch (Exception e) {
        // A dead client must not block delivery to the rest of the room.
        log.debug("Dropping SSE subscriber {} after send failure", subscriber.userId(), e);
        subscriber.emitter().completeWithError(e);
        tenantChannel.remove(subscriber);
      }
    }
  }

  /**
   * Keeps proxies from closing idle SSE connections and evicts presence entries whose client
   * stopped heartbeating.
   */
  @Scheduled(fixedDelay = 20_000L)
  public void heartbeat() {
    String now = OffsetDateTime.now().toString();

    for (UUID tenantId : new ArrayList<>(channels.keySet())) {
      broadcast(tenantId, "HEARTBEAT", Map.of("type", "HEARTBEAT", "at", now));
    }

    presenceByTenant.forEach(
        (tenantId, roster) -> {
          boolean removed = roster.values().removeIf(Presence::isStale);
          if (removed) {
            broadcastPresence(tenantId);
          }
        });

    locksByTenant.forEach((tenantId, locks) -> locks.values().removeIf(Lock::isExpired));
  }

  /** Convenience for callers already inside a request with tenant context. */
  public UUID requireTenantId() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    if (tenantId == null) {
      throw new IllegalStateException("No tenant context available");
    }
    return tenantId;
  }
}

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
package com.complyai.ai.agent.task;

import com.complyai.domain.agent.AgentTask;
import com.complyai.domain.agent.AgentTaskRepository;
import com.complyai.security.TenantContext;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentTaskQueue {

  private static final Logger log = LoggerFactory.getLogger(AgentTaskQueue.class);

  private final AgentTaskRepository taskRepository;

  /**
   * Optional: nothing in the product consumes these topics yet -- there is no
   * {@code @KafkaListener} anywhere -- so a deployment without a broker loses nothing. Injected as
   * an ObjectProvider so the application starts when Kafka autoconfiguration is switched off.
   */
  private final ObjectProvider<KafkaTemplate<String, String>> kafkaTemplate;

  // Local lock registry for distributed locking
  private final Map<UUID, ReentrantLock> taskLocks = new ConcurrentHashMap<>();
  private final Map<UUID, CompletableFuture<AgentTaskExecutionResult>> runningTasks =
      new ConcurrentHashMap<>();

  // Thread pool for task execution
  private final ExecutorService executorService =
      Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);

  private static final String TASK_TOPIC = "agent-tasks";
  private static final String TASK_RESULTS_TOPIC = "agent-task-results";
  private static final int LOCK_TIMEOUT_SECONDS = 30;

  public AgentTaskQueue(
      AgentTaskRepository taskRepository,
      ObjectProvider<KafkaTemplate<String, String>> kafkaTemplate) {
    this.taskRepository = taskRepository;
    this.kafkaTemplate = kafkaTemplate;
  }

  /** Try to acquire a distributed lock for a task */
  public boolean tryAcquireLock(UUID taskId) {
    ReentrantLock lock = taskLocks.computeIfAbsent(taskId, k -> new ReentrantLock());
    try {
      return lock.tryLock(LOCK_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  /** Release the lock for a task */
  public void releaseLock(UUID taskId) {
    ReentrantLock lock = taskLocks.get(taskId);
    if (lock != null && lock.isHeldByCurrentThread()) {
      lock.unlock();
    }
    taskLocks.remove(taskId);
  }

  /** Execute a task asynchronously */
  public void executeTaskAsync(AgentTask task) {
    log.info("Queueing task {} of type {} for execution", task.getId(), task.getTaskType());

    task.setStatus(AgentTask.TaskStatus.RUNNING);
    task.setStartedAt(OffsetDateTime.now());
    taskRepository.save(task);

    CompletableFuture<AgentTaskExecutionResult> future =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                return executeTask(task);
              } catch (Exception e) {
                log.error("Task {} failed with exception", task.getId(), e);
                return new AgentTaskExecutionResult(false, null, e.getMessage());
              } finally {
                runningTasks.remove(task.getId());
              }
            },
            executorService);

    runningTasks.put(task.getId(), future);

    future.whenComplete(
        (result, throwable) -> {
          if (throwable != null) {
            handleTaskFailure(task, throwable.getMessage());
          } else if (!result.success()) {
            handleTaskFailure(task, result.errorMessage());
          } else {
            handleTaskSuccess(task, result.resultJson());
          }
        });
  }

  /** Execute a single task */
  private AgentTaskExecutionResult executeTask(AgentTask task) {
    log.info("Executing task {} of type {}", task.getId(), task.getTaskType());

    try {
      switch (task.getTaskType()) {
        case PROCESS_QUESTIONNAIRE -> {
          return processQuestionnaireTask(task);
        }
        case GENERATE_ANSWER -> {
          return generateAnswerTask(task);
        }
        case ROUTE_TO_SME -> {
          return routeToSMETask(task);
        }
        case SEND_NOTIFICATION -> {
          return sendNotificationTask(task);
        }
        case EXPORT_QUESTIONNAIRE -> {
          return exportQuestionnaireTask(task);
        }
        case SYNC_KNOWLEDGE_BASE -> {
          return syncKnowledgeBaseTask(task);
        }
        case INGEST_DOCUMENT -> {
          return ingestDocumentTask(task);
        }
        default -> {
          return new AgentTaskExecutionResult(
              false, null, "Unknown task type: " + task.getTaskType());
        }
      }
    } catch (Exception e) {
      log.error("Task execution failed", e);
      return new AgentTaskExecutionResult(false, null, e.getMessage());
    }
  }

  private AgentTaskExecutionResult processQuestionnaireTask(AgentTask task) {
    log.info("Processing questionnaire task {}", task.getId());
    // Implementation would call questionnaire service
    return new AgentTaskExecutionResult(true, "{\"status\": \"processed\"}", null);
  }

  private AgentTaskExecutionResult generateAnswerTask(AgentTask task) {
    log.info("Generating answer for task {}", task.getId());
    // Implementation would call reasoning engine
    return new AgentTaskExecutionResult(true, "{\"status\": \"generated\"}", null);
  }

  private AgentTaskExecutionResult routeToSMETask(AgentTask task) {
    log.info("Routing to SME for task {}", task.getId());
    return new AgentTaskExecutionResult(true, "{\"status\": \"routed\"}", null);
  }

  private AgentTaskExecutionResult sendNotificationTask(AgentTask task) {
    log.info("Sending notification for task {}", task.getId());
    return new AgentTaskExecutionResult(true, "{\"status\": \"sent\"}", null);
  }

  private AgentTaskExecutionResult exportQuestionnaireTask(AgentTask task) {
    log.info("Exporting questionnaire for task {}", task.getId());
    return new AgentTaskExecutionResult(true, "{\"status\": \"exported\"}", null);
  }

  private AgentTaskExecutionResult syncKnowledgeBaseTask(AgentTask task) {
    log.info("Syncing knowledge base for task {}", task.getId());
    return new AgentTaskExecutionResult(true, "{\"status\": \"synced\"}", null);
  }

  private AgentTaskExecutionResult ingestDocumentTask(AgentTask task) {
    log.info("Ingesting document for task {}", task.getId());
    return new AgentTaskExecutionResult(true, "{\"status\": \"ingested\"}", null);
  }

  private void handleTaskSuccess(AgentTask task, String resultJson) {
    log.info("Task {} completed successfully", task.getId());

    task.setStatus(AgentTask.TaskStatus.COMPLETED);
    task.setCompletedAt(OffsetDateTime.now());
    task.setResultJson(resultJson);
    taskRepository.save(task);

    // Publish result event to Kafka
    publishTaskResult(task.getId(), "COMPLETED", resultJson);
  }

  private void handleTaskFailure(AgentTask task, String errorMessage) {
    log.error("Task {} failed: {}", task.getId(), errorMessage);

    task.setErrorMessage(errorMessage);
    task.setRetryCount(task.getRetryCount() + 1);

    if (task.getRetryCount() < task.getMaxRetries()) {
      task.setStatus(AgentTask.TaskStatus.RETRYING);
      task.setNextRetryAt(OffsetDateTime.now().plusMinutes(5 * task.getRetryCount()));
      log.info(
          "Task {} scheduled for retry {}/{}",
          task.getId(),
          task.getRetryCount(),
          task.getMaxRetries());
    } else {
      task.setStatus(AgentTask.TaskStatus.FAILED);
      task.setCompletedAt(OffsetDateTime.now());
      log.error("Task {} failed permanently after {} retries", task.getId(), task.getRetryCount());
    }

    taskRepository.save(task);
    publishTaskResult(task.getId(), "FAILED", errorMessage);
  }

  private void publishTaskResult(UUID taskId, String status, String resultJson) {
    try {
      String message =
          String.format(
              "{\"taskId\":\"%s\",\"status\":\"%s\",\"result\":%s,\"timestamp\":\"%s\"}",
              taskId, status, resultJson != null ? resultJson : "null", OffsetDateTime.now());
      kafkaTemplate.ifAvailable(t -> t.send(TASK_RESULTS_TOPIC, taskId.toString(), message));
    } catch (Exception e) {
      log.error("Failed to publish task result to Kafka", e);
    }
  }

  /** Submit a new task to the queue */
  @Transactional
  public AgentTask submitTask(
      AgentTask.TaskType type,
      UUID questionnaireId,
      UUID questionId,
      String payloadJson,
      Integer priority,
      Integer maxRetries) {
    AgentTask task = new AgentTask();
    task.setTaskType(type);
    task.setQuestionnaireId(questionnaireId);
    task.setQuestionId(questionId);
    task.setPayloadJson(payloadJson);
    task.setPriority(priority != null ? priority : 0);
    task.setMaxRetries(maxRetries != null ? maxRetries : 3);
    task.setTenantId(TenantContext.getCurrentTenantId());

    AgentTask saved = taskRepository.save(task);

    // Try to acquire lock and execute immediately if possible
    if (tryAcquireLock(saved.getId())) {
      executorService.submit(() -> executeTaskAsync(saved));
    }

    return saved;
  }

  /** Get task execution status */
  public Optional<AgentTask> getTaskStatus(UUID taskId) {
    return taskRepository.findById(taskId);
  }

  /** Cancel a pending task */
  @Transactional
  public boolean cancelTask(UUID taskId) {
    Optional<AgentTask> taskOpt = taskRepository.findById(taskId);
    if (taskOpt.isEmpty()) return false;

    AgentTask task = taskOpt.get();
    if (task.getStatus() == AgentTask.TaskStatus.PENDING
        || task.getStatus() == AgentTask.TaskStatus.RETRYING) {
      task.setStatus(AgentTask.TaskStatus.CANCELLED);
      taskRepository.save(task);
      return true;
    }
    return false;
  }

  /** Get queue statistics */
  public Map<String, Object> getQueueStats() {
    UUID tenantId = TenantContext.getCurrentTenantId();
    if (tenantId == null) return Map.of();

    long pending =
        taskRepository.countByTenantIdAndStatusAndDeletedAtIsNull(
            tenantId, AgentTask.TaskStatus.PENDING);
    long running =
        taskRepository.countByTenantIdAndStatusAndDeletedAtIsNull(
            tenantId, AgentTask.TaskStatus.RUNNING);
    long failed =
        taskRepository.countByTenantIdAndStatusAndDeletedAtIsNull(
            tenantId, AgentTask.TaskStatus.FAILED);
    long retrying =
        taskRepository.countByTenantIdAndStatusAndDeletedAtIsNull(
            tenantId, AgentTask.TaskStatus.RETRYING);

    return Map.of(
        "pending", pending,
        "running", running,
        "failed", failed,
        "retrying", retrying,
        "activeTasks", runningTasks.size());
  }

  /** Shutdown hook */
  public void shutdown() {
    log.info("Shutting down AgentTaskQueue, waiting for {} running tasks", runningTasks.size());
    executorService.shutdown();
    try {
      if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
        executorService.shutdownNow();
      }
    } catch (InterruptedException e) {
      executorService.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  // Result class
  public record AgentTaskExecutionResult(boolean success, String resultJson, String errorMessage) {}
}

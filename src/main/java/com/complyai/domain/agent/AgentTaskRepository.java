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
package com.complyai.domain.agent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AgentTaskRepository extends JpaRepository<AgentTask, UUID> {

  Page<AgentTask> findByTenantIdAndDeletedAtIsNull(UUID tenantId, Pageable pageable);

  Page<AgentTask> findByTenantIdAndStatusAndDeletedAtIsNull(
      UUID tenantId, AgentTask.TaskStatus status, Pageable pageable);

  List<AgentTask> findByTenantIdAndStatusAndDeletedAtIsNullOrderByPriorityDescCreatedAtAsc(
      UUID tenantId, AgentTask.TaskStatus status);

  List<AgentTask> findByTenantIdAndStatusAndNextRetryAtBeforeAndDeletedAtIsNull(
      UUID tenantId, AgentTask.TaskStatus status, java.time.OffsetDateTime now);

  Optional<AgentTask> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

  Long countByTenantIdAndStatusAndDeletedAtIsNull(UUID tenantId, AgentTask.TaskStatus status);

  @Query(
      "SELECT t FROM AgentTask t WHERE t.tenant.id = :tenantId AND t.status IN (:statuses) AND t.deletedAt IS NULL ORDER BY t.priority DESC, t.createdAt ASC")
  List<AgentTask> findReadyTasks(
      @Param("tenantId") UUID tenantId,
      @Param("statuses") List<AgentTask.TaskStatus> statuses,
      Pageable pageable);
}

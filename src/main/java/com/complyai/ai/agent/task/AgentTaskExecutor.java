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
import org.springframework.stereotype.Component;

@Component
public interface AgentTaskExecutor {

  /** Execute a task and return the result */
  AgentTaskExecutionResult execute(AgentTask task);

  /** Get the task type this executor handles */
  AgentTask.TaskType getSupportedTaskType();

  /** Result of task execution */
  record AgentTaskExecutionResult(boolean success, String resultJson, String errorMessage) {}
}

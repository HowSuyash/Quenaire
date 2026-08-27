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
package com.complyai.domain.sme;

/** Lifecycle of a single question sitting in an SME's review queue. */
public enum SMEAssignmentStatus {
  /** Routed to the SME, not yet opened. */
  PENDING,
  /** SME has claimed the item and is actively working it. */
  IN_PROGRESS,
  /** SME finished the review (approved or edited-and-approved). */
  COMPLETED,
  /** SME could not answer; bumped to SecOps/Legal. */
  ESCALATED,
  /** Assignment withdrawn (re-routed elsewhere or question deleted). */
  CANCELLED
}

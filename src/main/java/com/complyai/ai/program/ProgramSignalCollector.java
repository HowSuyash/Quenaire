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

import com.complyai.domain.program.ProgramSignal;
import java.util.List;

/**
 * Reads security-program evidence out of one connected system.
 *
 * <p>Collectors return detached {@link ProgramSignal} instances with everything except tenant and
 * persistence identity filled in; {@code ProgramAwarenessService} owns de-duplication and saving so
 * every collector stays a pure read.
 */
public interface ProgramSignalCollector {

  ProgramSignal.Source source();

  /** False when the integration has no credentials configured. */
  boolean isConfigured();

  /**
   * Collect current signals. Implementations must not throw for ordinary remote failures — return
   * what was gathered and log the rest, so one broken integration cannot fail a whole refresh.
   */
  List<ProgramSignal> collect();
}

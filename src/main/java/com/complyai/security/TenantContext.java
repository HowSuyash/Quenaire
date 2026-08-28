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
package com.complyai.security;

import com.complyai.domain.tenant.Tenant;
import com.complyai.domain.user.User;
import java.util.UUID;

public final class TenantContext {

  private static final ThreadLocal<Tenant> currentTenant = new ThreadLocal<>();
  private static final ThreadLocal<User> currentUser = new ThreadLocal<>();

  private TenantContext() {}

  public static void setCurrentTenant(Tenant tenant) {
    currentTenant.set(tenant);
  }

  public static Tenant getCurrentTenant() {
    return currentTenant.get();
  }

  public static UUID getCurrentTenantId() {
    Tenant tenant = currentTenant.get();
    return tenant != null ? tenant.getId() : null;
  }

  public static void setCurrentUser(User user) {
    currentUser.set(user);
  }

  public static User getCurrentUser() {
    return currentUser.get();
  }

  /**
   * Id of the authenticated user for this request, or {@code null} when the request is
   * unauthenticated or the JWT subject has no matching user row.
   *
   * <p>Callers that write attribution fields (reviewedBy, approvedBy, …) must tolerate {@code null}
   * rather than substituting another id — this used to return the tenant id, which silently
   * attributed every review to the tenant.
   */
  public static UUID getCurrentUserId() {
    User user = currentUser.get();
    return user != null ? user.getId() : null;
  }

  public static boolean hasTenant() {
    return currentTenant.get() != null;
  }

  public static boolean hasUser() {
    return currentUser.get() != null;
  }

  public static void clear() {
    currentTenant.remove();
    currentUser.remove();
  }

  /**
   * The tenant for this request, never {@code null}.
   *
   * <p>For callers that cannot do their work without one. Two services previously kept a private
   * {@code getCurrentTenant()} that returned {@code null} as a placeholder; every call through them
   * failed with a NullPointerException whose stack trace pointed at the dereference rather than at
   * the missing context.
   */
  public static Tenant requireCurrentTenant() {
    Tenant tenant = currentTenant.get();
    if (tenant == null) {
      throw new IllegalStateException("No tenant context available");
    }
    return tenant;
  }

  public static void requireTenant() {
    if (!hasTenant()) {
      throw new IllegalStateException("No tenant context available");
    }
  }

  public static UUID requireUserId() {
    UUID userId = getCurrentUserId();
    if (userId == null) {
      throw new IllegalStateException("No authenticated user in context");
    }
    return userId;
  }
}

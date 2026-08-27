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
package com.complyai.domain.user;

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
public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByEmailAndTenantId(String email, UUID tenantId);

  /**
   * Cross-tenant email lookup, used only by signup to reject an address that already owns a
   * workspace. Everything else must stay tenant-scoped.
   */
  Optional<User> findFirstByEmailAndDeletedAtIsNull(String email);

  Optional<User> findByKeycloakId(String keycloakId);

  List<User> findByTenantId(UUID tenantId);

  Page<User> findByTenantIdAndDeletedAtIsNull(UUID tenantId, Pageable pageable);

  Page<User> findByTenantIdAndStatusAndDeletedAtIsNull(
      UUID tenantId, UserStatus status, Pageable pageable);

  Page<User> findByTenantIdAndRoleAndDeletedAtIsNull(
      UUID tenantId, UserRole role, Pageable pageable);

  Page<User> findByTenantIdAndStatusAndRoleAndDeletedAtIsNull(
      UUID tenantId, UserStatus status, UserRole role, Pageable pageable);

  Optional<User> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

  @Query(
      "SELECT u FROM User u WHERE u.tenant.id = :tenantId AND u.status = :status AND u.deletedAt IS NULL")
  List<User> findActiveByTenantId(
      @Param("tenantId") UUID tenantId, @Param("status") UserStatus status);

  boolean existsByEmailAndTenantId(String email, UUID tenantId);

  long countByTenantIdAndRole(UUID tenantId, UserRole role);

  long countByTenantIdAndDeletedAtIsNull(UUID tenantId);

  long countByTenantIdAndStatusAndDeletedAtIsNull(UUID tenantId, UserStatus status);
}

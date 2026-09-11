/*
 * Copyright (c) 2026 Quenaire. All rights reserved.
 *
 * This file is proprietary and confidential. It is part of an unpublished work
 * owned by Quenaire and is protected by copyright and trade secret law.
 *
 * No licence, express or implied, is granted. No part of this file may be
 * copied, modified, distributed, disclosed, or used to create derivative works
 * without the prior written permission of Quenaire.
 *
 * Unauthorised copying or distribution of this file, via any medium, is
 * strictly prohibited.
 */
package com.quenaire;

import java.sql.Connection;
import java.sql.DriverManager;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base for tests that need a real Postgres with pgvector.
 *
 * <p>Prefers a database that is already running, and only starts a container when there isn't one.
 * That ordering matters for two reasons: the local development stack already has Postgres up, so
 * starting a second copy per run is pure waiting; and Testcontainers' Docker discovery is
 * unreliable on Windows, where Docker Desktop exposes its engine on a named pipe that
 * Testcontainers does not probe by default.
 *
 * <p>Resolution order:
 *
 * <ol>
 *   <li>{@code DATABASE_URL} from the environment — how CI supplies its service container
 *   <li>the local development Postgres on port 5432, database {@code quenaire_test}
 *   <li>a Testcontainers instance, as a last resort
 * </ol>
 *
 * <p>Tagged {@code integration} so the fast suite can skip these. Requiring Docker to run any test
 * at all previously made the whole suite unrunnable without it, including tests of pure string
 * handling.
 */
@Tag("integration")
// Without this the default `local` profile applies, which configures H2 and loads
// orm-local-h2.xml -- that override rewrites the embedding columns to VARBINARY,
// which cannot validate against a real vector column.
@ActiveProfiles("test")
@SpringBootTest
public abstract class IntegrationTestBase {

  private static final String LOCAL_URL =
      "jdbc:postgresql://localhost:5432/quenaire_test?stringtype=unspecified";
  private static final String LOCAL_USER = "quenaire";
  private static final String LOCAL_PASSWORD = "quenaire";

  /** Started only if no reachable database was found. Never stopped — the JVM outlives it. */
  private static PostgreSQLContainer<?> container;

  private static String url;
  private static String username;
  private static String password;

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    resolve();
    registry.add("spring.datasource.url", () -> url);
    registry.add("spring.datasource.username", () -> username);
    registry.add("spring.datasource.password", () -> password);
    registry.add("spring.flyway.enabled", () -> "true");
  }

  private static synchronized void resolve() {
    if (url != null) {
      return;
    }

    String fromEnv = System.getenv("DATABASE_URL");
    if (fromEnv != null && !fromEnv.isBlank()) {
      url = fromEnv;
      username = System.getenv().getOrDefault("DATABASE_USER", LOCAL_USER);
      password = System.getenv().getOrDefault("DATABASE_PASSWORD", LOCAL_PASSWORD);
      return;
    }

    if (reachable(LOCAL_URL, LOCAL_USER, LOCAL_PASSWORD)) {
      url = LOCAL_URL;
      username = LOCAL_USER;
      password = LOCAL_PASSWORD;
      return;
    }

    container =
        new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("quenaire")
            .withUsername(LOCAL_USER)
            .withPassword(LOCAL_PASSWORD);
    container.start();

    // The driver needs the same type-inference relaxation as production: entity
    // enums bind as varchar, and Postgres will not implicitly cast those into a
    // native enum column.
    url = container.getJdbcUrl() + "?stringtype=unspecified";
    username = container.getUsername();
    password = container.getPassword();
  }

  private static boolean reachable(String jdbcUrl, String user, String pass) {
    try (Connection ignored = DriverManager.getConnection(jdbcUrl, user, pass)) {
      return true;
    } catch (Exception e) {
      return false;
    }
  }
}

plugins {
    id("org.springframework.boot") version "4.1.0"
    id("java")
    id("org.flywaydb.flyway") version "10.14.0"
    id("com.diffplug.spotless") version "6.25.0"
    // Static analysis. CI already invoked spotbugsMain and spotbugs-exclude.xml
    // was already written, but the plugin was never applied, so the task did not
    // exist and the step failed.
    id("com.github.spotbugs") version "6.0.19"
}

group = "com.quenaire"
version = "0.0.1-SNAPSHOT"
// A toolchain, not just sourceCompatibility: the latter sets -source/-target but leaves the
// build running on whatever JDK launched Gradle. That difference is not cosmetic -- Mockito's
// inline mock maker and SpotBugs both read bytecode, and on a newer JDK than the one declared
// they fail or silently skip classes. Pinning the JDK makes a local build mean what CI means.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://repo.spring.io/milestone") }
    maven { url = uri("https://repo.spring.io/snapshot") }
    maven { url = uri("https://packages.atlassian.com/maven-3rdparty/") }
    maven { url = uri("https://maven.atlassian.com/public/") }
}

ext {
    set("testcontainersVersion", "1.19.7")
    set("keycloakVersion", "24.0.4")
    set("ollama4jVersion", "1.0.78")
}

dependencies {
    // Spring Boot BOM (replaces io.spring.dependency-management plugin)
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))

    // Spring Boot Starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // Database
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("com.h2database:h2")
    implementation("org.flywaydb:flyway-core")

    // Vector Search (pgvector)
    implementation("com.pgvector:pgvector:0.1.6")
    // Gives SqlTypes.VECTOR a real binding. Without it Hibernate serialises the
    // field to bytea and Postgres rejects the insert:
    //   column "embedding" is of type vector but expression is of type bytea
    // Version comes from Boot's platform so it tracks the Hibernate the rest of
    // the application is compiled against. Pinning it to 6.4.4 against Boot 4's
    // Hibernate 7 would load two incompatible copies of the ORM's internals.
    implementation("org.hibernate.orm:hibernate-vector")

    // Kafka
    implementation("org.springframework.kafka:spring-kafka")

    // Document Processing
    implementation("org.apache.tika:tika-core:2.9.1")
    implementation("org.apache.tika:tika-parsers-standard-package:2.9.1")
    implementation("org.apache.poi:poi-ooxml:5.2.5")
    // PDFBox over iText/OpenPDF: Apache-2.0 licensed, so it imposes no
    // copyleft obligations on this proprietary codebase.
    implementation("org.apache.pdfbox:pdfbox:2.0.31")

    // AI / LLM
    implementation("io.github.amithkoujalgi:ollama4j:1.0.73")

    // Keycloak Admin Client (for user provisioning)
    implementation("org.keycloak:keycloak-admin-client:24.0.4")

    // Utilities
    implementation("org.modelmapper:modelmapper:3.2.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("org.zalando:problem-spring-web:0.28.0")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0")
    

    // Integrations
    // The Jira client drags in Jersey 1.19 (2015), which brings the relocated
    // com.sun.xml.bind:jaxb-core alongside the org.glassfish.jaxb one Boot manages.
    // Two groups, one file name, and bootJar refuses to package a duplicate entry --
    // which is why the application had never been built into a runnable jar.
    implementation("com.atlassian.jira:jira-rest-java-client-core:5.2.0") {
        exclude(group = "com.sun.xml.bind", module = "jaxb-impl")
        exclude(group = "com.sun.xml.bind", module = "jaxb-core")
    }
    implementation("com.atlassian.jira:jira-rest-java-client-api:5.2.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.slack.api:slack-api-client:1.30.0")
    implementation("com.slack.api:bolt:1.30.0")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // Billing / Stripe
    implementation("com.stripe:stripe-java:24.16.0")

    // AWS — Security Program Awareness reads posture from Config/SecurityHub/IAM.
    // Individual service modules rather than the "aws-sdk-java" fat artifact so
    // the image does not carry every AWS client.
    implementation(platform("software.amazon.awssdk:bom:2.25.60"))
    implementation("software.amazon.awssdk:config")
    implementation("software.amazon.awssdk:securityhub")
    implementation("software.amazon.awssdk:iam")
    implementation("software.amazon.awssdk:sts")

    // Testing
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.19.8"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter:1.19.8")
    testImplementation("org.testcontainers:postgresql:1.19.8")
    testImplementation("org.testcontainers:kafka:1.19.8")
    testImplementation("org.mockito:mockito-junit-jupiter:5.12.0")
    testImplementation("org.assertj:assertj-core:3.25.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

// Only the `test` task itself. Using withType<Test> here would also apply the
// exclusion to integrationTest, which then matched nothing at all.
tasks.named<Test>("test") {
    useJUnitPlatform {
        // Fast suite: no Docker, no Spring context. Must stay runnable anywhere.
        excludeTags("integration")
    }
}

spotless {
    java {
        googleJavaFormat("1.28.0")
        licenseHeaderFile("$rootDir/license-header.txt")
    }
    kotlin {
        ktlint("0.49.0")
    }
}

flyway {
    url = "jdbc:postgresql://localhost:5432/quenaire"
    user = "quenaire"
    password = "quenaire"
    locations = arrayOf("classpath:db/migration")
    baselineOnMigrate = true
}

// SpotBugs configuration (disabled for now due to Gradle 9 compatibility)
// spotbugs {
//     toolVersion = "4.8.6"
//     ignoreFailures = false
//     effort = "max"
//     reportLevel = "medium"
//     excludeFilter = file("spotbugs-exclude.xml")
// }
//
// tasks.spotbugsMain {
//     reports {
//         xml.required.set(true)
//         html.required.set(true)
//     }
// }

/**
 * Tests that need Docker (Postgres + pgvector via Testcontainers).
 *
 * Separate from `test` so a missing Docker install cannot stop the fast suite,
 * which is what previously happened -- every test extended the Testcontainers
 * base, so `./gradlew test` failed outright without Docker.
 */
tasks.register<Test>("integrationTest") {
    description = "Runs tests that require Docker."
    group = "verification"

    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath

    useJUnitPlatform {
        includeTags("integration")
    }

    // These tests assert against Postgres, pgvector and a language model, none of which are
    // inputs Gradle can see. Left cacheable, an unchanged source tree reports BUILD SUCCESSFUL
    // without running anything -- including when the database has been dropped or the model is
    // no longer installed. The point of the suite is that it actually ran.
    outputs.upToDateWhen { false }

    shouldRunAfter(tasks.named("test"))
}

spotbugs {
    toolVersion.set("4.8.6")
    excludeFilter.set(file("$rootDir/spotbugs-exclude.xml"))
    // Report findings without breaking the build. Tighten this to `true` once
    // the existing findings are triaged, otherwise every build is red and the
    // signal gets ignored.
    ignoreFailures.set(true)
    // Deep analysis is slow; "default" keeps CI usable.
    effort.set(com.github.spotbugs.snom.Effort.DEFAULT)
    reportLevel.set(com.github.spotbugs.snom.Confidence.MEDIUM)
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    reports.create("html") {
        required.set(true)
        outputLocation.set(file("$buildDir/reports/spotbugs/${name}.html"))
    }
}

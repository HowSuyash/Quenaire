# syntax=docker/dockerfile:1

# ---- build ------------------------------------------------------------------
# The Gradle wrapper, not the `gradle` image's own Gradle. The project is on
# 9.7.1 and its settings plugin requires it; the previous `gradle:8.5-jdk21`
# base would have failed this build the first time it was run.
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

# Wrapper and build scripts first, so a source-only change reuses the
# dependency layer below.
COPY gradlew ./
COPY gradle/ gradle/
COPY build.gradle.kts settings.gradle.kts ./
COPY license-header.txt spotbugs-exclude.xml ./

# Belt and braces: .gitattributes keeps gradlew as LF and the wrapper is regenerated,
# but a checkout predating both would still carry CRLF into the image, which /bin/sh
# cannot parse. Written with an octal escape: a literal backslash-r here was itself
# turned into a newline by a text tool, which broke this file.
RUN tr -d '\015' < gradlew > /tmp/gradlew \
 && mv /tmp/gradlew gradlew \
 && chmod +x gradlew \
 && ./gradlew --no-daemon dependencies --quiet || true

COPY src/ src/

# Tests need Postgres and a language model; neither exists during an image
# build. CI runs them against real services before anything is deployed.
RUN ./gradlew --no-daemon bootJar -x test -x integrationTest --quiet

# ---- runtime ----------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN apk add --no-cache curl tzdata

RUN addgroup -g 1000 -S appgroup \
 && adduser -u 1000 -S appuser -G appgroup

COPY --from=builder /app/build/libs/*.jar app.jar

# Uploaded questionnaires and exported PDFs land here; mounted as a volume so
# they outlive the container.
RUN mkdir -p /var/quenaire/storage \
 && chown -R appuser:appgroup /app /var/quenaire

USER appuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
    CMD curl -fsS http://localhost:8080/api/actuator/health/liveness || exit 1

ENV JAVA_OPTS="-XX:MaxRAMPercentage=70.0 \
    -XX:+UseContainerSupport \
    -XX:+UseSerialGC \
    -XX:+ExitOnOutOfMemoryError \
    -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]

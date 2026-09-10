#!/usr/bin/env bash
# Readiness probe for the Keycloak container.
#
# The image ships no curl and no wget, and writing the request inline in
# docker-compose.yml means escaping CRLF through YAML, which is how the previous
# attempt corrupted the file. A script avoids the question.
#
# Health is served on the management port (9000), only when KC_HEALTH_ENABLED is
# set, and under KC_HTTP_RELATIVE_PATH -- so /auth/health/ready here, not
# /health/ready. The unprefixed path answers 404 and looks like a dead server.
set -u

exec 3<>/dev/tcp/127.0.0.1/9000 || exit 1
printf 'GET /auth/health/ready HTTP/1.1\r\nHost: keycloak\r\nConnection: close\r\n\r\n' >&3
grep -q '"status": "UP"' <&3

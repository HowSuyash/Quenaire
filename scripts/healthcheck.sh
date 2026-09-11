#!/bin/bash
# Quenaire Health Check Script
# Usage: ./healthcheck.sh [--verbose]

set -euo pipefail

VERBOSE="${1:-}"

APP_URL="${APP_URL:-http://localhost:8080}"
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8081}"
OLLAMA_URL="${OLLAMA_URL:-http://localhost:11434}"

log() {
    if [[ -n "${VERBOSE}" ]]; then
        echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"
    fi
}

error() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] ERROR: $*" >&2
}

check_endpoint() {
    local name=$1
    local url=$2
    local expected_status=${3:-200}
    
    log "Checking ${name} at ${url}..."
    
    if response=$(curl -s -w "%{http_code}" -o /dev/null --max-time 10 "${url}" 2>/dev/null); then
        if [[ "${response}" == "${expected_status}" ]]; then
            log "✓ ${name} is healthy (HTTP ${response})"
            return 0
        else
            error "✗ ${name} returned HTTP ${response} (expected ${expected_status})"
            return 1
        fi
    else
        error "✗ ${name} is unreachable"
        return 1
    fi
}

check_json_endpoint() {
    local name=$1
    local url=$2
    local jq_filter=$3
    
    log "Checking ${name} at ${url}..."
    
    if response=$(curl -s --max-time 10 "${url}" 2>/dev/null); then
        if echo "${response}" | jq -e "${jq_filter}" >/dev/null 2>&1; then
            log "✓ ${name} is healthy"
            return 0
        else
            error "✗ ${name} returned invalid response"
            return 1
        fi
    else
        error "✗ ${name} is unreachable"
        return 1
    fi
}

# Main health checks
FAILED=0

# Application health
check_endpoint "Application" "${APP_URL}/api/actuator/health" 200 || FAILED=1
check_json_endpoint "Application Details" "${APP_URL}/api/actuator/health" '.status == "UP"' || FAILED=1

# Keycloak
check_endpoint "Keycloak" "${KEYCLOAK_URL}/health/ready" 200 || FAILED=1

# Ollama
check_endpoint "Ollama" "${OLLAMA_URL}/api/tags" 200 || FAILED=1
check_json_endpoint "Ollama Models" "${OLLAMA_URL}/api/tags" '.models | length > 0' || FAILED=1

# Database (via application)
check_json_endpoint "Database" "${APP_URL}/api/actuator/health/db" '.status == "UP"' || FAILED=1

# Redis (via application)
check_json_endpoint "Redis" "${APP_URL}/api/actuator/health/redis" '.status == "UP"' || FAILED=1

# Disk space
log "Checking disk space..."
DISK_USAGE=$(df -h / | awk 'NR==2 {print $5}' | sed 's/%//')
if [[ ${DISK_USAGE} -lt 90 ]]; then
    log "✓ Disk usage: ${DISK_USAGE}%"
else
    error "✗ Disk usage critical: ${DISK_USAGE}%"
    FAILED=1
fi

# Memory
log "Checking memory..."
MEM_USAGE=$(free | awk 'NR==2 {printf "%.0f", $3*100/$2}')
if [[ ${MEM_USAGE} -lt 90 ]]; then
    log "✓ Memory usage: ${MEM_USAGE}%"
else
    error "✗ Memory usage critical: ${MEM_USAGE}%"
    FAILED=1
fi

# Summary
echo ""
if [[ ${FAILED} -eq 0 ]]; then
    log "All health checks passed ✓"
    exit 0
else
    error "${FAILED} health check(s) failed"
    exit 1
fi
#!/bin/bash
# Quenaire Database Backup Script
# Usage: ./backup.sh [daily|weekly|monthly]

set -euo pipefail

# Configuration
BACKUP_TYPE="${1:-daily}"
DB_HOST="${DB_HOST:-postgres}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-quenaire}"
DB_USER="${DB_USER:-quenaire}"
DB_PASSWORD="${DB_PASSWORD}"
BACKUP_DIR="${BACKUP_DIR:-/backups}"
RETENTION_DAILY=7
RETENTION_WEEKLY=4
RETENTION_MONTHLY=12
S3_BUCKET="${BACKUP_S3_BUCKET}"
AWS_REGION="${AWS_REGION:-us-east-1}"

# Timestamp
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
DATE_DIR=$(date +"%Y/%m/%d")
BACKUP_FILE="${DB_NAME}_${BACKUP_TYPE}_${TIMESTAMP}.sql.gz"
BACKUP_PATH="${BACKUP_DIR}/${DATE_DIR}/${BACKUP_FILE}"

# Logging
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"
}

error() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] ERROR: $*" >&2
}

# Create backup directory
mkdir -p "${BACKUP_DIR}/${DATE_DIR}"

log "Starting ${BACKUP_TYPE} backup of ${DB_NAME}..."

# Perform backup
if PGPASSWORD="${DB_PASSWORD}" pg_dump \
    -h "${DB_HOST}" \
    -p "${DB_PORT}" \
    -U "${DB_USER}" \
    -d "${DB_NAME}" \
    --no-owner \
    --no-privileges \
    --format=custom \
    --compress=9 \
    | gzip > "${BACKUP_PATH}"; then
    
    log "Backup completed: ${BACKUP_PATH}"
    
    # Get file size
    FILE_SIZE=$(du -h "${BACKUP_PATH}" | cut -f1)
    log "Backup size: ${FILE_SIZE}"
    
    # Upload to S3 if configured
    if [[ -n "${S3_BUCKET}" ]]; then
        log "Uploading to S3..."
        if aws s3 cp "${BACKUP_PATH}" "s3://${S3_BUCKET}/quenaire/backups/${DATE_DIR}/${BACKUP_FILE}" \
            --region "${AWS_REGION}" \
            --storage-class STANDARD_IA; then
            log "Upload to S3 completed"
        else
            error "Failed to upload to S3"
            exit 1
        fi
    fi
    
    # Cleanup old backups
    log "Cleaning up old backups..."
    
    case "${BACKUP_TYPE}" in
        daily)
            find "${BACKUP_DIR}" -name "${DB_NAME}_daily_*.sql.gz" -mtime +${RETENTION_DAILY} -delete
            if [[ -n "${S3_BUCKET}" ]]; then
                aws s3 ls "s3://${S3_BUCKET}/quenaire/backups/" --recursive | \
                    grep "_daily_" | \
                    awk '{print $4}' | \
                    while read -r key; do
                        DATE=$(echo "${key}" | grep -oE '[0-9]{8}')
                        if [[ -n "${DATE}" ]]; then
                            FILE_DATE=$(date -d "${DATE}" +%s 2>/dev/null || date -j -f "%Y%m%d" "${DATE}" +%s 2>/dev/null)
                            if [[ -n "${FILE_DATE}" ]]; then
                                AGE=$(( ($(date +%s) - FILE_DATE) / 86400 ))
                                if [[ ${AGE} -gt ${RETENTION_DAILY} ]]; then
                                    aws s3 rm "s3://${S3_BUCKET}/${key}"
                                    log "Deleted old daily backup: ${key}"
                                fi
                            fi
                        fi
                    done
            fi
            ;;
        weekly)
            find "${BACKUP_DIR}" -name "${DB_NAME}_weekly_*.sql.gz" -mtime +$((RETENTION_WEEKLY * 7)) -delete
            ;;
        monthly)
            find "${BACKUP_DIR}" -name "${DB_NAME}_monthly_*.sql.gz" -mtime +$((RETENTION_MONTHLY * 30)) -delete
            ;;
    esac
    
    log "Backup process completed successfully"
    
else
    error "Backup failed"
    exit 1
fi
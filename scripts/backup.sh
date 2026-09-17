#!/usr/bin/env bash
set -euo pipefail

: "${DB_URL:?Debe definir DB_URL con la URL de conexión de PostgreSQL}"
: "${B2_KEY_ID:?Debe definir B2_KEY_ID}"
: "${B2_APP_KEY:?Debe definir B2_APP_KEY}"
: "${B2_ENDPOINT:?Debe definir B2_ENDPOINT, por ejemplo https://s3.us-west-004.backblazeb2.com}"
: "${B2_BUCKET:?Debe definir B2_BUCKET}"

BACKUP_DIR="${BACKUP_DIR:-./backups}"
TIMESTAMP="$(date +%Y%m%d_%H%M)"
BACKUP_FILE="backup_${TIMESTAMP}.sql"
BACKUP_PATH="${BACKUP_DIR}/${BACKUP_FILE}"

mkdir -p "$BACKUP_DIR"

echo "Creando respaldo PostgreSQL: ${BACKUP_PATH}"
pg_dump "$DB_URL" --format=plain --file="$BACKUP_PATH"

echo "Subiendo ${BACKUP_FILE} a Backblaze B2"
AWS_ACCESS_KEY_ID="$B2_KEY_ID" \
AWS_SECRET_ACCESS_KEY="$B2_APP_KEY" \
aws s3 cp "$BACKUP_PATH" "s3://${B2_BUCKET}/${BACKUP_FILE}" \
  --endpoint-url "$B2_ENDPOINT"

echo "Respaldo completado: s3://${B2_BUCKET}/${BACKUP_FILE}"
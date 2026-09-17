#!/usr/bin/env bash
set -euo pipefail

: "${DB_URL:?Debe definir DB_URL con la URL de conexión de PostgreSQL}"
: "${B2_KEY_ID:?Debe definir B2_KEY_ID}"
: "${B2_APP_KEY:?Debe definir B2_APP_KEY}"
: "${B2_ENDPOINT:?Debe definir B2_ENDPOINT, por ejemplo https://s3.us-west-004.backblazeb2.com}"
: "${B2_BUCKET:?Debe definir B2_BUCKET}"

RESTORE_DIR="${RESTORE_DIR:-./backups/restore}"
mkdir -p "$RESTORE_DIR"

echo "Buscando el último respaldo en Backblaze B2"
LATEST_BACKUP="$(
  AWS_ACCESS_KEY_ID="$B2_KEY_ID" \
  AWS_SECRET_ACCESS_KEY="$B2_APP_KEY" \
  aws s3 ls "s3://${B2_BUCKET}/" --endpoint-url "$B2_ENDPOINT" |
    awk '{print $4}' |
    grep -E '^backup_[0-9]{8}_[0-9]{4}\.sql$' |
    sort |
    tail -n 1
)"

if [[ -z "$LATEST_BACKUP" ]]; then
  echo "No se encontró ningún archivo backup_YYYYMMDD_HHMM.sql en el bucket." >&2
  exit 1
fi

RESTORE_PATH="${RESTORE_DIR}/${LATEST_BACKUP}"

echo "Descargando ${LATEST_BACKUP}"
AWS_ACCESS_KEY_ID="$B2_KEY_ID" \
AWS_SECRET_ACCESS_KEY="$B2_APP_KEY" \
aws s3 cp "s3://${B2_BUCKET}/${LATEST_BACKUP}" "$RESTORE_PATH" \
  --endpoint-url "$B2_ENDPOINT"

echo "Restaurando ${LATEST_BACKUP} en PostgreSQL"
psql "$DB_URL" --file="$RESTORE_PATH"

echo "Restauración completada desde ${LATEST_BACKUP}"
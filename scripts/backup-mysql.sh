#!/usr/bin/env bash
set -euo pipefail

: "${MYSQL_HOST:?MYSQL_HOST is required}"
: "${MYSQL_PORT:=3306}"
: "${MYSQL_USERNAME:?MYSQL_USERNAME is required}"
: "${MYSQL_PASSWORD:?MYSQL_PASSWORD is required}"
: "${MYSQL_DATABASE:?MYSQL_DATABASE is required}"
if [[ ! "$MYSQL_DATABASE" =~ ^[A-Za-z0-9_]+$ ]]; then
  echo "MYSQL_DATABASE contains unsupported characters" >&2; exit 2
fi

output="${1:?usage: backup-mysql.sh /absolute/path/backup.sql.gz}"
case "$output" in
  /*.sql.gz) ;;
  *) echo "backup target must be an absolute .sql.gz path" >&2; exit 2 ;;
esac

umask 077
export MYSQL_PWD="$MYSQL_PASSWORD"
mysqldump --host="$MYSQL_HOST" --port="$MYSQL_PORT" --user="$MYSQL_USERNAME" \
  --single-transaction --quick --routines --triggers --events --hex-blob \
  --set-gtid-purged=OFF --no-tablespaces --default-character-set=utf8mb4 \
  -- "$MYSQL_DATABASE" | gzip -9 > "$output"
gzip -t "$output"
if command -v sha256sum >/dev/null 2>&1; then
  sha256sum "$output" > "$output.sha256"
else
  shasum -a 256 "$output" > "$output.sha256"
fi
echo "backup verified: $output"

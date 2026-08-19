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
output_dir=$(dirname "$output")
output_name=$(basename "$output")
test -d "$output_dir"

umask 077
export MYSQL_PWD="$MYSQL_PASSWORD"
temp_backup=$(mktemp "${output}.tmp.XXXXXX")
temp_checksum=$(mktemp "${output}.sha256.tmp.XXXXXX")
cleanup() {
  [[ -z "${temp_backup:-}" ]] || rm -f -- "$temp_backup"
  [[ -z "${temp_checksum:-}" ]] || rm -f -- "$temp_checksum"
}
trap cleanup EXIT

mysqldump --host="$MYSQL_HOST" --port="$MYSQL_PORT" --user="$MYSQL_USERNAME" \
  --single-transaction --quick --routines --triggers --events --hex-blob \
  --set-gtid-purged=OFF --no-tablespaces --default-character-set=utf8mb4 \
  -- "$MYSQL_DATABASE" | gzip -9 > "$temp_backup"
gzip -t "$temp_backup"
if command -v sha256sum >/dev/null 2>&1; then
  checksum=$(sha256sum "$temp_backup" | awk '{print $1}')
else
  checksum=$(shasum -a 256 "$temp_backup" | awk '{print $1}')
fi
printf '%s  %s\n' "$checksum" "$output_name" > "$temp_checksum"
mv -f -- "$temp_backup" "$output"
temp_backup=""
mv -f -- "$temp_checksum" "$output.sha256"
temp_checksum=""
echo "backup verified: $output"

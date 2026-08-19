#!/usr/bin/env bash
set -euo pipefail

: "${MYSQL_HOST:?MYSQL_HOST is required}"
: "${MYSQL_PORT:=3306}"
: "${MYSQL_USERNAME:?MYSQL_USERNAME is required}"
: "${MYSQL_PASSWORD:?MYSQL_PASSWORD is required}"
: "${MYSQL_DATABASE:?MYSQL_DATABASE is required}"
if [[ ! "$MYSQL_DATABASE" =~ ^[A-Za-z0-9_]+_restore_test$ ]]; then
  echo "MYSQL_DATABASE must be a simple name ending in _restore_test" >&2; exit 2
fi

backup="${1:?usage: restore-mysql-test.sh /absolute/path/backup.sql.gz}"
case "$MYSQL_DATABASE" in
  *_restore_test) ;;
  *) echo "refusing restore: MYSQL_DATABASE must end with _restore_test" >&2; exit 2 ;;
esac
case "$backup" in
  /*.sql.gz) ;;
  *) echo "backup source must be an absolute .sql.gz path" >&2; exit 2 ;;
esac

test -f "$backup"
test -f "$backup.sha256"
if command -v sha256sum >/dev/null 2>&1; then
  (cd "$(dirname "$backup")" && sha256sum -c "$(basename "$backup").sha256")
else
  (cd "$(dirname "$backup")" && shasum -a 256 -c "$(basename "$backup").sha256")
fi
gzip -t "$backup"
export MYSQL_PWD="$MYSQL_PASSWORD"
initial_table_count=$(mysql --host="$MYSQL_HOST" --port="$MYSQL_PORT" --user="$MYSQL_USERNAME" \
  --batch --skip-column-names -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${MYSQL_DATABASE}'")
if [[ "$initial_table_count" != "0" ]]; then
  echo "refusing restore: $MYSQL_DATABASE is not empty ($initial_table_count tables)" >&2
  exit 3
fi
if ! gzip -dc "$backup" | mysql --host="$MYSQL_HOST" --port="$MYSQL_PORT" \
  --user="$MYSQL_USERNAME" --default-character-set=utf8mb4 "$MYSQL_DATABASE"; then
  echo "restore failed: $MYSQL_DATABASE may contain partial data and must be recreated" >&2
  exit 4
fi
table_count=$(mysql --host="$MYSQL_HOST" --port="$MYSQL_PORT" --user="$MYSQL_USERNAME" \
  --batch --skip-column-names -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${MYSQL_DATABASE}' AND table_type='BASE TABLE'")
test "$table_count" -ge 43
failed_migrations=$(mysql --host="$MYSQL_HOST" --port="$MYSQL_PORT" --user="$MYSQL_USERNAME" \
  --batch --skip-column-names "$MYSQL_DATABASE" -e "SELECT COUNT(*) FROM flyway_schema_history WHERE success=0")
test "$failed_migrations" -eq 0
echo "restore verified in $MYSQL_DATABASE ($table_count tables including Flyway history)"

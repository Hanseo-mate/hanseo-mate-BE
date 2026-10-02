#!/usr/bin/env bash

set -euo pipefail

REPO="/opt/hanseo-mate/backend/source"
APP="/opt/hanseo-mate/backend/app.jar"
BACKUP="/opt/hanseo-mate/backend/app.jar.bak"
CONFIG="/etc/hanseo-mate/backend/application-prod.properties"
SERVICE="hanseo-mate"
HEALTH="http://127.0.0.1:8080/actuator/health"
DB_NAME="hanseo_mate"
DB_LOGIN_PATH="hsm-deploy"
DB_BACKUP_DIR="/opt/hanseo-mate/backend/db-backups"

export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
export PATH="$JAVA_HOME/bin:$PATH"

service_stopped=0
jar_replaced=0
db_backup_file=""

recover() {
    local status="$1"
    trap - ERR INT TERM
    echo "Deployment failed. DB backup: ${db_backup_file:-not created}" >&2

    if [ "$service_stopped" -eq 1 ]; then
        if [ "$jar_replaced" -eq 1 ] && [ -f "$BACKUP" ]; then
            sudo systemctl stop "$SERVICE" || true
            cp "$BACKUP" "$APP" || echo "Failed to restore previous JAR: $BACKUP" >&2
        fi
        sudo systemctl start "$SERVICE" || echo "Failed to restart previous service: $SERVICE" >&2
    fi

    echo "Database changes are not rolled back automatically. Keep the backup and investigate." >&2
    exit "$status"
}

trap 'recover $?' ERR
trap 'recover 130' INT
trap 'recover 143' TERM

if [ ! -f "$CONFIG" ]; then
    echo "Production config is missing: $CONFIG" >&2
    exit 1
fi

if [ ! -s "$APP" ]; then
    echo "Previous JAR is missing; safe rollback is unavailable: $APP" >&2
    exit 1
fi

for program in mysql mysqldump curl; do
    command -v "$program" >/dev/null || {
        echo "Required command is missing: $program" >&2
        exit 1
    }
done

cd "$REPO"

for sql_file in docs/home-message-migration-mysql.sql docs/club-review-strong-seniority-removal-mysql.sql; do
    if [ ! -f "$sql_file" ]; then
        echo "Required SQL file is missing: $sql_file" >&2
        exit 1
    fi
done

mysql_client=(mysql --no-defaults --login-path="$DB_LOGIN_PATH" --default-character-set=utf8mb4 --batch --skip-column-names --database="$DB_NAME")
mysql_dump=(mysqldump --no-defaults --login-path="$DB_LOGIN_PATH" --default-character-set=utf8mb4 --single-transaction --quick --no-tablespaces --routines --triggers --events)

selected_db="$("${mysql_client[@]}" --execute='SELECT DATABASE()')"
if [ "$selected_db" != "$DB_NAME" ]; then
    echo "Wrong MySQL database: $selected_db" >&2
    exit 1
fi

echo "1. Build Spring Boot JAR"
chmod +x gradlew
./gradlew clean bootJar -x test --no-daemon

JAR="$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*plain.jar' -print -quit)"
if [ -z "$JAR" ]; then
    echo "Built JAR was not found." >&2
    exit 1
fi

echo "2. Back up current JAR"
if [ -f "$APP" ]; then
    cp "$APP" "$BACKUP"
fi

echo "3. Stop application service"
service_stopped=1
sudo systemctl stop "$SERVICE"

echo "4. Back up production database"
umask 077
mkdir -p "$DB_BACKUP_DIR"
db_backup_file="$DB_BACKUP_DIR/${DB_NAME}-$(date -u +%Y%m%dT%H%M%SZ)-$$.sql"
"${mysql_dump[@]}" "$DB_NAME" > "$db_backup_file"
if [ ! -s "$db_backup_file" ]; then
    echo "Database backup is empty: $db_backup_file" >&2
    recover 1
fi

echo "5. Create home_messages table"
"${mysql_client[@]}" < docs/home-message-migration-mysql.sql

echo "6. Remove retired club review option"
"${mysql_client[@]}" < docs/club-review-strong-seniority-removal-mysql.sql

echo "7. Verify database migration"
"${mysql_client[@]}" --execute='SELECT COUNT(*) FROM home_messages' >/dev/null
remaining="$("${mysql_client[@]}" --execute="SELECT COUNT(*) FROM club_review_selections WHERE review_option = 'STRONG_SENIORITY'")"
if [ "$remaining" != "0" ]; then
    echo "STRONG_SENIORITY selections remain: $remaining" >&2
    recover 1
fi

echo "8. Install new JAR and start service"
jar_replaced=1
cp "$JAR" "$APP"
sudo systemctl start "$SERVICE"

echo "9. Check health"
for i in $(seq 1 30); do
    if curl -fsS "$HEALTH" >/dev/null 2>&1; then
        trap - ERR INT TERM
        echo "Deployment complete. DB backup: $db_backup_file"
        exit 0
    fi
    sleep 2
done

echo "Health check failed" >&2
recover 1

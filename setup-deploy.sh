#!/usr/bin/env bash

set -Eeuo pipefail

REPO="/opt/hanseo-mate/backend/source"
ENTRY="/usr/local/bin/hsm-deploy"
CONFIG="/etc/hanseo-mate/backend/application-prod.properties"
DB_NAME="hanseo_mate"
DB_BACKUP_DIR="/opt/hanseo-mate/backend/db-backups"
MYSQL_CREDENTIALS="$(mktemp /tmp/hsm-mysql-client.XXXXXX)"
trap 'rm -f "$MYSQL_CREDENTIALS"' EXIT
chmod 600 "$MYSQL_CREDENTIALS"

if [ "$(id -un)" != "hsmate" ]; then
    echo "Run this as the hsmate user." >&2
    exit 1
fi

cd "$REPO"
for program in java mysql mysqldump sudo systemctl; do
    command -v "$program" >/dev/null || {
        echo "Required command is missing: $program" >&2
        exit 1
    }
done

if [ ! -f deploy.sh ] || [ ! -f scripts/deploy-release.sh ] || [ ! -f scripts/write-mysql-client-config.java ]; then
    echo "Deploy scripts are missing. Fetch and reset to origin/main first." >&2
    exit 1
fi

java scripts/write-mysql-client-config.java "$CONFIG" "$MYSQL_CREDENTIALS"
selected_db="$(mysql --defaults-file="$MYSQL_CREDENTIALS" --no-login-paths --database="$DB_NAME" --batch --skip-column-names --execute='SELECT DATABASE()')"
if [ "$selected_db" != "$DB_NAME" ]; then
    echo "Connected to an unexpected MySQL database." >&2
    exit 1
fi

echo 'Production database connection verified:'
mysql --defaults-file="$MYSQL_CREDENTIALS" --no-login-paths --database="$DB_NAME" --table --execute='SELECT @@hostname AS mysql_host, @@port AS mysql_port, DATABASE() AS database_name'
read -r -p 'Is this the production application database? Type yes: ' confirmed
if [ "$confirmed" != "yes" ]; then
    echo 'Server deploy command was not replaced.' >&2
    exit 1
fi

mysqldump --defaults-file="$MYSQL_CREDENTIALS" --no-login-paths --single-transaction --no-tablespaces --no-data --routines --triggers --events "$DB_NAME" >/dev/null

sudo install -d -m 0700 -o hsmate -g hsmate "$DB_BACKUP_DIR"

if [ -f "$ENTRY" ]; then
    sudo cp -p "$ENTRY" "$ENTRY.before-db-migration"
fi
sudo install -m 0755 "$REPO/deploy.sh" "$ENTRY"

echo "Setup complete. Run hsm-deploy to deploy."

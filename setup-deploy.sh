#!/usr/bin/env bash

set -Eeuo pipefail

REPO="/opt/hanseo-mate/backend/source"
ENTRY="/usr/local/bin/hsm-deploy"
DB_NAME="hanseo_mate"
DB_BACKUP_DIR="/opt/hanseo-mate/backend/db-backups"

if [ "$(id -un)" != "hsmate" ]; then
    echo "Run this as the hsmate user." >&2
    exit 1
fi

cd "$REPO"
for program in mysql mysql_config_editor mysqldump; do
    command -v "$program" >/dev/null || {
        echo "Required command is missing: $program" >&2
        exit 1
    }
done

if [ ! -f deploy.sh ] || [ ! -f scripts/deploy-release.sh ]; then
    echo "Deploy scripts are missing. Fetch and reset to origin/main first." >&2
    exit 1
fi

read -r -p 'MySQL backup/migration user [root]: ' db_user
db_user="${db_user:-root}"
echo 'Enter the MySQL password from application-prod.properties, not the Linux/sudo password.'
mysql_config_editor set --login-path=hsm-deploy --host=127.0.0.1 --port=3306 --user="$db_user" --password

selected_db="$(mysql --no-defaults --login-path=hsm-deploy --protocol=TCP --host=127.0.0.1 --port=3306 --database="$DB_NAME" --batch --skip-column-names --execute='SELECT DATABASE()')"
if [ "$selected_db" != "$DB_NAME" ]; then
    echo "Wrong MySQL database: $selected_db" >&2
    exit 1
fi

echo 'Connected MySQL server and database:'
mysql --no-defaults --login-path=hsm-deploy --protocol=TCP --host=127.0.0.1 --port=3306 --database="$DB_NAME" --table --execute='SELECT @@hostname AS mysql_host, @@port AS mysql_port, DATABASE() AS database_name'
read -r -p 'Is this the production application database? Type yes: ' confirmed
if [ "$confirmed" != "yes" ]; then
    echo 'Server deploy command was not replaced.' >&2
    exit 1
fi

mysqldump --no-defaults --login-path=hsm-deploy --protocol=TCP --host=127.0.0.1 --port=3306 --single-transaction --no-tablespaces --no-data --routines --triggers --events "$DB_NAME" >/dev/null

sudo install -d -m 0700 -o hsmate -g hsmate "$DB_BACKUP_DIR"

if [ -f "$ENTRY" ]; then
    sudo cp -p "$ENTRY" "$ENTRY.before-db-migration"
fi
sudo install -m 0755 "$REPO/deploy.sh" "$ENTRY"

echo "Setup complete. Run hsm-deploy to deploy."

#!/usr/bin/env bash

set -Eeuo pipefail

REPO="/opt/hanseo-mate/backend/source"
APP="/opt/hanseo-mate/backend/app.jar"
BACKUP="/opt/hanseo-mate/backend/app.jar.bak"
CONFIG="/etc/hanseo-mate/backend/application-prod.properties"
SERVICE="hanseo-mate"
HEALTH="http://127.0.0.1:8080/actuator/health"

export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
export PATH="$JAVA_HOME/bin:$PATH"

echo "1. 운영 설정 확인"

if [ ! -f "$CONFIG" ]; then
    echo "application-prod.properties가 없습니다."
    exit 1
fi

echo "2. 최신 main 가져오기"

cd "$REPO"
git fetch origin main
git reset --hard origin/main

echo "3. Spring Boot 빌드"

chmod +x gradlew
./gradlew clean bootJar -x test --no-daemon

JAR="$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*plain.jar' | head -n 1)"

if [ -z "$JAR" ]; then
    echo "실행 JAR를 찾지 못했습니다."
    exit 1
fi

echo "4. 기존 JAR 백업"

if [ -f "$APP" ]; then
    cp "$APP" "$BACKUP"
fi

echo "5. 새 JAR 적용"

cp "$JAR" "$APP"

echo "6. Spring Boot 재시작"

sudo systemctl restart "$SERVICE"

echo "7. Health 확인"

for i in $(seq 1 30); do
    if curl -fsS "$HEALTH" >/dev/null 2>&1; then
        echo "배포 완료"
        exit 0
    fi

    sleep 2
done

echo "Health 체크 실패"

if [ -f "$BACKUP" ]; then
    echo "이전 JAR로 롤백"
    cp "$BACKUP" "$APP"
    sudo systemctl restart "$SERVICE"
    echo "롤백 완료"
fi

exit 1

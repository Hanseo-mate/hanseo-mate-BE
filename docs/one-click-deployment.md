# 원클릭 배포와 운영 DB 마이그레이션

`/usr/local/bin/hsm-deploy`는 서버에 한 번 설치하는 진입점입니다. 실행할 때마다 `origin/main`을 받은 뒤 저장소의 `scripts/deploy-release.sh`를 실행합니다. 따라서 이후 배포 절차는 GitHub의 파일을 수정해 갱신할 수 있습니다.

## 서버에서 최초 한 번만 설정

`hsmate` 계정으로 `/opt/hanseo-mate/backend/source`에 접속한 다음 아래 명령을 한 줄씩 실행합니다. 기존 원클릭 스크립트도 `git reset --hard origin/main`을 사용하므로 서버 소스 디렉터리에 보존할 로컬 변경이 없는지 먼저 확인합니다.

```bash
git status --short
git fetch origin main
git reset --hard origin/main
bash setup-deploy.sh
```

`setup-deploy.sh`는 `/etc/hanseo-mate/backend/application-prod.properties`와 systemd 서비스 환경에서 앱의 DB 접속 정보를 읽어 연결과 백업 권한을 확인합니다. 비밀번호를 다시 입력할 필요가 없습니다. 자격 증명은 실행 중에만 권한 600인 임시 파일에 기록되고 종료 때 삭제되며 Git에는 저장되지 않습니다. 연결 확인 후 운영 DB인지 `yes`로 확인합니다. 스크립트가 기존 `/usr/local/bin/hsm-deploy`를 `.before-db-migration`으로 보존한 뒤 새 진입점을 설치하고, `hsmate`만 접근할 수 있는 DB 백업 디렉터리를 준비합니다.

설정이 성공한 다음 배포는 아래 한 명령으로 실행합니다.

```bash
hsm-deploy
```

## 실행 순서

1. 최신 `main`을 받은 뒤 Spring Boot JAR를 빌드합니다.
2. 기존 JAR를 백업하고 서비스를 중지해 애플리케이션의 DB 쓰기를 멈춥니다.
3. 운영 DB를 `/opt/hanseo-mate/backend/db-backups`에 덤프합니다.
4. `docs/home-message-migration-mysql.sql`과 `docs/club-review-strong-seniority-removal-mysql.sql`을 순서대로 실행합니다.
5. 홈 메시지 테이블을 조회하고, 폐기된 `STRONG_SENIORITY` 선택 항목이 0건인지 확인합니다.
6. 새 JAR를 적용하고 서비스를 시작한 뒤 health 응답을 확인합니다.

빌드·백업·SQL·검증 중 하나라도 실패하면 새 JAR를 적용하지 않습니다. 서비스 중지 이후의 실패에는 기존 JAR로 서비스를 다시 시작하려고 시도합니다. SQL의 `COMMIT`이 끝난 데이터 변경은 JAR 복구와 함께 자동 복원되지 않습니다. DB 덤프를 보존하고 원인을 확인한 뒤 필요한 경우 별도로 복구해야 합니다. 백업 파일은 자동 삭제하지 않으므로 서버의 보관 기간과 디스크 용량을 관리합니다.

이 절차는 현재 서버에서 직접 실행·검증하기 전까지 운영 적용으로 간주하지 않습니다.

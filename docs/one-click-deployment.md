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
4. 기존 배포 절차의 `docs/home-message-migration-mysql.sql`, `docs/club-review-strong-seniority-removal-mysql.sql`, `docs/essential-link-category-removal-mysql.sql`, `docs/club-display-order-migration-mysql.sql`을 순서대로 실행하고 기존 검증을 수행합니다.
5. `docs/0001_club_recruitment_period.sql`, `docs/0002_club_recruitment_url.sql`을 포함하여 `docs/`의 4자리 숫자로 시작하는 신규 SQL을 파일명 순서대로 확인합니다. 운영 DB의 `deploy_schema_migrations`에 실행 이력이 없는 파일만 적용하고 파일명과 체크섬을 기록합니다. 이미 적용된 파일은 건너뛰며, 적용된 파일의 체크섬이 달라지면 배포를 중단합니다.
6. 새 JAR를 적용하고 서비스를 시작한 뒤 health 응답을 확인합니다.

빌드·백업·SQL·검증 중 하나라도 실패하면 새 JAR를 적용하지 않습니다. 서비스 중지 이후의 실패에는 기존 JAR로 서비스를 다시 시작하려고 시도합니다. SQL의 `COMMIT`이 끝난 데이터 변경은 JAR 복구와 함께 자동 복원되지 않습니다. DB 덤프를 보존하고 원인을 확인한 뒤 필요한 경우 별도로 복구해야 합니다. 백업 파일은 자동 삭제하지 않으므로 서버의 보관 기간과 디스크 용량을 관리합니다.

이 절차는 현재 서버에서 직접 실행·검증하기 전까지 운영 적용으로 간주하지 않습니다.

동아리 순서 SQL은 최초 실행 시 기존 ID 오름차순을 유지하도록 `display_order`를 채웁니다.
재실행해도 관리자가 저장한 순서는 초기화하지 않습니다. 운영은 `ddl-auto=validate`이므로
수동 배포에서도 새 JAR를 시작하기 전에 이 SQL을 적용해야 합니다.

모집기간 SQL `docs/0001_club_recruitment_period.sql`은 `clubs.recruitment_start_date`, `clubs.recruitment_end_date`를
nullable DATE로 추가합니다. 기존 공고 내용과 이미 저장된 날짜를 보존하며 재실행할 수 있습니다.
기존 날짜가 없는 공고는 모집완료로 응답합니다. 이 변경도 새 JAR 시작 전에 적용해야 합니다.

모집 링크 SQL `docs/0002_club_recruitment_url.sql`은 `clubs.recruitment_url`을 nullable `VARCHAR(2048)`로 추가합니다.
기존 공고와 기간은 보존하고 기존 링크는 `NULL`로 둡니다. 재실행할 수 있으며 새 JAR 시작 전에 적용해야 합니다.

상시모집 SQL `docs/0004_club_recruitment_always_open.sql`은 `clubs.recruitment_always_open`을
`BIT(1) NOT NULL DEFAULT b'0'`으로 추가합니다. 기존 동아리는 `false`로 시작하므로 현재의 기간 기준 상태를 유지합니다.
기존 내용·날짜·링크는 변경하지 않으며 컬럼이 이미 있으면 추가를 건너뜁니다. 이 파일도
`docs/deploy-migrations.txt`에 등록하며 테스트·운영 DB에 같은 파일을 새 JAR 시작 전에 적용해야 합니다.

위 동아리 모집공고 SQL은 DB 이름을 지정하지 않으므로 테스트 DB `hanseo_mate_test`와 운영 DB `hanseo_mate`에 같은 파일을 사용합니다.
테스트 서버는 `main` Push 시 기존 GitHub Actions가 `docs/`의 같은 번호 형식 SQL을 복사한 뒤
`migrate-hanseo-test`로 적용하고 새 애플리케이션을 배포합니다. 운영 서버는 사용자가 `hsm-deploy`를 실행할 때 적용됩니다.
`docs/deploy-migrations.txt`에도 해당 파일명을 기록합니다. 기존 이름의 모집기간·링크 SQL은 보존하지만
신규 자동 배포 대상으로는 위 번호 형식 파일을 사용합니다. 이미 컬럼이 있다면 위 SQL은 해당 컬럼 추가를 건너뜁니다.

필수 링크의 카테고리 제거 SQL은 이미 제거된 DB에서도 재실행할 수 있습니다. 삭제된 카테고리 데이터는 JAR 복구만으로 되돌릴 수 없습니다. 구버전 JAR로 복구해야 한다면 백업을 기준으로 DB 스키마도 함께 복구해야 링크 등록·수정이 정상 동작합니다.

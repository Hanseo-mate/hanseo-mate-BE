# 한서 메이트 백엔드

한서대학교 학생들이 학교생활에 필요한 정보를 한곳에서 확인할 수 있도록 제공하는 REST API 서버입니다.

현재 구현된 기능은 학교생활 필수 링크 관리, 학기별 강좌 일괄 수입·조회,
중앙동아리 정보 관리, 익명 요청 기반 좋아요와 선택형 활동 후기, 동아리 이미지 업로드입니다.

## 기술 스택

- Java 17
- Spring Boot 4.1.0
- Gradle 9.5.1
- Spring Web MVC
- Spring Data JPA
- MySQL
- Apache POI
- Bean Validation
- Springdoc OpenAPI
- H2 테스트 데이터베이스

## 로컬 실행 준비

JDK 17과 MySQL 8 이상이 필요합니다. 데이터베이스는 한글 저장을 보장하도록 `utf8mb4`로 생성합니다.

```sql
CREATE DATABASE hanseo_mate
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
```

로컬과 운영 환경 모두 `ddl-auto=validate`를 사용합니다. JPA가 테이블을 임의로 변경하지 않으며,
애플리케이션 시작 시 엔티티와 실제 DB 구조가 일치하는지만 확인합니다.

로컬 프로필은 기본적으로 다음 접속 정보를 사용합니다.

```text
URL: jdbc:mysql://localhost:3306/hanseo_mate
Username: root
```

비밀번호는 코드에 저장하지 않고 환경변수로 전달합니다.

```powershell
$env:DB_PASSWORD="MySQL 비밀번호"
.\gradlew.bat bootRun
```

기본값과 다른 데이터베이스를 사용하면 다음 환경변수도 설정합니다.

```text
DB_URL
DB_USERNAME
DB_PASSWORD
```

운영 환경에서는 `SPRING_PROFILES_ACTIVE=prod`를 사용하며 세 가지 DB 환경변수가 모두 필요합니다.

동아리 이미지 파일 저장 위치와 반환 URL은 다음 환경변수로 설정합니다.

```text
UPLOAD_DIRECTORY=uploads
UPLOAD_PUBLIC_BASE_URL=http://localhost:8080
UPLOAD_MAX_IMAGE_BYTES=5242880
```

운영 환경에서는 `UPLOAD_DIRECTORY`를 영속 디스크나 Docker 볼륨에 연결해야 합니다.

새 DB에는 전체 엔티티를 기준으로 정리한 단일 스키마 파일을 한 번 적용합니다.
이 파일은 기존 테이블을 변경하거나 삭제하지 않는 **빈 데이터베이스 전용** 파일입니다.

```powershell
mysql --default-character-set=utf8mb4 -u 사용자명 -p hanseo_mate --execute="source docs/database-schema-mysql.sql"
```

테이블이 하나라도 남아 있으면 스키마 실행이 중단되도록 구성했습니다. 기존 DB를 백업하고
완전히 비운 뒤 실행해야 구버전 컬럼이 섞이지 않습니다.

## API

요청·응답 예시와 오류 형식은 [필수 링크 API 명세서](docs/essential-link-api.md)에서 확인할 수 있습니다.

강좌 수입·조회 계약은 [강좌 수입·조회 API 명세서](docs/course-import-api.md)에서 확인할 수 있습니다.

동아리 기능의 Postman 테스트 순서와 요청·응답 계약은 [동아리 API 명세서](docs/club-api.md)에서 확인할 수 있습니다.

| Method | Endpoint | 설명 |
|---|---|---|
| `GET` | `/api/links` | 링크 목록을 ID 오름차순으로 조회 |
| `GET` | `/api/links?category=REMOTE_CLASS` | 카테고리별 링크 조회 |
| `GET` | `/api/links/{linkId}` | 링크 상세 조회 |
| `POST` | `/api/admin/links` | 링크 등록 |
| `PUT` | `/api/admin/links/{linkId}` | 링크 전체 수정 |
| `DELETE` | `/api/admin/links/{linkId}` | 링크 삭제 |
| `POST` | `/api/v1/timetables/major` | 전공 시간표 엑셀 분석 및 일괄 저장 |
| `POST` | `/api/v1/timetables/general-education` | 교양 시간표 엑셀 분석 및 일괄 저장 |
| `GET` | `/api/courses` | 학기·분류·학과·과목·교수 조건으로 강좌 조회 |
| `GET` | `/api/clubs` | 전체 또는 분과별 동아리 목록 조회 |
| `GET` | `/api/clubs/{clubId}` | 동아리 전체 상세 정보와 후기 작성 수 조회 |
| `PUT` | `/api/clubs/likes/{clubId}` | 익명 요청 단위 좋아요 수 변경 |
| `GET` | `/api/clubs/reviews/{clubId}` | 전체 활동 후기 키워드별 선택 비율 조회 |
| `PUT` | `/api/clubs/reviews/{clubId}` | 익명 후기 등록 또는 최근 후기 제거 |
| `POST` | `/api/admin/clubs` | 동아리 등록 |
| `PUT` | `/api/admin/clubs/background-images/{clubId}` | 배경 이미지 파일 업로드 |
| `DELETE` | `/api/admin/clubs/background-images/{clubId}` | 배경 이미지 삭제 |
| `PUT` | `/api/admin/clubs/profile-images/{clubId}` | 프로필 이미지 파일 업로드 |
| `DELETE` | `/api/admin/clubs/profile-images/{clubId}` | 프로필 이미지 삭제 |
| `PUT` | `/api/admin/clubs/{clubId}` | 동아리 텍스트 정보 통합 수정 |
| `DELETE` | `/api/admin/clubs/{clubId}` | 동아리와 좋아요·후기 데이터 삭제 |

링크 데이터는 `id`, `name`, `url`, `category`, `created_at`, `updated_at` 여섯 컬럼만 사용합니다.

## API 문서

로컬 실행 후 다음 주소에서 확인합니다.

```text
Swagger UI: http://localhost:8080/swagger-ui.html
OpenAPI JSON: http://localhost:8080/v3/api-docs
Health Check: http://localhost:8080/actuator/health
```

운영 프로필에서는 Swagger와 OpenAPI가 기본적으로 비활성화됩니다. 필요한 제한 환경에서만 다음 환경변수로 활성화합니다.

```text
SWAGGER_UI_ENABLED=true
SWAGGER_API_DOCS_ENABLED=true
```

## 테스트

테스트는 실제 MySQL이 아닌 H2의 MySQL 호환 모드에서 실행됩니다.

```powershell
.\gradlew.bat test
.\gradlew.bat build
```

## 현재 보안 주의사항

로그인, 관리자 계정, JWT, 권한 검사는 아직 구현하지 않았습니다. 링크 관리 API,
강좌 엑셀 수입 API, 동아리 관리 API를 포함한 모든 API는 현재 인증 없이 접근할 수 있습니다.

동아리 좋아요와 선택형 후기는 현재 사용자 식별 없이 요청 건수 단위로 누적합니다.
빈 활동 후기 요청은 해당 동아리의 최근 후기 한 건을 제거합니다.
이는 로그인 기능을 구현하기 전 기능 검증을 위한 임시 방식입니다.

이 상태의 서버는 기능 검증용으로만 사용해야 하며, 인터넷에 정식 공개하기 전에 별도의 인증·권한 기능을 구현해야 합니다.

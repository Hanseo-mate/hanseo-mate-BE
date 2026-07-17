# 한서 메이트 백엔드

한서대학교 학생들이 학교생활에 필요한 정보를 한곳에서 확인할 수 있도록 제공하는 REST API 서버입니다.

현재 구현된 기능은 학교생활 필수 링크 조회 및 관리입니다.

## 기술 스택

- Java 17
- Spring Boot 4.1.0
- Gradle 9.5.1
- Spring Web MVC
- Spring Data JPA
- MySQL
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

로컬 환경에서는 JPA가 엔티티 변경사항을 데이터베이스 스키마에 반영하도록 `ddl-auto=update`를 사용합니다. 운영 환경은 스키마가 임의로 변경되지 않도록 `ddl-auto=validate`를 사용합니다.

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

## API

요청·응답 예시와 오류 형식은 [필수 링크 API 명세서](docs/essential-link-api.md)에서 확인할 수 있습니다.

| Method | Endpoint | 설명 |
|---|---|---|
| `GET` | `/api/links` | 링크 목록을 ID 오름차순으로 조회 |
| `GET` | `/api/links?category=REMOTE_CLASS` | 카테고리별 링크 조회 |
| `GET` | `/api/links/{linkId}` | 링크 상세 조회 |
| `POST` | `/api/admin/links` | 링크 등록 |
| `PUT` | `/api/admin/links/{linkId}` | 링크 전체 수정 |
| `DELETE` | `/api/admin/links/{linkId}` | 링크 삭제 |

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

로그인과 관리자 권한은 아직 구현되지 않았습니다. 따라서 `/api/admin/**` API는 현재 인증 없이 접근할 수 있습니다.

이 상태의 서버는 기능 검증용으로만 사용해야 하며, 인터넷에 정식 공개하기 전에 Spring Security 기반 관리자 인증과 권한 검사를 반드시 추가해야 합니다.

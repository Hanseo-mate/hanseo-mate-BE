# 관리자 홈 포스터 API 명세서

## 1. 기능 개요

관리자가 메인 화면에 사용할 포스터 이미지를 등록, 조회, 교체, 삭제하는 기능입니다.

- 포스터 한 장을 하나의 독립된 데이터로 저장합니다.
- 등록 가능한 포스터 개수에는 제한이 없습니다.
- 포스터가 없는 상태도 허용합니다.
- 관리자 목록이 비어 있으면 `[]`를 반환합니다.
- 실제 메인 화면 조회 API는 아직 구현하지 않았습니다.
- 추후 메인 화면 API에서는 포스터가 없을 때 `null`, 있으면 등록된 전체 포스터 목록을 반환하도록 연결할 수 있습니다.

## 2. 공통 정보

```text
Base URL: http://localhost:8080
Authorization: Bearer {ADMIN_ACCESS_TOKEN}
Image Content-Type: multipart/form-data
Response Content-Type: application/json
```

모든 API는 유효한 JWT의 `role` 값이 `ADMIN`인 사용자만 호출할 수 있습니다.

| 상황 | 상태 코드 |
|---|---:|
| JWT 없음, 잘못된 JWT, 만료된 JWT | `401 Unauthorized` |
| 로그인했지만 `USER` 역할 | `403 Forbidden` |
| `ADMIN` 역할 | 요청 처리 |

## 3. 포스터 등록

포스터 이미지 한 장을 새 데이터로 등록합니다. 같은 API를 반복 호출하여 필요한 만큼 추가할 수 있습니다.

```http
POST /api/admin/home-posters
Authorization: Bearer {ADMIN_ACCESS_TOKEN}
Content-Type: multipart/form-data
```

### form-data

| Key | Type | 필수 | 설명 |
|---|---|---|---|
| `file` | File | 필수 | JPG, PNG 또는 GIF 이미지 |

### 성공 응답

```http
201 Created
Location: /api/admin/home-posters/1
```

```json
{
  "id": 1,
  "imageUrl": "http://localhost:8080/uploads/home-posters/550e8400-e29b-41d4-a716-446655440000.png",
  "createdAt": "2026-08-09T17:30:00",
  "updatedAt": "2026-08-09T17:30:00"
}
```

## 4. 포스터 목록 조회

관리자 화면에서 등록된 포스터 전체를 등록 순서대로 조회합니다.

```http
GET /api/admin/home-posters
Authorization: Bearer {ADMIN_ACCESS_TOKEN}
```

### 성공 응답

```http
200 OK
```

```json
[
  {
    "id": 1,
    "imageUrl": "http://localhost:8080/uploads/home-posters/first.png",
    "createdAt": "2026-08-09T17:30:00",
    "updatedAt": "2026-08-09T17:30:00"
  },
  {
    "id": 2,
    "imageUrl": "http://localhost:8080/uploads/home-posters/second.png",
    "createdAt": "2026-08-09T17:31:00",
    "updatedAt": "2026-08-09T17:31:00"
  }
]
```

포스터가 없으면 다음과 같이 빈 배열을 반환합니다.

```json
[]
```

## 5. 포스터 이미지 교체

지정한 포스터의 ID는 유지하면서 이미지와 URL만 교체합니다.

```http
PUT /api/admin/home-posters/{posterId}
Authorization: Bearer {ADMIN_ACCESS_TOKEN}
Content-Type: multipart/form-data
```

### Path Variable

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `posterId` | Number | 필수 | 교체할 포스터 ID, 1 이상 |

### form-data

| Key | Type | 필수 | 설명 |
|---|---|---|---|
| `file` | File | 필수 | 새 JPG, PNG 또는 GIF 이미지 |

### 성공 응답

```http
200 OK
```

```json
{
  "id": 1,
  "imageUrl": "http://localhost:8080/uploads/home-posters/replaced.png",
  "createdAt": "2026-08-09T17:30:00",
  "updatedAt": "2026-08-09T18:00:00"
}
```

교체가 정상적으로 커밋된 뒤 기존 이미지 파일은 삭제됩니다. DB 처리가 실패하면 새 파일을 삭제하고 기존 URL과 파일을 유지합니다.

## 6. 포스터 삭제

지정한 포스터 데이터와 서버가 관리하는 이미지 파일을 함께 삭제합니다.

```http
DELETE /api/admin/home-posters/{posterId}
Authorization: Bearer {ADMIN_ACCESS_TOKEN}
```

### 성공 응답

```http
204 No Content
```

응답 본문은 없습니다. 이미 삭제되었거나 존재하지 않는 ID를 다시 요청하면 `404 Not Found`를 반환합니다.

## 7. 이미지 검증

- JPG, PNG, GIF만 지원합니다.
- 요청의 확장자나 MIME 문자열이 아니라 실제 이미지 내용을 검사합니다.
- 기본 이미지 한 장의 최대 크기는 `5 MiB`입니다.
- 파일명은 UUID 기반으로 서버에서 생성합니다.
- UUID는 별도 응답 필드로 제공하지 않고 `imageUrl` 내부 파일명으로만 사용합니다.
- 등록 가능한 포스터 장수에는 애플리케이션 제한이 없습니다.

## 8. 오류 응답

```json
{
  "status": 404,
  "message": "홈 포스터를 찾을 수 없습니다. posterId=999",
  "path": "/api/admin/home-posters/999",
  "timestamp": "2026-08-09T09:00:00Z"
}
```

| 상태 코드 | 발생 상황 |
|---:|---|
| `400` | `file` 누락, 빈 파일, 이미지가 아닌 파일, 미지원 이미지, 5 MiB 초과, 1보다 작은 ID |
| `401` | JWT 없음, 잘못된 JWT, 만료된 JWT |
| `403` | `USER` 역할로 관리자 API 호출 |
| `404` | 교체하거나 삭제할 포스터가 없음 |
| `413` | 운영 프록시·웹 서버 등 외부 업로드 제한 초과 |

## 9. API 요약

| Method | URL | 기능 |
|---|---|---|
| `POST` | `/api/admin/home-posters` | 포스터 한 장 추가 |
| `GET` | `/api/admin/home-posters` | 관리자용 전체 포스터 목록 조회 |
| `PUT` | `/api/admin/home-posters/{posterId}` | 지정 포스터 이미지 교체 |
| `DELETE` | `/api/admin/home-posters/{posterId}` | 지정 포스터 삭제 |

## 10. 현재 범위에서 제외한 기능

- 일반 사용자용 메인 화면 조회 API
- 포스터 최대 개수 제한
- 포스터 제목, 링크, 활성화 여부, 만료일
- 드래그 정렬 또는 표시 순서 변경
- 포스터 상세 조회 API

표시 순서를 직접 변경해야 하는 요구가 생기면 이후 `displayOrder` 필드와 정렬 API를 별도로 추가합니다.

## 11. 운영 DB 적용

현재 프로젝트는 Flyway를 사용하지 않으며 운영 환경의 Hibernate 설정은 `ddl-auto=validate`입니다. 따라서 기존 운영 DB에는 배포 전에 아래 신규 테이블만 직접 생성해야 합니다.

```sql
CREATE TABLE home_posters (
    id BIGINT NOT NULL AUTO_INCREMENT,
    image_url VARCHAR(2048) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

`docs/database-schema-mysql.sql`은 완전히 비어 있는 새 데이터베이스용 전체 스키마이므로 기존 운영 DB에 통째로 실행하지 않습니다.

이미지 파일이 재배포 후에도 유지되도록 운영 서버에서는 `UPLOAD_DIRECTORY`를 영속 볼륨 경로로 설정하고, `UPLOAD_PUBLIC_BASE_URL`을 실제 외부 접근 주소로 설정해야 합니다.

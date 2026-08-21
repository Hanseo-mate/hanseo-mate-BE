# 시스템 공지 API 명세서

## 1. 기능 개요

관리자가 서비스 운영에 필요한 시스템 공지를 제목과 내용으로 등록·수정·삭제하고,
모든 사용자가 로그인 없이 시스템 공지 전체를 조회하는 기능입니다.

- 학생회 공지 및 학교 홈페이지 크롤링 공지와 별도로 저장합니다.
- 작성자, 조회수, 첨부파일, 이미지 기능은 제공하지 않습니다.
- 공개 조회는 페이지네이션 없이 모든 공지를 최신 작성순으로 반환합니다.
- 데이터가 없으면 `404`가 아니라 `200 OK`와 빈 배열 `[]`을 반환합니다.

## 2. API 목록

| Method | Endpoint | 인증 | 설명 |
|---|---|---|---|
| `GET` | `/api/system-notices` | 불필요 | 시스템 공지 전체 조회 |
| `POST` | `/api/admin/system-notices` | ADMIN | 시스템 공지 등록 |
| `PUT` | `/api/admin/system-notices/{noticeId}` | ADMIN | 시스템 공지 전체 수정 |
| `DELETE` | `/api/admin/system-notices/{noticeId}` | ADMIN | 시스템 공지 삭제 |

## 3. 공통 데이터 구조

```json
{
  "id": 12,
  "title": "서비스 점검 안내",
  "content": "8월 25일 02:00부터 03:00까지 서비스 점검을 진행합니다.",
  "createdAt": "2026-08-21T14:30:00",
  "updatedAt": "2026-08-21T14:30:00"
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Number | 시스템 공지 ID |
| `title` | String | 제목, 최대 500자 |
| `content` | String | 내용, 최대 100,000자, 줄바꿈과 이모지를 보존 |
| `createdAt` | String | 생성 시각, ISO LocalDateTime |
| `updatedAt` | String | 마지막 수정 시각, ISO LocalDateTime |

## 4. 시스템 공지 전체 조회

```http
GET /api/system-notices
```

- JWT와 로그인은 필요하지 않습니다.
- `createdAt DESC`, 같은 생성 시각이면 `id DESC` 순으로 반환합니다.
- 각 항목에 제목과 전체 내용이 모두 포함됩니다.

### 성공 응답

```json
[
  {
    "id": 12,
    "title": "서비스 점검 안내",
    "content": "8월 25일 02:00부터 03:00까지 서비스 점검을 진행합니다.",
    "createdAt": "2026-08-21T14:30:00",
    "updatedAt": "2026-08-21T14:30:00"
  },
  {
    "id": 11,
    "title": "앱 업데이트 안내",
    "content": "새 버전이 배포되었습니다.",
    "createdAt": "2026-08-20T09:00:00",
    "updatedAt": "2026-08-20T09:00:00"
  }
]
```

공지 없음:

```json
[]
```

## 5. 시스템 공지 등록

```http
POST /api/admin/system-notices
Authorization: Bearer {adminAccessToken}
Content-Type: application/json
```

```json
{
  "title": "서비스 점검 안내",
  "content": "8월 25일 02:00부터 03:00까지 서비스 점검을 진행합니다."
}
```

- 요청 필드는 `title`, `content` 두 개뿐입니다.
- 성공 시 `201 Created`와 생성된 공지를 반환합니다.
- 제목의 앞뒤 공백은 제거하고, 본문은 입력한 줄바꿈을 그대로 보존합니다.

## 6. 시스템 공지 수정

```http
PUT /api/admin/system-notices/12
Authorization: Bearer {adminAccessToken}
Content-Type: application/json
```

```json
{
  "title": "서비스 점검 시간 변경 안내",
  "content": "점검 시간이 03:00부터 04:00까지로 변경되었습니다."
}
```

- 제목과 내용을 모두 보내는 전체 수정 방식입니다.
- 성공 시 `200 OK`와 수정된 공지를 반환합니다.
- `id`, `createdAt`은 유지되고 `updatedAt`은 갱신됩니다.

## 7. 시스템 공지 삭제

```http
DELETE /api/admin/system-notices/12
Authorization: Bearer {adminAccessToken}
```

성공 시 `204 No Content`이며 응답 본문은 없습니다.

## 8. 입력 검증

| 조건 | 응답 |
|---|---|
| 제목 누락·`null`·공백 | `400 Bad Request` |
| 제목 500자 초과 | `400 Bad Request` |
| 내용 누락·`null`·공백 | `400 Bad Request` |
| 내용 100,000자 초과 | `400 Bad Request` |
| 공지 ID가 0 이하 또는 숫자가 아님 | `400 Bad Request` |
| 수정·삭제 대상이 없음 | `404 Not Found` |
| 관리자 토큰 없음·만료·위조 | `401 Unauthorized` |
| 일반 사용자 토큰으로 관리자 API 호출 | `403 Forbidden` |

오류 응답은 공통 형식을 사용합니다.

```json
{
  "status": 404,
  "message": "시스템 공지를 찾을 수 없습니다. noticeId=999",
  "path": "/api/admin/system-notices/999",
  "timestamp": "2026-08-21T06:00:00Z"
}
```

## 9. DB 배포

운영은 `ddl-auto=validate`이므로 코드 배포 전에
`docs/system-notice-migration-mysql.sql`을 실행해야 합니다.

- 신규 테이블: `system_notices`
- 기존 운영 DB에는 `docs/database-schema-mysql.sql` 전체를 실행하지 않습니다.
- 신규 properties와 파일 저장소 설정은 필요하지 않습니다.

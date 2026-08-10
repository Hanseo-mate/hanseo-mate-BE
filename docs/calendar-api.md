# 학생회 캘린더 API

## 1. 기능 개요

학생회가 일정을 등록·수정·삭제하고, 일반 사용자가 로그인 없이 전체 일정을 조회하는 기능입니다.

- 일정 등록 개수 제한 없음
- 같은 날짜 또는 서로 겹치는 일정 등록 가능
- 시작일과 종료일이 같은 하루 일정 등록 가능
- 공개 조회는 로그인 불필요
- 관리자 기능은 `ADMIN` 역할의 Bearer JWT 필요
- 날짜 형식은 `YYYY-MM-DD`
- 목록은 시작일 → 종료일 → ID 오름차순
- 페이지네이션 없이 전체 일정을 배열로 반환

## 2. 공통 응답 필드

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Number | 일정 ID, 수정·삭제에 사용 |
| `startDate` | String | 일정 시작일, `YYYY-MM-DD` |
| `endDate` | String | 일정 종료일, `YYYY-MM-DD` |
| `title` | String | 일정 제목 |
| `content` | String | 일정 내용 |

```json
{
  "id": 1,
  "startDate": "2026-08-10",
  "endDate": "2026-08-12",
  "title": "학생회 행사 안내",
  "content": "학생회 행사를 진행합니다. 🎉\n많은 참여 부탁드립니다."
}
```

## 3. 일반 사용자 일정 전체 조회

로그인하지 않은 사용자도 학생회 일정을 조회할 수 있습니다.

```http
GET /api/calendars
```

### 성공 응답

```http
HTTP/1.1 200 OK
```

```json
[
  {
    "id": 1,
    "startDate": "2026-08-10",
    "endDate": "2026-08-12",
    "title": "학생회 행사 안내",
    "content": "학생회 행사를 진행합니다."
  },
  {
    "id": 2,
    "startDate": "2026-08-15",
    "endDate": "2026-08-15",
    "title": "학생회 회의",
    "content": "정기 회의를 진행합니다."
  }
]
```

등록된 일정이 없으면 다음과 같이 빈 배열을 반환합니다.

```json
[]
```

## 4. 관리자 일정 전체 조회

```http
GET /api/admin/calendars
Authorization: Bearer {ADMIN_ACCESS_TOKEN}
```

성공 응답 구조와 정렬 기준은 일반 사용자 조회 API와 같습니다.

## 5. 관리자 일정 등록

```http
POST /api/admin/calendars
Authorization: Bearer {ADMIN_ACCESS_TOKEN}
Content-Type: application/json
```

### 요청

```json
{
  "startDate": "2026-08-10",
  "endDate": "2026-08-12",
  "title": "학생회 행사 안내",
  "content": "학생회 행사를 진행합니다. 🎉\n많은 참여 부탁드립니다."
}
```

### 성공 응답

```http
HTTP/1.1 201 Created
```

```json
{
  "id": 1,
  "startDate": "2026-08-10",
  "endDate": "2026-08-12",
  "title": "학생회 행사 안내",
  "content": "학생회 행사를 진행합니다. 🎉\n많은 참여 부탁드립니다."
}
```

## 6. 관리자 일정 수정

기존 일정의 네 필드를 모두 전달하는 전체 수정 방식입니다.

```http
PUT /api/admin/calendars/{calendarId}
Authorization: Bearer {ADMIN_ACCESS_TOKEN}
Content-Type: application/json
```

### 요청

```json
{
  "startDate": "2026-08-20",
  "endDate": "2026-08-25",
  "title": "수정된 학생회 행사 안내",
  "content": "행사 일정이 변경되었습니다. ✅"
}
```

### 성공 응답

```http
HTTP/1.1 200 OK
```

```json
{
  "id": 1,
  "startDate": "2026-08-20",
  "endDate": "2026-08-25",
  "title": "수정된 학생회 행사 안내",
  "content": "행사 일정이 변경되었습니다. ✅"
}
```

## 7. 관리자 일정 삭제

```http
DELETE /api/admin/calendars/{calendarId}
Authorization: Bearer {ADMIN_ACCESS_TOKEN}
```

### 성공 응답

```http
HTTP/1.1 204 No Content
```

응답 본문은 없습니다.

## 8. 요청 검증

| 필드 | 필수 | 검증 |
|---|---|---|
| `startDate` | 필수 | `YYYY-MM-DD` 형식 |
| `endDate` | 필수 | `YYYY-MM-DD` 형식, 시작일보다 빠를 수 없음 |
| `title` | 필수 | 공백만 입력 불가, 최대 500자 |
| `content` | 필수 | 공백만 입력 불가, 애플리케이션 글자 수 제한 없음 |

다음 일정은 모두 등록할 수 있습니다.

- 시작일과 종료일이 같은 하루 일정
- 다른 일정과 날짜가 일부 또는 전부 겹치는 일정
- 시작일·종료일·제목·내용이 완전히 같은 일정
- 기존 일정 개수와 관계없는 추가 일정

제목의 앞뒤 공백은 제거합니다. 내용은 줄바꿈과 이모지를 포함한 원문을 그대로 저장합니다.

## 9. 오류 응답

오류는 공통 형식으로 반환합니다.

```json
{
  "status": 400,
  "message": "일정 종료일은 시작일보다 빠를 수 없습니다.",
  "path": "/api/admin/calendars",
  "timestamp": "2026-08-10T10:00:00Z"
}
```

| HTTP 상태 | 발생 상황 |
|---:|---|
| `400 Bad Request` | 필수값 누락, 공백 제목·내용, 날짜 형식 오류, 종료일이 시작일보다 빠름, 잘못된 일정 ID |
| `401 Unauthorized` | 관리자 API에 토큰이 없거나 토큰이 유효하지 않음 |
| `403 Forbidden` | `USER` 역할로 관리자 API 요청 |
| `404 Not Found` | 수정·삭제할 일정이 존재하지 않음 |
| `405 Method Not Allowed` | 지원하지 않는 HTTP 메서드 요청 |
| `415 Unsupported Media Type` | JSON이 아닌 지원하지 않는 Content-Type 요청 |

## 10. API 요약

| 구분 | Method | URL | 인증 | 기능 |
|---|---|---|---|---|
| 일반 사용자 | `GET` | `/api/calendars` | 불필요 | 학생회 일정 전체 조회 |
| 관리자 | `GET` | `/api/admin/calendars` | ADMIN JWT | 관리자용 일정 전체 조회 |
| 관리자 | `POST` | `/api/admin/calendars` | ADMIN JWT | 일정 등록 |
| 관리자 | `PUT` | `/api/admin/calendars/{calendarId}` | ADMIN JWT | 일정 전체 수정 |
| 관리자 | `DELETE` | `/api/admin/calendars/{calendarId}` | ADMIN JWT | 일정 삭제 |

## 11. 운영 DB 적용

운영 환경은 Hibernate `ddl-auto=validate`를 사용하므로 배포 전에 운영 DB에 `student_council_calendar_events` 테이블을 직접 생성해야 합니다.

기존 운영 DB에는 `docs/database-schema-mysql.sql` 전체를 실행하지 말고 신규 테이블 생성문만 적용해야 합니다.

```sql
CREATE TABLE student_council_calendar_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    title VARCHAR(500) NOT NULL,
    content LONGTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_calendar_event_dates (start_date, end_date, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

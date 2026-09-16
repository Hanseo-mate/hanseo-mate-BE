# 학교 공식 일정 API

## 1. 기능 개요

학교 공식 일정을 로그인 없이 조회하고, 관리자가 등록·수정·삭제하는 기능입니다.
학생회 일정 및 사용자 개인 일정과 별도 테이블로 관리합니다.

- 공개 조회는 로그인 불필요
- 변경 API는 ADMIN Bearer JWT 필수
- 일정 개수 제한 없음
- 같은 날짜·제목 및 서로 겹치는 일정 등록 가능
- 날짜 형식: `YYYY-MM-DD`
- 정렬: 시작일 → 종료일 → ID 오름차순

## 2. 요청 및 응답

### 등록·수정 요청

```json
{
  "startDate": "2026-08-24",
  "endDate": "2026-08-24",
  "title": "2학기 개강"
}
```

### 응답

```json
{
  "id": 1,
  "startDate": "2026-08-24",
  "endDate": "2026-08-24",
  "title": "2학기 개강"
}
```

## 3. 학교 일정 공개 조회

```http
GET /api/calendars/school
```

인증 없이 요청할 수 있습니다. 성공 시 `200 OK`와 전체 학교 일정 배열을 반환하며,
등록된 일정이 없으면 `[]`를 반환합니다.

## 4. 관리자용 학교 일정 조회

```http
GET /api/admin/school-calendars
Authorization: Bearer {ADMIN_ACCESS_TOKEN}
```

성공 시 `200 OK`와 전체 학교 일정 배열을 반환합니다.

## 5. 학교 일정 등록

```http
POST /api/admin/school-calendars
Authorization: Bearer {ADMIN_ACCESS_TOKEN}
Content-Type: application/json
```

성공 시 `201 Created`와 생성된 일정을 반환합니다.

## 6. 학교 일정 수정

```http
PUT /api/admin/school-calendars/{calendarId}
Authorization: Bearer {ADMIN_ACCESS_TOKEN}
Content-Type: application/json
```

시작일·종료일·제목을 모두 보내는 전체 수정입니다. 성공 시 `200 OK`를 반환합니다.

## 7. 학교 일정 삭제

```http
DELETE /api/admin/school-calendars/{calendarId}
Authorization: Bearer {ADMIN_ACCESS_TOKEN}
```

성공 시 `204 No Content`를 반환하며 본문은 없습니다.

## 8. 검증 및 오류

| 필드 | 필수 | 검증 |
|---|---|---|
| `startDate` | 필수 | `YYYY-MM-DD` 형식 |
| `endDate` | 필수 | `YYYY-MM-DD` 형식, 시작일보다 빠를 수 없음 |
| `title` | 필수 | 공백만 입력 불가, 최대 500자 |

| 상태 | 발생 상황 |
|---:|---|
| `400 Bad Request` | 잘못된 요청값, 날짜 범위, 일정 ID |
| `401 Unauthorized` | 관리자 API에 토큰 없음 또는 유효하지 않은 토큰 |
| `403 Forbidden` | USER 권한으로 관리자 API 요청 |
| `404 Not Found` | 수정·삭제할 학교 일정이 없음 |

## 9. 운영 DB 적용

운영 환경이 `ddl-auto=validate`이므로 배포 전에 다음 테이블을 생성합니다.

```sql
CREATE TABLE school_calendar_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    title VARCHAR(500) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_school_calendar_events_dates (start_date, end_date, id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
```

실제 학교 일정 값은 공식 학사일정을 확인한 뒤 관리자 API로 등록하는 방식을 권장합니다.
직접 SQL로 넣을 경우 `created_at`, `updated_at`에 `NOW(6)`을 입력해야 합니다.

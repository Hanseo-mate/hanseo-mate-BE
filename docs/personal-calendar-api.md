# 개인 일정 API

## 1. 기능 개요

로그인 사용자가 자신의 일정을 조회·등록·수정·삭제하는 기능입니다.
학생회 일정과 별도의 데이터로 저장되며 다른 사용자의 일정은 조회하거나 변경할 수 없습니다.

- 모든 API에 Bearer JWT 필요
- 요청에서 사용자 ID를 받지 않고 JWT 사용자 ID 사용
- 일정 개수 제한 없음
- 같은 날짜와 제목의 일정 중복 등록 가능
- 서로 날짜가 겹치는 일정 등록 가능
- 날짜 형식: `YYYY-MM-DD`
- 목록 정렬: 시작일 → 종료일 → ID 오름차순

## 2. 공통 요청 및 응답

### 요청

```json
{
  "startDate": "2026-08-10",
  "endDate": "2026-08-12",
  "title": "개인 일정"
}
```

### 응답

```json
{
  "id": 1,
  "startDate": "2026-08-10",
  "endDate": "2026-08-12",
  "title": "개인 일정"
}
```

## 3. 내 일정 전체 조회

```http
GET /api/calendars/me
Authorization: Bearer {ACCESS_TOKEN}
```

성공 시 `200 OK`와 현재 로그인 사용자의 일정 배열을 반환합니다.
일정이 없으면 `[]`를 반환합니다.

## 4. 내 일정 등록

```http
POST /api/calendars/me
Authorization: Bearer {ACCESS_TOKEN}
Content-Type: application/json
```

성공 시 `201 Created`와 생성된 일정을 반환합니다.

## 5. 내 일정 수정

```http
PUT /api/calendars/me/{calendarId}
Authorization: Bearer {ACCESS_TOKEN}
Content-Type: application/json
```

시작일·종료일·제목을 모두 전달하는 전체 수정 방식입니다.
성공 시 `200 OK`와 수정된 일정을 반환합니다.

존재하지 않거나 다른 사용자가 소유한 일정 ID는 `404 Not Found`로 처리합니다.

## 6. 내 일정 삭제

```http
DELETE /api/calendars/me/{calendarId}
Authorization: Bearer {ACCESS_TOKEN}
```

성공 시 `204 No Content`를 반환하며 응답 본문은 없습니다.
존재하지 않거나 다른 사용자가 소유한 일정 ID는 `404 Not Found`로 처리합니다.

## 7. 요청 검증

| 필드 | 필수 | 검증 |
|---|---|---|
| `startDate` | 필수 | `YYYY-MM-DD` 형식 |
| `endDate` | 필수 | `YYYY-MM-DD` 형식, 시작일보다 빠를 수 없음 |
| `title` | 필수 | 공백만 입력 불가, 최대 500자 |

## 8. 오류 응답

```json
{
  "status": 400,
  "message": "일정 종료일은 시작일보다 빠를 수 없습니다.",
  "path": "/api/calendars/me",
  "timestamp": "2026-08-10T10:00:00Z"
}
```

| 상태 | 발생 상황 |
|---:|---|
| `400 Bad Request` | 필수값 누락, 공백 제목, 잘못된 날짜, 종료일이 시작일보다 빠름, 잘못된 일정 ID |
| `401 Unauthorized` | 토큰 없음, 잘못되거나 만료된 토큰, 존재하지 않는 사용자 토큰 |
| `404 Not Found` | 본인 소유가 아닌 일정 또는 존재하지 않는 일정 |
| `405 Method Not Allowed` | 지원하지 않는 HTTP 메서드 |
| `415 Unsupported Media Type` | JSON이 아닌 지원하지 않는 Content-Type |

## 9. 운영 DB 적용

운영 환경이 `ddl-auto=validate`이므로 배포 전에 다음 신규 테이블을 생성해야 합니다.

```sql
CREATE TABLE personal_calendar_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    title VARCHAR(500) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_personal_calendar_events_owner_dates (
        owner_id,
        start_date,
        end_date,
        id
    ),
    CONSTRAINT fk_personal_calendar_events_owner
        FOREIGN KEY (owner_id)
        REFERENCES user_accounts (id)
        ON DELETE CASCADE
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
```

기존 운영 DB에는 전체 스키마 파일을 실행하지 말고 위 신규 `CREATE TABLE`만 실행합니다.

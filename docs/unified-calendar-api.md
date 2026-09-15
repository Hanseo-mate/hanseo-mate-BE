# 통합 일정 조회 API

## 1. 기능 개요

학교 공식 일정, 학생회 일정, 로그인 사용자의 개인 일정을 하나의 배열로 조회합니다.

- 비로그인: 학교 일정 + 학생회 일정
- 로그인: 학교 일정 + 학생회 일정 + 본인 개인 일정
- 관리자 전체 공개 일정 조회: 학교 일정 + 학생회 일정, 개인 일정 제외
- 잘못되거나 만료된 토큰을 보내면 `401 Unauthorized`
- 다른 사용자의 개인 일정은 포함하지 않음

## 2. 요청

```http
GET /api/calendars/all
```

로그인 상태로 조회할 때만 다음 Header를 전달합니다.

```http
Authorization: Bearer {ACCESS_TOKEN}
```

## 3. 성공 응답

```json
[
  {
    "id": 1,
    "calendarType": "SCHOOL",
    "startDate": "2026-08-24",
    "endDate": "2026-08-24",
    "title": "2학기 개강"
  },
  {
    "id": 1,
    "calendarType": "STUDENT_COUNCIL",
    "startDate": "2026-09-10",
    "endDate": "2026-09-12",
    "title": "학생회 행사"
  },
  {
    "id": 1,
    "calendarType": "PERSONAL",
    "startDate": "2026-09-15",
    "endDate": "2026-09-15",
    "title": "개인 일정"
  }
]
```

## 4. 일정 유형

| 값 | 의미 | 공개 범위 |
|---|---|---|
| `SCHOOL` | 학교 공식 일정 | 모든 사용자 |
| `STUDENT_COUNCIL` | 학생회 일정 | 모든 사용자 |
| `PERSONAL` | 개인 일정 | 작성한 로그인 사용자만 |

각 일정 종류는 별도 테이블을 사용하므로 숫자 `id`가 서로 같을 수 있습니다.
프론트엔드의 고유 Key와 상세 처리에는 `calendarType + id` 조합을 사용해야 합니다.

## 5. 정렬

다음 기준으로 오름차순 정렬합니다.

1. `startDate`
2. `endDate`
3. `calendarType`: `SCHOOL` → `STUDENT_COUNCIL` → `PERSONAL`
4. `id`

## 6. 빈 결과

조회할 일정이 없으면 `404`가 아니라 `200 OK`와 빈 배열을 반환합니다.

```json
[]
```

## 7. 인증 오류

```json
{
  "status": 401,
  "message": "로그인이 필요합니다.",
  "path": "/api/calendars/all",
  "timestamp": "2026-08-10T10:00:00Z"
}
```

토큰을 아예 보내지 않는 것은 정상적인 비로그인 요청입니다. 명시적으로 전달한 토큰이
잘못되었거나 만료된 경우에만 `401 Unauthorized`가 발생합니다.

## 8. 관리자 전체 공개 일정 조회

기존 `GET /api/admin/calendars`는 학생회가 작성한 일정만 반환합니다. 다음 API는
일반 사용자에게 공통 공개되는 학교 공식 일정과 학생회 일정을 한 배열로 반환합니다.

```http
GET /api/admin/calendars/all
Authorization: Bearer {ADMIN_ACCESS_TOKEN}
```

- 응답 필드와 정렬 기준은 통합 일정 조회 응답과 같습니다.
- `calendarType`은 `SCHOOL` 또는 `STUDENT_COUNCIL`입니다.
- 특정 사용자에게만 보이는 `PERSONAL` 일정은 관리자에게도 반환하지 않습니다.
- 관리자 토큰이 없으면 `401 Unauthorized`, `USER` 권한이면 `403 Forbidden`입니다.

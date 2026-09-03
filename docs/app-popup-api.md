# 앱 시작 팝업 API 명세서

## 1. 기능 개요

관리자가 이미지와 글, 노출 기간, 순서 및 클릭 이동 대상을 설정한 팝업을 관리하고 모든
사용자가 로그인 여부와 관계없이 현재 노출 대상만 조회하는 기능입니다.

- 공개 조회는 JWT가 필요하지 않습니다.
- 관리 API는 `ADMIN` 권한이 필요합니다.
- 시간 기준은 `Asia/Seoul`이며 시각은 ISO `LocalDateTime` 형식입니다.
- 공개 응답은 `Cache-Control: no-store`를 반환합니다.
- 팝업이 없으면 `200 OK`와 빈 배열 `[]`을 반환합니다.
- “오늘 하루 보지 않기”는 앱이 `popupId + revision` 기준으로 로컬 저장합니다.
- 백엔드는 Expo Router 경로를 저장하지 않고 합의된 `navigation.type`과 `params`만 저장합니다.

## 2. API 목록

| Method | Endpoint | 인증 | 설명 |
|---|---|---|---|
| `GET` | `/api/popups/active` | 불필요 | 현재 노출 중인 팝업 조회 |
| `GET` | `/api/admin/popups` | ADMIN | 전체 팝업과 현재 상태 조회 |
| `GET` | `/api/admin/popups/{popupId}` | ADMIN | 팝업 상세 조회 |
| `POST` | `/api/admin/popups` | ADMIN | 팝업 등록 |
| `PUT` | `/api/admin/popups/{popupId}` | ADMIN | 팝업 전체 수정 |
| `PATCH` | `/api/admin/popups/{popupId}/enabled` | ADMIN | 활성 상태 변경 |
| `DELETE` | `/api/admin/popups/{popupId}` | ADMIN | 팝업 삭제 |

## 3. navigation 계약

이동이 없으면 `navigation`을 명시적으로 `null`로 전달합니다. 이동이 있으면 다음 구조를
사용합니다.

```json
{
  "schemaVersion": 1,
  "type": "NOTICE_DETAIL",
  "params": {
    "noticeId": 123,
    "noticeType": "ACADEMIC"
  }
}
```

| 필드 | 필수 | 설명 |
|---|---:|---|
| `navigation` | O | 이동 없음이면 `null` |
| `schemaVersion` | 객체일 때 O | 현재 `1`만 지원 |
| `type` | 객체일 때 O | 합의된 의미 기반 enum |
| `params` | 조건부 | 해당 type이 요구하는 값만 전달 |

### 지원하는 navigation.type

| type | 필수 params | 검증 |
|---|---|---|
| `HOME` | 없음 | `params` 금지 |
| `NOTICE_LIST` | 없음 | `params` 금지 |
| `NOTICE_DETAIL` | `noticeId`, `noticeType` | ID는 1 이상 정수, noticeType enum |
| `CLUB_LIST` | 없음 | `params` 금지 |
| `CLUB_DETAIL` | `clubId` | 1 이상의 정수 |
| `CAFETERIA` | 없음 | `params` 금지 |
| `CALENDAR` | 없음 | `params` 금지 |
| `TIMETABLE` | 없음 | `params` 금지 |
| `CAMPUS_MAP` | 없음 | `params` 금지 |
| `SYSTEM_NOTICE_LIST` | 없음 | `params` 금지 |
| `FESTIVAL` | 없음 | `params` 금지 |
| `EXTERNAL_URL` | `url` | 사용자 정보가 없는 HTTPS 절대 URL |

`NOTICE_DETAIL.noticeType`은 다음 값만 허용합니다.

```text
STUDENT_COUNCIL
ACADEMIC
GENERAL
SCHOLARSHIP
GRADUATE
```

앱 경로, custom scheme 및 합의되지 않은 추가 params는 저장할 수 없습니다.

## 4. 팝업 노출 규칙

```text
enabled = true
AND (startsAt IS NULL OR startsAt <= 현재 한국 시각)
AND (endsAt IS NULL OR 현재 한국 시각 < endsAt)
```

종료 시각은 미포함 경계입니다. 공개 응답은 `displayOrder ASC`, 같은 순서에서는 `id ASC`로
정렬됩니다.

관리자 응답의 `status`는 다음과 같습니다.

| 값 | 의미 |
|---|---|
| `ACTIVE` | 현재 공개 노출 조건을 만족함 |
| `SCHEDULED` | 활성화됐지만 시작 시각 전임 |
| `EXPIRED` | 활성화됐지만 종료 시각에 도달함 |
| `INACTIVE` | 관리자가 비활성화함 |

## 5. 공개 팝업 조회

```http
GET /api/popups/active
```

```json
[
  {
    "id": 12,
    "title": "축제 기간 안내",
    "content": "자세한 내용을 공지사항에서 확인해 주세요.",
    "imageUrl": "https://api.example.com/uploads/app-popups/uuid.png",
    "navigation": {
      "schemaVersion": 1,
      "type": "NOTICE_DETAIL",
      "params": {
        "noticeId": 123,
        "noticeType": "ACADEMIC"
      }
    },
    "startsAt": "2026-09-03T00:00:00",
    "endsAt": "2026-09-07T23:59:59",
    "displayOrder": 1,
    "revision": 4
  }
]
```

로그인 사용자도 같은 API를 사용합니다. 잘못된 Bearer 토큰이 우연히 전달돼도 공개 조회
결과에는 영향을 주지 않습니다. 이동이 없는 팝업은 `"navigation": null`로 반환됩니다.

## 6. 관리자 전체 및 상세 조회

```http
GET /api/admin/popups
Authorization: Bearer {adminAccessToken}
```

전체 목록은 `createdAt DESC`, 같은 생성 시각에서는 `id DESC`입니다.

```http
GET /api/admin/popups/12
Authorization: Bearer {adminAccessToken}
```

```json
{
  "id": 12,
  "title": "축제 기간 안내",
  "content": "자세한 내용을 공지사항에서 확인해 주세요.",
  "imageUrl": "https://api.example.com/uploads/app-popups/uuid.png",
  "navigation": {
    "schemaVersion": 1,
    "type": "NOTICE_DETAIL",
    "params": {
      "noticeId": 123,
      "noticeType": "ACADEMIC"
    }
  },
  "enabled": true,
  "status": "ACTIVE",
  "startsAt": "2026-09-03T00:00:00",
  "endsAt": "2026-09-07T23:59:59",
  "displayOrder": 1,
  "revision": 4,
  "createdAt": "2026-09-02T14:30:00",
  "updatedAt": "2026-09-03T09:10:00"
}
```

## 7. 팝업 등록

```http
POST /api/admin/popups
Authorization: Bearer {adminAccessToken}
Content-Type: multipart/form-data
```

| 파트 | Content-Type | 필수 | 설명 |
|---|---|---|---|
| `request` | `application/json` | O | 팝업 정보 |
| `image` | `image/jpeg`, `image/png`, `image/gif` | X | 선택 이미지 한 장 |

### 내부 공지 상세 이동

```json
{
  "title": "축제 기간 안내",
  "content": "자세한 내용을 공지사항에서 확인해 주세요.",
  "navigation": {
    "schemaVersion": 1,
    "type": "NOTICE_DETAIL",
    "params": {
      "noticeId": 123,
      "noticeType": "ACADEMIC"
    }
  },
  "enabled": true,
  "startsAt": "2026-09-03T00:00:00",
  "endsAt": "2026-09-07T23:59:59",
  "displayOrder": 1
}
```

### 외부 웹페이지 이동

```json
{
  "title": "학교 홈페이지 안내",
  "content": "이미지를 누르면 학교 홈페이지로 이동합니다.",
  "navigation": {
    "schemaVersion": 1,
    "type": "EXTERNAL_URL",
    "params": {
      "url": "https://www.hanseo.ac.kr/notice/123"
    }
  },
  "enabled": true,
  "startsAt": null,
  "endsAt": null,
  "displayOrder": 1
}
```

### 이동 없음

```json
{
  "title": "단순 안내",
  "content": "클릭 이동이 없는 팝업입니다.",
  "navigation": null,
  "enabled": true,
  "startsAt": null,
  "endsAt": null,
  "displayOrder": 1
}
```

성공 시 `201 Created`, `Location: /api/admin/popups/{popupId}`와 관리자 응답을 반환합니다.
최초 `revision`은 `1`입니다.

## 8. 팝업 전체 수정

```http
PUT /api/admin/popups/12
Authorization: Bearer {adminAccessToken}
Content-Type: multipart/form-data
```

기존 PUT 정책대로 모든 필드를 전달하며 `navigation`도 반드시 전달합니다.

```json
{
  "title": "축제 운영시간 변경 안내",
  "content": "축제 운영시간이 변경되었습니다.",
  "navigation": {
    "schemaVersion": 1,
    "type": "NOTICE_DETAIL",
    "params": {
      "noticeId": 456,
      "noticeType": "GENERAL"
    }
  },
  "enabled": true,
  "startsAt": "2026-09-03T00:00:00",
  "endsAt": "2026-09-08T00:00:00",
  "displayOrder": 1,
  "imageAction": "KEEP"
}
```

| imageAction | image 파트 | 처리 |
|---|---|---|
| `KEEP` | 보내지 않음 | 기존 이미지 유지 |
| `REPLACE` | 필수 | 새 이미지 저장 후 DB 커밋이 성공하면 기존 이미지 삭제 |
| `REMOVE` | 보내지 않음 | DB 커밋이 성공하면 기존 이미지 삭제 |

PUT 성공 시 `revision`이 1 증가합니다. 따라서 navigation이 변경된 팝업은 같은 날 숨김
처리된 상태여도 앱에서 다시 노출할 수 있습니다.

## 9. 활성 상태 변경

```http
PATCH /api/admin/popups/12/enabled
Authorization: Bearer {adminAccessToken}
Content-Type: application/json
```

```json
{
  "enabled": false
}
```

성공 응답에도 동일한 `navigation` 구조가 포함됩니다. 값이 실제로 달라지면 `revision`이 1
증가하고, 이미 같은 값이면 변경하지 않습니다.

## 10. 팝업 삭제

```http
DELETE /api/admin/popups/12
Authorization: Bearer {adminAccessToken}
```

성공 시 `204 No Content`입니다. DB 커밋 후 서버가 관리하는 이미지도 삭제합니다.

## 11. 오늘 하루 보지 않기 및 클릭 처리

앱은 `app-popup:{popupId}:{revision}` 키로 한국 시간 기준 다음 날 00시까지 로컬 숨김 상태를
저장합니다. 백엔드는 사용자별 숨김 API를 제공하지 않습니다.

팝업 이미지 클릭 시 앱은 다음 순서로 처리합니다.

1. 현재 팝업을 먼저 닫음
2. `navigation`이 `null`이면 이동하지 않음
3. 지원하는 type과 유효한 params인지 앱에서도 확인
4. 중복 탭을 방지하고 한 번만 이동
5. 클릭 이동은 오늘 하루 보지 않기로 기록하지 않음

미지원 type이나 잘못된 params를 받으면 팝업은 표시하고 이동만 비활성화합니다.

## 12. navigation 검증과 오류

| 조건 | 결과 |
|---|---|
| `navigation` 필드 누락 | `400 Bad Request` |
| `navigation = null` | 이동 없음으로 저장 |
| 알 수 없는 schemaVersion 또는 type | `400 Bad Request` |
| 정적 화면 type에 params 전달 | `400 Bad Request` |
| 필수 params 누락·초과·혼합 | `400 Bad Request` |
| noticeId 또는 clubId가 1 미만·실수·문자열 | `400 Bad Request` |
| 알 수 없는 noticeType | `400 Bad Request` |
| EXTERNAL_URL이 HTTPS 절대 URL이 아님 | `400 Bad Request` |
| URL에 username/password 포함 | `400 Bad Request` |
| custom scheme 또는 앱 실제 route 전달 | `400 Bad Request` |

오류 예시:

```json
{
  "status": 400,
  "message": "navigation.params.noticeId: 1 이상의 정수여야 합니다.",
  "path": "/api/admin/popups",
  "timestamp": "2026-09-03T01:30:00Z"
}
```

기존 제목·본문·기간·순서·이미지·인증 검증 정책은 그대로 유지합니다. 본문은 HTML 계약이
없는 일반 텍스트이므로 앱에서도 HTML로 직접 삽입하지 않습니다.

## 13. 이미지 저장

```text
실제 저장: ${UPLOAD_DIRECTORY}/app-popups/{UUID}.{확장자}
공개 URL: ${UPLOAD_PUBLIC_BASE_URL}/uploads/app-popups/{UUID}.{확장자}
```

운영의 `UPLOAD_DIRECTORY`는 영속 디스크나 Docker 볼륨이어야 합니다.

## 14. 운영 DB 적용

아직 `app_popups` 테이블이 없다면 코드 배포 전에 다음 파일을 실행합니다.

```text
docs/app-popup-migration-mysql.sql
```

기존 `link_url` 기반 `app_popups` 테이블을 이미 만들었다면 다음 증분 파일을 실행합니다.

```text
docs/app-popup-navigation-migration-mysql.sql
```

증분 파일은 기존 HTTPS `link_url`을 `EXTERNAL_URL` navigation으로 변환하고 `revision`을 1
증가시킵니다. 롤백 확인을 위해 `link_url` 컬럼은 이번 배포에서 물리적으로 삭제하지 않지만,
신규 애플리케이션은 해당 컬럼을 읽거나 쓰거나 응답하지 않습니다.

`docs/database-schema-mysql.sql`은 완전히 비어 있는 신규 DB 전용입니다.

# 앱 시작 팝업 API 명세서

## 1. 기능 개요

관리자가 이미지와 글, 노출 기간 및 순서를 설정한 앱 시작 팝업을 관리하고 모든 사용자가
로그인 여부와 관계없이 현재 노출 대상만 조회하는 기능입니다.

- 공개 조회는 JWT가 필요하지 않습니다.
- 관리 API는 `ADMIN` 권한이 필요합니다.
- 제목과 본문은 필수이고 이미지는 선택입니다.
- 시간 기준은 `Asia/Seoul`이며 API 시각은 ISO `LocalDateTime` 형식입니다.
- 공개 응답은 캐시하지 않도록 `Cache-Control: no-store`를 반환합니다.
- 팝업이 없으면 `404`가 아니라 `200 OK`와 빈 배열 `[]`을 반환합니다.
- “오늘 하루 보지 않기” 기록은 비로그인 사용자도 지원해야 하므로 앱 로컬 저장소가 관리합니다.

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

## 3. 노출 규칙

다음 조건을 모두 만족하는 팝업만 공개 API에 포함됩니다.

```text
enabled = true
AND (startsAt IS NULL OR startsAt <= 현재 한국 시각)
AND (endsAt IS NULL OR 현재 한국 시각 < endsAt)
```

종료 시각은 미포함 경계입니다. 예를 들어 `endsAt`이 `2026-09-04T00:00:00`이면 해당
시각부터 응답에서 제외합니다. 공개 응답은 `displayOrder ASC`, 같은 순서에서는 `id ASC`입니다.

관리자 응답의 `status`는 다음과 같습니다.

| 값 | 의미 |
|---|---|
| `ACTIVE` | 현재 공개 노출 조건을 만족함 |
| `SCHEDULED` | 활성화되어 있지만 시작 시각 전임 |
| `EXPIRED` | 활성화되어 있지만 종료 시각에 도달함 |
| `INACTIVE` | 관리자가 비활성화함 |

비활성화가 다른 시간 조건보다 우선하므로 미래 팝업도 `enabled=false`이면 `INACTIVE`입니다.

## 4. 공개 팝업 조회

```http
GET /api/popups/active
```

성공 응답:

```json
[
  {
    "id": 12,
    "title": "축제 기간 안내",
    "content": "축제 기간 동안 일부 강의실 이용이 제한됩니다.",
    "imageUrl": "https://api.example.com/uploads/app-popups/uuid.png",
    "linkUrl": "https://www.hanseo.ac.kr/notice/123",
    "startsAt": "2026-09-03T00:00:00",
    "endsAt": "2026-09-07T23:59:59",
    "displayOrder": 1,
    "revision": 3
  }
]
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Number | 팝업 ID |
| `title` | String | 제목, 최대 200자 |
| `content` | String | 본문, 최대 100,000자 |
| `imageUrl` | String 또는 null | 선택 이미지 공개 URL |
| `linkUrl` | String 또는 null | 팝업 클릭 시 이동할 HTTP/HTTPS URL |
| `startsAt` | String 또는 null | 노출 시작 한국 시각, null이면 즉시 |
| `endsAt` | String 또는 null | 노출 종료 한국 시각, null이면 무기한 |
| `displayOrder` | Number | 작은 값부터 먼저 노출 |
| `revision` | Number | 오늘 하루 숨김 기록에 사용하는 콘텐츠 버전 |

로그인 사용자도 동일한 API와 응답을 사용합니다. 만료되거나 잘못된 Bearer 토큰이 우연히
전달되더라도 이 공개 API의 조회 결과에는 영향을 주지 않습니다.

## 5. 관리자 전체 및 상세 조회

```http
GET /api/admin/popups
Authorization: Bearer {adminAccessToken}
```

전체 목록은 노출 여부와 관계없이 `createdAt DESC`, 같은 생성 시각이면 `id DESC`입니다.

```http
GET /api/admin/popups/12
Authorization: Bearer {adminAccessToken}
```

관리자 응답:

```json
{
  "id": 12,
  "title": "축제 기간 안내",
  "content": "축제 기간 동안 일부 강의실 이용이 제한됩니다.",
  "imageUrl": "https://api.example.com/uploads/app-popups/uuid.png",
  "linkUrl": "https://www.hanseo.ac.kr/notice/123",
  "enabled": true,
  "status": "ACTIVE",
  "startsAt": "2026-09-03T00:00:00",
  "endsAt": "2026-09-07T23:59:59",
  "displayOrder": 1,
  "revision": 3,
  "createdAt": "2026-09-02T14:30:00",
  "updatedAt": "2026-09-03T09:10:00"
}
```

## 6. 팝업 등록

```http
POST /api/admin/popups
Authorization: Bearer {adminAccessToken}
Content-Type: multipart/form-data
```

파트 구성:

| 파트 | Content-Type | 필수 | 설명 |
|---|---|---|---|
| `request` | `application/json` | O | 팝업 정보 |
| `image` | `image/jpeg`, `image/png`, `image/gif` | X | 선택 이미지 한 장 |

`request` 예시:

```json
{
  "title": "축제 기간 안내",
  "content": "축제 기간 동안 일부 강의실 이용이 제한됩니다.",
  "linkUrl": "https://www.hanseo.ac.kr/notice/123",
  "enabled": true,
  "startsAt": "2026-09-03T00:00:00",
  "endsAt": "2026-09-07T23:59:59",
  "displayOrder": 1
}
```

`startsAt`을 생략하거나 `null`로 보내면 즉시 노출 조건을 충족하고, `endsAt`이 `null`이면
종료 시각 제한이 없습니다. 성공 시 `201 Created`, `Location: /api/admin/popups/{popupId}`와
관리자 응답을 반환합니다. 최초 `revision`은 `1`입니다.

## 7. 팝업 전체 수정

```http
PUT /api/admin/popups/12
Authorization: Bearer {adminAccessToken}
Content-Type: multipart/form-data
```

`request` 파트에는 등록 필드 전체와 `imageAction`을 보냅니다.

```json
{
  "title": "축제 운영시간 변경 안내",
  "content": "운영시간이 변경되었습니다.",
  "linkUrl": null,
  "enabled": true,
  "startsAt": "2026-09-03T00:00:00",
  "endsAt": "2026-09-08T00:00:00",
  "displayOrder": 1,
  "imageAction": "KEEP"
}
```

| `imageAction` | `image` 파트 | 처리 |
|---|---|---|
| `KEEP` | 보내지 않음 | 기존 이미지 유지 |
| `REPLACE` | 필수 | 새 이미지 저장 후 DB 커밋이 성공하면 기존 이미지 삭제 |
| `REMOVE` | 보내지 않음 | DB 커밋이 성공하면 기존 이미지 삭제 |

`KEEP` 또는 `REMOVE`이면서 이미지가 전달되거나, `REPLACE`인데 이미지가 없으면
`400 Bad Request`입니다. 수정 성공 시 `revision`이 1 증가하므로 같은 날 내용을 수정한
팝업을 앱에서 다시 노출할 수 있습니다.

## 8. 활성 상태 변경

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

상태 값이 실제로 달라지면 `revision`이 1 증가합니다. 이미 같은 값이면 데이터와
`revision`을 변경하지 않습니다.

## 9. 팝업 삭제

```http
DELETE /api/admin/popups/12
Authorization: Bearer {adminAccessToken}
```

성공 시 `204 No Content`입니다. DB 커밋 후 서버가 관리하는 이미지도 삭제합니다.

## 10. 오늘 하루 보지 않기

비로그인 사용자를 서버가 안정적으로 식별할 수 없으므로 숨김 API와 사용자별 숨김 테이블은
제공하지 않습니다. 앱은 팝업별로 다음 값을 로컬 저장소에 보관합니다.

```json
{
  "popupId": 12,
  "revision": 3,
  "hiddenUntil": "2026-09-04T00:00:00+09:00"
}
```

권장 로컬 키는 `app-popup:{popupId}:{revision}`입니다. 체크 후 닫을 때 한국 시간 기준 다음
날 00시를 `hiddenUntil`로 저장하고, 앱 시작 시 현재 시간이 그보다 작으면 해당 팝업만
건너뜁니다. 단순 닫기는 현재 화면에서만 닫고 로컬 숨김 값은 저장하지 않습니다.

관리자가 팝업 내용을 수정하거나 껐다 다시 켜서 `revision`이 증가하면 같은 날이라도 새
로컬 키가 되므로 다시 노출됩니다.

## 11. 입력 검증과 오류

| 조건 | 응답 |
|---|---|
| 제목 누락·공백 또는 200자 초과 | `400 Bad Request` |
| 본문 누락·공백 또는 100,000자 초과 | `400 Bad Request` |
| `enabled`, `displayOrder` 누락 | `400 Bad Request` |
| `displayOrder`가 0 미만 또는 9999 초과 | `400 Bad Request` |
| `endsAt <= startsAt` | `400 Bad Request` |
| `linkUrl`이 HTTP/HTTPS URL이 아님 | `400 Bad Request` |
| 이미지가 JPG·PNG·GIF가 아니거나 5MB 기본 제한 초과 | `400 Bad Request` |
| 팝업 ID가 0 이하 또는 숫자가 아님 | `400 Bad Request` |
| 수정·삭제 대상이 없음 | `404 Not Found` |
| 관리자 토큰 없음·만료·위조 | `401 Unauthorized` |
| 일반 사용자 토큰으로 관리 API 호출 | `403 Forbidden` |

본문은 HTML 실행 계약이 없는 일반 텍스트입니다. 프론트에서도 HTML로 직접 삽입하지 말고
텍스트와 줄바꿈으로 렌더링합니다.

## 12. 이미지 저장과 운영 DB 배포

팝업 이미지는 기존 공용 이미지 설정을 사용합니다.

```text
실제 저장: ${UPLOAD_DIRECTORY}/app-popups/{UUID}.{확장자}
공개 URL: ${UPLOAD_PUBLIC_BASE_URL}/uploads/app-popups/{UUID}.{확장자}
```

운영의 `UPLOAD_DIRECTORY`는 영속 디스크나 Docker 볼륨에 연결되어야 하며, 외부 웹 서버가
있다면 `/uploads/` 경로가 동일 디렉터리를 제공해야 합니다.

운영 프로필은 `spring.jpa.hibernate.ddl-auto=validate`이므로 코드 배포 전에 다음 순서를
지킵니다.

1. 운영 DB 백업 및 `app_popups` 테이블 존재 여부 확인
2. 테이블이 없을 때만 `docs/app-popup-migration-mysql.sql`의 `CREATE TABLE` 실행
3. 스크립트 하단의 컬럼·인덱스 확인 쿼리 실행
4. 애플리케이션 배포 후 `ddl-auto=validate` 기동 성공 확인
5. 관리자 등록과 공개 조회를 각각 스모크 테스트

`docs/database-schema-mysql.sql`은 완전히 비어 있는 신규 DB 전용이므로 기존 운영 DB에는
실행하지 않습니다.

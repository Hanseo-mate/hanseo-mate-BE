# 알림함(Notification Inbox) API 명세서

> **Base URL** : `/api/v1/notifications`  
> **인증** : 불필요 (Public) — 식별자는 `installationId` 쿼리 파라미터로 전달  
> **콘텐츠 타입** : `application/json`

---

## 설계 원칙

| 항목 | 내용 |
|------|------|
| 알림 원본 | `notifications` 테이블에 1건만 저장 (전체 발송이므로 중복 없음) |
| 읽음 기록 | `notification_reads` 테이블에 installationId + notificationId 쌍으로 기록 |
| 유효 범위 | 전체 알림 중 **최신 20건**만 유효한 데이터로 취급 (20건 초과분은 API에서 반환하지 않음) |
| 사용자 식별 | 비로그인 사용자를 포함한 모든 기기를 `installationId`로 구분 |

---

## DB 스키마

### `notifications`

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | 알림 고유 ID |
| `title` | VARCHAR | 알림 제목 |
| `body` | TEXT | 알림 본문 |
| `payload_data` | TEXT | 딥링크·라우팅용 JSON 문자열 |
| `created_at` | DATETIME | 생성일시 |

**`payload_data` 예시**
```json
{
  "version": 1,
  "type": "notice",
  "route": "/notices",
  "entityId": "42"
}
```

### `notification_reads`

| 컬럼명 | 타입 | 설명 |
|--------|------|------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | 읽음 기록 ID |
| `installation_id` | VARCHAR | 기기 식별자 |
| `notification_id` | BIGINT (FK) | 읽은 알림 ID |
| `created_at` | DATETIME | 읽음 처리 시각 |

> `(installation_id, notification_id)` 에 **UNIQUE 제약** 설정 — 중복 읽음 기록 방지

---

## API 목록

| # | Method | Path | 설명 |
|---|--------|------|------|
| 1 | `GET` | `/api/v1/notifications` | 알림 목록 조회 |
| 2 | `GET` | `/api/v1/notifications/unread-count` | 미읽음 배지 카운트 조회 |
| 3 | `PATCH` | `/api/v1/notifications/{notificationId}/read` | 단건 읽음 처리 |
| 4 | `PATCH` | `/api/v1/notifications/read-all` | 전체 읽음 처리 |

---

## 1. 알림 목록 조회

### Request

```
GET /api/v1/notifications?installationId={installationId}&page={page}&size={size}
```

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|----------|------|------|--------|------|
| `installationId` | String | ✅ | - | 기기 고유 식별자 |
| `page` | int | ❌ | `0` | 페이지 번호 (0부터 시작) |
| `size` | int | ❌ | `10` | 페이지당 항목 수 |

> ⚠️ **범위 제한**: 전체 알림 중 최신 20건만 유효합니다.  
> `size=10` 기준 `page=0` → 1~10번째, `page=1` → 11~20번째, `page=2` 이상 → **빈 배열 반환**

### Response `200 OK`

```json
[
  {
    "id": 15,
    "title": "[학사공지] 2026학년도 수강신청 안내",
    "body": "해당 공지가 조회수 100회를 돌파하며 화제가 되고 있어요!",
    "payloadData": "{\"version\":1,\"type\":\"notice\",\"route\":\"/notices\",\"entityId\":\"42\"}",
    "isRead": false,
    "createdAt": "2026-08-19T14:30:00"
  },
  {
    "id": 14,
    "title": "[학생회 공지] 2학기 총회 안내",
    "body": "총학생회에서 새로운 공지를 등록했습니다.",
    "payloadData": "{\"version\":1,\"type\":\"notice\",\"route\":\"/notices\",\"entityId\":\"88\"}",
    "isRead": true,
    "createdAt": "2026-08-18T09:00:00"
  }
]
```

#### Response 필드

| 필드 | 타입 | 설명 |
|------|------|------|
| `id` | Long | 알림 고유 ID |
| `title` | String | 알림 제목 |
| `body` | String | 알림 본문 |
| `payloadData` | String | 딥링크용 JSON 문자열 (앱에서 파싱하여 라우팅에 사용) |
| `isRead` | Boolean | 해당 `installationId`의 읽음 여부 (`true` = 읽음, 회색 처리) |
| `createdAt` | String (ISO 8601) | 알림 생성 시각 |

---

## 2. 미읽음 배지 카운트 조회

앱 아이콘 또는 알림 탭의 배지 숫자 표시에 사용합니다.

### Request

```
GET /api/v1/notifications/unread-count?installationId={installationId}
```

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `installationId` | String | ✅ | 기기 고유 식별자 |

### Response `200 OK`

```json
{
  "unreadCount": 3
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `unreadCount` | Long | 최신 20건 중 읽지 않은 알림 수 |

> 최신 20건 이외의 알림은 카운트에 포함되지 않습니다.

---

## 3. 단건 읽음 처리

### Request

```
PATCH /api/v1/notifications/{notificationId}/read?installationId={installationId}
```

| 위치 | 파라미터 | 타입 | 필수 | 설명 |
|------|----------|------|------|------|
| Path | `notificationId` | Long | ✅ | 읽음 처리할 알림 ID |
| Query | `installationId` | String | ✅ | 기기 고유 식별자 |

### Response

| 상태 코드 | 설명 |
|-----------|------|
| `204 No Content` | 읽음 처리 성공 (이미 읽은 경우도 동일하게 반환) |
| `404 Not Found` | `notificationId`에 해당하는 알림이 없음 |

#### 404 Response Body

```json
{
  "message": "알림을 찾을 수 없습니다. id=99"
}
```

> 이미 읽은 알림에 재요청해도 중복 저장 없이 `204`를 반환합니다 (멱등성 보장).

---

## 4. 전체 읽음 처리

최신 20건 중 아직 읽지 않은 모든 알림을 한 번에 읽음 처리합니다.

### Request

```
PATCH /api/v1/notifications/read-all?installationId={installationId}
```

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `installationId` | String | ✅ | 기기 고유 식별자 |

### Response

| 상태 코드 | 설명 |
|-----------|------|
| `204 No Content` | 처리 성공 (이미 모두 읽은 경우도 동일하게 반환) |

> 내부적으로 미읽음 항목만 필터링 후 **벌크 INSERT** 처리합니다.

---

## 알림 트리거 조건

알림은 다음 비즈니스 이벤트 발생 시 자동으로 `notifications` 테이블에 저장됩니다.  
(서버 내부 동작이며 프론트엔드 호출 없음)

| 트리거 | `title` 형식 | `body` |
|--------|-------------|--------|
| 일반 공지 조회수 **정확히 100회** 달성 | `[{공지유형}] {공지제목}` | `해당 공지가 조회수 100회를 돌파하며 화제가 되고 있어요!` |
| 학생회 공지 **신규 작성** | `[학생회 공지] {공지제목}` | `총학생회에서 새로운 공지를 등록했습니다.` |

> 알림 저장(`notifications`)과 Expo 푸시 발송 큐(`notification_outbox`) 등록이 **동일 트랜잭션**에서 원자적으로 처리됩니다.

---

## payloadData 라우팅 규격

```json
{
  "version": 1,
  "type": "notice",
  "route": "/notices",
  "entityId": "42"
}
```

| 필드 | 값 | 설명 |
|------|----|------|
| `version` | `1` | 페이로드 스키마 버전 |
| `type` | `"notice"` | 알림 유형 |
| `route` | `"/notices"` | 앱 내 이동 경로 |
| `entityId` | 공지 ID (String) | 상세 화면 진입에 사용할 엔티티 ID |

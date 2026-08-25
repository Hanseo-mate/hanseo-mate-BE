# 한서메이트 백엔드 시스템 명세서 — 식단 크롤링 & 푸시 알림 모듈

> **대상 독자**: 프론트엔드 개발자, 신규 백엔드 개발자, 인수인계자
> **최종 수정일**: 2026-08-12
> **프레임워크**: Spring Boot 4.x / Java 17

---

## 목차

1. [시스템 아키텍처 요약](#1-시스템-아키텍처-요약)
2. [도메인 A — 식단(Cafeteria) 모듈](#2-도메인-a--식단cafeteria-모듈)
   - [2.1 아키텍처 흐름](#21-아키텍처-흐름)
   - [2.2 데이터베이스 스키마 및 Entity 구조](#22-데이터베이스-스키마-및-entity-구조)
   - [2.3 REST API 명세](#23-rest-api-명세)
   - [2.4 스케줄러 및 크롤러 클라이언트 동작 정책](#24-스케줄러-및-크롤러-클라이언트-동작-정책)
3. [도메인 B — 푸시 알림(Push Notification) 모듈](#3-도메인-b--푸시-알림push-notification-모듈)
   - [3.1 아키텍처 흐름](#31-아키텍처-흐름)
   - [3.2 데이터베이스 스키마 및 Entity 구조](#32-데이터베이스-스키마-및-entity-구조)
   - [3.3 REST API 명세](#33-rest-api-명세)
   - [3.4 Worker 동작 정책](#34-worker-동작-정책)
   - [3.5 Expo Push API 통신 정책](#35-expo-push-api-통신-정책)
4. [환경 변수 목록](#4-환경-변수-목록)

---

## 1. 시스템 아키텍처 요약

한서메이트 백엔드는 두 개의 비동기 데이터 공급 파이프라인을 운용한다.

| 모듈 | 데이터 공급 방식 | 외부 의존성 |
|------|----------------|------------|
| 식단 | 주 1회 자동 크롤링 → DB 적재 → API 제공 | Python FastAPI 크롤러 서버 |
| 푸시 알림 | Outbox 패턴 → 발송 Worker → Expo Push Service | Expo Push API (exp.host) |

---

## 2. 도메인 A — 식단(Cafeteria) 모듈

### 2.1 아키텍처 흐름

```
[Spring Boot 스케줄러]
 매주 월요일 09:00 (Asia/Seoul) 자동 실행
        │
        │ POST /cafeteria-crawl/run (JSON body)
        ▼
[Python FastAPI 크롤러]  ← http://34.64.250.12:8000
 한서대학교 급식 페이지 HTML 파싱
        │
        │ MySQL 직접 적재 (daily_menus / meal_sections / dishes)
        ▼
[MySQL DB]
        │
        │ QueryDSL 동적 쿼리
        ▼
[Spring Boot REST API]  →  클라이언트(앱/웹)
```

> **중요**: 크롤러 서버는 `mode: "background"` 파라미터를 받아 비동기로 크롤링을 수행하며 즉시 응답을 반환합니다. Spring Boot는 크롤링 완료를 기다리지 않습니다.

---

### 2.2 데이터베이스 스키마 및 Entity 구조

#### Enum 정의

| Enum | 값 | 설명 |
|------|----|------|
| `RestaurantType` | `MAIN_STUDENT`, `MAIN_STAFF`, `TAEAN_STUDENT`, `TAEAN_STAFF` | 식당 구분 |
| `MealTime` | `LUNCH`, `DINNER` | 식사 시간대 |
| `MenuCategory` | `KOREAN`, `SPECIAL`, `NORMAL` | 메뉴 코너 구분 |

#### Entity 계층 구조

```
DailyMenu (1)
│  ├─ id          BIGINT PK AUTO_INCREMENT
│  ├─ restaurant_type  VARCHAR(20) NOT NULL  [MAIN_STUDENT|MAIN_STAFF|TAEAN_STUDENT|TAEAN_STAFF]
│  ├─ menu_date   DATE NOT NULL
│  └─ UNIQUE (restaurant_type, menu_date)
│
└── MealSection (N)
    │  ├─ id              BIGINT PK
    │  ├─ daily_menu_id   BIGINT FK → daily_menus.id
    │  ├─ meal_time        VARCHAR(10) NOT NULL  [LUNCH|DINNER]
    │  └─ menu_category    VARCHAR(10) NOT NULL  [KOREAN|SPECIAL|NORMAL]
    │
    └── Dish (N)
           ├─ id              BIGINT PK
           ├─ meal_section_id BIGINT FK → meal_sections.id
           ├─ name            VARCHAR(200) NOT NULL
           └─ is_main_dish    BOOLEAN NOT NULL
```

#### 테이블 관계

```sql
daily_menus   1 ─── N   meal_sections   1 ─── N   dishes
```

- `DailyMenu` → `MealSection`: `CascadeType.ALL`, `orphanRemoval = true`
- `MealSection` → `Dish`: `CascadeType.ALL`, `orphanRemoval = true`
- N+1 문제 방지: `@BatchSize(size=30)` (MealSection), `@BatchSize(size=50)` (Dish)

#### QueryDSL 조회 전략

단일 페치 조인 시 카르테시안 곱을 방지하기 위해 **2단계 조회**를 사용한다.

1. **1단계**: 조건에 맞는 `DailyMenu` ID 목록만 조회 (필터·정렬 적용)
2. **2단계**: 해당 ID들을 `IN` 절로 받아 `DailyMenu → MealSection → Dish` 전체를 Fetch Join

---

### 2.3 REST API 명세

#### `GET /api/cafeteria/menus` — 식단 조회

| 항목 | 내용 |
|------|------|
| **Method** | `GET` |
| **URL** | `/api/cafeteria/menus` |
| **인증** | 불필요 (Public) |

##### Query Parameters

| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| `restaurantType` | `String` (Enum) | ✅ | `MAIN_STUDENT` \| `MAIN_STAFF` \| `TAEAN_STUDENT` \| `TAEAN_STAFF` |
| `menuDate` | `String` (ISO 8601) | ❌ | `yyyy-MM-dd` 형식. **없으면 오늘 기준 이번 주(월~일) 전체를 반환** |
| `menuCategory` | `String` (Enum) | ❌ | `KOREAN` \| `SPECIAL` \| `NORMAL`. **없으면 모든 코너 반환** |

##### 요청 예시

```
# 이번 주 학생 식당 전체 조회
GET /api/cafeteria/menus?restaurantType=MAIN_STUDENT

# 특정 날짜 + 코너 필터
GET /api/cafeteria/menus?restaurantType=MAIN_STUDENT&menuDate=2026-08-11&menuCategory=KOREAN
```

##### 응답 — `200 OK`

```json
[
  {
    "id": 1,
    "restaurantType": "MAIN_STUDENT",
    "menuDate": "2026-08-11",
    "mealSections": [
      {
        "id": 10,
        "mealTime": "LUNCH",
        "menuCategory": "KOREAN",
        "dishes": [
          { "id": 100, "name": "잡곡밥",   "isMainDish": false },
          { "id": 101, "name": "된장찌개", "isMainDish": true  },
          { "id": 102, "name": "제육볶음", "isMainDish": true  }
        ]
      },
      {
        "id": 11,
        "mealTime": "DINNER",
        "menuCategory": "SPECIAL",
        "dishes": [
          { "id": 110, "name": "스파게티", "isMainDish": true }
        ]
      }
    ]
  }
]
```

##### 응답 — `404 Not Found`

해당 조건의 식단 데이터가 DB에 없을 때 반환됩니다.

```json
{
  "message": "식단 데이터를 찾을 수 없습니다. [restaurantType=MAIN_STUDENT, menuDate=이번 주 전체]"
}
```

---

### 2.4 스케줄러 및 크롤러 클라이언트 동작 정책

#### 자동 크롤링 스케줄

| 항목 | 내용 |
|------|------|
| **Cron 표현식** | `0 0 9 * * MON` |
| **실행 시각** | 매주 월요일 오전 9시 (Asia/Seoul) |
| **대상** | 4개 식당 (MAIN_STUDENT, MAIN_STAFF, TAEAN_STUDENT, TAEAN_STAFF) 순차 트리거 |

#### 크롤러 서버 연동 API

Spring Boot → Python FastAPI 크롤러 서버 (`http://34.64.250.12:8000`)

| 목적 | Method | Endpoint |
|------|--------|----------|
| 크롤링 트리거 | `POST` | `/cafeteria-crawl/run` |
| 헬스 체크 | `GET` | `/health` |
| 마지막 크롤링 상태 | `GET` | `/cafeteria-crawl/status` |

**크롤링 트리거 요청 Body**

```json
{
  "url": "https://www.hanseo.ac.kr/food/main-student.do",
  "restaurant_type": "MAIN_STUDENT",
  "mode": "background"
}
```

**크롤링 상태 응답 Body**

```json
{
  "status": "done",
  "last_run_at": "2026-08-11T09:00:12",
  "message": "크롤링 성공"
}
```

#### 오류 처리 정책

| 오류 | 처리 방식 |
|------|----------|
| 크롤러 서버 연결 불가 (`ResourceAccessException`) | ERROR 로그 기록 후 조용히 실패. 다음 주 스케줄에서 재시도 |
| HTTP 오류 (`RestClientException`) | ERROR 로그 기록 후 조용히 실패 |

> **주의**: 크롤러 서버가 다운되어도 Spring Boot 스케줄러는 재시도 없이 다음 실행 주기까지 대기합니다. 수동 트리거가 필요할 경우 `CafeteriaCrawlerClient.triggerCrawl()`을 관리자 API에서 호출하도록 구현할 수 있습니다.

---

## 3. 도메인 B — 푸시 알림(Push Notification) 모듈

### 3.1 아키텍처 흐름

본 모듈은 **Outbox 패턴**을 사용하여 알림 발송 요청을 안전하게 처리한다. API 요청 스레드에서 직접 Expo API를 호출하지 않으므로 발송 실패가 비즈니스 트랜잭션에 영향을 미치지 않는다.

```
[모바일 앱]
    │ PUT /api/v1/push-tokens
    │ (Expo Push Token 전달)
    ▼
[push_devices 테이블]  ← installation_id 기준 Upsert
    
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

[관리자 / 크롤러 / 비즈니스 로직]
    │ NotificationService.enqueueXxxNotification()
    │ (payload JSON Serialize)
    ▼
[notification_outbox 테이블]  ← status: PENDING

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

[NotificationSendWorker]  ← @Scheduled(fixedDelay=30s)
    │ PENDING → PROCESSING (claim)
    │ 활성화된 토큰 전체 조회 (push_devices WHERE is_active=true)
    │ 플랫폼별 payload 생성 (Android: channelId / iOS: sound)
    │ 100개 단위 청크 분할
    │ POST https://exp.host/--/api/v2/push/send
    │ Ticket ID 저장 → push_tickets
    ▼
[PROCESSING → SENT / FAILED]

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

[ReceiptCheckWorker]  ← @Scheduled(fixedDelay=5m)
    │ 생성 후 15분 경과한 PENDING_RECEIPT 티켓 조회
    │ POST https://exp.host/--/api/v2/push/getReceipts
    │ status: "error" + DeviceNotRegistered
    │   → push_devices.is_active = false
    ▼
[push_tickets.status → OK / ERROR]
```

---

### 3.2 데이터베이스 스키마 및 Entity 구조

#### `push_devices` — 기기별 Expo Push Token 저장

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `user_id` | BIGINT | **Nullable** | 로그인 사용자 ID. 비로그인 시 NULL |
| `installation_id` | VARCHAR(100) | NOT NULL, **UNIQUE** | 앱 설치 단위 고유 UUID. Upsert 기준 키 |
| `expo_push_token` | VARCHAR(200) | NOT NULL, UNIQUE | Expo Push Service 발급 토큰 |
| `platform` | VARCHAR(10) | NOT NULL | `ios` \| `android` |
| `project_id` | VARCHAR(100) | NOT NULL | 한서메이트 EAS 프로젝트 ID |
| `app_version` | VARCHAR(20) | NOT NULL | 앱 버전 (예: `0.1.0`) |
| `is_active` | BOOLEAN | NOT NULL | 발송 대상 여부. `false`이면 발송 제외 |
| `last_registered_at` | DATETIME | NOT NULL | 마지막 토큰 등록/갱신 시각 |
| `disabled_at` | DATETIME | Nullable | 비활성화 처리된 시각 |
| `last_error_code` | VARCHAR(100) | Nullable | 마지막 에러 코드 (예: `DeviceNotRegistered`) |
| `created_at` | DATETIME | NOT NULL | JPA Auditing 자동 기록 |
| `updated_at` | DATETIME | NOT NULL | JPA Auditing 자동 기록 |

#### `notification_outbox` — 알림 발송 요청 큐

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `payload` | TEXT | NOT NULL | 알림 내용 JSON (`title`, `body`, `data`) |
| `status` | VARCHAR(20) | NOT NULL | `PENDING` → `PROCESSING` → `SENT` \| `FAILED` |
| `error_message` | VARCHAR(500) | Nullable | FAILED 시 오류 메시지 |
| `created_at` | DATETIME | NOT NULL | |
| `updated_at` | DATETIME | NOT NULL | |

**payload JSON 구조**

```json
{
  "title": "새로운 학사 공지가 등록됐어요",
  "body": "2026학년도 2학기 수강신청 안내",
  "data": {
    "version": 1,
    "type": "notice",
    "route": "/notices",
    "entityId": "12345"
  }
}
```

> **알림 데이터 규격 (`data` 필드)**: 앱은 보안상 서버가 보낸 임의 경로를 그대로 열지 않습니다. 허용된 `type` 값은 아래와 같습니다.
>
> | type | route | 추가 필드 | 이동 화면 |
> |------|-------|----------|----------|
> | `notice` | `/notices` | `entityId` (문자열, 최대 128자) | `/notices/{entityId}` |
> | `schedule` | `/timetable` | 없음 | 시간표 화면 |
> | `test` | `/notifications` | 없음 | 알림 목록 화면 (관리자 전용) |

#### `push_tickets` — Expo Ticket 추적

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| `id` | BIGINT | PK, AUTO_INCREMENT | |
| `expo_ticket_id` | VARCHAR(100) | NOT NULL | Expo API가 반환한 Ticket UUID |
| `outbox_id` | BIGINT | NOT NULL | 트리거한 `notification_outbox.id` |
| `push_device_id` | BIGINT | NOT NULL | 발송 대상 `push_devices.id` |
| `status` | VARCHAR(20) | NOT NULL | `PENDING_RECEIPT` → `OK` \| `ERROR` |
| `error_code` | VARCHAR(100) | Nullable | Receipt 에러 코드 (예: `DeviceNotRegistered`) |
| `checked_at` | DATETIME | Nullable | Receipt 확인 완료 시각 |
| `created_at` | DATETIME | NOT NULL | |
| `updated_at` | DATETIME | NOT NULL | |

---

### 3.3 REST API 명세

#### `PUT /api/v1/push-tokens` — Push Token 등록/갱신

| 항목 | 내용 |
|------|------|
| **Method** | `PUT` |
| **URL** | `/api/v1/push-tokens` |
| **인증** | Optional (Bearer JWT) — 로그인 사용자는 `user_id` 연결 저장 |
| **Content-Type** | `application/json` |

##### Request Body

```json
{
  "expoPushToken": "ExponentPushToken[xxxxxxxxxxxxxxxxxxxxxx]",
  "projectId": "808e731c-f396-43c8-9ef2-e382521305d3",
  "platform": "android",
  "installationId": "기기별-앱설치-UUID",
  "appVersion": "0.1.0"
}
```

##### Request Fields

| 필드 | 타입 | 필수 | 제약 | 설명 |
|------|------|------|------|------|
| `expoPushToken` | String | ✅ | max 200자 | Expo Push Service 발급 기기 토큰 |
| `projectId` | String | ✅ | max 100자 | 한서메이트 EAS 프로젝트 ID |
| `platform` | String | ✅ | `ios` 또는 `android` | 플랫폼 구분 |
| `installationId` | String | ✅ | max 100자 | 앱 설치 단위 고유 UUID |
| `appVersion` | String | ✅ | max 20자 | 현재 앱 버전 |

##### 응답

| 상태 코드 | 설명 |
|----------|------|
| `200 OK` | 등록 또는 갱신 성공 (Body 없음) |
| `400 Bad Request` | 필드 유효성 검사 실패 |

##### Upsert 로직

1. `installationId`로 기존 레코드 조회
2. **존재 시**: `expo_push_token`, `user_id`, `project_id`, `app_version` 갱신. `is_active = true`, `last_registered_at` 갱신
3. **없을 시**: 신규 레코드 생성
4. 토큰이 변경된 경우, 해당 토큰을 보유한 다른 기기를 자동으로 비활성화(`TOKEN_REASSIGNED`)

##### 앱에서 이 API를 호출해야 하는 시점

- 알림 권한을 허용한 직후
- 로그인 직후
- 앱 실행 시 저장된 토큰과 새 토큰이 다를 때
- 앱 재설치 후
- 사용자 계정이 변경됐을 때

---

#### `DELETE /api/v1/push-tokens/{installationId}` — Push Token 사용자 연결 해제

| 항목 | 내용 |
|------|------|
| **Method** | `DELETE` |
| **URL** | `/api/v1/push-tokens/{installationId}` |
| **인증** | Optional (Bearer JWT) |

##### Path Parameter

| 파라미터 | 타입 | 설명 |
|---------|------|------|
| `installationId` | String | 앱 설치 단위 UUID |

##### 응답

| 상태 코드 | 설명 |
|----------|------|
| `204 No Content` | 연결 해제 성공 (이미 없는 기기도 204 반환) |

> **설계 의도**: 토큰 자체를 삭제하지 않고 `user_id` 컬럼만 `NULL`로 초기화합니다. 비로그인 상태에서도 공지 등 전체 대상 알림을 계속 수신할 수 있도록 하기 위함입니다. 완전한 알림 수신 거부는 앱에서 알림 권한을 취소하는 방식을 사용해야 합니다.

##### 사용 시점

- 사용자 로그아웃
- 사용자가 개인화 알림 수신 해제
- 기기와 계정 연결 해제

---

### 3.4 Worker 동작 정책

#### NotificationSendWorker — 알림 발송

| 항목 | 내용 |
|------|------|
| **실행 주기** | `fixedDelay = 30초` (이전 실행 완료 후 30초 대기) |
| **처리 단위** | PENDING Outbox 전체 일괄 처리 |
| **청크 크기** | 100개 메시지 단위로 분할 (Expo API 최대 허용 크기) |

**처리 흐름**

```
1. notification_outbox WHERE status='PENDING' 조회
2. status → PROCESSING (중복 처리 방지)
3. push_devices WHERE is_active=true 조회
4. 플랫폼별 Expo 메시지 생성
   - Android: channelId = "general-v1"  (sound 생략)
   - iOS:     sound = "default"
5. 100개씩 청크 분할 → Expo Push API 호출
6. 응답 Ticket ID → push_tickets 저장
7. status → SENT (성공) 또는 FAILED (예외 발생)
```

**플랫폼별 Payload 차이**

```json
// Android
{
  "to": "ExponentPushToken[...]",
  "title": "...", "body": "...",
  "channelId": "general-v1",
  "priority": "high",
  "data": { ... }
}

// iOS
{
  "to": "ExponentPushToken[...]",
  "title": "...", "body": "...",
  "sound": "default",
  "priority": "high",
  "data": { ... }
}
```

> Android 8.0 이상에서는 소리·진동 정책을 채널이 관리하므로 `sound` 필드를 생략합니다. 앱에는 `general-v1` 채널이 등록되어 있어야 합니다.

#### ReceiptCheckWorker — Receipt 확인 및 토큰 비활성화

| 항목 | 내용 |
|------|------|
| **실행 주기** | `fixedDelay = 5분` |
| **조회 기준** | 생성 후 15분 이상 경과한 `push_tickets` (status = `PENDING_RECEIPT`) |
| **Receipt 보관 기한** | Expo는 최대 **24시간** 보관. 그 전에 반드시 조회 필요 |

**DeviceNotRegistered 처리**

앱 삭제, 토큰 만료 등으로 더 이상 사용할 수 없는 토큰에 대해 Expo가 이 에러를 반환합니다.

```
push_devices.is_active       = false
push_devices.disabled_at     = 현재 시각
push_devices.last_error_code = "DeviceNotRegistered"
push_tickets.status          = ERROR
push_tickets.error_code      = "DeviceNotRegistered"
push_tickets.checked_at      = 현재 시각
```

비활성화된 기기는 사용자가 앱을 재실행하여 `PUT /api/v1/push-tokens`를 재호출하면 자동으로 재활성화됩니다.

---

### 3.5 Expo Push API 통신 정책

#### 외부 API 엔드포인트

| 목적 | Method | URL |
|------|--------|-----|
| 메시지 발송 | `POST` | `https://exp.host/--/api/v2/push/send` |
| Receipt 조회 | `POST` | `https://exp.host/--/api/v2/push/getReceipts` |

#### 재시도 정책 (지수 백오프)

HTTP `429 Too Many Requests` 또는 `5xx` 서버 오류 발생 시 최대 4회 재시도합니다.

| 시도 | 대기 시간 |
|------|----------|
| 1회차 | 1초 |
| 2회차 | 2초 |
| 3회차 | 4초 |
| 4회차 | 8초 (최종) |

#### HTTP 오류별 처리 방침

| HTTP 상태 | 처리 |
|----------|------|
| `429`, `5xx` | 지수 백오프 재시도 (최대 4회) |
| `400` | 재시도 없이 즉시 실패 + ERROR 로그 기록. payload 또는 인증 문제를 확인 |

#### 발송 속도 제한

Expo Push Service는 프로젝트당 **초당 600개** 알림 제한이 있습니다.
현재 구현에서는 발송 Worker가 30초 단위로 실행되므로 대량 발송 시에도 한 번에 600개를 초과하지 않도록 주의해야 합니다.

#### 인증 (Enhanced Push Security)

EAS Dashboard에서 Enhanced Push Security를 활성화한 경우:

```
Authorization: Bearer {EXPO_ACCESS_TOKEN}
```

환경 변수 `EXPO_ACCESS_TOKEN`이 설정되어 있으면 자동으로 헤더에 추가됩니다. 비워두면 인증 없이 동작합니다.

> **보안 주의**: `EXPO_ACCESS_TOKEN`은 앱 코드, GitHub 저장소, `EXPO_PUBLIC_` 환경 변수에 절대 포함하지 마세요.

---

## 4. 환경 변수 목록

### 식단 크롤러 관련

| 변수명 | 기본값 | 설명 |
|--------|--------|------|
| `CAFETERIA_MAIN_STUDENT_URL` | `https://www.hanseo.ac.kr/food/main-student.do` | 메인캠퍼스 학생 식당 URL |
| `CAFETERIA_MAIN_STAFF_URL` | `https://www.hanseo.ac.kr/food/main-staff.do` | 메인캠퍼스 교직원 식당 URL |
| `CAFETERIA_TAEAN_STUDENT_URL` | `https://www.hanseo.ac.kr/food/taean-student.do` | 태안캠퍼스 학생 식당 URL |
| `CAFETERIA_TAEAN_STAFF_URL` | `https://www.hanseo.ac.kr/food/taean-staff.do` | 태안캠퍼스 교직원 식당 URL |

크롤러 서버 base URL은 `application-prod.properties`에서 `cafeteria.crawler.api-base-url`로 설정합니다.

### 푸시 알림 관련

| 변수명 | 기본값 | 설명 |
|--------|--------|------|
| `EXPO_ACCESS_TOKEN` | (빈 값) | Expo Enhanced Push Security 토큰. 비워두면 인증 없이 동작 |

# 앱 업데이트 정책 백엔드 API

기준: 2026-09-09 프론트 요청서. 이 문서는 백엔드에서 구현한 실제 계약입니다.
사용자 판정 API, 관리자 정책 API, 예약 워커, 감사 이력, MySQL 증분 SQL을 제공합니다.
관리자 화면과 모바일 앱 코드는 이 저장소의 구현 범위에 포함하지 않습니다.

## 1. 적용 범위와 기본 동작

- iOS와 Android는 서로 다른 정책을 사용합니다.
- 표시 버전 문자열은 비교하지 않습니다. 양의 정수 네이티브 빌드를 비교합니다.
- 정책이 없으면 `200 {"action":"NONE","policy":null,"checkedAt":"...Z"}`입니다. 초기 정책을 자동 등록하지 않습니다.
- `REQUIRED`일 때도 확인 API는 200입니다. 공개 확인 API는 JWT를 요구하지 않고 만료된 JWT 헤더도 판정에 사용하지 않습니다.
- 관리자 API는 기존 `SecurityConfig`의 `/api/admin/**` 체인에서 `ADMIN` JWT를 검사합니다.
- 모든 신규 경로의 성공·오류 응답에 `Cache-Control: no-store`를 설정합니다.
- 정책을 서버 메모리/분산 캐시에 저장하지 않습니다. 게시·롤백 커밋 후 다음 조회는 DB의 현재 활성 정책을 사용합니다.
- 일반 API를 앱 빌드로 차단하는 미들웨어, 기기별 영향 사용자 집계는 요청서의 2차 범위이며 추가하지 않았습니다.

## 2. Endpoint

| 인증 | Method | 경로 | 설명 |
|---|---|---|---|
| 없음 | GET | `/api/app-updates/check` | 설치 빌드의 업데이트 판정 |
| ADMIN | GET | `/api/admin/app-update-policies` | 정책 목록 |
| ADMIN | GET | `/api/admin/app-update-policies/{policyId}` | 정책 상세·처리 결과 재조회 |
| ADMIN | POST | `/api/admin/app-update-policies` | 초안 생성, 201 |
| ADMIN | PUT | `/api/admin/app-update-policies/{policyId}` | 초안 수정 |
| ADMIN | POST | `/api/admin/app-update-policies/{policyId}/preview` | 저장된 정책의 판정 미리보기 |
| ADMIN | POST | `/api/admin/app-update-policies/{policyId}/publish` | 즉시/예약 게시 |
| ADMIN | POST | `/api/admin/app-update-policies/{policyId}/cancel` | 예약 취소 |
| ADMIN | POST | `/api/admin/app-update-policies/{targetPolicyId}/rollback` | 과거 게시 정책을 복제해 복원 |
| ADMIN | POST | `/api/admin/app-update-policies/{policyId}/relax` | 새 초안으로 긴급 완화 |
| ADMIN | GET | `/api/admin/app-update-policies/audit-logs` | 감사 이력 |

요청서의 9개 Endpoint를 유지했습니다. 상세 재조회와 긴급 완화 경로를 추가했습니다.
성공 응답은 아래 JSON 자체이며 `data` 등의 추가 envelope가 없습니다.
초안 생성 외 성공 상태는 200입니다. 생성 응답에는 `Location` 헤더도 포함합니다.

## 3. 공개 업데이트 확인

```http
GET /api/app-updates/check?platform=ANDROID&build=27&version=1.2
```

| Query | 규칙 |
|---|---|
| platform | 필수, `IOS` / `ANDROID` |
| build | 필수, 1~9223372036854775807 십진 정수. 소수·지수·16진 표기는 거부 |
| version | 필수, 공백 제거 후 1~32자 일반 텍스트. 판정에는 사용하지 않음 |

```json
{
  "action": "REQUIRED",
  "policy": {
    "id": 17,
    "revision": 2,
    "platform": "ANDROID",
    "latestVersion": "1.3",
    "latestBuild": 30,
    "forceUpdateEnabled": true,
    "minimumSupportedBuild": 28,
    "storeUrl": "https://play.google.com/store/apps/details?id=com.hanseomate.app",
    "title": "업데이트가 필요해요",
    "message": "안정적인 서비스 이용을 위해 최신 버전을 설치해 주세요.",
    "effectiveAt": "2026-09-09T04:00:00Z"
  },
  "checkedAt": "2026-09-09T04:05:00Z"
}
```

판정 순서:

1. 활성 정책 없음 → `NONE`.
2. 강제 사용이고 설치 빌드 < 최소 지원 빌드 → `REQUIRED`.
3. 선택 안내 사용이고 설치 빌드 < 최신 빌드 → `OPTIONAL`.
4. 나머지 → `NONE`.

최소 지원 빌드와 같은 설치 빌드는 REQUIRED가 아닙니다. 최신 빌드 이상이면 NONE입니다.
`NONE` 응답도 활성 정책이 있으면 객체를 반환합니다.
`OPTIONAL` / `REQUIRED`의 `policy`는 항상 객체입니다.
공개 정책에는 내부 사유, 작업자, 감사 이력, `optionalUpdateEnabled`를 노출하지 않습니다.

## 4. 초안 생성·수정

```http
POST /api/admin/app-update-policies
Authorization: Bearer {adminAccessToken}
Content-Type: application/json
```

```json
{
  "platform": "ANDROID",
  "latestVersion": "1.3",
  "latestBuild": 30,
  "forceUpdateEnabled": true,
  "minimumSupportedBuild": 28,
  "optionalUpdateEnabled": true,
  "storeUrl": "https://play.google.com/store/apps/details?id=com.hanseomate.app",
  "title": "업데이트가 필요해요",
  "message": "안정적인 서비스 이용을 위해 최신 버전을 설치해 주세요.",
  "reason": "Google Play 빌드 30 배포 준비"
}
```

수정은 `PUT /{policyId}`로 보내며 위 본문에서 `platform`을 빼고 현재 `revision`을 추가합니다.
현재 `DRAFT`만 수정할 수 있습니다. 게시 정책을 바꾸려면 편집할 내용을 새 초안으로 생성합니다.
복제할 때 서버 관리 필드를 통째로 전송하면 400이므로 생성 필드만 선택해야 합니다.

| 필드 | 타입 | 검증 |
|---|---|---|
| platform | enum | 생성 필수, 생성 후 변경 불가 |
| latestVersion | string | 1~32자 |
| latestBuild | number / BIGINT | 1 이상 정수 |
| forceUpdateEnabled | boolean | 필수, 문자열/숫자 변환 허용 안 함 |
| minimumSupportedBuild | number \| null | 강제 사용 시 1 이상이며 latestBuild 이하. 미사용 시 반드시 null |
| optionalUpdateEnabled | boolean | 필수 |
| storeUrl | string | 최대 500자, 아래 플랫폼별 검증 |
| title | string | 공백 제거 후 2~40자 |
| message | string | 공백 제거 후 1~300자 |
| reason | string | 공백 제거 후 10~500자 내부 사유 |
| revision | number | 수정에서만 필수, 현재 값과 일치 |

텍스트의 HTML 태그 표기, 제어 문자, 개행, 보이지 않는 서식 문자를 거부합니다.
JSON 정수는 문자열이나 소수에서 강제 변환하지 않습니다. 알 수 없는 필드는 거부합니다.
생성 요청에 `status`, `revision`, 게시자·시각 등 서버 필드를 보내면 400입니다.

### 관리자 정책 응답

```json
{
  "id": 17,
  "revision": 1,
  "platform": "ANDROID",
  "latestVersion": "1.3",
  "latestBuild": 30,
  "forceUpdateEnabled": true,
  "minimumSupportedBuild": 28,
  "optionalUpdateEnabled": true,
  "storeUrl": "https://play.google.com/store/apps/details?id=com.hanseomate.app",
  "title": "업데이트가 필요해요",
  "message": "안정적인 서비스 이용을 위해 최신 버전을 설치해 주세요.",
  "status": "DRAFT",
  "effectiveAt": null,
  "storeAvailabilityConfirmedAt": null,
  "storeAvailabilityConfirmedBy": null,
  "createdBy": 7,
  "updatedBy": 7,
  "publishedBy": null,
  "createdAt": "2026-09-09T03:00:00Z",
  "updatedAt": "2026-09-09T03:00:00Z",
  "publishedAt": null
}
```

초안 revision은 1부터 시작하고 내용·상태 변경 시 증가합니다.
revision은 해당 정책 행의 동시 수정 검사 값입니다. 플랫폼 전체의 순번이 아닙니다.
`publishedAt`은 관리자가 게시를 승인한 시각이며, `effectiveAt`은 정책 적용 시각입니다.
예약 활성화 시 `effectiveAt`은 예약 당시 지정한 시각을 유지합니다.
모든 API 시각은 UTC이고 DB와 응답의 소수점 정밀도는 최대 마이크로초입니다.

## 5. 정책 목록과 상세

```http
GET /api/admin/app-update-policies?platform=IOS&status=ACTIVE&page=0&size=20
GET /api/admin/app-update-policies/17
```

목록의 platform/status는 선택입니다. status는 `DRAFT | SCHEDULED | ACTIVE | SUPERSEDED | CANCELLED`입니다.
page는 0 이상, size는 1~100이고 기본값은 0/20입니다. id 내림차순입니다.

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0,
  "hasNext": false
}
```

`content` 각 항목은 관리자 정책 응답입니다.
목록에서 ACTIVE/SCHEDULED를 플랫폼별로 조회하고, 최근 사유·작업자는 감사 이력에서 조회할 수 있습니다.
기기 수를 집계하지 않으므로 영향 사용자 수 필드를 만들지 않았습니다.

## 6. 서버 미리보기

```http
POST /api/admin/app-update-policies/17/preview
```

```json
{"revision":1,"builds":[27,28,30,31]}
```

```json
{
  "policyId": 17,
  "revision": 1,
  "results": [
    {"build":27,"action":"REQUIRED"},
    {"build":28,"action":"OPTIONAL"},
    {"build":30,"action":"NONE"},
    {"build":31,"action":"NONE"}
  ]
}
```

1~100개 빌드를 입력할 수 있습니다. 각 빌드는 양의 정수여야 하며 입력 순서를 유지합니다.
초안 외 저장된 정책도 조회할 수 있고 revision은 반드시 일치해야 합니다.
공개 API와 동일한 `AppUpdateDecision` 함수를 사용합니다. 상태·revision·감사 이력을 변경하지 않습니다.
최소 빌드가 1이면 0은 유효한 설치 빌드가 아니므로 미리보기에서도 거부합니다.

## 7. 즉시/예약 게시와 재시도

게시·취소·롤백·긴급 완화에는 `Idempotency-Key` 헤더가 필수입니다.
영문·숫자·점·밑줄·콜론·하이픈 1~128자를 허용하며 UUID 사용을 권장합니다.

```http
POST /api/admin/app-update-policies/17/publish
Authorization: Bearer {adminAccessToken}
Idempotency-Key: 47a9dfb4-b14f-447d-bfb6-5133900a8cb1
Content-Type: application/json
```

```json
{
  "revision": 1,
  "publishMode": "IMMEDIATE",
  "effectiveAt": null,
  "storeAvailabilityConfirmed": true,
  "reason": "Google Play 빌드 30 설치 가능 상태 확인 후 적용"
}
```

예약은 `publishMode: "SCHEDULED"`, `effectiveAt: "2026-09-10T04:00:00Z"`처럼 보냅니다.

- 게시할 대상은 DRAFT이며 revision이 일치해야 합니다.
- 즉시 게시의 effectiveAt은 null입니다.
- 예약은 서버 현재 시각보다 미래의 UTC `Z` 시각입니다. KST는 API 호출 전에 UTC로 변환합니다.
- 스토어 배포 확인은 반드시 JSON `true`여야 합니다. 서버는 실제 스토어 배포 여부를 자동 조회하지 않습니다.
- 같은 플랫폼에 예약이 있으면 일반 즉시/예약 게시를 409로 거부합니다. 기존 예약을 취소한 뒤 게시합니다.
- 활성 정책의 최소 지원 빌드를 낮추거나 강제 기능을 끄는 일반 게시를 409로 거부합니다. 아래 relax/rollback을 사용합니다.
- 즉시 게시: 기존 ACTIVE → SUPERSEDED, 대상 DRAFT → ACTIVE.
- 예약 게시: 대상 DRAFT → SCHEDULED. 현재 ACTIVE는 유지합니다.
- 기본 1초 간격 서버 워커가 도래한 예약을 적용합니다. 브라우저가 필요하지 않습니다.
- 워커가 늦거나 재시작되면 다음 주기에 이미 지난 예약도 처리합니다. 정상 실행 시 지연은 워커 간격과 DB 처리 시간에 좌우됩니다.
- 적용 실패 시 기존 정책과 예약 상태를 유지하며 다음 주기에 재시도합니다.
- 여러 서버가 같은 예약을 처리해도 활성화와 성공 감사 로그는 한 번만 발생합니다.

### 멱등 처리 규칙

같은 키 + 같은 관리자 + 같은 작업 경로 + 같은 본문은 **최초 성공 응답**을 그대로 반환합니다.
정책 상태가 이후 SUPERSEDED/ACTIVE 등으로 바뀌어도 재시도 응답은 최초 결과이므로, 최신 상태는 상세 GET으로 확인합니다.
같은 키를 다른 관리자/대상/작업/본문에 재사용하면 409입니다.
다른 키로 오래된 revision을 다시 게시하면 409이며, 키 없이 보내면 400입니다.

키 예약·상태 변경·성공 감사 이력·응답 스냅샷은 한 트랜잭션입니다.
실패한 트랜잭션은 키 예약도 취소되어 같은 키로 재시도할 수 있습니다.
성공 키/결과는 DB에 계속 보존합니다. 자동 만료나 별도 삭제 API는 없습니다.

## 8. 예약 취소

```http
POST /api/admin/app-update-policies/17/cancel
Idempotency-Key: {새 UUID}
```

```json
{"revision":2,"reason":"스토어 배포 지연으로 게시 예약 취소"}
```

SCHEDULED만 CANCELLED로 변경합니다. ACTIVE 취소는 409입니다.
예약 워커와 취소가 동시에 실행되면 플랫폼 잠금 획득 순서대로 처리하며,
활성화가 먼저 완료된 경우 최신 상태를 조회하고 rollback/relax를 사용해야 합니다.

## 9. 롤백과 긴급 완화

### 과거 정책 복원

```http
POST /api/admin/app-update-policies/15/rollback
Idempotency-Key: {새 UUID}
```

```json
{
  "expectedActivePolicyId": 17,
  "expectedActiveRevision": 2,
  "reason": "최소 지원 빌드 오류로 이전 정책을 긴급 복원"
}
```

15는 같은 플랫폼의 SUPERSEDED 정책이어야 합니다.
현재 ACTIVE의 ID/revision이 기대값과 일치하지 않으면 409입니다.
과거 행은 그대로 두고, 해당 설정값을 복제한 **새 ID / revision 1** 정책을 ACTIVE로 만듭니다.
기존 스토어 배포 확인자/시각은 복원 원본의 확인 기록을 보존합니다.
새 정책의 생성자·게시자·게시 시각은 이번 롤백 작업을 기록합니다.

### 새 초안으로 최소 지원 빌드 완화

1. 최소 빌드를 낮추거나 `forceUpdateEnabled:false, minimumSupportedBuild:null`인 새 초안을 만듭니다.
2. 다음 전용 API로 즉시 게시합니다.

```http
POST /api/admin/app-update-policies/19/relax
Idempotency-Key: {새 UUID}
```

```json
{
  "revision": 1,
  "expectedActivePolicyId": 17,
  "expectedActiveRevision": 2,
  "storeAvailabilityConfirmed": true,
  "reason": "잘못된 최소 빌드 기준으로 발생한 차단을 긴급 해제"
}
```

relax는 현재 ACTIVE의 최소 지원 조건을 실제로 낮추는 DRAFT만 허용합니다.
강제·선택 안내를 모두 끈 초안을 relax하면 모든 유효한 빌드가 NONE을 받습니다.

**롤백·긴급 완화는 남아 있는 같은 플랫폼의 예약도 같은 트랜잭션에서 취소합니다.**
이후 예약이 잘못된 강제 정책을 다시 활성화하지 않게 하기 위한 규칙입니다.
관련 기존 활성/예약 정책의 변경 전·후 스냅샷을 함께 남깁니다.
반대 플랫폼 정책은 변경하지 않습니다.

## 10. 스토어 URL 검증과 설정

iOS:

- HTTPS, host `apps.apple.com`.
- `/app/id{숫자}`, `/kr/app/id{숫자}`, `/kr/app/{앱이름}/id{숫자}` 같은 정상 경로.
- 숫자 ID는 서버 설정 `APP_UPDATES_IOS_APP_STORE_ID`와 정확히 일치해야 합니다.
- 이 설정이 비어 있으면 iOS 저장·게시를 400으로 거부합니다. 공개 조회와 Android 기능은 계속 동작합니다.
- 실제 한서메이트 iOS ID는 요청서에 없으므로 임의 값을 기본값으로 넣지 않았습니다.

Android:

- HTTPS, host `play.google.com`, 경로 `/store/apps/details`.
- query의 `id`가 정확히 하나이며 설정된 패키지와 일치해야 합니다.
- 기본 패키지: `com.hanseomate.app`.
- 잘못된 패키지·중복 id·다른 호스트는 거부합니다.

두 플랫폼 모두 사용자명/비밀번호, fragment, HTTPS 이외 scheme, 443 이외 명시 포트를 거부합니다.
코드가 URL에 접속하는 방식이 아닌, URI 구조와 앱 식별자를 검증하는 방식입니다.

## 11. 감사 이력과 오류

```http
GET /api/admin/app-update-policies/audit-logs?platform=IOS&eventType=PUBLISH&actorId=7&from=2026-09-01T00:00:00Z&to=2026-10-01T00:00:00Z&page=0&size=20
```

목록 envelope는 정책 목록과 같습니다. 필터는 모두 선택입니다.
from 포함 / to 미포함, id 내림차순이며 두 시각이 있으면 from < to여야 합니다.

eventType: `CREATE, UPDATE, PUBLISH, SCHEDULE, ACTIVATE, CANCEL, ROLLBACK, RELAX`.

각 감사 항목:

| 필드 | 설명 |
|---|---|
| id, policyId, revision, platform | 감사 ID, 대상 정책 및 당시 revision |
| eventType, success | 작업 종류와 성공 여부 |
| beforeSnapshot, afterSnapshot | 변경 전·후 값 |
| actorId | 관리자 ID. 서버 자동 예약 적용은 null |
| publishedBy, publishedAt | 대상 정책의 게시 승인자/시각 |
| createdAt | 감사 이벤트 시각, UTC |
| reason | 내부 변경 사유 |
| requestIp, userAgent, requestId | 요청 문맥. 자동 작업은 IP null |
| failureMessage | 실패 사유. 성공은 null |

성공 스냅샷은 `{policy, previousActivePolicy, scheduledPolicy}` 형태이고,
각 값은 해당 시점의 관리자 정책 전체 객체 또는 null입니다.
예약 취소/롤백 후 스냅샷에는 관련 정책의 CANCELLED/SUPERSEDED 상태까지 남깁니다.

인증된 정책 명령이 서비스에서 실패하면 트랜잭션 종료 후 별도 감사 트랜잭션에 실패를 기록합니다.
실패의 beforeSnapshot은 실패 후 재조회한 현재 정책 객체이며 afterSnapshot은 null입니다.
동시 작업이 있었다면 이 재조회 값은 실패한 시도의 최초 읽기 값과 다를 수 있습니다.
잘못된 사유 원문은 감사에 저장하지 않습니다.
JSON 파싱/인증 단계에서 거부된 요청은 정책 명령 감사 대상이 아닙니다.
DB 자체 장애로 실패 감사도 저장할 수 없으면 서버 ERROR 로그를 남깁니다.

`X-Request-ID`는 선택 헤더이며, 영문·숫자·점·밑줄·콜론·하이픈 1~128자를 받습니다.
없거나 잘못된 값이면 서버가 UUID를 생성합니다. 응답에도 같은 헤더를 반환합니다.
감사 IP는 `request.getRemoteAddr()`를 기록하고 임의 X-Forwarded-For를 직접 신뢰하지 않습니다.

기존 오류 형식:

```json
{"status":409,"message":"revision이 변경되었습니다. 최신 정책을 조회해 주세요.","path":"/api/admin/app-update-policies/17","timestamp":"2026-09-09T04:00:00Z"}
```

| 상태 | 의미 |
|---|---|
| 400 | 필드·타입·UTC 시각·URL·사유·멱등 키 오류, 미확인 게시 |
| 401 | 관리자 인증 누락/만료 |
| 403 | 관리자 권한 없음 |
| 404 | 대상 정책 없음 |
| 409 | revision/활성 정책 불일치, 잘못된 상태 전환, 예약 충돌, 키 재사용 충돌 |
| 429 | 공개 확인 API의 IP 요청 제한. Retry-After: 60 |
| 500 | 저장 등 서버 처리 실패. 정책 트랜잭션 전체 취소 |

## 12. DB 및 운영 적용

증분 SQL: [app-update-policy-migration-mysql.sql](app-update-policy-migration-mysql.sql).

생성 테이블:

- `app_update_policies`: 플랫폼별 초안/게시 정책과 슬롯 유니크 제약.
- `app_update_platform_locks`: 플랫폼별 동시 명령을 직렬화하는 DB 잠금 행.
- `app_update_commands`: 요청 키·관리자/본문 지문·최초 성공 응답.
- `app_update_audits`: append-only 감사 기록.

플랫폼별 활성·예약 슬롯을 UNIQUE로 제한하고 CHECK로 상태/최소 빌드 관계를 강제합니다.
잠금 순서는 멱등 키 → 플랫폼 → 정책입니다. READ_COMMITTED와 locking read를 사용합니다.
기존 계정/정책 삭제로 감사 기록이 소실되지 않도록 cascading FK는 두지 않습니다.

적용 순서:

1. 운영 DB 백업, 접속 대상 확인(`SELECT DATABASE()`), 기존 4개 테이블 유무/구조 확인.
2. **코드 배포 전에** 증분 SQL 실행. 기존 데이터/정책을 덮어쓰지 않습니다.
3. iOS 운영에 사용할 실제 App Store 숫자 ID 설정.
4. 기존 `spring.jpa.hibernate.ddl-auto=validate`를 유지하여 서버 배포.
5. 공개 확인 API의 초기 NONE 응답, 관리자 401/403, 초안/미리보기 확인.
6. 실제 설치 가능한 스토어 빌드만 확인 후 게시.

MySQL DDL은 자동 커밋입니다. `CREATE TABLE IF NOT EXISTS`는 기존의 잘못된 스키마를 복구하지 않습니다.
새 DB용 전체 스키마를 기존 운영 DB에 실행하지 않습니다.
SQL 재실행은 기존 정책·감사·멱등 결과를 보존합니다.

| 환경변수 | 기본값 | 의미 |
|---|---|---|
| APP_UPDATES_IOS_APP_STORE_ID | 빈 문자열 | 실제 iOS App Store 숫자 ID. iOS 정책 운영 전 필수 |
| APP_UPDATES_ANDROID_PACKAGE_ID | com.hanseomate.app | Android 패키지 |
| APP_UPDATES_SCHEDULER_ENABLED | true | 예약 워커 실행 |
| APP_UPDATES_SCHEDULER_DELAY_MS | 1000 | 예약 검사 주기(ms) |
| APP_UPDATES_CHECK_REQUESTS_PER_MINUTE | 600 | 공개 확인 API의 IP별 분당 요청 수, 1 이상 |
| APP_UPDATES_RATE_LIMIT_MAX_IPS | 10000 | 한 분에 보관할 IP 수 상한, 1 이상 |

UTC Instant를 DATETIME(6)에 저장하므로 기존 애플리케이션의 KST 기본 시간대 설정을 바꾸지 않습니다.
요청 제한은 프로세스별 메모리 카운터입니다. 분이 바뀌면 초기화합니다.
프록시 환경에서는 신뢰한 프록시가 복원한 클라이언트 IP를 remoteAddr에서 얻도록 배포 설정을 점검해야 합니다.
여러 인스턴스를 합산한 제한이 필요하면 인입 프록시에서도 제한해야 합니다.

## 13. 모니터링·앱 연동 경계

공개 호출 로그에는 platform, build, version, action, policyRevision을 기록합니다.
Micrometer 지표:

- `app.update.checks`: platform/action별 확인 **호출 횟수**.
- `app.update.scheduler.activations`: 성공한 예약 적용 수.
- `app.update.scheduler.failures`: query/activate 단계별 실패 수.

빌드·버전·revision은 무한한 metric 태그를 만들지 않고 로그에 남깁니다.
이 값은 실제 사용자 수/기기 수를 뜻하지 않습니다.
예약 실패는 ERROR 로그·감사 이력·실패 지표로 노출하며, 알림 수신 채널 연결은 운영 모니터링 설정에서 수행해야 합니다.
기존 actuator 공개 범위를 임의로 늘리지 않았습니다.

앱의 실행/포그라운드 복귀 호출, REQUIRED/OPTIONAL 화면, 스토어 열기와 장애 시 fail-open은 프론트 담당입니다.
오프라인/타임아웃/5xx/429를 REQUIRED로 해석하지 않고, 만료된 강제 응답을 계속 쓰지 않아야 합니다.
확인 API를 호출하는 브리지 버전이 배포되어야 실제 앱에서 이 정책을 사용할 수 있습니다.
백엔드 구현·테스트만으로 운영 DB 적용, 서버 배포, 실제 스토어 배포 또는 기기 표시를 확인한 것은 아닙니다.

## 14. 검증 실행

```powershell
.\gradlew.bat test --tests '*AppUpdate*Test' --console=plain
.\gradlew.bat build --console=plain
```

`AppUpdatePolicyMySqlTest`는 Docker의 별도 MySQL 컨테이너를 사용합니다.
Docker가 없으면 명시적으로 만든 loopback 전용 별도 포트의 `app_update_policy_test` DB만 받을 수 있습니다:

```powershell
$env:APP_UPDATE_TEST_MYSQL_URL='jdbc:mysql://127.0.0.1:33389/app_update_policy_test?useSSL=false&allowPublicKeyRetrieval=true'
$env:APP_UPDATE_TEST_MYSQL_USER='root'
$env:APP_UPDATE_TEST_MYSQL_PASSWORD=''
.\gradlew.bat test --tests '*AppUpdatePolicyMySqlTest' --console=plain
```

기본 포트 3306, 외부 호스트, 다른 DB명은 테스트 입력에서 거부합니다.
이 변수들은 테스트 전용이며 운영 설정에 넣지 않습니다.

# 축제 플로팅 버튼 노출 제어 API

## 1. 기능과 범위

앱 홈의 `대동제` 바로가기 버튼 노출 여부를 DB에 저장합니다. 기본값은 `false`입니다.
로그인/비로그인 사용자에게 동일하게 적용되며, 관리자 PATCH 완료 후 다음 `/api/home` 조회부터 반영됩니다.

버튼 이미지·문구·크기·위치 및 기존 `/festival` 이동은 앱에서 유지합니다.
축제 페이지 접근, 홈 포스터, 앱 시작 팝업 상태는 변경하지 않습니다.
예약 노출, 실시간 push/polling은 제공하지 않습니다.

## 2. API 요약

| Method | 경로 | 인증 | 성공 |
| --- | --- | --- | --- |
| GET | `/api/home` | 선택 JWT | 200 |
| GET | `/api/admin/settings/festival-floating-button` | ADMIN JWT | 200 |
| PATCH | `/api/admin/settings/festival-floating-button` | ADMIN JWT | 200 |

관리자 요청 헤더: `Authorization: Bearer {ADMIN_ACCESS_TOKEN}`.
별도 `data`/`success` envelope를 추가하지 않습니다. 세 응답에 `Cache-Control: no-store`를 적용합니다.

## 3. 메인 응답

기존 필드를 유지하면서 다음 최상위 필드를 추가합니다. 아래는 해당 필드만 발췌한 예시입니다.

```json
{
  "festivalFloatingButtonVisible": false
}
```

- 필수 JSON boolean이며 null이나 문자열을 반환하지 않습니다.
- DB 설정 행이 없으면 false를 반환하며 GET으로 행을 생성하지 않습니다.
- 로그인 여부에 따른 차이가 없습니다. 잘못되거나 만료된 JWT를 보내면 기존대로 401입니다.
- 기존 포스터가 없는 경우 `posterImageUrls`/`posters`는 기존 계약대로 null입니다.

## 4. 관리자 조회

```http
GET /api/admin/settings/festival-floating-button
Authorization: Bearer {ADMIN_ACCESS_TOKEN}
Accept: application/json
```

설정 행이 없거나 기본값 상태에서 실제 변경이 한 번도 없으면:

```json
{
  "visible": false,
  "updatedAt": null
}
```

변경 이력이 있으면:

```json
{
  "visible": true,
  "updatedAt": "2026-09-05T04:10:00.123456Z"
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| visible | boolean | 필수. 현재 노출 상태 |
| updatedAt | string 또는 null | 필수. 상태가 실제로 마지막 변경된 UTC ISO 8601 시각 |

기본 상태 조회는 404가 아니라 200입니다.

## 5. 관리자 변경

```http
PATCH /api/admin/settings/festival-floating-button
Authorization: Bearer {ADMIN_ACCESS_TOKEN}
Content-Type: application/json
Accept: application/json
```

```json
{
  "visible": true
}
```

성공 응답은 200이며 관리자 GET과 같은 형태입니다.

- visible에는 JSON true/false만 허용합니다. 누락, null, 문자열, 숫자, 배열, 객체는 400입니다.
- 원하는 최종 상태를 지정합니다. 토글 명령이 아니므로 재전송해도 상태가 뒤집히지 않습니다.
- 설정이 없으면 최초 PATCH에서 기본 false 행을 생성합니다.
- 최초 false PATCH는 updatedAt/updatedBy가 null이고 감사 이력은 없습니다.
- 실제 상태 변경에만 updatedAt/updatedBy와 감사 이력을 기록합니다.
- 동일 값 PATCH는 200을 반환하되 기존 값·변경 시각·변경자를 유지하고 감사 이력을 추가하지 않습니다.
- 실패 시 이전 상태를 유지하며 서버 오류는 500으로 반환합니다.

## 6. 저장·동시성·감사 이력

설정: `app_feature_settings`, 키: `FESTIVAL_FLOATING_BUTTON`.
enabled는 NOT NULL이고 DB/서버 기본값은 모두 false입니다.
최종 변경 시각과 변경 관리자 ID를 각각 updated_at/updated_by에 저장합니다.

최초 생성은 MySQL `INSERT ... ON DUPLICATE KEY UPDATE`로 중복 키 경합을 직렬화합니다.
이미 있는 행에서는 key를 자기 값으로 유지하고 enabled/updated_at/updated_by는 변경하지 않습니다.
이후 쓰기 잠금으로 현재 상태를 읽으며, 잠금은 트랜잭션 커밋까지 유지됩니다.
동시 PATCH는 커밋 순서대로 적용되므로 마지막으로 커밋된 명시 값이 최종 상태입니다.

별도 `app_feature_setting_audits`에는 실제 변경만 append-only로 저장합니다.

| 컬럼 | 내용 |
| --- | --- |
| setting_key | 변경한 설정 키 |
| changed_by | 인증 JWT의 관리자 식별자 |
| changed_at | 실제 변경 시각, UTC |
| previous_enabled | 변경 전 값 |
| new_enabled | 변경 후 값 |
| request_ip | 서블릿에서 확인한 요청 원격 주소 |

설정 변경과 감사 행 삽입은 같은 트랜잭션입니다. 둘 중 하나라도 실패하면 모두 롤백됩니다.
감사 엔티티는 immutable이며 변경/삭제 API를 제공하지 않습니다.
관리자 계정 삭제가 감사 이력을 지우지 않도록 관리자 FK cascade를 사용하지 않습니다.

임의로 전달한 `X-Forwarded-For`를 직접 신뢰하지 않고 `request.getRemoteAddr()`를 기록합니다.
리버스 프록시 환경에서는 현재 서버가 인식한 원격 주소(프록시 IP일 수 있음)가 기록됩니다.
클라이언트 IP 보존이 필요하면 배포 환경에서 신뢰 프록시를 설정해야 합니다. 토큰 원문은 저장하지 않습니다.

## 7. 캐시와 반영

- 설정은 매 요청 DB에서 읽으며 별도 메모리/분산 설정 캐시가 없습니다.
- `/api/home`, 관리자 GET/PATCH는 `Cache-Control: no-store`를 반환합니다.
- PATCH 성공은 DB 커밋 후 반환됩니다. 이후 시작한 홈 요청은 새 값을 읽습니다.
- 별도 서버 캐시가 없어 무효화할 내부 항목이 없습니다.
- CDN을 사용하는 환경에서는 이 API들을 캐시 제외하고, 기존에 강제 캐시한 응답이 있으면 배포 시 제거합니다.
- 홈에 계속 머무는 앱으로 변경을 push하지 않습니다. 다음 홈 조회에 반영됩니다.

## 8. 오류

기존 ApiErrorResponse 형식을 유지합니다.

```json
{
  "status": 400,
  "message": "visible: true 또는 false 값이 필요합니다.",
  "path": "/api/admin/settings/festival-floating-button",
  "timestamp": "2026-09-05T04:10:00Z"
}
```

| 상황 | 상태 | 대표 메시지 |
| --- | --- | --- |
| visible 누락/null | 400 | visible: true 또는 false 값이 필요합니다. |
| 문자열/숫자 등 boolean 이외 타입 또는 잘못된 JSON | 400 | 요청 형식이 올바르지 않습니다. |
| JWT 누락/만료/잘못된 토큰 | 401 | 로그인이 필요합니다. |
| USER JWT | 403 | 관리자 권한이 필요합니다. |
| JSON 이외 Content-Type | 415 | 지원하지 않는 Content-Type입니다. |
| DB 저장 등 서버 내부 실패 | 500 | 서버 내부 오류가 발생했습니다. |

## 9. 프론트 연동 계약

TypeScript 타입 및 fail-closed 판정 함수는
[contracts/festival-floating-button.ts](contracts/festival-floating-button.ts)에 있습니다.
이 파일은 전달용 계약이며 관리자/앱 UI 자체는 이 백엔드 저장소에 포함되지 않습니다.

### 관리자 페이지

1. ADMIN에게 `축제 플로팅 버튼` 설정 메뉴를 표시합니다.
2. 설명은 `앱 홈의 대동제 바로가기 버튼만 제어합니다`로 표시합니다.
3. GET 완료 전에는 스위치를 비활성화합니다. GET 실패 시 오류와 다시 시도를 표시합니다.
4. 현재 값을 조회하지 못했을 때 임의의 기본값을 PATCH하지 않습니다.
5. 스위치 변경 시 별도 저장 버튼 없이 목표 visible 값으로 PATCH합니다.
6. 저장 중 스위치를 비활성화하고 서버 성공 응답으로 최종 상태를 갱신합니다.
7. 저장 성공 메시지를 표시합니다. 실패 시 이전 표시값을 유지하고 오류/재시도를 제공합니다.

### 앱

- `/api/home`의 `festivalFloatingButtonVisible === true`일 때만 렌더링합니다.
- false/필드 누락/null/잘못된 타입/최초 오류/갱신 오류에서는 숨깁니다.
- 갱신 오류 시 과거 true 응답으로 버튼을 계속 표시하지 않습니다.
- 기존 정적 자산과 `/festival` 이동을 유지합니다.
- 서버 기능만 배포하면 구버전 앱의 상시 노출 동작은 바뀌지 않습니다. 새 필드를 읽는 앱 배포가 필요합니다.

## 10. 배포

1. 백업 후 [증분 SQL](festival-floating-button-migration-mysql.sql)의 사전 조회로 기존 테이블을 확인합니다.
2. 신규 테이블을 생성합니다. 첫 상태는 false / updatedAt=null입니다.
3. 테이블이 이미 있으면 구조를 확인합니다. IF NOT EXISTS는 스키마 차이를 자동 보정하지 않습니다.
4. API를 배포합니다. 운영 ddl-auto=validate이므로 반드시 DB가 먼저 준비되어야 합니다.
5. 관리자 GET 및 로그인/비로그인 홈 응답의 false, no-store를 확인합니다.
6. 관리자 UI와 조건부 렌더링 앱 버전을 배포합니다.
7. 운영 앱에서 false 상태를 확인한 뒤 축제 공개 시 관리자가 true로 변경합니다.
8. 축제 종료 시 false로 변경하고 다음 홈 조회와 감사 이력을 확인합니다.

증분 SQL은 기존 true와 감사 데이터를 보존하도록 작성되어 있습니다. 기존 DB에 전체 스키마를 실행하지 않습니다.
새 환경 변수는 필요하지 않습니다.

## 11. 검증

- API 통합 테스트: 기본 false, strict boolean, ADMIN JWT, no-op, 감사 실패 롤백, 홈 반영, CORS, OpenAPI.
- MySQL 컨테이너 테스트: 증분 SQL과 엔티티 validate, 독립 인스턴스 영속성, 최초 생성 경합, 커밋 순서, 감사 저장 실패 롤백.
- 관리자 화면과 앱 테스트는 해당 프론트 저장소에서 위 연동 계약에 따라 별도로 수행합니다.

```powershell
.\gradlew.bat test --tests "hsu.hanseomate.domain.appsetting.*" --tests "hsu.hanseomate.domain.home.HomeApiIntegrationTest"
```

MySQL 테스트는 기본적으로 Docker 컨테이너를 사용합니다. 별도로 초기화한 로컬 테스트 인스턴스를
사용하려면 `FESTIVAL_TEST_MYSQL_URL=jdbc:mysql://127.0.0.1:{별도포트}/festival_setting_test`를 지정합니다.
선택적으로 `FESTIVAL_TEST_MYSQL_USER`/`FESTIVAL_TEST_MYSQL_PASSWORD`를 지정할 수 있습니다.
테스트는 해당 DB의 두 설정 테이블을 비우므로 실제 애플리케이션 DB를 지정하지 않습니다.
Docker와 로컬 테스트 인스턴스가 모두 없으면 MySQL 테스트는 스킵됩니다.
H2 통과만으로 MySQL 동시성 검증이 끝났다고 판단하지 않습니다.

### 2026-09-05 검증 결과

- `./gradlew.bat build` 성공: 총 526개 중 520개 통과, 실패/오류 0개, 기존 환경 의존 테스트 6개 스킵.
- 신규 설정 API 20개, 실제 MySQL 8.0.41 검증 4개, 기존 홈 API 13개 모두 통과.
- MySQL은 별도 포트와 데이터 폴더의 임시 인스턴스를 사용했습니다. 운영 DB 적용 결과가 아닙니다.
- `git diff --check` 통과.

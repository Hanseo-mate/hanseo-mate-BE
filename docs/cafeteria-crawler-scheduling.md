# 학식 크롤러 정기 실행

Spring이 시간을 관리하고 Python이 학교 조회, 식당별 당일 성공 판단, DB 저장을 담당합니다.
Spring의 식단 조회 API는 같은 DB의 저장된 식단을 읽습니다.

## 실행 일정

- 시간대: `Asia/Seoul`
- 월~금 01, 03, 05, 07, 09, 11, 13, 15, 17시: 하루 최대 9개 정기 호출 시점
- cron: `0 0 1-17/2 * * MON-FRI`
- 정의 위치: `CafeteriaScheduler.CRON` 한 곳
- 주말과 17시 이후에는 새 자동 요청을 보내지 않습니다. 17시에 접수된 백그라운드 수집은 크롤러에서 완료될 때까지 진행합니다.
- 휴일도 월~금이면 같은 일정입니다. 학교가 뒤늦게 게시하면 당일의 후속 시각 또는 다음 평일에 다시 확인합니다.
- 배포/재시작 시 다음 예정 시각부터 실행합니다. 예를 들어 화요일 10:15 배포 시 화요일 11:00에 호출하며, 금요일 17시 이후 배포 시 월요일 01:00에 호출합니다. 놓친 과거 시각을 즉시 재생하지 않습니다.
- 별도 최대 5회 제한, Spring 지연 재시도 타이머, 즉시 재시도 루프는 없습니다.

## 정기 요청

모든 시각에 아래 요청을 한 번만 보냅니다.

```http
POST /cafeteria-crawl/run
Content-Type: application/json

{"mode":"background","only_pending":true}
```

`restaurant_types`는 생략합니다. 크롤러가 전체 식당의 당일 기록을 조회하여
완료한 식당은 `skipped`, 미완료 식당은 수집 대상으로 처리합니다.
Spring은 이번 주 메뉴 존재 여부나 이전 응답을 이유로 정기 요청을 생략하지 않습니다.

매일 01시에도 같은 요청을 보냅니다. 어제의 성공은 오늘을 건너뛰는 근거가 아닙니다.
이번 주의 유효한 식단을 읽어 저장한 `completed`와 기존 내용이 같은 `unchanged`가
당일 성공입니다. 지난주/다음 주 메뉴, 빈 결과, HTTP/파싱/DB 실패는 성공이 아닙니다.
한 식당 성공과 실패는 다른 식당의 기록 및 후속 시도에 영향을 주지 않습니다.
이 규칙의 실행 주체와 영구 기록 소유자는 Python입니다.

수동 강제 요청은 `only_pending=false`를 사용합니다. 클라이언트의
`triggerAllCrawl()`은 전체 강제 수집이며, 특정 식당은 다음처럼 문자열 배열을 보냅니다.
기존 `url`, 단수 `restaurant_type` 필드는 사용하지 않습니다.

```json
{"mode":"background","only_pending":false,"restaurant_types":["MAIN_STUDENT"]}
```

이 변경에서 별도 Spring 수동 실행 HTTP 엔드포인트는 추가하지 않습니다.

## 접수, 중복, 실패

- `status=starting`과 `run_id`는 요청 접수 확인입니다. Spring 로그는 접수로 기록하며 DB 저장 완료로 기록하지 않습니다.
- `409`는 이미 작업이 실행 중이라는 로그를 남기고 이번 시각을 종료합니다.
- 그 외 HTTP 오류, 연결 실패, 응답 처리 실패도 로그를 남기고 종료합니다. 다음 정기 시각에는 같은 요청을 다시 보냅니다.
- 재시작 후 영구 기록에 `running`이 남아도 Spring은 요청을 차단하지 않습니다. 실제 실행 중인 작업이 없다면 크롤러가 다시 시도합니다.

## 상태 조회

`CafeteriaCrawlerClient.checkStatus()`는 `GET /cafeteria-crawl/status`로
현재 프로세스의 최근 실행을 읽습니다. `run_id`, `status`, `started_at`,
`finished_at`, `results`를 매핑합니다. 식단은
`results[식당유형].menus`에 있으며 최상위 `menus`로 받지 않습니다.
`retry_count=0`, `max_retry_count=0`, `next_retry_at=null`은 호환용 필드로 무시합니다.

`checkDailyStatus()`는 `GET /cafeteria-crawl/daily-status`로 DB의 영구 상태를 읽습니다.

| 위치 | 필드 |
|---|---|
| 최상위 | `business_date`, `pending_restaurant_types`, `results` |
| `results[식당유형]` | `completed_today`, `status`, `business_date`, `run_id`, `last_attempt_at`, `last_success_date`, `last_success_at`, `error` |

날짜는 KST 업무일(`LocalDate`), 시각은 UTC ISO 8601(`OffsetDateTime`)입니다.
상태 조회는 관찰용이며 정기 POST의 사전 조건으로 사용하지 않습니다.

## 기존 코드와 배포

`6e75a08` 기준으로 정기 스케줄은 `triggerAllCrawl()`만 호출했고,
`CafeteriaSyncOrchestrator`와 `CafeteriaRetryStateService`는 운영 호출자가 없었습니다.
이 변경에서는 미사용 오케스트레이터, 재시도 서비스, 전용 타이머 설정과 구형 동기 HTTP 경로를 제거합니다.
기존 DB 교체 유틸리티는 정기 경로에 연결하지 않습니다. 학식 자동 실행 경로는
`CafeteriaScheduler → CafeteriaCrawlerClient → Python` 하나입니다.

1. Python 새 코드와 `cafeteria_crawl_progress` 준비 스크립트를 먼저 배포합니다.
2. Spring 새 코드를 배포합니다. `cafeteria.crawler.api-base-url`이 크롤러 주소를 가리켜야 합니다. 식당 원본 URL은 Python 설정이 관리합니다.
3. 다음 예정 시각의 Spring 접수/실패 로그, Python 실행 로그 및 `daily-status`를 확인합니다.
4. 당일 성공 식당이 후속 요청에서 건너뛰어지고 미완료 식당이 다시 실행되는지 확인합니다.

Spring은 진행 테이블을 생성하거나 초기화하지 않습니다.
현재의 단일 크롤러 프로세스 systemd 구성을 유지합니다.
다중 worker/인스턴스로 늘릴 경우 분산 실행 잠금을 별도로 설계해야 합니다.

## 로컬 검증 범위

`CafeteriaSchedulerTest`는 월~금 45개 시각, 19시·주말 제외,
배포 후 다음 예정 시각과 호스트 UTC/KST 차이를 검증합니다.
`CafeteriaCrawlerClientTest`는 로컬 HTTP 서버로 정확한 요청 JSON, 시각당 한 번 전송,
409/HTTP 실패 후 다음 호출, 접수 로그, 식당별 상태와 영구 상태 역직렬화를 검증합니다.

이 테스트는 새 Python 계약을 모사합니다. Python의 실제 성공 판정, 운영 DB 상태,
systemd 설정, 배포된 두 버전의 동시 적용과 예정 시각의 실수행은 운영 환경에서 별도 확인해야 합니다.

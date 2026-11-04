# 학사일정 Python 호환성 검토 및 적용 절차

검토일: 2026-11-04. 운영 적용 실행 기록이 아닌, 코드 대조와 격리 검증 문서다.

## 검토 기준과 Git 상태 — 릴리스 전 확인 기록

아래 Git 상태와 미검증 범위는 호환성 검토 단계의 기록이다. 이후 별도 요청으로 진행하는 릴리스의 병합·자동 배포 결과는 해당 PR과 Actions 이력을 기준으로 확인한다.

- Spring: build/worktrees/academic-calendar, feature/academic-calendar, 기준 ec1548e09ca6da525a618928ea597ccbd64234fa.
- git fetch origin main 후 확인한 Spring 원격 main도 ec1548e다. 학사일정 파일과 0005 SQL은 아직 커밋되지 않은 작업이며 원격 main에 포함되지 않았다.
- Spring의 로컬 main은 7c58bc9로 오래된 참조다. 이번 검토 기준으로 사용하지 않았다.
- Python: C:/Users/박찬우/Desktop/LikeLionHSU/hanseo-mate-crawler, HEAD/main cea8f7d4338ab7265b8fda299e2c931d64ddd0b8.
- 검토한 API/DB/academic_calendar/테스트 구현은 지정 커밋과 일치했다. Python 원격 main은 확인 시 028ccd1이며 지정 커밋을 포함한다. Python 체크아웃을 이동하지 않았다.
- 기존 Spring 변경, 원래 메인 작업 폴더, Python의 IDE 변경·미추적 첨부 폴더를 보존했다.
- 호환성 검토 단계에서는 커밋·푸시·병합·배포·운영 DB 변경을 실행하지 않았다. 읽기 확인을 위한 fetch로 원격 참조만 갱신했다.

## 호환성 판정

| 항목 | 대조 결과 |
|---|---|
| 요청 경로/JSON | POST /academic-calendar-crawl/run, academic_year 정수, semesters=[1,2], mode=background 일치 |
| 실행 연도 | Clock을 Asia/Seoul로 변환한 현재 달력연도. 2026년에는 2026만, 2027년에는 2027만 요청 |
| 스케줄 | Spring AcademicCalendarScheduler의 04:00 KST 한 경로. 관리자 수동 실행은 별도이며 Python 내부 예약 없음 |
| 다중 프로세스 | Spring 인스턴스별 예약 호출은 발생할 수 있음. Python의 DB 전역 lease가 동시 수집을 차단하며 Spring은 409를 ALREADY_RUNNING으로 처리 |
| 접수 | HTTP 202 + status=starting + 비어 있지 않은 문자열 run_id를 모두 검증하도록 보완 |
| 완료 판정 | ACCEPTED는 접수만 의미. 이후 수집 완료/저장 성공으로 기록하지 않음 |
| 시간 제한 | 연결 5초, 읽기 15초 유지. 읽기 타임아웃이 일반 RestClientException으로 감싸져도 원인 예외를 찾아 분류 |
| 저장 주체 | Python이 동일 환경 MySQL에 직접 저장. Spring은 Python 결과를 다시 쓰지 않음 |
| 컬럼/키 | 날짜 DATE, UTC DATETIME(6), active BIT(1), 문자열 길이와 원본 복합 유일 키 일치 |
| SQL 소유 | 학교 일정 확장/공휴일 스냅샷은 Spring 0005. 실행 이력/잠금 2개 표는 Python migrate 소유 |
| 수집 보존 | 실패·미게시·핵심 유형 누락 시 해당 학기 보존. 정상 스냅샷에서 사라진 해당 학기 HANSEO만 비활성화 |
| 공개 조회 | active=false 제외. MANUAL 일정 유지. 비로그인 학교/통합 캘린더 조회 가능 |
| 공식 개시일 | 원본 SEMESTER_START는 캘린더에 보존. Spring의 3월/9월 첫 비공휴일 평일 계산을 덮어쓰지 않음 |
| D-day/알림 | 시험 시작~종료일 포함 0, 방학 시작 당일 종강 0, 같은 날 수업/셔틀 차단 규칙 유지 |

created_at은 우리 DB에 최초 저장한 UTC 시각이다. 학교가 처음 게시한 시각이나 데이터 최신성 확인 시각으로 표시하지 않는다.
2학기 다음 해 1·2월, 일반 일정의 8→9월/2→3월 연장은 원본 날짜와 페이지 학년도·학기를 그대로 읽는다.

## 관리자 sync 응답 및 오류 처리

POST /api/admin/academic-calendar/sync, ADMIN JWT 필요. 요청 본문 없음.

```json
{
  "academicYear": 2026,
  "crawlStatus": "ACCEPTED",
  "runId": "example-run-id",
  "failedHolidayMonths": [],
  "failureReason": null
}
```

기존 네 필드는 유지하고 nullable failureReason을 추가했다.
이 응답의 HTTP 200은 Python 수집 완료를 의미하지 않는다.

| Python 응답/장애 | crawlStatus | failureReason |
|---|---|---|
| 정상 202 접수 | ACCEPTED | null |
| 409 이미 실행 중 | ALREADY_RUNNING | null |
| 422 요청 검증 거절 | FAILED | INVALID_REQUEST |
| 503 이력 DB/스키마/시작 장애 | FAILED | CRAWLER_UNAVAILABLE |
| 기타 HTTP 오류 | FAILED | HTTP_ERROR |
| 연결/읽기 타임아웃 | FAILED | REQUEST_TIMEOUT |
| 연결 거절 등 | FAILED | CONNECTION_ERROR |
| 200/201, 잘못된/비어 있는 JSON, 잘못된 run_id 등 | FAILED | INVALID_RESPONSE_OR_CLIENT_ERROR |

응답 원문, 접속 정보, 비밀 값이 포함될 수 있는 전체 예외 URL은 로그에 남기지 않는다.
타임아웃/연결 유실은 Python이 접수하지 않았다는 증거가 아니다. 즉시 재시도 루프를 만들지 않는다.
409 역시 기존 작업이 살아 있는지 상태부터 확인한다.

## Python 영구 상태 조회

서버 내부에서 호출한다. 아래 localhost 주소는 현재 코드의 기본값일 뿐 실제 운영 포트/프로세스 상태를 확인한 결과는 아니다.

```bash
curl --fail-with-body 'http://127.0.0.1:8000/academic-calendar-crawl/status?run_id=<접수받은-runId>'
curl --fail-with-body 'http://127.0.0.1:8000/academic-calendar-crawl/status'
```

- run_id 생략: 최신 실행. 이력 없음: {"status":"idle","run_id":null}.
- 지정 ID 없음: 404. 이력 DB 또는 스키마 장애: 503.
- 실행 필드: run_id, academic_year, semesters, status, started_at, finished_at, error, results.
- results의 "1"/"2": academic_year, semester, status, source_url, event_count, inserted, updated, unchanged, deactivated, started_at, finished_at, error, preservation_reason.
- 시각은 UTC ISO 8601(+00:00). 숫자는 해당 학기의 결과이며 전체 합계로 실패를 가리지 않는다.
- Spring 상태 조회 프록시나 자동 polling 작업은 추가하지 않았다. 일반 프론트에서 내부 Python API를 직접 호출하지 않는다.

| 최상위 status | 해석 |
|---|---|
| starting / running | 접수/실행 중. 아직 완료 아님 |
| completed | 요청 학기 모두 완료. 실제 DB와 공개 조회도 확인 |
| unpublished | 요청 학기 모두 미게시. 기존 데이터 보존 |
| partial_unpublished | 완료/미게시가 섞임. 학기별 확인 |
| partial_failed | 실패와 다른 결과가 섞임. 학기별 확인 |
| failed | 전체 실패 또는 중단 복구. 이미 커밋된 학기는 results에서 별도 확인 |

학기 status는 pending/running/completed/unpublished/failed다.
Python의 lease는 DB UTC 기준 5분이다. 프로세스 중단 직후에는 남은 lease 동안 409일 수 있다.
만료 후 다음 POST/GET status에서 중단 이력을 복구한다. 늦게 돌아온 이전 실행은 저장 권한을 잃는다.
상태 조회는 만료된 이력을 복구할 수 있으므로 순수 메모리 조회가 아니다. 실제 MySQL 동시성/중단 복구는 별도 검증 대상이다.

## 환경 설정

```properties
ACADEMIC_CALENDAR_SCHEDULER_ENABLED=true
ACADEMIC_CALENDAR_CRAWLER_BASE_URL=http://127.0.0.1:8000
ACADEMIC_ADDITIONAL_HOLIDAYS=
```

추가 필수 Spring 설정은 없다. ACADEMIC_HOLIDAY_SERVICE_KEY는 이전 설계의 설정이며 현재 사용하지 않는다.
공휴일/설·추석/대체공휴일은 Jollyday로 양력 날짜를 계산한다. 새 임시공휴일은 추가 날짜 설정 후 재시작한다.
지원 범위 2026~2035와 이후 연도 갱신 필요 조건은 기존 문서를 따른다.

Python의 DB_CONFIG는 crawler/config.py가 관리한다. 환경변수만 바꾸면 대상 DB가 바뀐다고 가정하지 않는다.
Spring datasource와 Python의 실제 host/schema/계정 권한을 안전하게 대조하되 비밀번호를 로그/문서/셸 이력에 복사하지 않는다.
테스트/운영을 분리한다. 다중 인스턴스 수, systemd override, 실제 포트와 DB 환경은 이번 작업에서 검증하지 않았다.

## 향후 적용 순서 — 이번에는 실행하지 않음

1. 각 환경의 DB 백업과 대상 확인. Spring 미병합 변경을 검토하고 배포본에 포함될 코드/0005 SQL/등록 파일을 확정한다.
2. 대상 MySQL 8에 Spring docs/0005_academic_calendar.sql을 환경별 마이그레이션 이력 정책에 따라 적용한다. 기존 SQL을 수정하거나 정상 데이터를 삭제하지 않는다.
3. SHOW CREATE TABLE school_calendar_events와 SHOW INDEX FROM school_calendar_events로 InnoDB, DATE/DATETIME(6), BIT(1), 컬럼 길이, 유일 키 순서를 확인한다. 기존 MANUAL 행과 ID/created_at 보존도 확인한다.
4. 검토한 Python 커밋과 requirements.txt를 준비한다. 같은 대상 DB로 설정된 상태에서 아래 이력 마이그레이션을 명시적으로 실행한다.

   ```bash
   python -m crawler.academic_calendar.migrate
   ```

5. academic_calendar_crawl_runs, academic_calendar_crawl_lock의 InnoDB/타입/기본 키와 id=1 잠금 행을 확인한다. Spring에서 이력 표를 새 규격으로 생성/초기화하지 않는다.
6. Python API를 재시작한다. 코드상 진입점은 python -m uvicorn api_main:app --host 127.0.0.1 --port 8000이다. 실제 서비스 명령/override를 확인한다. scripts/deploy.sh의 학식 마이그레이션만으로는 학사 이력이 준비되지 않는다.
7. Spring 주소/공휴일 설정 후 승인된 환경에 배포한다. Python이 준비되기 전 호출을 피하려면 준비 단계에서 scheduler-enabled=false로 둔다. 활성화 시점은 운영 담당자가 결정한다.
8. ADMIN sync 한 번 호출 → runId로 영구 상태 조회 → 학기별 결과/보존 사유 → 실제 MySQL 저장 순으로 확인한다. ACCEPTED만으로 완료 처리하지 않는다.
9. 비로그인 학교·통합 캘린더, 로그인 본인 일정, 메인 D-day, 학기 중/방학 알림 생성·발송, 기기별 수신 설정을 확인한다.
10. 매일 04:00 KST 실행 로그/이력과 중복 요청 409 처리를 관찰한다. 실패 시 기존 데이터를 유지하고 상태/스키마/로그를 확인한 뒤 재실행 시점을 결정한다.

Python 이력 마이그레이션 계정에는 CREATE, 실행 계정에는 이력 SELECT/INSERT/UPDATE 및 학교 일정 SELECT/INSERT/UPDATE 권한이 필요하다.
Spring 서버 계정의 기존 권한과 배포 자동화는 임의로 변경하지 않았다.
DDL 실패 시 자동 롤백을 가정하지 않는다. 문제 발생 시 수집 스케줄을 중지하고 기존 데이터/이력을 보존한 상태로 복구한다.

## 격리 테스트 재현

기존 Python .venv-test의 requirements-dev.txt 의존성을 사용한다. 아래 변수는 테스트 JVM에만 전달하며 운영 Spring 설정이 아니다.

```powershell
$env:ACADEMIC_CALENDAR_TEST_PYTHON='C:\Users\박찬우\Desktop\LikeLionHSU\hanseo-mate-crawler\.venv-test\Scripts\python.exe'
$env:ACADEMIC_CALENDAR_TEST_CRAWLER_ROOT='C:\Users\박찬우\Desktop\LikeLionHSU\hanseo-mate-crawler'
.\gradlew.bat test --tests 'hsu.hanseomate.domain.academiccalendar.*' --rerun-tasks --no-daemon
```

PythonCrawlerContractIntegrationTest는 위 Python 변수가 없으면 skip한다. 코드와 HTML fixture가 있는 명시적 크롤러 경로가 필요하다.
테스트 helper는 config를 읽은 뒤 DB_CONFIG를 무해한 로컬 값으로 교체하고 모든 MySQL 실행을 차단한다.
운영 DB로 fallback하지 않는다. Python 바이트코드 저장을 끄고 임시 파일은 Spring 테스트 폴더에 둔다.
실제 학교 요청도 차단하고 지정 커밋의 HTML fixture만 사용한다.

- 실제 Spring RestClient → Python FastAPI → 실제 parser/service/repository → 임시 SQLite 저장/영구 상태 조회를 확인한다.
- 34/33건, 원본 ID 중복 제거, 두 번째 실행 unchanged, ID/created_at 유지, 수기 보존, 과거 OTHER 비활성화를 확인한다.
- Python의 저장 행을 JSON으로 내보내 H2에 명시적으로 옮긴 뒤 Spring 공개 조회/D-day를 검증한다.
- **SQLite→JSON→H2는 테스트용 연결이며, 동일 MySQL에 두 런타임이 접속한 검증이 아니다.**
- 0005 SQL을 SQLite/H2에 적용했다고 주장하지 않는다. MySQL SQL·BIT(1)·InnoDB·동시성은 Docker/격리 MySQL이 필요하다.

Python 전체 테스트 재실행도 helper --unit 모드로 tests 폴더를 작업 디렉터리에 복사하여 수행한다.
운영 DB URL 환경변수를 제거하고 MySQL 연결을 차단한다. 원본 Python tests 폴더에 임시 DB를 쓰지 않는다.

## 이번 검증 결과

2026-11-04에 이번 코드로 새로 실행했다. 이전 결과를 이번 연동 검증으로 재사용하지 않았다.

| 검증 | 결과 |
|---|---|
| Spring 관련 회귀 테스트 | 155개 중 154개 통과, 실패/오류 0, MySQL 마이그레이션 1개 skip |
| Python 전체 테스트 | 62개 중 59개 통과, 실패/오류 0, MySQL 전용 3개 skip |
| 실제 Spring→Python HTTP 계약 테스트 | 1개 실행·통과. opt-in 테스트가 skip된 결과가 아님 |
| HTTP 오류 회귀 | 18개 통과. 202/200/201, 잘못된 JSON/status/run_id, 409/422/503/500, 연결 실패, 실제 15초 읽기 타임아웃 포함 |
| 실행 JAR | compileJava 및 bootJar 성공 |
| 변경 검사 | git diff --check 통과 |
| SQL 사본 | Python MySQL fixture와 Spring 0005 SQL의 내용 일치(파일 끝 개행·공백 정규화) |

호환성 검토에서는 새 SQL이나 Python 이력 테이블을 추가하지 않았다.
릴리스 준비 시 아직 커밋·적용되지 않은 신규 0005 SQL의 마지막 빈 줄만 정리했으며 SQL 문장과 DB 변경 내용은 동일하다.

Spring 실행 범위: academiccalendar.*, schoolcalendar.*, calendar.*, home.*, NotificationPreferenceIntegrationTest,
NotificationSendWorkerTest, ShuttleReservationReminderWorkerTest, timetable.reminder.*와 bootJar.
전체 프로젝트의 모든 테스트를 실행한 결과로 표현하지 않는다.

실제 Python HTTP 검증에서 첫 실행 결과 34/33건, 두 번째 실행 unchanged 34/33건,
ID와 created_at 유지, MANUAL 보존, 오래된 OTHER 비활성화를 확인했다.
Python 저장 69행(원본 67+수기 1+비활성 1)을 H2로 명시적으로 옮겨 공개 통합 68건과
1학기 필터 34건, 공식 개시일과 계산 개강일의 분리, 시험 종료일 D-DAY,
다음 날 FINAL 전환, 방학 당일 종강 0/알림 차단, 겨울방학 다음 개강 목표를 확인했다.
실제 MySQL 공유 저장 검증은 아니다.

Python 전체 테스트 재현:

```powershell
& $env:ACADEMIC_CALENDAR_TEST_PYTHON -B src/test/resources/academic-calendar/python_fixture_server.py $env:ACADEMIC_CALENDAR_TEST_CRAWLER_ROOT build/crawler-contract-unit --unit
```

Spring 보고서: build/reports/tests/test/index.html, build/test-results/test/TEST-*.xml.
기존 Python 저장소의 IDE/첨부 변경은 그대로 유지했다.

## 미검증 범위

- 실제 MySQL 8에서 0005 및 Python 이력 migration, BIT(1), InnoDB 트랜잭션/다중 연결/lease, 커밋 응답 유실.
- 실제 Spring→Python→공유 MySQL 전체 연결과 운영 설정 일치.
- 실제 학교 페이지 재조회/운영 저장, 실제 서버 재시작·배포·04시 실행, 실기기 푸시.
- 호환성 검토 단계에서는 운영/테스트 DB와 서버에 어떠한 변경도 실행하지 않았다. Docker 엔진 pipe가 없어 실제 MySQL 테스트는 실행할 수 없었다.

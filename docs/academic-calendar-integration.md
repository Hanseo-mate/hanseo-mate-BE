# 학사일정 수집 연동과 메인 D-day

기준: main ec1548e. Spring 구현과 별도 Python 저장소의 연동 계약이다. Python 구현은 cea8f7d 기준으로 검토했으며 실제 환경 배포·공유 MySQL 적용은 별도 단계다.

2026-11-04 Python cea8f7d와 실제 구현을 대조했다. 호환성 검토, 격리 연동 검증 범위,
상태 조회 및 운영 적용 절차는 [Python 호환성 검토 및 적용 절차](academic-calendar-python-compatibility.md)를 따른다.

## 확정 규칙

- 1학기 개강: 3월 첫 토·일·공휴일 아닌 날. 2학기: 9월의 같은 기준.
- 공식 학기개시일은 공개 캘린더 원본으로만 사용한다. D-day/학기 시작 기준을 덮어쓰지 않는다.
- 날짜 계산과 API 응답은 모두 양력이다. Jollyday 2.24.0의 한국 공휴일 데이터를 사용해 설·추석·대체공휴일도 해당 양력 날짜로 제외한다. 공휴일 API 키나 실행 중 외부 공휴일 조회는 필요 없다.
- 라이브러리에 아직 반영되지 않은 임시공휴일은 ACADEMIC_ADDITIONAL_HOLIDAYS에 양력 날짜를 추가하고 서버를 재시작해 반영한다. 새 임시공휴일이 자동으로 내려오는 방식은 아니다.
- 종강 D-day 목표일은 하계/동계방학 시작일 **당일**이다.
- 개강 당일 SEMESTER_START 0, 다음 날부터 SEMESTER_END 카운트. 방학 시작 당일 SEMESTER_END 0, 다음 날부터 다음 개강 카운트.
- 시험 시작~종료일(양끝 포함)은 계속 daysRemaining=0. 중간고사 종료 다음 날 기말고사 카운트, 기말고사 종료 다음 날부터 다음 개강 전날은 examDday=null.
- 학기 중 알림 허용 기간: 계산 개강일 이상, 방학 시작일 미만. 방학 당일 종강 D-day 표시와 알림 차단은 함께 적용된다.
- 시간 기준은 Asia/Seoul. 계절학기는 정규학기 기간을 연장하지 않는다.
- 날짜/공휴일/필수 일정이 미확인 또는 모호하면 UNKNOWN/null, 알림 생성·발송을 보류한다. 데이터 미확인을 방학으로 가장하지 않는다.
- D-day와 알림은 라이브러리+추가 날짜 설정으로 직접 계산한다. DB 공휴일 스냅샷은 확인용 기록으로 유지하며, 비어 있거나 오래되어도 개강일 계산에 영향을 주지 않는다.
- 공휴일 계산 지원 연도는 2026~2035다. 고정한 라이브러리의 한국 음력 공휴일 날짜가 2035년까지만 수록되어 있어, 이후 연도는 UNKNOWN/null로 처리하고 학기 알림을 보류한다. 2035년 겨울의 다음 개강 계산 전까지 라이브러리와 지원 범위를 갱신·검증해야 한다. 학교 일정 저장·공개 범위와 공휴일 계산 지원 범위는 별개다.

## 공개 API

기존 경로와 기존 필드는 유지된다.

### GET /api/calendars/school

로그인 불필요. 토큰 없이도 모든 공개 학교 일정 조회 가능.
선택 query: academicYear (2026~9998), semester (1 또는 2).
미지정이면 모든 학년도와 관리자 수기 일정 포함. 필터를 지정하면 해당 메타데이터가 있는 일정만 반환한다.
기존 수기 일정은 학년도/학기가 null이므로 필터 조회에는 포함되지 않는다.
inactive 수집 일정은 숨긴다. 날짜순 반환.

```json
[{
  "id": 123,
  "startDate": "2026-10-20",
  "endDate": "2026-10-26",
  "title": "2학기 중간고사",
  "academicYear": 2026,
  "semester": 2,
  "source": "HANSEO",
  "eventType": "MIDTERM"
}]
```

GET /api/calendars/all에도 수집 학교 일정이 자동 포함된다.
비로그인: 학교+학생회. 로그인: 학교+학생회+본인 개인 일정.
통합 응답의 기존 calendarType=SCHOOL 계약 유지. 학년도 필터는 학교 일정 API에서 제공한다.

관리자 수기 일정 CRUD는 기존대로 사용한다. HANSEO 원본 행의 관리자 PUT/DELETE는 400으로 거절한다.
학교 원본 변경은 Python 동기화에서 반영한다.

### GET /api/home

로그인 여부와 무관하게 다음 세 필드를 추가한다.
- academicStatus: IN_SEMESTER / VACATION / UNKNOWN
- semesterDday: SEMESTER_START / SEMESTER_END 또는 null
- examDday: MIDTERM / FINAL 또는 null

D-day 객체:
```json
{
  "type": "MIDTERM",
  "targetDate": "2026-10-20",
  "endDate": "2026-10-26",
  "daysRemaining": 0
}
```

targetDate는 시험 시작일이며 종료일까지 daysRemaining=0이다.
프론트는 0을 D-DAY로 표시한다. 방학에는 examDday=null.
학교 종강/시험 일정이 없으면 해당 D-day가 null이다. 공휴일 지원 범위 밖의 연도를 평일로 가정하지 않는다.

## Spring 실행 및 설정

```properties
ACADEMIC_CALENDAR_SCHEDULER_ENABLED=true
ACADEMIC_CALENDAR_CRAWLER_BASE_URL=http://127.0.0.1:8000
ACADEMIC_ADDITIONAL_HOLIDAYS=
```

- 매일 04:00 Asia/Seoul: 한국 시간의 현재 달력연도만 Python에 요청.
- 2026년에는 2026년 1·2학기, 2027년에는 2027년 1·2학기. 전년도 재수집/다음 학년도 선조회 없음.
- 첫 정기 실행까지 기다리지 않고 학교 수집을 요청하려면 ADMIN JWT로 POST /api/admin/academic-calendar/sync. 공휴일 계산 자체는 서버 시작 직후부터 가능하다.
- 학교 수집과 별도로 올해 3월/9월, 다음 해 3월의 공휴일 계산 결과를 DB에 기록한다. 다음 해 3월은 겨울방학 개강 D-day 계산에 필요하며 다음 학년도 학교 크롤링을 요청하는 것은 아니다.
- Python 통신 연결 제한 5초/읽기 제한 15초. 공휴일은 네트워크 요청 없이 계산하며, 한 월의 기록 실패가 다른 월이나 학교 수집 요청을 막지 않는다.
- 실행 응답 crawlStatus=ACCEPTED, ALREADY_RUNNING, FAILED. ACCEPTED와 runId는 Python의 접수 확인이며 DB 저장 완료가 아니다.
- Python HTTP 202와 status=starting, 문자열 run_id를 모두 검증한다. HTTP 200/201이나 숫자·빈 run_id는 접수 성공으로 취급하지 않는다.
- 관리자 sync 응답에 failureReason을 추가한다. 정상 접수/이미 실행 중이면 null이며 실패 시 INVALID_REQUEST(422), CRAWLER_UNAVAILABLE(503), REQUEST_TIMEOUT, CONNECTION_ERROR, HTTP_ERROR, INVALID_RESPONSE_OR_CLIENT_ERROR 중 하나다. crawlStatus와 기존 필드는 유지한다.
- 409와 타임아웃을 포함해 요청의 즉시 자동 재시도는 하지 않는다. 타임아웃은 작업 미접수를 보장하지 않으므로 Python의 영구 상태를 확인한다.
- failedHolidayMonths에 지원 범위 밖이거나 DB 기록에 실패한 월을 반환한다. 스냅샷 DB 저장 실패만으로 개강일 계산이 중단되지는 않는다.
- 기존 ACADEMIC_HOLIDAY_SERVICE_KEY와 공휴일 API URL 설정은 사용하지 않는다.
- 의존성: [Jollyday](https://github.com/focus-shift/jollyday), [한국 공휴일 정의](https://github.com/focus-shift/jollyday/blob/main/jollyday-core/src/main/resources/holidays/Holidays_kr.xml). 빌드할 때 의존성을 내려받으며 실행 시에는 내장 데이터를 사용한다.
- 학교 Python HTTP/DB 연동 검증은 별도 필요하다.

추가 공휴일은 쉼표로 구분한 YYYY-MM-DD 형식이다. 값이 비어 있으면 내장 데이터만 사용하고, 공백·중복은 허용하며 잘못된 날짜는 서버 시작 시 오류로 알린다.
예를 들어 **가상의 임시공휴일**로 2026-09-01을 지정한다면 ACADEMIC_ADDITIONAL_HOLIDAYS=2026-09-01 설정 후 재시작 시 2학기 개강일이 2026-09-02로 이동한다. 실제 지정 여부를 확인하고 입력해야 한다.
이 설정은 계산용이며 학교 공개 캘린더에 임시공휴일 일정을 새로 생성하지 않는다. 테스트·운영 환경별로 각각 설정한다.

## Python이 구현할 HTTP 계약

POST /academic-calendar-crawl/run
```json
{"academic_year": 2026, "semesters": [1, 2], "mode": "background"}
```
빠르게 백그라운드 작업 접수:
```json
{"status": "starting", "run_id": "unique-run-id"}
```
실행 중이면 HTTP 409. 완료 여부/성공 건수/미게시 여부는 Python의 영구 실행 이력으로 기록한다.
Spring이 응답을 받아 다시 저장하지 않는다. Python이 같은 환경의 MySQL에 직접 저장한다.
테스트/운영 DB를 반드시 분리한다.

Python 상태 조회: GET /academic-calendar-crawl/status?run_id=<runId>.
run_id를 생략하면 최신 실행이며, 이력이 없으면 status=idle, run_id=null이다.
지정 ID가 없으면 404, 이력 DB/스키마 장애는 503이다. Spring의 상태 조회 프록시 API는 추가하지 않았다.
Python 내부 API는 서버 내부에서 조회하며 일반 사용자 앱에서 직접 호출하지 않는다.
최상위 상태뿐 아니라 results의 학기별 상태·건수·error·preservation_reason을 함께 확인한다.

## Python 수집·저장 규칙

원본: https://www.hanseo.ac.kr/schdList.do?m=0405&s=hs
조회: schdYear=요청학년도, section=1 또는 2.
월별 행사 목록을 파싱한다. li의 date... ID와 제목, 월 영역, 날짜 문자열을 사용한다.
1학기 3~8월은 해당 연도, 2학기 9~12월은 해당 연도, 1~2월은 다음 연도.
단일 날짜는 시작=종료. 월을 넘는 범위/12월→1월 범위를 처리한다.
PC/모바일 중복 항목은 원본 ID로 제거한다. 같은 ID의 서로 다른 내용은 실패로 처리한다.
source_event_id에는 date 접두사를 포함한 원본 문자열을 저장한다.

school_calendar_events 신규 컬럼:
| 컬럼 | 규격 |
|---|---|
| source | HANSEO, 기존 관리자 일정은 MANUAL |
| source_event_id | 원본 ID, HANSEO에는 필수 |
| academic_year | 페이지 조회 학년도 |
| semester | 페이지 조회 학기 1/2 |
| event_type | OTHER / SEMESTER_START / MIDTERM / FINAL / VACATION_START |
| source_url | 조회한 원본 URL |
| last_synced_at | 마지막 확인 UTC DATETIME(6) |
| active | 공개 여부, 기본 1 |

기존 start_date/end_date/title/created_at/updated_at도 채워야 한다.
created_at/updated_at/last_synced_at는 UTC, 날짜는 학교 현지 날짜. 제목은 500자 이하.
고유 키는 (source,academic_year,semester,source_event_id). 새 ID 삽입, 같은 키는 날짜/제목/유형 갱신.
기존 id와 created_at은 갱신 시 유지한다. 동일 결과의 재수집은 중복을 만들지 않는다.

유형 판별:
- '학기개시일 30일/60일/90일'을 개강으로 분류하지 않는다.
- '중간 강의평가'를 MIDTERM으로 분류하지 않는다.
- 중간고사/기말고사만 각각 MIDTERM/FINAL.
- 하계방학/동계방학 시작 일정만 VACATION_START.
- 계절학기, 계절학기 수강신청, 수강신청, 공휴일 등은 OTHER로 보존.
- 2학기 페이지에 다음 1학기 수강신청이 있어도 페이지 범위 metadata는 변경하지 않는다.
- 학기 중 시험/방학의 종류별 중복·상충이 있으면 Python에서 검증 실패로 기록한다.

학기별 전체 응답을 메모리에서 검증한 뒤 트랜잭션으로 반영한다.
HTTP/파싱 실패·미게시 빈 목록·일부 월 누락 시 기존 정상 일정은 보존한다.
정상적인 완전 스냅샷에서 제거가 확인된 HANSEO 해당 학년도/학기 행만 active=0 처리한다.
MANUAL/다른 학년도/다른 학기/개인·학생회 일정을 변경하지 않는다.
모호한 삭제는 보존하고 기록한다. 신규 번호로 전체 재등록하거나 테이블 전체 삭제하지 않는다.

## 알림 연결

TimetableReminderQueryService: 실제 수업 날짜의 activeTerm으로 연도/학기 선택.
TimetableReminderEnqueueService: 트랜잭션 안에서 재확인.
ShuttleReservationReminderWorker: 학기 중에만 수요일 17:50 기존 예매 알림 생성.
NotificationSendWorker: payload.startsAt를 한국 날짜로 변환해 실제 발송 직전 재확인.
차단된 Outbox는 EXPIRED 처리. 이미 생성된 알림함 기록은 회수하지 않는다.
기존 수신 대상·기기 설정·카테고리 ON/OFF·중복 방지·만료 처리는 유지한다.
이 학기 조건은 class_start_reminder와 shuttle_reservation에만 적용한다.
공휴일 데이터는 개강일 계산에 쓰이며 학기 중 모든 공휴일의 수업을 자동 휴강 처리하는 기능은 포함하지 않는다.

## 적용 순서 및 검증

1. docs/0005_academic_calendar.sql 적용 (deploy-migrations.txt에 등록됨).
2. Python cea8f7d 이상에서 동일 계약을 확인하고 의존성을 준비한다.
3. 같은 대상 DB를 사용하는 Python 설정으로 python -m crawler.academic_calendar.migrate 실행. Python 이력 테이블 2개는 Python이 관리하며 Spring에서 생성하지 않는다.
4. Python API 재시작. 현재 scripts/deploy.sh는 학사일정 이력 마이그레이션을 자동 실행하지 않는다.
5. Spring 설정에 크롤러 주소 등록. 별도로 지정된 임시공휴일이 있을 때만 ACADEMIC_ADDITIONAL_HOLIDAYS 설정.
6. 관리자 sync 후 runId로 Python 상태 조회. 학기별 완료 이력·실제 DB 저장·공휴일 스냅샷 확인.
7. 공개 캘린더 → 메인 D-day → 학기 중/방학 알림 생성과 실제 발송 순서로 확인.
8. 일반 Spring 배포 후 매일 04시 실행 관찰.

SQL은 기존 일정을 보존하며 반복 실행 가능하도록 작성했다.
학교 시험/방학 데이터가 없는 상태로 배포하면 D-day가 null/UNKNOWN일 수 있고 두 알림이 보류된다. 공휴일 DB 스냅샷을 미리 넣을 필요는 없다.
빌드/테스트 성공과 운영 DB 적용·Python 수집·실기기 푸시 성공은 별도로 검증한다.


## 공휴일 변경 시 로컬 검증 (2026-11-04, Python 계약 검토 이전)

관련 테스트 137개 중 136개 통과, 실패/오류 0개, Docker 미제공으로 MySQL 마이그레이션 테스트 1개 건너뜀. compileJava 및 bootJar 성공, git diff --check 통과.
Jollyday의 실제 한국 데이터를 사용해 삼일절 대체공휴일, 설·추석의 양력 날짜, 주말 경계를 검증했다. Spring 추가 공휴일 설정이 개강 D-day와 두 알림 허용일에 함께 적용되고, 비어 있거나 오래된 DB 스냅샷이 계산을 바꾸지 않는 것도 확인했다.
Python HTTP 계약 테스트는 로컬 모의 서버를 사용했다. 실제 Python 학사일정 수집, 운영 DB 마이그레이션, 실기기 알림은 실행하지 않았다.
이번 공휴일 API 제거에는 별도 SQL이 필요 없다. 학사일정 기능 전체를 처음 적용할 때는 기존에 작성한 0005_academic_calendar.sql이 필요하다.

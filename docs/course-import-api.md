# 강좌 수입·조회 API 명세서

## 1. 호출 구조

```text
관리자 프론트 → Excel multipart → FastAPI parser
FastAPI → TimetableParseResult 1.2 JSON → Spring Boot → MySQL
사용자 앱 → 강좌 필터 GET → Spring Boot
```

Spring은 Excel이나 multipart를 받지 않으며 FastAPI를 호출하지도 않는다. 내부 수입 API는 FastAPI가 완성한 JSON만 검증하고 학기 스냅샷으로 저장한다.

---

## 2. 요청 헤더

현재 로그인과 권한 기능을 구현하지 않았으므로 두 강좌 수입 API에는 인증 검사가 없다. 아래 데이터 계약용 헤더만 필요하다.

| 헤더 | 값 |
|---|---|
| `X-IMPORT-ID` | body의 `importId`와 동일한 값 |
| `X-PARSER-SCHEMA-VERSION` | `1.2` |
| `Idempotency-Key` | `{academicYear}:{semester}:{curriculumType}:{fileSha256}` |

---

## 3. 전공 강좌 수입

```http
POST /api/internal/course-imports/major
Content-Type: application/json
```

`curriculumType`은 반드시 `MAJOR`여야 한다.

## 4. 교양 강좌 수입

```http
POST /api/internal/course-imports/general-education
Content-Type: application/json
```

`curriculumType`은 반드시 `GENERAL_EDUCATION`이어야 한다.

---

## 5. 수입 요청

최상위 필드:

| 필드 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `schemaVersion` | string | O | `1.2` |
| `importId` | string | O | 수입 요청 식별자 |
| `fileName` | string | O | 원본 파일명 |
| `fileSha256` | string | O | 64자리 SHA-256 |
| `curriculumType` | enum | O | `MAJOR`, `GENERAL_EDUCATION` |
| `parserVersion` | string | O | FastAPI parser 버전 |
| `academicYear` | integer | O | 2000~2100 |
| `semester` | integer | O | 1 또는 2 |
| `displayName` | string | O | 화면 표시용 학기명 |
| `status` | enum | O | `READY`, `REVIEW_REQUIRED`, `UNSUPPORTED_FORMAT` |
| `confidence` | number | O | 0~1 |
| `statistics` | object | O | 파싱 통계 |
| `academicUnits` | array | O | 파일에서 발견한 학과·전공 |
| `generalCategories` | array | O | 실제 강좌가 사용하는 교양 context |
| `generalCategoryNodes` | array | O | 강좌 0개 제목까지 포함한 전체 교양 분류 트리 |
| `lectures` | array | O | 저장할 강좌 목록 |
| `issues` | array | O | parser warning/error |

강좌 예시:

```json
{
  "sourceSheet": "전공",
  "sourceRow": 5,
  "curriculumType": "MAJOR",
  "academicUnit": {
    "originalName": "항공소프트웨어공학과",
    "departmentName": "항공소프트웨어공학과",
    "majorName": null
  },
  "generalEducation": null,
  "courseCode": "001234",
  "courseName": "웹프로그래밍",
  "sectionNo": "01",
  "credit": 3,
  "classHours": 3,
  "instructorName": "홍길동",
  "targetGrade": 2,
  "commonGrade": false,
  "allowedGrades": [2],
  "eligibleDepartmentNames": ["항공소프트웨어공학과"],
  "teamTeaching": null,
  "note": null,
  "eligibilityNote": null,
  "scheduleText": "월1,2,3",
  "classroomText": "본관 101호",
  "schedules": [
    {
      "dayOfWeek": "MONDAY",
      "periods": [1, 2, 3],
      "classroom": {
        "campusCode": "TAEAN",
        "buildingName": "본관",
        "roomNumber": "101",
        "originalValue": "본관 101호"
      }
    }
  ],
  "sourceCells": [
    {
      "columnIndex": 1,
      "headerName": "학수번호",
      "canonicalField": "courseCode",
      "value": "001234"
    },
    {
      "columnIndex": 4,
      "headerName": "미인식 미래 열",
      "canonicalField": null,
      "value": null
    }
  ]
}
```

`courseCode`, `courseName`, `teamTeaching`은 nullable이다. 과목코드와 분반은 숫자가 아닌 문자열로 보내 선행 0을 보존해야 한다. `sourceCells`는 미인식 열과 빈 셀도 원본 순서로 포함한다.

---

## 6. 수입 정상 응답

응답은 항상 아래 다섯 필드만 사용한다.

```json
{
  "importId": "요청과 동일한 importId",
  "storageStatus": "STORED",
  "databaseChanged": true,
  "offeringCount": 918,
  "message": "2026학년도 1학기 전공 강좌 저장 완료"
}
```

| `storageStatus` | DB 변경 | 설명 |
|---|---:|---|
| `STORED` | O | 동일 학기·교육과정 범위를 트랜잭션으로 교체 |
| `REVIEW_REQUIRED` | X | 오류·검토 상태이므로 기존 스냅샷 유지 |
| `DUPLICATE` | X | 동일 성공 파일을 이미 처리하여 멱등 응답 |

`status != READY`, `statistics.errorCount > 0`, 1~30 범위를 벗어난 교시, 중복 교시, 학과/교양 context 누락, 강좌 수 불일치, 끊어진·순환 분류 트리, 차단 issue가 있으면 `REVIEW_REQUIRED`이다.

---

## 7. 강좌 조회

```http
GET /api/courses
```

인증 없이 조회할 수 있다.

선택 필터:

| Query parameter | 설명 |
|---|---|
| `academicYear` | 학년도 |
| `semester` | 학기 |
| `curriculumType` | `MAJOR`, `GENERAL_EDUCATION` |
| `academicUnit` | 학과·전공명 부분 일치 |
| `classification` | `REQUIRED`, `ELECTIVE`, `UNKNOWN` |
| `area` | `EXPLORATION`, `COEXISTENCE`, `INITIATIVE`, `OTHER` |
| `deliveryProvider` | `ON_CAMPUS`, `E_CLASS`, `HSU_CYBER`, `OCU`, `CHUNGNAM_ELEARNING`, `SDU`, `OTHER` |
| `courseName` | 과목명 부분 일치 |
| `instructorName` | 교수명 부분 일치 |

예시:

```http
GET /api/courses?academicYear=2026&semester=1&curriculumType=MAJOR&academicUnit=항공소프트웨어&courseName=웹
```

응답 예시:

```json
[
  {
    "offeringId": "UUID",
    "courseCode": "001234",
    "courseName": "웹프로그래밍",
    "sectionNo": "01",
    "credit": 3.000,
    "instructorName": "홍길동",
    "curriculumType": "MAJOR",
    "academicUnit": {
      "originalName": "항공소프트웨어공학과",
      "departmentName": "항공소프트웨어공학과",
      "majorName": null
    },
    "generalEducation": null,
    "targetGrade": 2,
    "commonGrade": false,
    "schedules": [
      {
        "dayOfWeek": "MONDAY",
        "periods": [1, 2, 3],
        "classroom": {
          "campusCode": "TAEAN",
          "buildingName": "본관",
          "roomNumber": "101",
          "originalValue": "본관 101호"
        }
      }
    ]
  }
]
```

수입 원본 JSON, 파일 hash, `sourceCells`, import 이력은 공개 조회 응답에 포함하지 않는다.

---

## 8. 오류 응답

```json
{
  "status": 422,
  "message": "X-IMPORT-ID와 body의 importId가 일치하지 않습니다.",
  "path": "/api/internal/course-imports/major",
  "timestamp": "2026-07-19T01:00:00Z"
}
```

| HTTP | 상황 |
|---:|---|
| 400 | JSON 타입·Bean Validation·필수 헤더 형식 오류 |
| 422 | importId/schema/path/idempotency 계약 불일치 |
| 500 | 저장 중 예기치 못한 오류. 전체 트랜잭션 rollback |

---

## 9. 스냅샷과 멱등 범위

교체 범위:

```text
academicYear + semester + curriculumType
```

- `2026-1 MAJOR` 재수입은 같은 범위 전공만 교체한다.
- 같은 학기 교양과 다른 학기는 유지한다.
- 학과·과목·강의실 master는 물리 삭제하지 않는다.
- 교양 taxonomy는 학기 스냅샷이므로 새 파일에 없는 OCU는 해당 학기에서 사라지고 새 SDU만 노출될 수 있다.
- 성공한 동일 `Idempotency-Key` 재요청은 `DUPLICATE`이며 DB를 변경하지 않는다.
- 원본 전체 JSON과 강좌별 `sourceCells`, `scheduleText`, `classroomText`를 보존한다.
- 전체 원본 JSON은 `course_import_histories.raw_payload_json`의 MySQL `LONGTEXT`에 저장한다.

Flyway는 사용하지 않는다. 로컬·테스트 스키마는 기존 프로젝트 정책대로 JPA가 관리하고, 운영은 `ddl-auto=validate`이므로 배포 전에 운영 DB 스키마를 별도로 반영해야 한다.

기존 MySQL 스키마에서 `course_import_issues.row_number`를 사용하고 있다면 MySQL 예약어 충돌을 피하도록 `issue_row_number`로 변경해야 한다.

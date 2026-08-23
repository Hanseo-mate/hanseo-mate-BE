# 강좌 엑셀 수입·조회 API 명세서

## 1. 처리 구조

```text
관리자 페이지
→ Excel multipart 업로드
→ Spring Boot(Apache POI로 분석·검토)
→ Spring Data JPA
→ MySQL 공통 과목 + 학기별 Offering 매핑 저장

사용자 앱
→ Spring Boot 강좌 조회 API
```

Spring Boot가 엑셀 업로드부터 파싱, 검토, 트랜잭션 저장까지 전부 처리한다.

전공·교양 엑셀 수입 API는 모두 관리자 전용이다. `ADMIN` 역할이 포함된 유효한
Bearer JWT가 필요하며, 토큰이 없으면 `401`, 일반 사용자이면 `403`을 반환한다.

---

## 2. 지원 파일

| 항목 | 값 |
|---|---|
| 확장자 | `.xlsx`, `.xlsm` |
| 최대 파일 크기 | 10MiB(10,485,760 bytes) |
| Multipart 필드명 | `file` |
| 최대 시트 수 | 20 |
| 최대 워크북 셀 범위 | 500,000 |

파일 내부 또는 파일명에서 학년도와 학기를 찾을 수 있어야 한다. 전공 API에는 전공 시간표를, 교양 API에는 교양 시간표를 업로드해야 한다.

---

## 3. 전공 강좌 엑셀 수입

```http
POST /api/v1/timetables/major
Authorization: Bearer {ADMIN_ACCESS_TOKEN}
Content-Type: multipart/form-data
```

### Postman 설정

1. Method를 `POST`로 선택한다.
2. URL에 `http://localhost:8080/api/v1/timetables/major`를 입력한다.
3. `Authorization`에서 `Bearer Token`을 선택하고 관리자 Access Token을 입력한다.
4. `Body` → `form-data`를 선택한다.
5. Key를 `file`, 타입을 `File`로 변경한다.
6. 전공 시간표 `.xlsx` 또는 `.xlsm` 파일을 선택해 전송한다.

별도 JSON body와 `X-IMPORT-ID`, `X-PARSER-SCHEMA-VERSION`, `Idempotency-Key` 헤더는 필요하지 않다.

---

## 4. 교양 강좌 엑셀 수입

```http
POST /api/v1/timetables/general-education
Authorization: Bearer {ADMIN_ACCESS_TOKEN}
Content-Type: multipart/form-data
```

Postman 설정은 전공과 같고, `file`에 교양 시간표를 선택한다.

---

## 5. 수입 응답

수입 API의 최종 응답은 아래 여섯 필드만 사용한다.

| 필드 | 타입 | 설명 |
|---|---|---|
| `importId` | string | 서버가 생성한 수입 작업 식별자 |
| `storageStatus` | enum | `STORED`, `REVIEW_REQUIRED`, `DUPLICATE` |
| `databaseChanged` | boolean | 이번 요청으로 강좌 데이터가 변경됐는지 여부 |
| `offeringCount` | integer | `STORED`·`DUPLICATE`이면 대상 강좌 수, `REVIEW_REQUIRED`이면 `0` |
| `message` | string | 최종 처리 결과 |
| `reviewIssues` | array | 저장을 막은 검토 항목. 정상·중복이면 빈 배열 |

### 저장 완료

```json
{
  "importId": "a3f04b56-21b8-4da3-b6dd-2e6fd0325358",
  "storageStatus": "STORED",
  "databaseChanged": true,
  "offeringCount": 918,
  "message": "2026학년도 1학기 전공 강좌 저장 완료",
  "reviewIssues": []
}
```

### 검토 필요로 미저장

```json
{
  "importId": "ec44eefd-75e4-4cd0-8c1a-a92f03ebd772",
  "storageStatus": "REVIEW_REQUIRED",
  "databaseChanged": false,
  "offeringCount": 0,
  "message": "검토가 필요한 항목이 1개 있어 저장하지 않았습니다.",
  "reviewIssues": [
    {
      "severity": "ERROR",
      "code": "INVALID_PERIOD",
      "message": "교시는 1~30 범위여야 합니다: 34",
      "sheetName": "2025학년도 1학기",
      "rowNumber": 130,
      "field": "scheduleText",
      "rawValue": "월2,34,5"
    }
  ]
}
```

`REVIEW_REQUIRED`이면 기존 학기 강좌는 그대로 유지되며, 새 강좌·일정·분류 데이터는 저장하지 않는다.
지원하지 않는 헤더, 분류 계층 모호성, 잘못된 교시처럼 검토할 위치를 찾을 수 있는 경우에는 `reviewIssues`에 시트명, 행 번호, 필드와 원본 값을 함께 반환한다. 강좌가 한 건도 파싱되지 않았더라도 상세 위치를 찾았다면 같은 형식으로 응답한다.

### 동일 파일 재요청

```json
{
  "importId": "d15af5dc-b21c-4879-86d6-f1baa78c41d5",
  "storageStatus": "DUPLICATE",
  "databaseChanged": false,
  "offeringCount": 918,
  "message": "이미 반영된 파일입니다.",
  "reviewIssues": []
}
```

중복 기준은 다음 값의 조합이다.

```text
학년도 + 학기 + 교육과정 유형 + 원본 파일 SHA-256
```

---

## 6. 업로드 오류 응답

엑셀 자체를 열거나 분석할 수 없는 경우 저장 결과 대신 다음 형식으로 응답한다.

```json
{
  "status": 422,
  "code": "CURRICULUM_TYPE_MISMATCH",
  "message": "요청한 API 종류와 엑셀 내부 교육과정 종류가 일치하지 않습니다.",
  "details": {
    "requested": "MAJOR",
    "detected": "GENERAL_EDUCATION"
  },
  "path": "/api/v1/timetables/major",
  "timestamp": "2026-07-21T05:00:00Z"
}
```

| HTTP | 대표 코드 | 설명 |
|---:|---|---|
| 400 | `FILE_MISSING`, `EMPTY_FILE`, `UNSUPPORTED_EXTENSION`, `INVALID_XLSX_SIGNATURE`, `WORKBOOK_OPEN_FAILED` | 요청 파일 또는 엑셀 컨테이너 오류 |
| 401 | - | JWT가 없거나 유효하지 않음 |
| 403 | - | 로그인했지만 `ADMIN` 역할이 아님 |
| 413 | `FILE_TOO_LARGE`, `WORKBOOK_TOO_LARGE`, `WORKBOOK_ARCHIVE_TOO_LARGE` | 업로드 또는 압축 해제 후 처리 크기 제한 초과 |
| 422 | `NO_LECTURES_PARSED`, `TOO_MANY_SHEETS`, `SEMESTER_NOT_FOUND`, `SEMESTER_CONFLICT`, `CURRICULUM_TYPE_NOT_DETECTED`, `CURRICULUM_TYPE_MISMATCH`, `MIXED_CURRICULUM_WORKBOOK` | 파일은 열렸지만 안전하게 강좌 스냅샷으로 판단할 수 없음 |
| 500 | - | 예기치 못한 서버·DB 오류. 저장 트랜잭션 전체 롤백 |

---

## 7. 저장 방식

수입 범위는 다음과 같다.

```text
academicYear + semester + curriculumType
```

- `courses`에는 학기와 무관한 공통 과목 데이터를 저장하고, `course_offerings`에는
  과목이 어느 학년도·학기에 개설됐는지와 수입 출처를 저장한다.
- 과목 identity는 공백이 아닌 `courseCode`만 사용한다. 과목명이 같아도 코드가 다르면
  서로 다른 과목이다.
- 같은 과목코드를 처음 저장할 때 과목명, 교수, 분반, 학점, 학년, 개설 학과, 수업 시간,
  강의실, 교양 분류와 수강 대상 정보를 함께 저장한다. 이후 다른 학기 파일에 같은 코드가
  다시 등장해도 이 공통 데이터는 덮어쓰지 않고 최초 저장값을 계속 사용한다.
- `courseCode`가 `null`이거나 공백이면 과목명으로 합치지 않는다. 해당 수입 행마다 별도
  `Course`를 만들기 때문에 이름이 같아도 서로 다른 과목으로 취급한다.
- 같은 과목이 여러 학기에 개설되면 `Course` 한 건을 공유하고 학기별 `CourseOffering`만
  각각 둔다. 따라서 2026학년도 1·2학기에 같은 코드가 있으면 공통 데이터는 한 건이다.
- 같은 학기·같은 과목의 재수입은 기존 `CourseOffering` UUID를 유지하면서 수입 이력과
  원본 위치를 갱신하고 `active=true`로 되돌린다.
- 재수입 파일에서 빠진 기존 매핑은 삭제하지 않고 `active=false`로 변경한다. 사용자 강좌
  검색에서는 활성 매핑만 노출하지만, 이미 저장된 개인 시간표의 Offering 참조는 유지한다.
- `course_offerings.curriculum_type`은 수입 범위 판별과 인덱스에 사용한다. 비정상적으로 같은
  코드가 같은 학기의 전공·교양 파일 양쪽에 있어도 `(semester_id, course_id)` 매핑은 한 건만
  유지한다.
- 강좌 일정, 허용 학년, 수강 가능 학과와 교양 분류는 공통 `Course`에 연결한다. 강좌별
  원본 셀은 파일·행 증거이므로 학기별 `CourseOffering`에 연결한다.
- 같은 학기 전공 재수입은 같은 학기 전공의 활성 상태만 갱신하며, 같은 학기 교양과 다른
  학기의 데이터는 유지한다.
- 파싱과 검토는 DB 트랜잭션 전에 끝낸다.
- 실제 upsert와 비활성화는 하나의 트랜잭션으로 실행한다.
- 오류가 발생하면 기존 활성 매핑을 유지하고 전체 롤백한다.
- 파일명·SHA-256·파싱 결과·검토 이력과 강좌별 원본 셀을 보존한다.
- 로컬은 JPA `update`, 운영은 `validate` 정책을 따른다.

---

## 8. 사용자용 강좌 검색

### 8-1. 강좌 검색

```http
GET /api/courses
```

인증 없이 조회할 수 있다. 모든 검색 조건은 선택 사항이며, 아무 조건도 전달하지 않으면
저장된 전체 강좌를 페이지 단위로 반환한다.

### Query parameter

| 이름 | 형식 | 기본값 | 설명 |
|---|---|---:|---|
| `academicYear` | 정수 | 없음 | 학년도, `2000`~`2100` |
| `semester` | 정수 | 없음 | 학기, `1` 또는 `2` |
| `curriculumType` | Enum | 없음 | `MAJOR`, `GENERAL_EDUCATION` |
| `academicUnits` | 문자열 목록 | 없음 | 전공 학과명 다중 선택 |
| `generalCategories` | Enum 목록 | 없음 | 교양 영역·제공기관 다중 선택 |
| `searchField` | Enum | `COURSE_NAME` | 검색어를 적용할 필드 |
| `keyword` | 문자열 | 없음 | 선택한 검색 필드의 부분 검색어 |
| `sort` | Enum | `DEFAULT` | 검색 결과 정렬 |
| `startPeriod` | 정수 | 없음 | 검색 시간 범위의 시작 교시, `0`~`30` |
| `endPeriod` | 정수 | 없음 | 검색 시간 범위의 종료 교시, `0`~`30` |
| `grades` | Enum 목록 | 없음 | 학년 다중 선택 |
| `credits` | Enum 목록 | 없음 | 학점 다중 선택 |
| `page` | 정수 | `0` | 페이지 번호, 0부터 시작 |
| `size` | 정수 | `20` | 페이지 크기, `1`~`100` |

목록 값은 쉼표로 구분하여 전달한다.

```text
academicUnits=항공소프트웨어공학과,항공운항학과
generalCategories=REQUIRED,AREA_1,OCU
grades=GRADE_1,GRADE_2
credits=CREDIT_2,CREDIT_3
```

`academicUnits`는 화면에서 선택한 학과명을 전달하며, 원본 학과명·학과명·전공명 중
일치하는 값으로 검색한다.

### Enum

#### `curriculumType`

| 값 | 의미 |
|---|---|
| `MAJOR` | 전공 |
| `GENERAL_EDUCATION` | 교양 |

#### `generalCategories`

| 값 | 의미 |
|---|---|
| `REQUIRED` | 교양필수 |
| `AREA_1` | 1영역 |
| `AREA_2` | 2영역 |
| `AREA_3` | 3영역 |
| `E_CLASS` | e-Class |
| `HSU_CYBER` | 한서대학교 사이버강좌 |
| `OCU` | OCU |
| `CHUNGNAM_ELEARNING` | 충남 e러닝 |
| `SDU` | SDU |
| `OTHER` | 기타 교양 제공기관 |

#### `searchField`

| 값 | 검색 대상 |
|---|---|
| `COURSE_NAME` | 과목명 |
| `INSTRUCTOR_NAME` | 교수명 |
| `COURSE_CODE` | 과목코드 |
| `LOCATION` | 강의 장소 |

`LOCATION`은 강좌의 원본 장소 문자열과 캠퍼스 코드, 건물명, 강의실 번호를 함께 검색한다.
검색어는 앞뒤 공백을 제거한 뒤 대소문자를 구분하지 않고 부분 일치로 검색한다.

#### `sort`

| 값 | 정렬 순서 |
|---|---|
| `DEFAULT` | 최신 학년도·학기 우선, 원본 엑셀의 시트·행 순서 |
| `COURSE_CODE` | 최신 학년도·학기 우선, 과목코드·분반 순서 |
| `COURSE_NAME` | 최신 학년도·학기 우선, 과목명·과목코드·분반 순서 |

#### `grades`

| 값 | 의미 |
|---|---|
| `GRADE_1` | 1학년 |
| `GRADE_2` | 2학년 |
| `GRADE_3` | 3학년 |
| `GRADE_4` | 4학년 |
| `OTHER` | 공통학년이 아니면서 학년이 없거나 1~4학년 이외인 강좌 |

공통학년 강좌는 1·2·3·4학년 중 하나를 선택하면 함께 조회된다.

#### `credits`

| 값 | 의미 |
|---|---|
| `CREDIT_1` | 1학점 |
| `CREDIT_2` | 2학점 |
| `CREDIT_3` | 3학점 |
| `CREDIT_4_OR_MORE` | 4학점 이상 |

### 검색 조합 규칙

- 같은 필터 그룹에서 여러 값을 선택하면 `OR`로 적용한다.
  - 예: `grades=GRADE_1,GRADE_2`는 1학년 또는 2학년 강좌를 조회한다.
  - 예: `generalCategories=AREA_1,OCU,SDU`는 1영역 또는 OCU 또는 SDU 강좌를 조회한다.
- 서로 다른 필터 그룹은 `AND`로 적용한다.
  - 예: 1·2학년이면서 3학점이고 0~6교시 안에 있는 강좌를 조회한다.
- 선택하지 않은 그룹은 필터링하지 않는다.
- Enum 다중 선택 그룹에서 모든 값을 선택해도 해당 그룹을 필터링하지 않는다.
- `academicUnits`와 `generalCategories`를 함께 전달하면 선택한 전공 학과 또는 선택한 교양
  카테고리에 속하는 강좌를 조회한다.
- `curriculumType`을 전달하면 최종 결과를 전공 또는 교양으로 한 번 더 제한한다.

### 시간 범위 규칙

- `startPeriod`와 `endPeriod`는 반드시 함께 전달한다.
- 범위는 `0`~`30`이며 시작 교시는 종료 교시보다 클 수 없다.
- 강좌의 모든 수업 일정이 선택한 시작·종료 교시 범위 안에 완전히 포함될 때만 조회한다.
- 구체적인 수업 일정이 없는 온라인·시간 미정 강좌는 시간 필터를 사용할 때 제외한다.
- `startPeriod=0&endPeriod=30`은 전체 시간 선택이므로 시간 필터를 적용하지 않는다.

### 요청 예시

2026학년도 1학기 항공소프트웨어공학과 전공 강좌 중 과목명에 `프로그래밍`이 포함되고,
모든 수업이 0~6교시 안에 있으며, 1·2학년 대상 3학점 강좌를 과목명순으로 조회한다.

```http
GET /api/courses?academicYear=2026&semester=1&curriculumType=MAJOR&academicUnits=항공소프트웨어공학과&searchField=COURSE_NAME&keyword=프로그래밍&sort=COURSE_NAME&startPeriod=0&endPeriod=6&grades=GRADE_1,GRADE_2&credits=CREDIT_3&page=0&size=20
```

2026학년도 1학기 교양필수·1영역·OCU·SDU 강좌를 과목코드순으로 조회한다.

```http
GET /api/courses?academicYear=2026&semester=1&curriculumType=GENERAL_EDUCATION&generalCategories=REQUIRED,AREA_1,OCU,SDU&sort=COURSE_CODE&page=0&size=20
```

### 성공 응답

```json
{
  "items": [
    {
      "offeringId": "7da5b546-d431-4b4d-9992-0a50d97399d5",
      "courseName": "웹프로그래밍",
      "sectionNo": "01",
      "credit": 3,
      "cyber": false,
      "instructorName": "홍길동",
      "curriculumType": "MAJOR",
      "targetGrade": 2,
      "originalAcademicUnitName": "항공소프트웨어공학과",
      "eligibleDepartmentNames": [],
      "generalCategory": null,
      "schedules": [
        {
          "dayOfWeek": "MONDAY",
          "periods": [1, 2, 3],
          "buildingName": "공학관",
          "roomNumber": "302"
        }
      ]
    }
  ],
  "page": 0,
  "size": 20,
  "totalPages": 1,
  "totalElements": 1,
  "hasNext": false
}
```

목록의 강좌 항목은 다음 규칙을 따른다.

- `offeringId`: 강좌 상세 조회와 시간표 강좌 추가에 사용하는 학기별 `CourseOffering` UUID.
  공통 `Course`를 공유해도 학기가 다르면 이 값은 다르며, 같은 학기·같은 과목 재수입에서는
  기존 값을 유지한다.
- `credit`: 현재 정수 학점은 불필요한 소수점 없이 JSON 숫자로 반환하며, 실제 소수 학점이 존재하면 값을 자르지 않고 보존
- `curriculumType`: `MAJOR` 또는 `GENERAL_EDUCATION`
- `targetGrade`: 단일 대상 학년을 숫자로 반환하며, 공통학년 또는 원본에서 학년을 확정할 수 없는 강좌는 `null`
- `originalAcademicUnitName`: 전공 엑셀의 원본 학과명이며 교양은 `null`
- `eligibleDepartmentNames`: 엑셀의 수강대상 학과 목록이며, 제한 정보가 없으면 빈 배열 `[]`
- `generalCategory`: 전공은 `null`, 교양은 아래 단일 Enum 값 중 하나
  - `REQUIRED`: 교양필수의 세부 종류를 모두 합친 값
  - `AREA_1`, `AREA_2`, `AREA_3`: 교양 1·2·3영역
  - `E_CLASS`, `HSU_CYBER`, `OCU`, `CHUNGNAM_ELEARNING`, `SDU`: 원격 제공 유형
  - `OTHER`: 그 밖의 교양 분류
- 교양 카테고리는 교양필수 → 원격 제공 유형 → 1·2·3영역 순서로 판별하며, 한 강좌는 한 카테고리에만 속한다.
- `schedules`: 강좌에 여러 일정이 있으면 모두 반환하며, 각 일정에 요일·숫자 교시·건물명·강의실 번호를 함께 제공

`cyber`는 교양 제공기관이 `OCU`, `SDU`, `HSU_CYBER`,
`CHUNGNAM_ELEARNING` 중 하나이면 `true`이다. 전공은 엑셀 비고에 `온라인`,
`사이버`, `원격` 표시가 있을 때 `true`이다. 교내 교양, e-Class, 기타 교양은 `false`이다.

검색 결과가 없으면 `items`가 빈 배열로 반환되며 페이지 정보는 유지된다.

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalPages": 0,
  "totalElements": 0,
  "hasNext": false
}
```

수입 원본, 파일 SHA-256, `sourceCells`, 수입 이력은 사용자 조회 응답에 포함하지 않는다.

### 8-2. 강좌 상세 조회

```http
GET /api/courses/{offeringId}
```

인증 없이 조회할 수 있다. `offeringId`에는 목록 응답의 값을 전달한다.
목록 강좌 항목과 같은 필드를 반환하고, 엑셀의 `비고` 값만 `note`로 추가한다.
응답의 과목명·교수·시간·분류 등은 Offering에 연결된 공통 `Course`의 최초 저장값이다.

```json
{
  "offeringId": "7da5b546-d431-4b4d-9992-0a50d97399d5",
  "courseName": "항공산업개론",
  "sectionNo": "01",
  "credit": 3,
  "cyber": true,
  "instructorName": "홍길동",
  "curriculumType": "MAJOR",
  "targetGrade": 2,
  "originalAcademicUnitName": "항공소프트웨어공학과",
  "eligibleDepartmentNames": [],
  "generalCategory": null,
  "schedules": [
    {
      "dayOfWeek": "MONDAY",
      "periods": [1, 2],
      "buildingName": null,
      "roomNumber": null
    }
  ],
  "note": "온라인수업"
}
```

`note`가 없는 강좌는 `null`이다. 존재하지 않는 `offeringId`는 `404 Not Found`를
반환한다. 목록 응답에는 `note`를 포함하지 않는다.
`eligibleDepartmentNames`는 목록과 상세에서 동일하게 반환하며 교양의 수강대상 학과도
이 배열로 제공한다. 교양의 `originalAcademicUnitName`은 개설 학과 데이터가 아니므로
임의의 값으로 채우지 않고 `null`을 유지한다.

---

## 9. 인증 및 권한

- 전공·교양 엑셀 수입은 `ADMIN` 역할만 사용할 수 있다.
- JWT가 없거나 잘못되었거나 만료되면 `401 Unauthorized`를 반환한다.
- `USER` 역할로 요청하면 `403 Forbidden`을 반환한다.
- 강좌 조회 API인 `GET /api/courses`, `GET /api/courses/{offeringId}`는 인증 없이 사용할 수 있다.

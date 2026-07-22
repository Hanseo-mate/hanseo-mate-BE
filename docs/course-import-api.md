# 강좌 엑셀 수입·조회 API 명세서

## 1. 처리 구조

```text
관리자 페이지
→ Excel multipart 업로드
→ Spring Boot(Apache POI로 분석·검토)
→ Spring Data JPA
→ MySQL 학기별 강좌 스냅샷 저장

사용자 앱
→ Spring Boot 강좌 조회 API
```

Spring Boot가 엑셀 업로드부터 파싱, 검토, 트랜잭션 저장까지 전부 처리한다.

현재 로그인·JWT·관리자 권한은 구현하지 않았으므로 수입 API에도 인증 검사가 없다.

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
Content-Type: multipart/form-data
```

### Postman 설정

1. Method를 `POST`로 선택한다.
2. URL에 `http://localhost:8080/api/v1/timetables/major`를 입력한다.
3. `Body` → `form-data`를 선택한다.
4. Key를 `file`, 타입을 `File`로 변경한다.
5. 전공 시간표 `.xlsx` 또는 `.xlsm` 파일을 선택해 전송한다.

별도 JSON body와 `X-IMPORT-ID`, `X-PARSER-SCHEMA-VERSION`, `Idempotency-Key` 헤더는 필요하지 않다.

---

## 4. 교양 강좌 엑셀 수입

```http
POST /api/v1/timetables/general-education
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
| 413 | `FILE_TOO_LARGE`, `WORKBOOK_TOO_LARGE`, `WORKBOOK_ARCHIVE_TOO_LARGE` | 업로드 또는 압축 해제 후 처리 크기 제한 초과 |
| 422 | `NO_LECTURES_PARSED`, `TOO_MANY_SHEETS`, `SEMESTER_NOT_FOUND`, `SEMESTER_CONFLICT`, `CURRICULUM_TYPE_NOT_DETECTED`, `CURRICULUM_TYPE_MISMATCH`, `MIXED_CURRICULUM_WORKBOOK` | 파일은 열렸지만 안전하게 강좌 스냅샷으로 판단할 수 없음 |
| 500 | - | 예기치 못한 서버·DB 오류. 저장 트랜잭션 전체 롤백 |

---

## 7. 저장 방식

교체 범위는 다음과 같다.

```text
academicYear + semester + curriculumType
```

- 같은 학기 전공 재수입은 같은 학기 전공만 교체한다.
- 같은 학기 교양과 다른 학기의 데이터는 유지한다.
- 파싱과 검토는 DB 트랜잭션 전에 끝낸다.
- 실제 교체 저장은 하나의 트랜잭션으로 실행한다.
- 오류가 발생하면 기존 스냅샷을 유지하고 전체 롤백한다.
- 파일명·SHA-256·파싱 결과·검토 이력과 강좌별 원본 셀을 보존한다.
- 로컬은 JPA `update`, 운영은 `validate` 정책을 따른다.

---

## 8. 사용자용 강좌 조회

```http
GET /api/courses
```

인증 없이 조회할 수 있다.

### 선택 필터

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

수입 원본, 파일 SHA-256, `sourceCells`, 수입 이력은 사용자 조회 응답에 포함하지 않는다.

---

## 9. 현재 보안 주의사항

로그인, JWT, 관리자 계정과 권한 검사는 아직 구현하지 않았다. 기능 검증 중에는 사용할 수 있지만, 인터넷에 정식 공개하기 전 관리자 수입 API에 인증과 권한 검사를 추가해야 한다.

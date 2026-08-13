# 동일교과목·타학과 전공인정 API

## 1. 기능 개요

관리자가 학교에서 제공하는 엑셀 파일을 업로드하면 서버가 파일명, 문서 제목,
시트명과 정해진 표 구조를 분석하여 동일교과목과 타학과 전공인정 정책을 저장한다.
두 업로드 모두 학년도·학기를 별도 요청값으로 받지 않고 `file`만 받는다.

- 동일교과목은 학년도와 학기별 스냅샷이다.
- 타학과 전공인정은 정책연도별 스냅샷이다.
- 정상 데이터가 이미 활성화되어 있으면 같은 의미의 재업로드는 `DUPLICATE`로 처리한다.
- 검토가 필요한 파일은 `REVIEW_REQUIRED` 이력만 저장하고 기존 활성 데이터를 변경하지 않는다.
- 새 정상 파일로 교체하면 기존 활성 이력은 `SUPERSEDED`로 보존한다.

## 2. 인증

두 업로드 API는 모두 `ADMIN` JWT가 필요하다.

```http
Authorization: Bearer {ADMIN_ACCESS_TOKEN}
```

- 토큰 없음 또는 유효하지 않은 토큰: `401 Unauthorized`
- 유효한 `USER` 토큰: `403 Forbidden`

## 3. 동일교과목 업로드

```http
POST /api/admin/course-enrichments/equivalent-courses/imports
Content-Type: multipart/form-data
```

| Part | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `file` | `.xlsx` 또는 `.xlsm` | O | 동일교과목 현황 파일 |

학년도·학기는 엑셀 셀, 시트명, 파일명에서 자동 감지한다. 서로 다른 학기가
발견되거나 어느 곳에서도 학기를 찾지 못하면 저장하지 않는다.

그룹 파싱 규칙은 다음과 같다.

- 일련번호가 비어 있으면 직전 동일교과목 그룹을 이어간다.
- 페이지 경계에서 같은 일련번호가 연속 재표시되면 같은 그룹으로 처리한다.
- 종료된 일련번호가 비연속 위치에서 다시 나오면 검토 대상으로 처리한다.
- 과목코드는 선행 0을 보존한 7자리 숫자여야 한다.
- 한 과목코드는 한 스냅샷에서 하나의 그룹에만 속할 수 있다.
- 구성원이 한 개인 그룹은 삭제하지 않고 경고와 함께 보존한다.

성공 응답 예시:

```json
{
  "importId": "550e8400-e29b-41d4-a716-446655440000",
  "storageStatus": "STORED",
  "databaseChanged": true,
  "groupCount": 1,
  "memberCount": 2,
  "message": "2026학년도 2학기 동일교과목 저장 완료",
  "issues": []
}
```

## 4. 타학과 전공인정 업로드

```http
POST /api/admin/course-enrichments/cross-major-recognitions/imports
Content-Type: multipart/form-data
```

| Part | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `file` | `.xlsx` 또는 `.xlsm` | O | 타학과 전공인정 교과목 목록 파일 |

정책연도와 업로드 학기는 파일명, 문서 제목, 시트명에서만 자동 감지한다.
표 각 행의 `적용년도`, `적용학기`는 개별 규칙의 적용 시점이며 업로드 범위
감지에는 사용하지 않는다.

- 정책연도가 같은 데이터는 연간 활성 스냅샷 하나로 관리한다.
- 1학기와 2학기에 같은 의미의 데이터를 다시 올리면 기존 데이터를 재사용한다.
- 완전히 중복된 규칙은 한 번만 저장하고 경고를 반환한다.
- 짧은 숫자 과목코드는 왼쪽에 `0`을 붙여 7자리로 정규화한다.
- 학생 학부·학과·전공과 개설 학부·학과·전공을 각각 보존한다.

성공 응답 예시:

```json
{
  "importId": "550e8400-e29b-41d4-a716-446655440000",
  "storageStatus": "STORED",
  "databaseChanged": true,
  "policyYear": 2026,
  "uploadedSemester": 1,
  "ruleCount": 1,
  "message": "2026학년도 타학과 전공인정 규칙 저장 완료",
  "reviewIssues": []
}
```

## 5. 저장 상태

| 값 | 의미 |
|---|---|
| `STORED` | 새 활성 스냅샷을 저장함 |
| `DUPLICATE` | 같은 범위에 같은 의미의 데이터가 있어 DB를 변경하지 않음 |
| `REVIEW_REQUIRED` | 오류 위치와 원본 값을 기록했지만 활성 데이터를 변경하지 않음 |

`databaseChanged`는 실제 활성 데이터가 바뀌었는지 나타낸다.

## 6. 강좌 상세조회

```http
GET /api/courses/{offeringId}
```

기존 상세 응답에 다음 두 배열이 추가된다. 데이터가 없으면 `null`이 아니라 `[]`이다.

```json
{
  "equivalentCourses": [
    {
      "courseCode": "0012345",
      "courseName": "동일교과목명"
    }
  ],
  "crossMajorRecognitions": [
    {
      "studentCollegeName": "항공융합학부",
      "studentDepartmentName": "항공소프트웨어공학과",
      "studentMajorName": "항공소프트웨어전공",
      "effectiveYear": 2026,
      "effectiveSemester": 1
    }
  ]
}
```

- `equivalentCourses`는 현재 과목 자신을 제외한 같은 그룹의 과목이다.
- 동일교과목은 강좌의 학년도·학기와 같은 활성 스냅샷만 사용한다.
- `crossMajorRecognitions`는 강좌 연도의 활성 정책 중 해당 학기까지 적용된 규칙만 반환한다.
- 타학과 전공인정은 활성 정책연도, 7자리 과목코드, 개설학과, 개설전공이 모두 일치해야 한다.
- 같은 코드·개설 조직에 여러 과목명이 있을 때만 과목명으로 대상을 구분한다.
- 조직이 다르거나 과목명만 같은 규칙은 연결하지 않으며, 식별이 모호하면 빈 배열을 반환한다.
- `eligibleDepartmentNames`는 수강대상 학과이고, `crossMajorRecognitions`는 전공학점 인정 정책이므로 서로 다른 필드로 유지한다.
- 강좌 목록 `GET /api/courses`에는 두 상세 전용 배열을 추가하지 않는다.

## 7. 오류 응답

엑셀 업로드 오류는 다음 형식을 사용한다.

```json
{
  "status": 422,
  "code": "SEMESTER_CONFLICT",
  "message": "파일에서 서로 다른 학기 정보가 발견되었습니다.",
  "details": {},
  "path": "/api/admin/course-enrichments/equivalent-courses/imports",
  "timestamp": "2026-08-13T12:00:00Z"
}
```

| 상태 | 대표 상황 |
|---:|---|
| `400` | 잘못된 확장자·서명, 빈 파일, 잘못된 multipart 요청 |
| `401` | 로그인 필요 |
| `403` | 관리자 권한 필요 |
| `413` | 허용된 엑셀 업로드 크기 초과 |
| `422` | 학기 감지 실패·충돌, 타학과 원본 표 감지 실패·충돌 |
| `500` | 예상하지 못한 서버 오류 |

## 8. 운영 DB 적용

운영 환경은 `spring.jpa.hibernate.ddl-auto=validate`이므로 배포 전에 다음 신규
테이블을 생성해야 한다.

```text
equivalent_course_import_histories
equivalent_course_groups
equivalent_course_members
cross_major_recognition_import_histories
cross_major_recognition_rules
```

정확한 신규 설치 DDL은 `docs/database-schema-mysql.sql`을 참고한다. 기존 운영 DB에는
전체 스키마 파일을 실행하지 말고 위 신규 테이블의 `CREATE TABLE` 문만 적용한다.

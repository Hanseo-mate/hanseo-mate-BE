# 학점 계산기 API

## 1. 기능 범위

학점 계산기는 로그인 사용자가 만든 시간표를 기준으로 동작한다.

- 사용자가 생성한 전체 시간표 학기를 최신순으로 조회한다.
- 학기를 선택하면 해당 시간표의 과목명과 학점을 자동으로 가져온다.
- 사용자는 과목별 예상 성적만 설정한다.
- 예상 성적은 학기별 시간표 과목에 저장된다.
- 선택 학기 통계와 사용자의 전체 학기 누적 통계를 함께 계산한다.
- 시간표 상세 응답에도 화면 하단 표시용 학기·누적 요약을 포함한다.

과목명과 학점은 `CourseOffering -> Course`에서 읽으며 성적 수정 요청으로 받지 않는다.
따라서 다른 사용자나 다른 학기의 공용 과목 데이터를 변경하지 않는다.

## 2. 인증

모든 `/api/grade-calculations` 및 `/api/grade-calculations/**` 요청에 JWT가 필요하다.

```http
Authorization: Bearer {accessToken}
```

토큰이 없거나 유효하지 않으면 `401 Unauthorized`를 반환한다. 조회와 수정은 JWT 사용자
소유의 시간표 및 시간표 과목으로 제한한다.

## 3. API 목록

| Method | Endpoint | 설명 |
|---|---|---|
| `GET` | `/api/grade-calculations/grades` | 한서대학교 성적 선택 옵션 조회 |
| `GET` | `/api/grade-calculations/overview` | 전체 학기 목록·학기별 요약·누적 요약 조회 |
| `GET` | `/api/grade-calculations/timetable-courses?year={year}&semester={semester}` | 선택 학기의 과목·자동 학점·저장 성적·요약 조회 |
| `PATCH` | `/api/grade-calculations/timetable-courses/{timetableCourseId}` | 과목 예상 성적 저장·수정·초기화 |
| `POST` | `/api/grade-calculations` | 기존 입력값 기반 계산 호환 API |

새 학점 계산기 화면은 `overview -> timetable-courses -> PATCH` 흐름을 사용한다. `POST`
호환 API는 저장하지 않으며 새 화면의 시간표 연동 흐름에서는 사용하지 않는다.

## 4. 전체 학기와 누적 통계 조회

```http
GET /api/grade-calculations/overview
Authorization: Bearer {accessToken}
```

```json
{
  "maximumGpa": 4.5,
  "cumulativeSummary": {
    "maximumGpa": 4.5,
    "totalCredits": 21.0,
    "gpaCredits": 20.0,
    "earnedCredits": 21.0,
    "averageGpa": 3.85,
    "ungradedCourseCount": 0,
    "unavailableCreditCourseCount": 0,
    "status": "COMPLETE"
  },
  "terms": [
    {
      "timetableId": 12,
      "year": 2026,
      "semester": 1,
      "courseCount": 6,
      "summary": {
        "maximumGpa": 4.5,
        "totalCredits": 18.0,
        "gpaCredits": 17.0,
        "earnedCredits": 18.0,
        "averageGpa": 3.90,
        "ungradedCourseCount": 0,
        "unavailableCreditCourseCount": 0,
        "status": "COMPLETE"
      }
    }
  ]
}
```

- `terms`는 사용자가 생성한 시간표를 `연도 내림차순 -> 학기 내림차순`으로 반환한다.
- 과목이 없는 빈 시간표도 `courseCount: 0`, `status: EMPTY`로 포함한다.
- 다른 사용자의 시간표와 성적은 포함하지 않는다.

## 5. 선택 학기 과목 조회

```http
GET /api/grade-calculations/timetable-courses?year=2026&semester=1
Authorization: Bearer {accessToken}
```

```json
{
  "timetableId": 12,
  "year": 2026,
  "semester": 1,
  "courses": [
    {
      "timetableCourseId": 31,
      "courseId": "7da5b546-d431-4b4d-9992-0a50d97399d5",
      "courseName": "자료구조",
      "credit": 3.0,
      "curriculumType": "MAJOR",
      "expectedGrade": "A+"
    }
  ],
  "termSummary": {
    "maximumGpa": 4.5,
    "totalCredits": 3.0,
    "gpaCredits": 3.0,
    "earnedCredits": 3.0,
    "averageGpa": 4.50,
    "ungradedCourseCount": 0,
    "unavailableCreditCourseCount": 0,
    "status": "COMPLETE"
  },
  "cumulativeSummary": {
    "maximumGpa": 4.5,
    "totalCredits": 21.0,
    "gpaCredits": 20.0,
    "earnedCredits": 21.0,
    "averageGpa": 3.85,
    "ungradedCourseCount": 0,
    "unavailableCreditCourseCount": 0,
    "status": "COMPLETE"
  }
}
```

- `courseId`는 학기별 `CourseOffering` UUID다.
- `credit`은 서버의 과목 데이터에서 자동으로 가져온다.
- `expectedGrade`가 `null`이면 아직 성적을 설정하지 않은 과목이다.
- 연도는 `2000~2100`, 학기는 `1` 또는 `2`만 허용한다.
- 해당 학기의 본인 시간표가 없으면 `404 TIMETABLE_NOT_FOUND`를 반환한다.

## 6. 예상 성적 저장·수정·초기화

```http
PATCH /api/grade-calculations/timetable-courses/31
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "expectedGrade": "B+"
}
```

요청에는 `expectedGrade`만 보낸다. `courseName`, `credit`, `curriculumType`은 받지 않는다.
응답은 변경된 과목이 속한 학기의 과목 목록과 갱신된 `termSummary`,
`cumulativeSummary` 전체다.

성적을 미입력 상태로 되돌리려면 다음과 같이 보낸다.

```json
{
  "expectedGrade": null
}
```

다른 사용자의 `timetableCourseId` 또는 존재하지 않는 ID는
`404 TIMETABLE_COURSE_NOT_FOUND`로 처리한다.

## 7. 시간표 화면 하단 컴팩트 요약

기존 시간표 상세 API 응답에 `gradeSummary`가 추가된다.

```http
GET /api/timetables?year=2026&semester=1
Authorization: Bearer {accessToken}
```

```json
{
  "timetableId": 12,
  "year": 2026,
  "semester": 1,
  "courses": [],
  "cyberCourses": [],
  "gradeSummary": {
    "termSummary": {
      "maximumGpa": 4.5,
      "totalCredits": 18.0,
      "gpaCredits": 17.0,
      "earnedCredits": 18.0,
      "averageGpa": 3.90,
      "ungradedCourseCount": 0,
      "unavailableCreditCourseCount": 0,
      "status": "COMPLETE"
    },
    "cumulativeSummary": {
      "maximumGpa": 4.5,
      "totalCredits": 21.0,
      "gpaCredits": 20.0,
      "earnedCredits": 21.0,
      "averageGpa": 3.85,
      "ungradedCourseCount": 0,
      "unavailableCreditCourseCount": 0,
      "status": "COMPLETE"
    }
  }
}
```

앱은 시간표 하단에 `termSummary.totalCredits`, `termSummary.averageGpa`,
`cumulativeSummary.totalCredits`, `cumulativeSummary.averageGpa`를 컴팩트하게 표시할 수
있다.

## 8. 성적 선택 옵션

```http
GET /api/grade-calculations/grades
Authorization: Bearer {accessToken}
```

성적 순서는 `A+`, `A`, `B+`, `B`, `C+`, `C`, `D+`, `D`, `P`, `F`다.

| 성적 | 평점 | GPA 반영 | 취득학점 |
|---|---:|---|---|
| `A+` | 4.5 | 포함 | 포함 |
| `A` | 4.0 | 포함 | 포함 |
| `B+` | 3.5 | 포함 | 포함 |
| `B` | 3.0 | 포함 | 포함 |
| `C+` | 2.5 | 포함 | 포함 |
| `C` | 2.0 | 포함 | 포함 |
| `D+` | 1.5 | 포함 | 포함 |
| `D` | 1.0 | 포함 | 포함 |
| `P` | 없음 | 제외 | 포함 |
| `F` | 0.0 | 포함 | 제외 |

## 9. 계산 기준

```text
평균 평점 = Σ(과목 학점 × 취득 평점) ÷ 평점 반영학점
```

| 필드 | 의미 |
|---|---|
| `totalCredits` | 학점 데이터가 유효한 시간표 과목 전체 학점 |
| `gpaCredits` | `P`와 성적 미입력 과목을 제외하고 `F`를 포함한 학점 |
| `earnedCredits` | `F`와 성적 미입력 과목을 제외하고 `P`를 포함한 학점 |
| `averageGpa` | 평점 반영 과목의 가중평균, 소수 둘째 자리 `HALF_UP` |
| `ungradedCourseCount` | 예상 성적이 `null`인 과목 수 |
| `unavailableCreditCourseCount` | 서버 학점이 `null` 또는 0 이하인 과목 수 |

누적 평점은 학기 평균의 단순 평균이 아니다. 전체 학기의 과목별 가중합과 전체
평점 반영학점으로 한 번만 계산한다.

`status`는 다음 값을 사용한다.

| 값 | 의미 |
|---|---|
| `EMPTY` | 과목이 없음 |
| `INCOMPLETE` | 성적 미입력 또는 사용할 수 없는 학점이 하나 이상 있음 |
| `COMPLETE` | 모든 과목의 성적과 학점을 계산할 수 있음 |

학점이 누락된 과목은 임의의 0학점으로 계산하거나 사용자 입력으로 덮어쓰지 않는다.
과목 응답에는 원본 `credit`을 그대로 반환하고 `unavailableCreditCourseCount`로 상태를
알린다.

## 10. 기존 입력값 계산 호환 API

```http
POST /api/grade-calculations
Authorization: Bearer {accessToken}
Content-Type: application/json
```

이 API는 기존 클라이언트 호환을 위해 유지하며 계산 결과를 저장하지 않는다. 새 시간표
연동 화면에서는 사용하지 않는다. 기존 신청·반영·취득학점 및 P/F 계산 규칙은 동일하다.

## 11. 운영 DB 반영

운영은 `spring.jpa.hibernate.ddl-auto=validate`이므로 코드 배포 전에 다음 증분 DDL을 한
번 적용해야 한다.

```text
docs/timetable-course-expected-grade-migration-mysql.sql
```

기존 `timetable_courses` 행은 `expected_grade = NULL`, 즉 성적 미입력 상태로 유지한다.
전체 스키마 파일은 빈 DB 생성용이므로 기존 운영 DB에 실행하지 않는다.

## 12. 공식 기준

성적별 평점과 P/F 처리는 한서대학교의
[성적평가 및 재수강 안내](https://www.hanseo.ac.kr/sub/info.do?m=040218&page=040218&s=hs)를
기준으로 한다.

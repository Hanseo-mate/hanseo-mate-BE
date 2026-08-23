# 학점 계산기 API

## 1. 기능과 인증 범위

학점 계산은 서버에 입력값을 저장하지 않는 무상태 API다. 직접 입력 계산과 성적표 옵션
조회는 로그인 없이 사용할 수 있고, 사용자가 만든 시간표에서 과목을 가져오는 API만 JWT가
필요하다.

| Method | Endpoint | 인증 | 설명 |
|---|---|---|---|
| `GET` | `/api/grade-calculations/grades` | 불필요 | 한서대학교 성적 선택 옵션 조회 |
| `POST` | `/api/grade-calculations` | 불필요 | 과목 목록의 예상 평점과 학점 합계 계산 |
| `GET` | `/api/grade-calculations/timetable-courses` | 필요 | 로그인 사용자의 시간표 과목 프리필 조회 |

익명 사용자의 과목 목록을 기기에서 유지하려면 앱의 로컬 상태나 로컬 저장소를 사용한다.
이 API는 익명 사용자 식별자나 계산 내역 테이블을 만들지 않는다.

## 2. 성적 선택 옵션

```http
GET /api/grade-calculations/grades
```

```json
{
  "maximumGpa": 4.5,
  "grades": [
    {
      "grade": "A+",
      "gradePoint": 4.5,
      "includedInGpa": true,
      "creditEarned": true
    },
    {
      "grade": "P",
      "gradePoint": null,
      "includedInGpa": false,
      "creditEarned": true
    },
    {
      "grade": "F",
      "gradePoint": 0.0,
      "includedInGpa": true,
      "creditEarned": false
    }
  ]
}
```

응답 배열의 실제 순서는 `A+`, `A`, `B+`, `B`, `C+`, `C`, `D+`, `D`, `P`, `F`다.
피커에는 `grade` 값을 표시하고 계산 요청에도 같은 문자열을 보낸다.

## 3. 예상 평점 계산

```http
POST /api/grade-calculations
Content-Type: application/json
```

### 요청

```json
{
  "courses": [
    {
      "courseName": "자료구조",
      "credit": 3,
      "expectedGrade": "A+",
      "curriculumType": "MAJOR"
    },
    {
      "courseName": "모바일프로그래밍",
      "credit": 2,
      "expectedGrade": "B",
      "curriculumType": "MAJOR"
    },
    {
      "courseName": "봉사활동",
      "credit": 1,
      "expectedGrade": "P",
      "curriculumType": "GENERAL_EDUCATION"
    }
  ]
}
```

| 필드 | 필수 | 규칙 |
|---|---|---|
| `courses` | O | 빈 배열 허용, 최대 100개 |
| `courseName` | O | 공백 불가, 최대 255자 |
| `credit` | O | `0.001` 이상 `20.000` 이하, 소수 셋째 자리까지 |
| `expectedGrade` | X | 성적 선택 전에는 `null` 또는 생략 가능 |
| `curriculumType` | X | `MAJOR`, `GENERAL_EDUCATION` 또는 `null` |

학점 상한 `20.000`은 학교 성적 규정이 아니라 비정상 요청을 막기 위한 API 입력 제한이다.

### 응답

```json
{
  "maximumGpa": 4.5,
  "appliedCredits": 6,
  "gpaCredits": 5,
  "earnedCredits": 6,
  "expectedGpa": 3.90,
  "ungradedCourseCount": 0,
  "status": "COMPLETE"
}
```

계산 기준은 다음과 같다.

```text
예상 평점 = Σ(과목 학점 × 취득 평점) ÷ 평점 반영학점
```

| 예상 성적 | 신청학점 | 평점 반영학점 | 취득학점 | 평점 분자 |
|---|---:|---:|---:|---:|
| `A+`~`D` | 포함 | 포함 | 포함 | 학점 × 평점 |
| `P` | 포함 | 제외 | 포함 | 제외 |
| `F` | 포함 | 포함 | 제외 | 0 |
| `null` | 포함 | 제외 | 제외 | 제외 |

- 중간 계산은 반올림하지 않고, 최종 예상 평점만 소수 둘째 자리에서 `HALF_UP`한다.
- 전 과목이 `P`이거나 아직 모든 성적이 미입력이라 분모가 0이면 `expectedGpa`는 `null`이다.
- `F` 과목만 있으면 평점 반영학점이 있으므로 `expectedGpa`는 `0.00`이다.
- 백분위 점수는 문자 성적만으로 확정할 수 없어 반환하지 않는다.

`status`는 다음 값을 사용한다.

| 값 | 의미 |
|---|---|
| `EMPTY` | 과목이 없음 |
| `INCOMPLETE` | 한 과목 이상 예상 성적이 미입력 |
| `COMPLETE` | 모든 과목의 예상 성적이 입력됨 |

## 4. 시간표에서 과목 가져오기

```http
GET /api/grade-calculations/timetable-courses?year=2026&semester=1
Authorization: Bearer {accessToken}
```

연도는 `2000`~`2100`, 학기는 `1` 또는 `2`만 허용한다. 현재 시간표 모델에는 계절학기가
없다.

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
      "credit": 3.000,
      "curriculumType": "MAJOR"
    }
  ]
}
```

- JWT의 사용자 ID와 일치하는 시간표만 조회한다.
- 일반 강좌와 사이버 강좌를 구분하지 않고 시간표에 추가된 순서대로 한 배열에 반환한다.
- `courseId`는 학기별 `CourseOffering` UUID다.
- 기존 수입 데이터에서 과목명이나 학점이 비어 있으면 임의 값이나 `0`으로 바꾸지 않고
  `null`을 반환한다. 앱에서 사용자가 보완한 뒤 계산 요청을 보낸다.

토큰이 없거나 유효하지 않으면 `401`, 해당 연도·학기의 본인 시간표가 없으면
`TIMETABLE_NOT_FOUND` 코드와 함께 `404`를 반환한다.

## 5. 공식 기준

성적별 평점과 P/F 처리는 한서대학교의
[성적평가 및 재수강 안내](https://www.hanseo.ac.kr/sub/info.do?m=040218&page=040218&s=hs)를
기준으로 한다.

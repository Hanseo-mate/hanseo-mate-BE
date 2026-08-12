# 사용자 시간표 구성 API

## 1. 기능 개요

사용자는 학년도와 학기별로 시간표를 하나 만들고, 기존 강좌 검색 결과에서 선택한
강좌를 시간표에 추가하거나 삭제할 수 있다.

모든 시간표 API는 로그인이 필요하다. 요청에서 `userId`를 받지 않고 JWT의 `sub`에
저장된 사용자 ID를 사용하므로, 각 사용자는 본인의 시간표만 조회하고 변경할 수 있다.

## 2. 공통 기준

### 인증 헤더

로그인 API에서 발급받은 Access Token을 모든 시간표 요청에 전달한다.

```http
Authorization: Bearer {accessToken}
```

토큰이 없거나 유효하지 않으면 `401 Unauthorized`를 반환한다.

### 강좌 ID

시간표 API 요청과 응답의 `courseId`에는 강좌 검색 API의 `offeringId` 값을 사용한다.

```json
{
  "offeringId": "7da5b546-d431-4b4d-9992-0a50d97399d5"
}
```

```json
{
  "courseId": "7da5b546-d431-4b4d-9992-0a50d97399d5"
}
```

`Course`는 과목코드와 과목명의 마스터 정보이며, 실제 학기·분반·교수·수업 시간은
`CourseOffering`에 저장되므로 시간표는 Offering UUID를 참조한다.

### 시간 충돌

현재 강좌 데이터는 시작·종료 시각이 아니라 `요일 + 교시 목록`으로 저장된다.

다음 두 조건을 모두 만족하면 충돌이다.

- 요일이 같음
- 두 교시 목록에 하나 이상의 같은 교시가 있음

구조화된 수업 시간이 없는 온라인·미정 강좌는 충돌 검사에서 제외되며 시간표에는
추가할 수 있다. 조회 응답의 `meetings`는 빈 배열이고 원본 `scheduleText`와
`classroomText`는 그대로 제공된다.

## 3. 시간표 생성

```http
POST /api/timetables
Content-Type: application/json
```

### 요청

```json
{
  "year": 2026,
  "semester": 2
}
```

### 성공 응답

```http
HTTP/1.1 201 Created
```

```json
{
  "timetableId": 1,
  "year": 2026,
  "semester": 2
}
```

같은 사용자는 동일한 연도와 학기에 시간표를 하나만 만들 수 있다.

## 4. 연도·학기별 시간표 조회

```http
GET /api/timetables?year=2026&semester=2
```

### 성공 응답

```json
{
  "timetableId": 1,
  "year": 2026,
  "semester": 2,
  "courses": [
    {
      "timetableCourseId": 31,
      "courseId": "7da5b546-d431-4b4d-9992-0a50d97399d5",
      "courseCode": "001234",
      "courseName": "자료구조",
      "sectionNo": "01",
      "credit": 3.000,
      "cyber": false,
      "generalCategory": null,
      "eligibleDepartmentNames": [],
      "instructorName": "김교수",
      "scheduleText": "월1,2 / 수1,2",
      "classroomText": "공학관 301호",
      "meetings": [
        {
          "dayOfWeek": "MONDAY",
          "periods": [1, 2],
          "classroom": {
            "campusCode": "TAEAN",
            "buildingName": "공학관",
            "roomNumber": "301",
            "originalValue": "공학관 301호"
          }
        }
      ]
    }
  ],
  "cyberCourses": []
}
```

`courses`에는 일반 강좌, `cyberCourses`에는 `cyber=true`인 사이버 강좌가 들어간다.
각 강좌는 두 배열 중 하나에만 포함된다.

- `generalCategory`: 전공은 `null`, 교양은 `REQUIRED`, `AREA_1`, `AREA_2`,
  `AREA_3`, `E_CLASS`, `HSU_CYBER`, `OCU`, `CHUNGNAM_ELEARNING`, `SDU`,
  `OTHER` 중 하나
- `eligibleDepartmentNames`: 엑셀의 수강대상 학과 목록이며 제한 정보가 없으면 `[]`
- `sectionNo`: 엑셀의 분반 값을 문자열로 보존

## 5. 시간표에 과목 추가

```http
POST /api/timetables/courses/{timetableId}
Content-Type: application/json
```

### 요청

```json
{
  "courseId": "7da5b546-d431-4b4d-9992-0a50d97399d5",
  "conflictPolicy": "REJECT"
}
```

`conflictPolicy`를 생략하면 `REJECT`가 적용된다.

| 값 | 동작 |
|---|---|
| `REJECT` | 충돌 과목이 있으면 변경하지 않고 `409`와 충돌 목록을 반환 |
| `REPLACE` | 현재 충돌 과목을 다시 계산하여 모두 삭제한 뒤 새 과목을 추가 |

### 성공 응답

```http
HTTP/1.1 201 Created
```

```json
{
  "timetableCourseId": 31,
  "courseId": "7da5b546-d431-4b4d-9992-0a50d97399d5",
  "courseCode": "001234",
  "courseName": "자료구조",
  "sectionNo": "01",
  "credit": 3.000,
  "cyber": false,
  "generalCategory": null,
  "eligibleDepartmentNames": [],
  "instructorName": "김교수",
  "scheduleText": "월1,2",
  "classroomText": "공학관 301호",
  "meetings": [
    {
      "dayOfWeek": "MONDAY",
      "periods": [1, 2],
      "classroom": {
        "campusCode": "TAEAN",
        "buildingName": "공학관",
        "roomNumber": "301",
        "originalValue": "공학관 301호"
      }
    }
  ]
}
```

### 시간 충돌 응답

```http
HTTP/1.1 409 Conflict
```

```json
{
  "status": 409,
  "code": "TIMETABLE_TIME_CONFLICT",
  "message": "기존 과목과 수업 시간이 겹칩니다.",
  "path": "/api/timetables/courses/1",
  "timestamp": "2026-07-30T10:00:00Z",
  "conflicts": [
    {
      "timetableCourseId": 30,
      "courseId": "490baf4d-f8c9-491c-bcb2-ddad71d35914",
      "courseCode": "009451",
      "courseName": "운영체제",
      "sectionNo": "01",
      "credit": 3.000,
      "cyber": false,
      "generalCategory": null,
      "eligibleDepartmentNames": [],
      "instructorName": "이교수",
      "scheduleText": "월1,2",
      "classroomText": "공학관 302호",
      "meetings": [
        {
          "dayOfWeek": "MONDAY",
          "periods": [1, 2],
          "classroom": {
            "campusCode": "TAEAN",
            "buildingName": "공학관",
            "roomNumber": "302",
            "originalValue": "공학관 302호"
          }
        }
      ]
    }
  ]
}
```

과목 추가 성공 응답과 시간 충돌의 `conflicts` 항목도 시간표 조회의 강좌 객체와
동일하게 `sectionNo`, `cyber`, `generalCategory`, `eligibleDepartmentNames`를 반환한다.

## 6. 시간표 과목 삭제

```http
DELETE /api/timetables/courses/{timetableCourseId}
```

성공하면 `204 No Content`를 반환한다. URL의 마지막 ID는 강좌의 `courseId`가 아니라
시간표에 추가된 항목의 `timetableCourseId`이다. 삭제 대상 항목에 연결된 시간표를
기준으로 현재 로그인 사용자의 소유권을 검사한다.

## 7. 시간표 전체 삭제

```http
DELETE /api/timetables/{timetableId}
```

성공하면 `204 No Content`를 반환하고 연결된 시간표 과목도 모두 삭제한다.

## 8. 오류 코드

| HTTP 상태 | 코드 | 설명 |
|---:|---|---|
| 401 | - | JWT가 없거나 유효하지 않음 |
| 409 | `TIMETABLE_ALREADY_EXISTS` | 같은 사용자·연도·학기 시간표가 이미 있음 |
| 404 | `TIMETABLE_NOT_FOUND` | 시간표가 존재하지 않음 |
| 403 | `TIMETABLE_ACCESS_DENIED` | 현재 사용자의 시간표가 아님 |
| 404 | `COURSE_NOT_FOUND` | `courseId`에 해당하는 Offering이 없음 |
| 409 | `COURSE_ALREADY_ADDED` | 같은 Offering이 이미 추가됨 |
| 400 | `COURSE_TERM_MISMATCH` | 시간표와 강좌의 연도 또는 학기가 다름 |
| 409 | `TIMETABLE_TIME_CONFLICT` | 기존 과목과 교시가 겹침 |
| 404 | `TIMETABLE_COURSE_NOT_FOUND` | 해당 시간표에 시간표 과목이 없음 |
| 400 | `INVALID_TIMETABLE_TERM` | 연도 또는 학기 값이 유효하지 않음 |

## 9. 현재 제한사항

- Access Token만 발급하며 Refresh Token과 로그아웃은 아직 지원하지 않는다.
- 한 사용자는 같은 연도·학기에 시간표 하나만 만들 수 있다.
- 시간 충돌은 실제 시각이 아닌 현재 저장된 교시 목록을 기준으로 판단한다.
- 과목 엑셀을 같은 학기·교육과정 범위로 다시 업로드하면 기존 Offering UUID가
  교체된다. 업로드를 막지 않기 위해 FK에 `ON DELETE CASCADE`를 적용했으므로,
  해당 범위의 시간표 과목 선택도 함께 제거된다.
- 같은 학기에 여러 시간표를 지원할 때는
  `uk_timetable_owner_term` 유니크 제약조건을 제거하고 시간표 이름 등의 구분값을
  추가하면 된다. `timetable_courses` 구조는 그대로 사용할 수 있다.

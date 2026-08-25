# 캠퍼스 맵 오늘 수업 위치 API

## 1. 기능 개요

로그인 사용자의 시간표에서 한국 시간 기준 오늘 요일에 해당하는 수업을 조회하고,
강의실 건물의 위도·경도를 반환합니다. 프론트엔드는 `locationStatus`가 `MAPPED`인
항목만 네이버 지도 마커로 표시하면 됩니다.

## 2. 요청

```http
GET /api/timetables/today-locations
Authorization: Bearer {accessToken}
```

- Query Parameter와 요청 Body는 없습니다.
- JWT 로그인이 필요합니다.
- 날짜 기준은 `Asia/Seoul`입니다.
- 현재 학기는 기존 홈 시간표와 동일하게 1~6월은 1학기, 7~12월은 2학기로
  계산합니다.

## 3. 성공 응답

```json
{
  "date": "2026-05-11",
  "dayOfWeek": "MONDAY",
  "academicYear": 2026,
  "semester": 1,
  "courseLocations": [
    {
      "scheduleId": "3f268828-b3d9-4cbf-90a3-75e32cf49356",
      "courseName": "항공소프트웨어개론",
      "periods": [0, 1],
      "campusCode": "TAEAN",
      "buildingName": "본관",
      "roomNumber": "101",
      "canonicalBuildingName": "태안 강의동(본관)",
      "latitude": 36.5944988,
      "longitude": 126.294045,
      "locationStatus": "MAPPED"
    },
    {
      "scheduleId": "fd25fb03-7a4a-43a0-928e-e2e54ceaa692",
      "courseName": "미정강의실세미나",
      "periods": [2, 3],
      "campusCode": "SEOSAN",
      "buildingName": "미래관",
      "roomNumber": "501",
      "canonicalBuildingName": null,
      "latitude": null,
      "longitude": null,
      "locationStatus": "UNMAPPED"
    }
  ]
}
```

## 4. 위치 상태

| 값 | 의미 | 위도·경도 |
|---|---|---|
| `MAPPED` | 건물 별칭이 좌표 카탈로그와 정확히 일치함 | 숫자 |
| `UNMAPPED` | 강의실은 있지만 등록되지 않았거나 모호한 건물명임 | `null` |
| `NO_CLASSROOM` | 구조화된 수업 일정에 강의실 정보가 없음 | `null` |

건물명을 부분 일치시키거나 `(0, 0)` 좌표를 만들지 않습니다. 예를 들어 실제 장소가
아닌 `비행교육원자체편성`은 `비행교육원`으로 간주하지 않고 `UNMAPPED`로 반환합니다.

## 5. 조회 및 정렬 규칙

- JWT 사용자 본인의 현재 연도·학기 시간표만 조회합니다.
- 오늘 요일과 일치하는 `CourseSchedule` 한 건을 응답 한 건으로 반환합니다.
- 첫 교시, 과목명, 일정 순번 순으로 정렬합니다.
- 같은 건물에서 여러 수업이 열려도 수업 항목을 합치지 않습니다.
- 오늘 수업이나 현재 학기 시간표가 없으면 `courseLocations: []`를 반환합니다.
- 구조화된 요일·교시 정보 자체가 없는 사이버 과목은 오늘 일정으로 판정할 수 없어
  응답에 포함되지 않습니다.

## 6. 프론트엔드 마커 기준

```javascript
const markers = response.courseLocations
  .filter((course) => course.locationStatus === "MAPPED")
  .map((course) => ({
    id: course.scheduleId,
    position: {
      lat: course.latitude,
      lng: course.longitude,
    },
    title: `${course.courseName} · ${course.buildingName} ${course.roomNumber ?? ""}`.trim(),
  }));
```

## 7. 현재 데이터 경계

- 좌표는 코드에 포함된 건물 카탈로그로 관리하므로 변경 시 백엔드 재배포가
  필요합니다.
- 현재 모델에서 일정과 강의실은 학기별 `CourseOffering`이 아닌 공통 `Course`에
  연결됩니다. 같은 과목코드의 강의실이 다음 학기에 바뀌어도 최초 수입된 강의실이
  유지될 수 있습니다.

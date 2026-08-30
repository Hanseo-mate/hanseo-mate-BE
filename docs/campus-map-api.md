# 캠퍼스 맵 시간표 위치 API

## 1. 기능 개요

로그인 사용자의 현재 학기 시간표에서 강의실 건물의 위도·경도를 반환합니다.
기존 오늘 수업 조회와 월요일부터 목요일까지 전체 조회를 모두 지원합니다.
프론트엔드는 `locationStatus`가 `MAPPED`인 항목만 네이버 지도 마커로 표시하면 됩니다.

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
      "latitude": 36.594581,
      "longitude": 126.294056,
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
| `MAPPED` | 캠퍼스와 건물 별칭이 DB 좌표 데이터와 정확히 일치함 | 숫자 |
| `UNMAPPED` | 강의실은 있지만 등록되지 않았거나 모호한 건물명임 | `null` |
| `NO_CLASSROOM` | 구조화된 수업 일정에 강의실 정보가 없음 | `null` |

건물명을 부분 일치시키거나 `(0, 0)` 좌표를 만들지 않습니다. 예를 들어 실제 장소가
아닌 `비행교육원자체편성`은 `비행교육원`으로 간주하지 않고 `UNMAPPED`로 반환합니다.

`campusCode` 응답은 `SEOSAN`, `TAEAN`, `null`만 사용합니다. 원천 시간표의
`H01`부터 `H17`까지는 서산캠퍼스 건물 코드로 해석해 `SEOSAN`으로 반환하며,
지원하지 않는 원천 코드는 그대로 노출하지 않고 `null`로 반환합니다. 건물 코드가
확인돼도 건물명이 좌표 데이터와 일치하지 않으면 `UNMAPPED` 상태를 유지합니다.

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

## 7. 월요일부터 목요일까지 전체 수업 위치 조회

현재 연도·학기의 월요일, 화요일, 수요일, 목요일 수업을 요일별로 묶어 반환합니다.
금요일부터 일요일까지의 일정은 응답에 포함하지 않습니다.

```http
GET /api/timetables/weekly-locations
Authorization: Bearer {accessToken}
```

- Query Parameter와 요청 Body는 없습니다.
- JWT 로그인이 필요합니다.
- `dayLocations`는 `MONDAY`, `TUESDAY`, `WEDNESDAY`, `THURSDAY` 고정 순서입니다.
- 해당 요일에 수업이 없어도 그 요일을 생략하지 않고 `courseLocations: []`로 반환합니다.
- 각 수업의 필드와 `locationStatus` 규칙은 오늘 수업 조회와 같습니다.
- 각 요일 안에서는 첫 교시, 과목명, 일정 순번, 일정 식별자 순으로 정렬합니다.

```json
{
  "academicYear": 2026,
  "semester": 1,
  "dayLocations": [
    {
      "dayOfWeek": "MONDAY",
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
        }
      ]
    },
    {
      "dayOfWeek": "TUESDAY",
      "courseLocations": []
    },
    {
      "dayOfWeek": "WEDNESDAY",
      "courseLocations": []
    },
    {
      "dayOfWeek": "THURSDAY",
      "courseLocations": []
    }
  ]
}
```

```javascript
const markersByDay = Object.fromEntries(
  response.dayLocations.map((day) => [
    day.dayOfWeek,
    day.courseLocations
      .filter((course) => course.locationStatus === "MAPPED")
      .map((course) => ({
        id: course.scheduleId,
        position: {
          lat: course.latitude,
          lng: course.longitude,
        },
        course,
      })),
  ]),
);
```

## 8. 전체 장소 목록과 공통 상세 조회

전체 장소 조회는 로그인 없이도 사용할 수 있고 JWT를 선택적으로 받을 수 있습니다.
`campusCode`와 `category`는 선택값이며, 카테고리를 아직 수동 분류하지 않은 장소도
`category: null`로 반환됩니다.

- 유효한 JWT가 있고 `campusCode`가 없으면 사용자의 선호 학생식당을 캠퍼스로 변환해
  해당 캠퍼스 장소만 반환합니다.
- `MAIN_STUDENT`는 `SEOSAN`, `TAEAN_STUDENT`는 `TAEAN`으로 변환합니다.
- `campusCode`를 직접 보내면 로그인 사용자의 선호값보다 요청값이 우선합니다.
- 비로그인 상태에서 `campusCode`가 없으면 기존과 같이 모든 캠퍼스 장소를 반환합니다.
- 잘못되었거나 만료된 JWT를 보내면 `401 Unauthorized`입니다.

로그인 사용자의 선호 캠퍼스를 적용하는 요청입니다.

```http
GET /api/campus-map/places
Authorization: Bearer {accessToken}
```

```http
GET /api/campus-map/places?campusCode=SEOSAN&category=CAFE
```

목록 응답의 각 항목은 지도 마커와 간단 정보창에서 사용합니다.

```json
{
  "selectedCampusCode": "SEOSAN",
  "places": [
    {
      "placeId": 2,
      "campusCode": "SEOSAN",
      "placeName": "가배앤빈",
      "category": "CAFE",
      "categoryName": "카페",
      "oneLineDescription": "한서대학교 대정문 인근 카페",
      "address": "충청남도 서산시 해미면 대곡리",
      "imageUrl": "https://api.example.com/uploads/campus-places/example.jpg",
      "latitude": 36.691166,
      "longitude": 126.574659
    }
  ]
}
```

`selectedCampusCode`는 이번 응답에 실제 적용된 캠퍼스입니다. 비로그인 상태에서
`campusCode`도 보내지 않아 전체 캠퍼스를 조회하면 해당 필드는 `null` 또는 생략됩니다.
프론트엔드는 이 값으로 서산·태안 지도 중심을 선택하고, 응답의 `places`만 마커로
표시하면 됩니다.

```http
GET /api/campus-map/places/{placeId}
```

교내시설 카테고리는 공통 필드에 다음 상세 정보가 추가됩니다. 아직 관리자가 상세
데이터를 입력하지 않은 교내시설은 `lectureBuildingDetails: null`입니다.

```json
{
  "placeId": 83,
  "campusCode": "SEOSAN",
  "placeName": "공학관",
  "category": "LECTURE_BUILDING",
  "categoryName": "교내시설",
  "oneLineDescription": "공학 계열 강의와 실습이 진행되는 건물",
  "imageUrl": "https://api.example.com/uploads/campus-places/example.jpg",
  "latitude": 36.690884,
  "longitude": 126.585761,
  "lectureBuildingDetails": {
    "location": "서산캠퍼스",
    "floorCount": 5,
    "hasElevator": true,
    "operatingHours": "평일 09:00~22:00",
    "departments": ["컴퓨터공학과", "항공소프트웨어공학과"],
    "majorFacilities": ["전산실습실", "학과사무실"]
  }
}
```

음식점·카페·편의시설의 카테고리별 상세 양식은 요구사항이 확정된 뒤 별도 상세
테이블과 응답 필드로 확장합니다.

카테고리는 `RESTAURANT`, `CAFE`, `LECTURE_BUILDING`,
`CONVENIENCE_FACILITY` 네 값만 허용합니다.
`LECTURE_BUILDING`의 표시명인 `categoryName`은 `교내시설`로 반환됩니다.

## 9. 장소 이미지 업로드

관리자 JWT가 필요한 API이며, 장소 DB 행을 수정하지 않고 저장된 이미지 URL만
반환합니다. 대표 이미지가 필요한 경우 반환 URL을 아래 장소 정보 저장 API의
`imageUrl`로 전달합니다. 장소는 이미지 없이도 등록하거나 수정할 수 있습니다.

```http
POST /api/admin/campus-map/place-images
Authorization: Bearer {adminAccessToken}
Content-Type: multipart/form-data

file: {JPG, PNG 또는 GIF 이미지}
```

```json
{
  "imageUrl": "https://api.example.com/uploads/campus-places/uuid.jpg"
}
```

기본 이미지 크기 제한은 `UPLOAD_MAX_IMAGE_BYTES` 설정을 사용하며 기본값은 5MiB입니다.

## 10. 관리자 장소 등록·수정·삭제

관리자 JWT로 장소를 등록, 전체 수정, 삭제합니다. 이미지 업로드 API가 반환한 URL은
필요한 경우 `imageUrl`에 사용하며 `placeNameKey`는 장소명으로 서버가 자동 생성합니다.

```http
POST /api/admin/campus-map/places
PUT /api/admin/campus-map/places/{placeId}
DELETE /api/admin/campus-map/places/{placeId}
Authorization: Bearer {adminAccessToken}
Content-Type: application/json
```

- `POST`는 `201 Created`와 생성된 장소 상세정보를 반환합니다.
- `PUT`은 `200 OK`와 수정된 장소 상세정보를 반환합니다.
- `DELETE`는 `204 No Content`를 반환합니다.
- 삭제 시 교내시설 상세정보는 함께 삭제하지만 업로드된 이미지 파일은 삭제하지 않습니다.

교내시설 카테고리 요청 예시입니다.

```json
{
  "campusCode": "SEOSAN",
  "placeName": "공학관",
  "latitude": 36.690884,
  "longitude": 126.585761,
  "category": "LECTURE_BUILDING",
  "oneLineDescription": "공학 계열 강의와 실습이 진행되는 건물",
  "imageUrl": "https://api.example.com/uploads/campus-places/uuid.jpg",
  "lectureBuildingDetails": {
    "location": "서산캠퍼스",
    "floorCount": 5,
    "hasElevator": true,
    "operatingHours": "평일 09:00~22:00",
    "departments": ["컴퓨터공학과", "항공소프트웨어공학과"],
    "majorFacilities": ["전산실습실", "학과사무실"]
  }
}
```

음식점, 카페, 편의시설은 `lectureBuildingDetails`를 보내지 않습니다.
`oneLineDescription`과 `imageUrl`은 선택값이므로 아래처럼 생략할 수 있습니다.

```json
{
  "campusCode": "SEOSAN",
  "placeName": "가배앤빈",
  "latitude": 36.691166,
  "longitude": 126.574659,
  "category": "CAFE",
  "address": "충청남도 서산시 해미면 대곡리"
}
```

등록·수정 성공 시 공개 장소 상세 조회와 동일한 응답을 반환합니다.

- 캠퍼스, 장소명, 위도, 경도, 카테고리는 필수입니다.
- 한 줄 소개와 이미지 URL은 선택값입니다. 생략하거나 `null` 또는 빈 문자열로 보내면
  DB에는 `NULL`로 저장되고 조회 응답에서는 `null`이거나 필드가 생략됩니다.
- 음식점, 카페, 편의시설은 `address`가 필수이며 최대 255자입니다.
- 교내시설은 `address`를 보내지 않으며 응답에서도 해당 필드를 생략합니다.
- 교내시설 위치는 `lectureBuildingDetails.location`을 사용합니다.
- 위도는 -90~90, 경도는 -180~180이며 소수점 이하 9자리까지 허용합니다.
- 장소명에서 공백과 밑줄을 제거하고 영문을 대문자로 바꾼 내부 키를 생성합니다.
- 같은 캠퍼스 안에서 내부 키가 같은 장소를 등록하거나 수정할 수 없습니다.
- `LECTURE_BUILDING`은 `lectureBuildingDetails`가 필수입니다.
- 교내시설 이외의 카테고리는 `lectureBuildingDetails`를 보낼 수 없습니다.
- 학과와 주요시설은 각각 한 개 이상이며, 같은 배열 안에서 이름이 중복될 수 없습니다.
- 교내시설에서 다른 카테고리로 변경하면 기존 교내시설 상세정보는 삭제됩니다.
- 관리자 JWT가 없으면 `401`, 일반 사용자 JWT이면 `403`, 수정·삭제할 장소가 없으면 `404`입니다.

## 11. 현재 데이터 경계

- 좌표는 `campus_buildings`에 저장하며 `campus_code`의 `SEOSAN`, `TAEAN`으로
  서산캠과 태안캠을 구분합니다.
- 건물 별칭은 `campus_building_aliases`에 저장합니다. 같은 `본관` 별칭도 캠퍼스가
  다르면 각각 저장할 수 있지만, 같은 캠퍼스 안에서는 하나의 건물에만 연결됩니다.
- 수업 건물 초기 데이터는 서산 11개·태안 3개 건물과 정규화 별칭 29개입니다.
- 전체 장소 좌표는 별도 `campus_places`에 서산 97곳·태안 17곳, 총 114곳을
  저장합니다. `today-locations`는 시간표 강의실과 일치하는 `campus_buildings`
  좌표만 반환하고, 전체 장소는 `/api/campus-map/places`에서 별도로 조회합니다.
- 기존 장소의 카테고리·한 줄 소개·이미지 URL은 자동으로 분류하거나 채우지 않습니다.
  관리자 장소 수정 API를 호출하기 전까지 nullable 상태를 유지합니다.
- 기존 운영 DB에는
  [`campus-building-location-migration-mysql.sql`](campus-building-location-migration-mysql.sql)을
  적용한 뒤
  [`campus-place-location-migration-mysql.sql`](campus-place-location-migration-mysql.sql)을
  적용한 뒤
  [`campus-place-metadata-migration-mysql.sql`](campus-place-metadata-migration-mysql.sql)을
  적용한 뒤
  [`campus-place-address-migration-mysql.sql`](campus-place-address-migration-mysql.sql)을
  적용하고, 마지막으로
  [`campus-place-lecture-building-detail-migration-mysql.sql`](campus-place-lecture-building-detail-migration-mysql.sql)을
  애플리케이션 코드보다 먼저 적용해야 합니다.
- 현재 모델에서 일정과 강의실은 학기별 `CourseOffering`이 아닌 공통 `Course`에
  연결됩니다. 같은 과목코드의 강의실이 다음 학기에 바뀌어도 최초 수입된 강의실이
  유지될 수 있습니다.

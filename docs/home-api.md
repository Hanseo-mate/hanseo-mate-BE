# 메인 페이지 통합 조회 API

## 1. 기능 개요

메인 페이지에 필요한 포스터, 오늘 시간표, 인기 공지, 오늘 학식을 한 번에
조회합니다.

- 비로그인 사용자는 서산 학생식당(`MAIN_STUDENT`) 기준 오늘 학식을 반환합니다.
- 로그인 사용자는 DB에 저장된 `preferredRestaurantType` 기준 오늘 학식만 반환합니다.
- 선택된 식당의 오늘 식단이 없으면 `todayCafeteriaMenus`는 빈 배열 `[]`입니다.
- `festivalFloatingButtonVisible`은 축제 플로팅 버튼 노출 여부입니다. 항상 boolean을 반환하고,
  설정이 없으면 `false`입니다. 관리자 PATCH 완료 후 다음 조회부터 변경값을 반환합니다.
- 응답 헤더는 `Cache-Control: no-store`이며 서버 내부 설정 캐시는 사용하지 않습니다.

## 2. 요청

```http
GET /api/home
```

- Query Parameter와 요청 Body는 없습니다.
- JWT 없이 호출할 수 있습니다.
- 유효한 Bearer JWT를 보내면 로그인 사용자의 오늘 시간표와 선호 식당 정보를
  함께 반환합니다.
- 날짜 기준은 `Asia/Seoul`입니다.

## 3. 성공 응답

```json
{
  "loggedIn": true,
  "festivalFloatingButtonVisible": false,
  "preferredRestaurantType": "TAEAN_STUDENT",
  "posterImageUrls": [
    "https://api.example.com/uploads/home-posters/poster.png"
  ],
  "posters": [
    {
      "id": 1,
      "imageUrl": "https://api.example.com/uploads/home-posters/poster.png",
      "linkUrl": "https://www.hanseo.ac.kr/event/1"
    }
  ],
  "todayCourses": [
    {
      "startTime": "12:00",
      "endTime": "13:00",
      "courseName": "델타프로젝트",
      "buildingName": "디자인관",
      "roomNumber": "401"
    }
  ],
  "popularNotices": [
    {
      "noticeType": "STUDENT_COUNCIL",
      "title": "학생회 공지 제목"
    },
    {
      "noticeType": "ACADEMIC",
      "title": "학사 공지 제목"
    },
    {
      "noticeType": "SCHOLARSHIP",
      "title": "장학 공지 제목"
    }
  ],
  "todayCafeteriaMenus": [
    {
      "restaurantType": "TAEAN_STUDENT",
      "menuDate": "2026-08-20",
      "mealSections": [
        {
          "mealTime": "LUNCH",
          "menuCategory": "KOREAN",
          "dishes": [
            {
              "name": "제육볶음",
              "isMainDish": true
            }
          ]
        }
      ]
    }
  ]
}
```

## 4. 최상위 필드

| 필드 | 타입 | 설명 |
|---|---|---|
| `loggedIn` | Boolean | 로그인 여부 |
| `festivalFloatingButtonVisible` | Boolean | 홈 축제 플로팅 버튼 노출 여부. 필수, null 불가, 기본 false. 로그인 여부와 무관하게 같은 값 |
| `preferredRestaurantType` | String or null | 로그인 사용자의 저장된 선호 학생식당 (`MAIN_STUDENT` 또는 `TAEAN_STUDENT`) |
| `posterImageUrls` | String[] or null | 기존 클라이언트 호환용 포스터 이미지 URL 목록 |
| `posters` | Object[] or null | 포스터 상세 목록 |
| `todayCourses` | Object[] | 로그인 사용자의 오늘 시간표 |
| `popularNotices` | Object[] | 학생회·학사·장학 분야별 인기 공지 |
| `todayCafeteriaMenus` | Object[] | 선택된 학생식당의 오늘 식단. 최대 1개 |

## 5. 오늘 학식 규칙

- 비로그인 사용자는 항상 `MAIN_STUDENT` 식단만 조회합니다.
- 로그인 사용자는 `preferredRestaurantType`에 저장된 학생식당만 조회합니다.
- `todayCafeteriaMenus`에는 최대 1개의 식당만 포함됩니다.
- 교직원식당(`MAIN_STAFF`, `TAEAN_STAFF`)은 메인 응답에서 제외합니다.
- 식단이 없으면 `todayCafeteriaMenus: []`를 반환합니다.
- 식단 데이터가 없더라도 메인 API는 `200 OK`를 유지합니다.

### 오늘 학식 필드

| 필드 | 타입 | 설명 |
|---|---|---|
| `restaurantType` | String | `MAIN_STUDENT` 또는 `TAEAN_STUDENT` |
| `menuDate` | String | 식단 날짜, `yyyy-MM-dd` |
| `mealSections` | Object[] | 점심·저녁 및 메뉴 종류별 식단 |
| `mealSections[].mealTime` | String | `LUNCH` 또는 `DINNER` |
| `mealSections[].menuCategory` | String | `KOREAN`, `SPECIAL`, `NORMAL` |
| `mealSections[].dishes` | Object[] | 음식 목록 |
| `mealSections[].dishes[].name` | String | 음식명 |
| `mealSections[].dishes[].isMainDish` | Boolean | 대표 메뉴 여부 |

### 정렬 규칙

- `mealSections`: `LUNCH` → `DINNER`
- 같은 끼니 내 `menuCategory`: `KOREAN` → `SPECIAL` → `NORMAL`
- `dishes`: 음식 ID 오름차순

## 6. 빈 데이터 처리

- 비로그인이고 오늘 서산 학생식당 식단이 없으면 `todayCafeteriaMenus: []`
- 로그인했고 선호 식당의 오늘 식단이 없으면 `todayCafeteriaMenus: []`
- 로그인하지 않으면 `preferredRestaurantType`은 `null`
- 포스터가 없으면 `posterImageUrls`, `posters`는 `null`
- 오늘 시간표가 없으면 `todayCourses: []`
- 인기 공지가 없는 분야는 `title: null`
- 축제 버튼 설정 행이 없으면 `festivalFloatingButtonVisible: false`

### 축제 플로팅 버튼 연동

- `festivalFloatingButtonVisible === true`일 때만 버튼을 표시합니다.
- 필드 누락, null, 잘못된 타입, 최초 조회/갱신 실패 시 버튼을 숨깁니다. 실패 시 과거 true를 재사용하지 않습니다.
- 버튼 클릭 시 기존 `/festival` 화면 이동과 정적 이미지/문구/위치를 유지합니다.
- 축제 페이지 접근 권한과 앱 시작 팝업 상태는 이 값의 영향을 받지 않습니다.
- 관리자 API 및 타입 계약: [축제 플로팅 버튼 API](festival-floating-button-api.md).

## 7. 인증 및 오류

- JWT는 선택 사항입니다.
- 토큰을 보내지 않으면 공개 정보와 서산 학생식당 기준 오늘 학식만 조회합니다.
- 잘못되었거나 만료된 토큰을 보내면 `401 Unauthorized`입니다.
- 예상하지 못한 서버 오류는 `500 Internal Server Error`입니다.

## 8. 구현 범위

- 메인 API는 DB에 저장된 오늘 식단만 읽습니다.
- 메인 API 요청 시 외부 크롤러를 실시간 호출하지 않습니다.
- 기존 포스터, 오늘 시간표, 인기 공지 계약은 유지합니다.

## 9. CORS

`/api/home`은 설정된 허용 Origin에서 브라우저로 호출할 수 있습니다.

- 허용 Method: `GET`, `OPTIONS`
- 허용 Header: `Authorization`, `Content-Type`, `Accept`
- Credentials: `false`
- Preflight max age: `3600`초

허용되지 않은 Origin의 브라우저 요청은 차단됩니다.

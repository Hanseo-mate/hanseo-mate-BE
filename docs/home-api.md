# 메인 페이지 통합 조회 API

## 1. 기능 개요

메인 페이지에 필요한 포스터, 오늘 시간표, 인기 공지, 오늘 학식을 한 번에
조회합니다. 로그인은 선택 사항이며 학식은 로그인 여부와 관계없이 반환합니다.

## 2. 요청

```http
GET /api/home
```

- Query Parameter와 요청 Body는 없습니다.
- JWT 없이 호출할 수 있습니다.
- 유효한 Bearer JWT를 보내면 로그인 사용자의 오늘 시간표도 반환합니다.
- 날짜는 `Asia/Seoul` 기준 요청 당일입니다.

## 3. 성공 응답

```json
{
  "loggedIn": true,
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
      "restaurantType": "MAIN_STUDENT",
      "menuDate": "2026-05-07",
      "mealSections": [
        {
          "mealTime": "LUNCH",
          "menuCategory": "KOREAN",
          "dishes": [
            {
              "name": "제육볶음",
              "isMainDish": true
            },
            {
              "name": "된장국",
              "isMainDish": false
            }
          ]
        },
        {
          "mealTime": "DINNER",
          "menuCategory": "NORMAL",
          "dishes": [
            {
              "name": "김치볶음밥",
              "isMainDish": true
            }
          ]
        }
      ]
    }
  ]
}
```

## 4. 오늘 학식 필드

| 필드 | 타입 | 설명 |
|---|---|---|
| `todayCafeteriaMenus` | Object[] | 한국 날짜 기준 오늘의 식당별 학식 |
| `restaurantType` | String | `MAIN_STUDENT` 또는 `TAEAN_STUDENT` |
| `menuDate` | String | 식단 날짜, `yyyy-MM-dd` |
| `mealSections` | Object[] | 점심·저녁 및 메뉴 종류별 식단 |
| `mealTime` | String | `LUNCH` 또는 `DINNER` |
| `menuCategory` | String | `KOREAN`, `SPECIAL`, `NORMAL` |
| `dishes` | Object[] | 해당 점심 또는 저녁의 음식 목록 |
| `dishes[].name` | String | 음식명 |
| `dishes[].isMainDish` | Boolean | 대표 메뉴 여부 |

응답 순서는 서산 학생식당, 태안 학생식당 순입니다. DB에 교직원식당 데이터가
있어도 메인 응답에서는 제외합니다. 각 식당 안에서는 점심이 저녁보다 먼저
반환됩니다.

## 5. 데이터가 없을 때

- 오늘 학식 전체가 없으면 `todayCafeteriaMenus: []`를 반환합니다.
- 일부 식당만 데이터가 있으면 존재하는 식당만 반환합니다.
- 점심 또는 저녁 한쪽만 있으면 존재하는 `mealSections`만 반환합니다.
- 학식이 없다는 이유로 메인 API 전체가 `404`가 되지 않습니다.
- 로그인하지 않은 경우 `loggedIn`은 `false`, `todayCourses`는 `[]`입니다.
- 포스터가 없으면 `posterImageUrls`와 `posters`는 `null`입니다.

## 6. 인증 및 오류

- JWT는 선택 사항입니다.
- 토큰을 보내지 않으면 공개 정보와 오늘 학식만 정상 조회합니다.
- 잘못되었거나 만료된 토큰을 보내면 `401 Unauthorized`입니다.
- 예상하지 못한 DB 오류는 `500 Internal Server Error`이며 데이터 없음으로 숨기지 않습니다.

## 7. 구현 범위

- 메인 조회는 DB에 저장된 오늘 식단만 읽습니다.
- 메인 API 요청 시 외부 크롤러를 실시간 호출하지 않습니다.
- 기존 `GET /api/cafeteria/menus`의 조회·404 계약은 변경하지 않습니다.
- 새 테이블, 컬럼, 서버 properties, 운영 DB 마이그레이션은 필요하지 않습니다.

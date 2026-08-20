# 학식 조회 API

## 1. 기능 개요

서산 또는 태안 캠퍼스의 학생식당 메뉴를 조회합니다. DB와 크롤러에는 기존
식당 구분 네 종류를 유지하지만, 공개 API에서는 학생식당 두 종류만 사용합니다.

| `restaurantType` | 의미 |
|---|---|
| `MAIN_STUDENT` | 서산 학생식당 |
| `TAEAN_STUDENT` | 태안 학생식당 |

`MAIN_STAFF`, `TAEAN_STAFF` 데이터는 삭제하지 않으며 공개 조회 결과에서만
제외합니다.

## 2. 요청

```http
GET /api/cafeteria/menus?restaurantType=MAIN_STUDENT
```

인증은 필요하지 않습니다.

| Query Parameter | 필수 | 설명 |
|---|---|---|
| `restaurantType` | 필수 | `MAIN_STUDENT` 또는 `TAEAN_STUDENT` |
| `menuDate` | 선택 | `yyyy-MM-dd`; 지정하면 해당 날짜만, 생략하면 한국 시간 기준 이번 주 월요일~금요일 |
| `menuCategory` | 선택 | `KOREAN`, `SPECIAL`, `NORMAL` |

`MAIN_STAFF`, `TAEAN_STAFF`를 요청하면 공개 조회 대상이 아니므로 `400 Bad Request`를 반환합니다.

## 3. 성공 응답

```json
[
  {
    "id": 1,
    "restaurantType": "MAIN_STUDENT",
    "menuDate": "2026-08-17",
    "dayOfWeek": "MONDAY",
    "mealSections": [
      {
        "id": 10,
        "mealTime": "LUNCH",
        "menuCategory": "KOREAN",
        "dishes": [
          {
            "id": 100,
            "name": "제육볶음",
            "isMainDish": true
          }
        ]
      },
      {
        "id": 11,
        "mealTime": "DINNER",
        "menuCategory": "NORMAL",
        "dishes": [
          {
            "id": 101,
            "name": "김치볶음밥",
            "isMainDish": true
          }
        ]
      }
    ]
  },
  {
    "id": 2,
    "restaurantType": "MAIN_STUDENT",
    "menuDate": "2026-08-18",
    "dayOfWeek": "TUESDAY",
    "mealSections": [
      {
        "id": 20,
        "mealTime": "LUNCH",
        "menuCategory": "NORMAL",
        "dishes": [
          {
            "id": 200,
            "name": "화요일 점심 메뉴",
            "isMainDish": true
          }
        ]
      },
      {
        "id": 21,
        "mealTime": "DINNER",
        "menuCategory": "NORMAL",
        "dishes": [
          {
            "id": 201,
            "name": "화요일 저녁 메뉴",
            "isMainDish": true
          }
        ]
      }
    ]
  }
]
```

- `menuDate`를 생략하면 월요일부터 금요일까지 날짜 오름차순으로 반환됩니다.
- `dayOfWeek`은 `MONDAY`, `TUESDAY`, `WEDNESDAY`, `THURSDAY`, `FRIDAY`입니다.
- `LUNCH`는 점심, `DINNER`는 저녁입니다.
- 점심이 저녁보다 먼저 반환됩니다.
- `KOREAN`은 한식, `SPECIAL`은 일품, `NORMAL`은 일반 메뉴입니다.
- `isMainDish`는 대표 메뉴 여부입니다.

## 4. 오류

- `restaurantType` 누락 또는 허용되지 않은 값: `400 Bad Request`
- 날짜·카테고리 형식 오류: `400 Bad Request`
- 지정 날짜 또는 이번 주 평일에 학생식당 데이터가 전혀 없음: `404 Not Found`
- 예상하지 못한 서버 오류: `500 Internal Server Error`

```json
{
  "status": 404,
  "message": "식단 데이터를 찾을 수 없습니다. [restaurantType=MAIN_STUDENT, menuDate=2026-08-20]",
  "path": "/api/cafeteria/menus",
  "timestamp": "2026-08-20T12:00:00Z"
}
```

## 5. 메인 페이지 연동

`GET /api/home`의 `todayCafeteriaMenus`에는 한국 날짜 기준 오늘의 서산·태안
학생식당만 함께 반환합니다. 교직원식당은 제외되며, 오늘 데이터가 없으면 빈
배열 `[]`입니다.

## 6. 배포 영향

- 기존 DB 데이터 삭제 또는 변환 없음
- 신규 테이블·컬럼·인덱스 없음
- 서버 properties 변경 없음
- 크롤러와 스케줄러의 기존 네 식당 수집 구조는 유지

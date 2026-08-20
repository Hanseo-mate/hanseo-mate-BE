# 학식 통합 조회 API

## 1. 기능 개요

서산 학생식당과 태안 학생식당의 식단을 한 번에 조회합니다.
응답에는 두 식당 버킷이 항상 포함되며, 해당 식당의 데이터가
없으면 `dailyMenus` 는 빈 배열입니다.

| `restaurantType` | 의미 |
|---|---|
| `MAIN_STUDENT` | 서산 학생식당 |
| `TAEAN_STUDENT` | 태안 학생식당 |

DB와 크롤러의 `MAIN_STAFF`, `TAEAN_STAFF` 데이터는 유지하지만
이 API 응답에는 포함하지 않습니다.

## 2. 요청

```http
GET /api/cafeteria/menus
```

인증은 선택 사항입니다.

- JWT 없음: `preferredRestaurantType` 은 `null`
- 유효한 JWT 있음: 로그인 사용자의 선호 식당 반환
- 잘못되었거나 만료된 JWT: `401 Unauthorized`

### Query Parameter

| 파라미터 | 필수 | 설명 |
|---|---:|---|
| `menuDate` | X | `yyyy-MM-dd`; 지정하면 해당 날짜만 조회 |
| `menuCategory` | X | `KOREAN`, `SPECIAL`, `NORMAL` |

`menuDate` 를 생략하면 `Asia/Seoul` 기준 이번 주 월요일부터
금요일까지 조회합니다. `menuDate` 를 지정하면 주말이나 과거·미래
날짜도 조회할 수 있습니다.

기존 `restaurantType` Query Parameter 는 제거되었습니다. 식당별로
따로 요청하지 않고, 프론트에서는 응답의 `restaurants` 버킷을
`restaurantType` 으로 구분합니다.

### 요청 예시

```http
GET /api/cafeteria/menus?menuDate=2026-08-20
```

```http
GET /api/cafeteria/menus?menuCategory=KOREAN
Authorization: Bearer {accessToken}
```

## 3. 성공 응답

```http
200 OK
```

```json
{
  "preferredRestaurantType": "MAIN_STUDENT",
  "restaurants": [
    {
      "restaurantType": "MAIN_STUDENT",
      "dailyMenus": [
        {
          "id": 1,
          "restaurantType": "MAIN_STUDENT",
          "menuDate": "2026-08-20",
          "dayOfWeek": "THURSDAY",
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
        }
      ]
    },
    {
      "restaurantType": "TAEAN_STUDENT",
      "dailyMenus": []
    }
  ]
}
```

### 최상위 필드

| 필드 | 타입 | 설명 |
|---|---|---|
| `preferredRestaurantType` | String \| null | 로그인 사용자의 선호 식당; 비로그인은 `null` |
| `restaurants` | Object[] | 서산·태안 학생식당 버킷 두 개 |

### 식당 버킷

| 필드 | 타입 | 설명 |
|---|---|---|
| `restaurantType` | String | `MAIN_STUDENT` 또는 `TAEAN_STUDENT` |
| `dailyMenus` | Object[] | 조건에 맞는 날짜별 식단; 없으면 `[]` |

### 날짜별 식단

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Number | 날짜별 식단 ID |
| `restaurantType` | String | 해당 식당 구분 |
| `menuDate` | String | `yyyy-MM-dd` |
| `dayOfWeek` | String | `MONDAY`~`SUNDAY` |
| `mealSections` | Object[] | 식사 시간·카테고리별 메뉴 |
| `mealSections[].id` | Number | 식사 구역 ID |
| `mealSections[].mealTime` | String | `LUNCH`, `DINNER` |
| `mealSections[].menuCategory` | String | `KOREAN`, `SPECIAL`, `NORMAL` |
| `mealSections[].dishes` | Object[] | 음식 목록 |
| `dishes[].id` | Number | 음식 ID |
| `dishes[].name` | String | 음식명 |
| `dishes[].isMainDish` | Boolean | 대표 메뉴 여부 |

## 4. 응답 순서

- `restaurants[0]`: `MAIN_STUDENT`
- `restaurants[1]`: `TAEAN_STUDENT`
- `dailyMenus`: 날짜 오름차순
- `mealSections`: `LUNCH` → `DINNER`
- 같은 식사 시간: `KOREAN` → `SPECIAL` → `NORMAL`
- `dishes`: ID 오름차순

## 5. 데이터가 없을 때

데이터가 전혀 없어도 `404` 를 반환하지 않습니다. 두 식당
버킷을 유지한 `200 OK` 응답을 반환합니다.

```json
{
  "preferredRestaurantType": null,
  "restaurants": [
    {
      "restaurantType": "MAIN_STUDENT",
      "dailyMenus": []
    },
    {
      "restaurantType": "TAEAN_STUDENT",
      "dailyMenus": []
    }
  ]
}
```

한 식당만 데이터가 있으면 나머지 식당의 `dailyMenus` 만 빈
배열입니다. 특정 날짜가 없으면 해당 날짜 객체를 만들지
않습니다.

## 6. 프론트엔드 처리

1. `preferredRestaurantType` 이 있으면 해당 버킷을 처음 표시합니다.
2. `null` 이면 앱의 기본값 또는 로컬 선택값을 사용합니다.
3. 선택한 `restaurantType` 과 같은 `restaurants` 항목을 찾습니다.
4. 월~금 화면은 `menuDate` 또는 `dayOfWeek` 로 매칭합니다.
5. 응답 인덱스를 요일로 간주하지 않습니다. 저장되지 않은 날짜는 빠집니다.
6. 점심·저녁은 배열 위치가 아니라 `mealTime` 으로 구분합니다.

## 7. 오류

| 상황 | 상태 |
|---|---:|
| `menuDate` 형식 오류 | `400 Bad Request` |
| `menuCategory` 허용값 아님 | `400 Bad Request` |
| 잘못되었거나 만료된 JWT | `401 Unauthorized` |
| 예상하지 못한 서버·DB 오류 | `500 Internal Server Error` |

정상적인 빈 식단은 오류가 아니므로 `404` 는 사용하지 않습니다.

## 8. 배포 영향

- 식단 테이블 변경 없음
- 식단 데이터 재업로드 불필요
- 크롤러·스케줄러의 기존 네 식당 수집 구조 유지
- `user_accounts.preferred_restaurant_type` 증분 DDL은 앱 배포 전 적용
- 서버 properties 추가 없음

## 9. CORS

`/api/cafeteria/**`는 설정된 허용 Origin에서 브라우저로 호출할 수 있습니다.

- 허용 Method: `GET`, `OPTIONS`
- 허용 Header: `Authorization`, `Content-Type`, `Accept`
- Credentials: `false`
- Preflight max age: `3600`초

허용되지 않은 Origin의 브라우저 요청은 차단됩니다. Postman, 네이티브 앱,
서버 간 요청은 브라우저 CORS 정책의 적용 대상이 아닙니다.

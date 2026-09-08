# 선호 캠퍼스 기능 변경 사항 — 프론트엔드 전달용

## 1. 변경 목적

기존에는 사용자 설정을 `선호 학생식당`으로 저장했습니다. 이제 사용자 설정을
`선호 캠퍼스` 하나로 통합하여 메인페이지 학식과 캠퍼스맵의 최초 선택값에 함께
사용합니다.

- 지원 캠퍼스: `SEOSAN`, `TAEAN`
- 신규 가입자의 기본값: `SEOSAN`
- 메인페이지에서 설정이 적용되는 항목: 오늘 학식
- 캠퍼스맵에서 설정이 적용되는 시점: 장소 목록 화면 최초 진입
- 학식 상세 및 캠퍼스맵 안에서의 임시 캠퍼스 전환은 저장된 설정을 변경하지 않음

## 2. 가장 중요한 Breaking Change

### 이름과 값 변경

| 구분 | 기존 | 변경 |
|---|---|---|
| 사용자 설정 필드 | `preferredRestaurantType` | `preferredCampusCode` |
| 서산 설정값 | `MAIN_STUDENT` | `SEOSAN` |
| 태안 설정값 | `TAEAN_STUDENT` | `TAEAN` |
| 설정 API | `PUT /api/auth/me/cafeteria-preference` | `PUT /api/auth/me/campus-preference` |
| 설정 요청 필드 | `preferredRestaurantType` | `preferredCampusCode` |

기존 설정 API와 기존 요청 필드는 더 이상 사용하지 않습니다.

`restaurantType` 자체가 모든 응답에서 삭제된 것은 아닙니다. 학식 데이터가 어느
식당에서 제공되었는지 나타내는 `restaurantType`은 학식 응답 내부에 그대로
유지됩니다. 사용자 설정값으로만 사용하지 않게 된 것입니다.

## 3. 공통 프론트 타입

```ts
export type CampusCode = 'SEOSAN' | 'TAEAN';

export type StudentRestaurantType =
  | 'MAIN_STUDENT'
  | 'TAEAN_STUDENT';
```

기존 사용자 모델의 다음 필드를 변경해야 합니다.

```ts
// 기존
preferredRestaurantType: StudentRestaurantType;

// 변경
preferredCampusCode: CampusCode;
```

공개 API에서는 로그인 여부에 따라 `preferredCampusCode`가 `null`일 수 있으므로
API별 null 가능 여부를 구분해야 합니다.

| 응답 API | 타입 |
|---|---|
| 회원가입·로그인 | `CampusCode` |
| 마이페이지 | `CampusCode` |
| 메인페이지 | `CampusCode \| null` |
| 학식 상세 | `CampusCode \| null` |

## 4. 선호 캠퍼스 변경 API

```http
PUT /api/auth/me/campus-preference
Authorization: Bearer {accessToken}
Content-Type: application/json
```

### 요청

서산으로 변경:

```json
{
  "preferredCampusCode": "SEOSAN"
}
```

태안으로 변경:

```json
{
  "preferredCampusCode": "TAEAN"
}
```

### 성공 응답

```http
204 No Content
```

응답 본문이 없으므로 JSON 파싱을 시도하지 않습니다.

### 오류

| 상황 | 상태 코드 |
|---|---:|
| 값 누락, `null`, 지원하지 않는 값 | `400 Bad Request` |
| 토큰 누락, 만료 또는 유효하지 않은 토큰 | `401 Unauthorized` |

다음과 같은 기존 값은 요청할 수 없습니다.

```json
{
  "preferredCampusCode": "MAIN_STUDENT"
}
```

### 저장 성공 후 처리

`204`를 받으면 프론트 사용자 상태의 `preferredCampusCode`를 변경하고 다음 쿼리를
무효화하거나 다시 조회합니다.

- `GET /api/auth/me`
- `GET /api/home`
- `GET /api/cafeteria/menus`
- `GET /api/campus-map/places`

## 5. 회원가입 및 로그인 응답 변경

대상 API:

```http
POST /api/auth/signup
POST /api/auth/login
```

### 기존 응답 일부

```json
{
  "preferredRestaurantType": "MAIN_STUDENT"
}
```

### 변경 응답 일부

```json
{
  "preferredCampusCode": "SEOSAN"
}
```

- 회원가입 직후 값은 항상 `SEOSAN`입니다.
- 로그인 응답에는 DB에 저장된 `SEOSAN` 또는 `TAEAN`이 반환됩니다.
- 토큰 관련 필드와 나머지 계정 필드는 변경되지 않았습니다.
- 로그인 성공 시 인증 전역 상태에도 `preferredCampusCode`를 저장해야 합니다.

## 6. 마이페이지 응답 변경

```http
GET /api/auth/me
Authorization: Bearer {accessToken}
```

### 변경 응답 일부

```json
{
  "userId": 1,
  "loginId": "user01",
  "role": "USER",
  "preferredCampusCode": "TAEAN"
}
```

- 기존 `preferredRestaurantType` 필드를 제거하고 `preferredCampusCode`를 사용합니다.
- 마이페이지의 선택 UI는 `SEOSAN`, `TAEAN` 두 값만 제공합니다.
- 선택 UI의 현재값은 응답의 `preferredCampusCode`로 초기화합니다.
- 저장 버튼을 누르면 `PUT /api/auth/me/campus-preference`를 호출합니다.
- 동아리 후기, 좋아요 동아리 등 기존 마이페이지 응답은 이번 변경의 영향을 받지
  않습니다.

## 7. 메인페이지 응답 및 동작 변경

```http
GET /api/home
Authorization: Bearer {accessToken}
```

JWT는 선택 사항이지만 로그인 화면에서는 반드시 Access Token을 전달해야 사용자
설정이 적용됩니다.

### 로그인 사용자 예시

```json
{
  "loggedIn": true,
  "preferredCampusCode": "TAEAN",
  "todayCafeteriaMenus": [
    {
      "restaurantType": "TAEAN_STUDENT",
      "menuDate": "2026-09-08",
      "mealSections": []
    }
  ]
}
```

### 처리 규칙

| 사용자 상태 | `preferredCampusCode` | 반환되는 오늘 학식 |
|---|---|---|
| 비로그인 | `null` | 서산 `MAIN_STUDENT` |
| 로그인 + 서산 설정 | `SEOSAN` | 서산 `MAIN_STUDENT` |
| 로그인 + 태안 설정 | `TAEAN` | 태안 `TAEAN_STUDENT` |

- `todayCafeteriaMenus`에는 선택된 캠퍼스의 식단만 들어가며 최대 1개입니다.
- 선택된 캠퍼스의 오늘 식단이 없으면 `todayCafeteriaMenus: []`입니다.
- 메인페이지에는 캠퍼스맵 장소 데이터가 없으므로 선호 캠퍼스 설정은 메인페이지의
  학식에만 적용됩니다.
- `todayCafeteriaMenus` 내부의 기존 학식 필드 구조는 변경되지 않았습니다.

## 8. 학식 상세 응답 및 화면 처리

```http
GET /api/cafeteria/menus
GET /api/cafeteria/menus?menuDate=2026-09-08
Authorization: Bearer {accessToken}
```

JWT는 선택 사항입니다.

### 응답

```json
{
  "preferredCampusCode": "TAEAN",
  "restaurants": [
    {
      "campusCode": "SEOSAN",
      "restaurantType": "MAIN_STUDENT",
      "dailyMenus": []
    },
    {
      "campusCode": "TAEAN",
      "restaurantType": "TAEAN_STUDENT",
      "dailyMenus": []
    }
  ]
}
```

### 변경 사항

- 최상위 `preferredRestaurantType`이 `preferredCampusCode`로 변경되었습니다.
- 각 `restaurants[]` 항목에 `campusCode`가 추가되었습니다.
- `restaurantType`과 `dailyMenus`는 그대로 유지됩니다.
- 로그인 사용자는 `preferredCampusCode`에 저장값이 반환됩니다.
- 비로그인 사용자는 `preferredCampusCode: null`입니다.
- 로그인 여부와 무관하게 서산·태안 학생식당 버킷 두 개를 모두 반환합니다.

### 화면 처리

```ts
const initialCampusCode: CampusCode =
  response.preferredCampusCode ?? 'SEOSAN';

const selectedRestaurant = response.restaurants.find(
  (restaurant) => restaurant.campusCode === initialCampusCode,
);
```

- 최초 탭은 `preferredCampusCode`와 같은 `campusCode` 항목으로 선택합니다.
- 비로그인이면 프론트 기본 탭을 `SEOSAN`으로 선택합니다.
- 화면에서 서산·태안 탭을 바꿀 때는 이미 받은 `restaurants` 배열에서 해당
  `campusCode` 항목을 선택하면 됩니다.
- 탭 전환만으로 선호 캠퍼스 변경 API를 호출하지 않습니다.
- 사용자가 명시적으로 기본 캠퍼스 저장을 선택할 때만 설정 API를 호출합니다.
- 배열 인덱스가 아니라 `campusCode`로 항목을 찾습니다.

## 9. 캠퍼스맵 최초 진입 동작 변경

캠퍼스맵 API URL과 응답 필드 이름은 변경되지 않았습니다. 로그인 사용자가
`campusCode`를 생략했을 때의 기본 필터 동작이 변경되었습니다.

```http
GET /api/campus-map/places
Authorization: Bearer {accessToken}
```

### 로그인 사용자

`campusCode`를 보내지 않으면 저장된 `preferredCampusCode`로 자동 필터링됩니다.

```json
{
  "selectedCampusCode": "TAEAN",
  "places": []
}
```

- 캠퍼스맵 최초 진입 요청에는 Access Token을 전달합니다.
- 응답의 `selectedCampusCode`를 현재 선택된 탭 또는 지도 상태에 반영합니다.
- 토큰을 보내지 않으면 서버는 사용자를 식별할 수 없어 저장된 기본 캠퍼스를 적용할
  수 없습니다.

### 비로그인 사용자

`campusCode` 없이 호출하면 기존처럼 전체 캠퍼스 장소가 반환됩니다.

```json
{
  "selectedCampusCode": null,
  "places": []
}
```

`places` 예시는 생략한 것이며 실제로는 서산·태안 장소가 함께 들어올 수 있습니다.

## 10. 캠퍼스맵 안에서 캠퍼스 임시 전환

서산 조회:

```http
GET /api/campus-map/places?campusCode=SEOSAN
Authorization: Bearer {accessToken}
```

태안 조회:

```http
GET /api/campus-map/places?campusCode=TAEAN
Authorization: Bearer {accessToken}
```

명시적으로 전달한 `campusCode`가 저장된 선호 캠퍼스보다 우선합니다.

```json
{
  "selectedCampusCode": "SEOSAN",
  "places": []
}
```

- 이 요청은 이번 조회 결과만 전환합니다.
- 저장된 `preferredCampusCode`는 변경되지 않습니다.
- 다시 기본 캠퍼스로 돌아갈 때는 `campusCode` 없이 인증 헤더와 함께 목록 API를
  호출하면 됩니다.
- 장소 상세 API `GET /api/campus-map/places/{placeId}`는 변경되지 않았습니다.
- 시간표 위치 API `GET /api/timetables/today-locations` 및
  `GET /api/timetables/weekly-locations`도 이번 변경의 영향을 받지 않습니다.

## 11. 권장 프론트 상태 구조

저장된 사용자 설정과 현재 화면에서 임시로 선택한 캠퍼스를 분리합니다.

```ts
type UserState = {
  preferredCampusCode: CampusCode;
};

type CafeteriaScreenState = {
  selectedCampusCode: CampusCode;
};

type CampusMapScreenState = {
  selectedCampusCode: CampusCode | null;
};
```

- `preferredCampusCode`: 서버에 저장된 사용자 기본값
- 각 화면의 `selectedCampusCode`: 해당 화면에서만 사용하는 현재 선택값
- 화면 탭을 바꿀 때 `preferredCampusCode`를 자동으로 덮어쓰지 않습니다.

## 12. 권장 화면 흐름

### 로그인 직후

1. 로그인 응답의 `preferredCampusCode`를 전역 사용자 상태에 저장합니다.
2. 메인페이지 요청에 Access Token을 전달합니다.
3. 메인페이지는 설정된 캠퍼스의 오늘 학식을 반환합니다.

### 마이페이지에서 기본 캠퍼스 변경

1. 현재 `preferredCampusCode`를 선택 UI에 표시합니다.
2. 사용자가 `SEOSAN` 또는 `TAEAN`을 선택합니다.
3. `PUT /api/auth/me/campus-preference`를 호출합니다.
4. `204` 성공 후 전역 사용자 상태를 갱신합니다.
5. 홈, 학식, 캠퍼스맵 관련 캐시를 무효화하거나 재조회합니다.

### 학식 상세 진입

1. `GET /api/cafeteria/menus`를 호출합니다.
2. `preferredCampusCode ?? 'SEOSAN'`을 최초 선택값으로 사용합니다.
3. 같은 `campusCode`의 `restaurants[]` 항목을 표시합니다.
4. 이후 탭 전환은 로컬 화면 상태로만 처리합니다.

### 캠퍼스맵 진입

1. 로그인 상태면 Access Token을 포함하고 `campusCode` 없이 호출합니다.
2. 응답의 `selectedCampusCode`를 최초 선택값으로 사용합니다.
3. 사용자가 캠퍼스를 바꾸면 `campusCode` 쿼리 파라미터로 다시 조회합니다.
4. 화면 전환만으로 설정 API는 호출하지 않습니다.

## 13. 프론트 수정 체크리스트

- [ ] 사용자 모델의 `preferredRestaurantType` 제거
- [ ] 사용자 모델에 `preferredCampusCode: 'SEOSAN' | 'TAEAN'` 추가
- [ ] 회원가입 응답 파싱 필드 변경
- [ ] 로그인 응답 파싱 필드 변경
- [ ] 마이페이지 응답 파싱 필드 변경
- [ ] 메인페이지 응답 파싱 필드 변경
- [ ] 학식 상세 응답 파싱 필드 변경
- [ ] 설정 API를 `/api/auth/me/campus-preference`로 변경
- [ ] 설정 요청 Body를 `preferredCampusCode`로 변경
- [ ] 설정 요청값을 `SEOSAN`, `TAEAN`으로 변경
- [ ] `204 No Content` 응답을 JSON으로 파싱하지 않도록 처리
- [ ] 로그인 상태의 홈 요청에 Access Token 전달
- [ ] 로그인 상태의 캠퍼스맵 최초 요청에 Access Token 전달
- [ ] 학식 상세의 `restaurants[].campusCode` 필드 타입 추가
- [ ] 학식 상세 최초 탭을 `preferredCampusCode`로 선택
- [ ] 캠퍼스맵 최초 탭을 응답의 `selectedCampusCode`로 선택
- [ ] 학식·캠퍼스맵 화면 전환과 사용자 기본 설정 저장을 분리
- [ ] 설정 저장 후 홈·학식·캠퍼스맵·마이페이지 캐시 갱신
- [ ] 기존 `/api/auth/me/cafeteria-preference` 호출 제거
- [ ] 기존 설정값 `MAIN_STUDENT`, `TAEAN_STUDENT` 전송 제거

## 14. QA 시나리오

1. 신규 가입 후 `preferredCampusCode`가 `SEOSAN`인지 확인합니다.
2. 서산 설정 계정의 메인페이지에서 서산 학식만 나오는지 확인합니다.
3. 태안으로 저장한 뒤 메인페이지에서 태안 학식만 나오는지 확인합니다.
4. 태안 설정 계정으로 캠퍼스맵 최초 진입 시 태안 장소만 나오는지 확인합니다.
5. 캠퍼스맵에서 서산으로 임시 전환한 뒤 다시 진입했을 때 기본값이 여전히 태안인지
   확인합니다.
6. 학식 상세에서 서산·태안 데이터가 모두 전달되고 최초 탭만 태안인지 확인합니다.
7. 학식 상세 탭을 서산으로 바꿔도 마이페이지 저장값이 태안으로 유지되는지
   확인합니다.
8. 비로그인 메인페이지가 서산 학식만 표시하는지 확인합니다.
9. 비로그인 학식 상세에서 두 캠퍼스가 모두 표시되는지 확인합니다.
10. 비로그인 캠퍼스맵 무필터 요청에서 전체 장소가 표시되는지 확인합니다.

## 15. 배포 연동 주의사항

이번 변경은 기존 필드명과 설정 API가 바뀌는 Breaking Change입니다. 프론트가 새
필드와 새 URL을 사용하기 전에 변경된 백엔드가 먼저 배포되어 있어야 합니다.
백엔드와 프론트의 배포 시점이 다르면 회원가입·로그인 응답 파싱과 설정 저장 요청이
실패할 수 있으므로 같은 릴리스 범위로 맞추는 것을 권장합니다.

# 동아리 API 명세서

## 공통 정보

```text
Base URL: http://localhost:8080
JSON Content-Type: application/json
Image Content-Type: multipart/form-data
```

JWT 로그인과 역할 기반 권한 검사가 적용되어 있으며 `/api/admin/**`는 ADMIN 역할만 접근할 수 있다.
`CLUB_ADMIN`은 앱에서 모든 동아리의 모집공고만 수정할 수 있으며 `/api/admin/**`에는 접근할 수 없다.
활동 후기 조회는 공개 API이고, 활동 후기 등록·수정·제거에는 로그인 JWT가 필요하다.
좋아요 변경에는 로그인 JWT가 필요하며 사용자별·동아리별로 좋아요 한 건만 저장된다.
토글 API를 호출할 때마다 해당 좋아요가 등록 또는 취소된다.
동아리 목록과 상세 조회는 로그인 없이 사용할 수 있다. 유효한 JWT를 함께 보내면
`likedByMe`에 현재 사용자의 좋아요 여부가 반영되고, 토큰이 없으면 `false`를 반환한다.
잘못되거나 만료된 토큰을 명시적으로 보내면 `401 Unauthorized`를 반환한다.

---

## API 목록

| 구분 | Method | URL | 기능 |
|---|---|---|---|
| 사용자 | `GET` | `/api/clubs` | 분과·모집중만 보기 필터와 검색결과 개수 조회 |
| 사용자 | `GET` | `/api/clubs/{clubId}` | 동아리 전체 상세 정보 조회 |
| 사용자 | `POST` | `/api/clubs/likes/{clubId}` | 로그인 사용자의 좋아요 상태 토글 |
| 사용자 | `GET` | `/api/clubs/reviews/{clubId}` | 활동 후기 비율 조회 |
| 사용자 | `GET` | `/api/clubs/reviews/{clubId}/me` | 로그인 사용자의 후기 등록 상태 조회 |
| 사용자 | `PUT` | `/api/clubs/reviews/{clubId}` | 로그인 사용자의 활동 후기 등록·수정·제거 |
| 동아리관리자 | `PUT` | `/api/clubs/{clubId}/recruitment` | 모든 동아리의 모집공고 내용·기간·링크·상시모집 여부 작성·수정·삭제 |
| 관리자 | `GET` | `/api/admin/clubs` | 분과·모집중만 보기 필터와 검색결과 개수 조회 |
| 관리자 | `PUT` | `/api/admin/clubs/order` | 전체 동아리 표시 순서 저장 |
| 관리자 | `GET` | `/api/admin/clubs/{clubId}` | 동아리 전체 상세 정보 조회 |
| 관리자 | `POST` | `/api/admin/clubs` | 동아리 등록 |
| 관리자 | `PUT` | `/api/admin/clubs/background-images/{clubId}` | 배경 이미지 파일 업로드 |
| 관리자 | `DELETE` | `/api/admin/clubs/background-images/{clubId}` | 배경 이미지 삭제 |
| 관리자 | `PUT` | `/api/admin/clubs/profile-images/{clubId}` | 프로필 이미지 파일 업로드 |
| 관리자 | `DELETE` | `/api/admin/clubs/profile-images/{clubId}` | 프로필 이미지 삭제 |
| 관리자 | `PUT` | `/api/admin/clubs/{clubId}` | 동아리 텍스트 정보 통합 수정 |
| 관리자 | `DELETE` | `/api/admin/clubs/{clubId}` | 동아리 삭제 |

## 모집 기간과 모집 상태

모집공고의 내용(`recruitmentContent`), 시작일(`recruitmentStartDate`), 종료일
(`recruitmentEndDate`), 링크(`recruitmentUrl`)는 각각 선택 입력이며 모두 `null`을 허용한다.
추가 입력 `recruitmentAlwaysOpen`은 boolean이며 생략하거나 `null`이면 `false`로 저장한다.
날짜는 `YYYY-MM-DD` 형식이다. 상시모집이 `false`이고 두 날짜가 모두 있으면 종료일은 시작일과 같거나 이후여야 한다.
과거 기간과 하루짜리 모집도 저장할 수 있다. 링크는 `http` 또는 `https` URL이며 최대 2048자다.

목록과 상세에 `recruitmentAlwaysOpen`과 `recruitmentStatus`를 반환한다. 상태는 상시모집 여부를 먼저 확인하고,
상시모집이 아니면 한국 시간(`Asia/Seoul`)의 오늘 날짜로 조회 시 계산하며 DB에 별도 저장하지 않는다.

| 값 | 화면 표시 | 조건 |
|---|---|---|
| `ALWAYS_OPEN` | 상시모집 | `recruitmentAlwaysOpen === true`. 날짜와 관계없이 최우선 적용 |
| `UPCOMING` | 모집전 | 상시모집이 false이고 두 날짜가 있으며 오늘 < 시작일 |
| `RECRUITING` | 모집중 | 상시모집이 false이고 시작일 ≤ 오늘 ≤ 종료일 |
| `CLOSED` | 모집완료 | 상시모집이 false이고 오늘 > 종료일 또는 시작일·종료일 중 하나 이상 없음 |

상시모집이 `true`이면 날짜가 없거나 한쪽만 있거나 과거·미래·역순이어도 `ALWAYS_OPEN`이다.
입력한 날짜와 요일은 그대로 저장·조회하며, 날짜 형식과 링크 검증은 계속 적용한다.
상시모집을 `false`로 바꿀 때는 날짜 순서 검증을 다시 적용하므로 역순 날짜는 수정하거나 비워야 한다.

상시모집이 아니면 시작일 00:00부터 종료일 23:59:59까지 모집중이다.
내용이나 링크가 없어도 두 날짜가 있다면 기간에 따른 상태를 반환한다. 날짜가 없거나
한쪽만 있으면 `CLOSED`다. 기존 동아리의 상시모집 값은 `false`이며 기존 내용과 날짜는 보존한다.
프론트는 이 값을 화면 문구로 매핑하고,
상세 응답의 두 날짜와 `recruitmentAlwaysOpen`을 편집 화면 초기값으로 사용한다.
상태 enum인 `recruitmentStatus`는 조회 전용이며 저장 요청에는 포함하지 않는다.
예제의 상태는 한국 날짜 2026-10-23을 기준으로 한다.

### 기간 조회 시 요일 표시

일반 상세, 마이페이지의 모집공고 관리 상세, 관리자 페이지는 기간을 조회할 때
날짜와 요일을 함께 표시한다. 작성·수정 입력창에서는 연·월·일만 선택하고,
요청에는 기존처럼 `YYYY-MM-DD` 또는 `null`을 보낸다.

상세 조회 응답에 다음 두 필드를 추가한다. 기존 날짜 필드의 형식은 바뀌지 않는다.

| 응답 필드 | 타입 | 설명 |
|---|---|---|
| `recruitmentStartDayOfWeek` | String 또는 null | 시작일에서 계산한 요일. 시작일이 null이면 null |
| `recruitmentEndDayOfWeek` | String 또는 null | 종료일에서 계산한 요일. 종료일이 null이면 null |

| API 값 | 화면 표시 |
|---|---|
| `MONDAY` | 월요일 |
| `TUESDAY` | 화요일 |
| `WEDNESDAY` | 수요일 |
| `THURSDAY` | 목요일 |
| `FRIDAY` | 금요일 |
| `SATURDAY` | 토요일 |
| `SUNDAY` | 일요일 |

예: 시작일 `2026-10-25` / `SUNDAY`, 종료일 `2026-10-31` / `SATURDAY`이면
`2026.10.25 (일요일) ~ 2026.10.31 (토요일)`로 표시한다.
상시모집이면 기간 표시 영역에 `상시모집`을 표시한다. 상시모집이 아니면서 날짜가 없는 쪽은
`시작일 미정` 또는 `종료일 미정`, 둘 다 없으면 `기간 미정`으로 표시한다.
한쪽 날짜만 있어도 그 날짜의 요일은 반환한다. 두 요일 필드는 조회 전용이며
저장 요청에는 포함하지 않는다. 요일은 조회 당시의 오늘 날짜가 아닌 저장된 모집 날짜에서 계산한다.

---

# 사용자 API

## 1. 동아리 목록 조회

```http
GET /api/clubs
GET /api/clubs?category=ACADEMIC
GET /api/clubs?recruitingOnly=true
GET /api/clubs?category=ACADEMIC&recruitingOnly=true
```

### 쿼리 파라미터

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `category` | string | 아니오 | 분과 코드. 생략하거나 비우면 전체 분과 |
| `recruitingOnly` | boolean | 아니오 | 모집중인 것만 보기 토글. `true`는 ON, `false` 또는 생략은 OFF |

분과 코드는 `ACADEMIC`, `VOLUNTEER`, `SPORTS`, `RELIGION`, `PERFORMANCE`, `HOBBY`다.
분과의 앞뒤 공백과 대소문자는 정규화한다. 전체 분과를 선택하면 `category`를 생략한다.
`category=ALL`은 지원하지 않는다.

| 선택한 분과 | 토글 | 요청 예시 | 결과 및 개수 기준 |
|---|---|---|---|
| 전체 | OFF | `/api/clubs?recruitingOnly=false` | 모든 동아리 |
| 전체 | ON | `/api/clubs?recruitingOnly=true` | 전체 동아리 중 모집중 또는 상시모집인 동아리 |
| 학술 | OFF | `/api/clubs?category=ACADEMIC&recruitingOnly=false` | 학술 동아리 전체 |
| 학술 | ON | `/api/clubs?category=ACADEMIC&recruitingOnly=true` | 학술 동아리 중 모집중 또는 상시모집인 동아리 |

두 조건은 **AND**로 적용한다. `true`일 때 `RECRUITING`과 `ALWAYS_OPEN`을 반환하며,
모집전·모집완료는 제외한다. 상시모집은 시작일 또는 종료일이 없어도 포함한다.
`false`는 모집 상태 필터만 해제하므로 선택한 분과 조건은 계속 적용된다.
모집 상태를 직접 보내는 쿼리 파라미터는 사용하지 않는다.

### 응답

`GET /api/clubs?category=ACADEMIC&recruitingOnly=true` 응답 예시:

```json
{
  "totalCount": 1,
  "clubs": [
    {
      "id": 1,
      "name": "멋쟁이사자처럼 한서대학교",
      "category": "ACADEMIC",
      "profileImageUrl": "http://localhost:8080/uploads/clubs/profile/example.png",
      "shortDescription": "함께 서비스를 만드는 IT 동아리",
      "likeCount": 20,
      "likedByMe": true,
      "topReviewTags": [
        "BUILD_RESUME",
        "ACADEMIC_PASSION"
      ],
      "recruitmentStatus": "RECRUITING",
      "recruitmentAlwaysOpen": false
    }
  ]
}
```

`totalCount`는 두 필터를 모두 적용한 검색결과 개수다. 페이지네이션이 없으므로
항상 `clubs.length`와 같다. 조건에 맞는 동아리가 없으면 `200 OK`와 다음 응답을 반환한다.

```json
{
  "totalCount": 0,
  "clubs": []
}
```

**응답 형식 변경:** 기존 최상위 배열이 `{ "totalCount": ..., "clubs": [...] }` 객체로 바뀐다.
사용자 앱과 관리자 페이지 모두 목록을 `response.clubs`에서 읽도록 변경해야 한다.
화면에는 `검색결과 ${response.totalCount}개`를 표시한다.

프론트는 선택한 분과와 토글 값을 각각 유지하고, 둘 중 하나를 바꿀 때 두 값을 함께 보내 재조회한다.
분과를 바꿔도 ON인 토글을 유지하고, 토글을 끄더라도 선택한 분과를 유지한다.
목록과 검색결과 개수는 같은 응답으로 갱신한다. 빠르게 필터를 바꿀 때는 이전 요청을 취소하거나
최신 요청 응답만 반영해 이전 조건의 결과가 화면을 덮어쓰지 않게 한다.

지원하지 않는 분과 또는 boolean으로 해석할 수 없는 토글 값(예: `recruitingOnly=invalid`)은
`400 Bad Request`를 반환한다. 프론트는 `true` 또는 `false`를 보낸다.

`recruitmentStatus`와 `recruitmentAlwaysOpen`을 포함하며 `displayOrder`는 응답에 포함하지 않는다.
`clubs` 배열은 관리자가 저장한 표시 순서대로 반환한다. 같은 내부 순서 값이면 `id` 오름차순으로 정렬한다.
전체·분과별·모집중만 보기 목록 모두 같은 전역 순서를 사용한다.
새 동아리는 목록 마지막에 추가된다. 프론트는 `clubs` 배열 순서대로 표시한다.

`topReviewTags`는 누적 선택 수가 많은 순서대로 최대 2개를 반환한다.
`likedByMe`는 로그인한 사용자가 해당 동아리에 좋아요를 등록했는지를 의미한다.
비로그인 요청에서는 모든 동아리가 `false`이며, `likeCount`는 로그인 여부와 관계없이
각 동아리의 전체 좋아요 개수를 반환한다.

---

## 2. 동아리 상세 조회

```http
GET /api/clubs/{clubId}
```

### 응답

```json
{
  "backgroundImageUrl": "http://localhost:8080/uploads/clubs/background/example.png",
  "profileImageUrl": "http://localhost:8080/uploads/clubs/profile/example.png",
  "likeCount": 20,
  "likedByMe": true,
  "name": "멋쟁이사자처럼 한서대학교",
  "shortDescription": "함께 서비스를 만드는 IT 동아리",
  "topReviewTags": [
    "BUILD_RESUME",
    "ACADEMIC_PASSION",
    "SOCIALIZING"
  ],
  "introduction": "동아리 소개 장문 내용 🎉",
  "activityContent": "동아리 활동 장문 내용 🚀",
  "instagramUrl": "https://instagram.com/example",
  "kakaoTalkUrl": "https://open.kakao.com/o/example",
  "recruitmentContent": "현재 신입 부원을 모집합니다 🙌",
  "recruitmentUrl": "https://example.com/apply",
  "reviewerCount": 3,
  "recruitmentStartDate": "2026-10-25",
  "recruitmentEndDate": "2026-10-31",
  "recruitmentStatus": "UPCOMING",
  "recruitmentStartDayOfWeek": "SUNDAY",
  "recruitmentEndDayOfWeek": "SATURDAY",
  "recruitmentAlwaysOpen": false
}
```

`topReviewTags`는 누적 선택 수가 많은 순서대로 최대 3개를 반환한다.
`reviewerCount`는 현재 해당 동아리에 활동 후기를 남긴 로그인 사용자 수다.
`likedByMe`는 현재 로그인 사용자가 해당 동아리에 좋아요를 등록했는지를 나타낸다.
토큰이 없으면 `false`이며 조회 권한은 제한하지 않는다.

---

## 3. 좋아요 토글

```http
POST /api/clubs/likes/{clubId}
Authorization: Bearer {accessToken}
```

요청 본문은 보내지 않는다. 호출할 때마다 현재 로그인 사용자의 좋아요 상태가 반전된다.

### 응답

```json
{
  "clubId": 1,
  "likedByMe": true,
  "likeCount": 21
}
```

- 현재 사용자의 좋아요가 없으면 등록하고 `likedByMe=true`를 반환한다.
- 현재 사용자의 좋아요가 있으면 삭제하고 `likedByMe=false`를 반환한다.
- 연속으로 두 번 호출하면 등록 후 취소되어 원래 상태로 돌아간다.
- `likeCount`는 현재 좋아요를 누른 로그인 사용자 수다.
- 토큰이 없거나 유효하지 않은 경우 `401 Unauthorized`를 반환한다.
- 중복 클릭으로 상태가 두 번 반전되지 않도록 요청 처리 중에는 프론트의 좋아요 버튼을 비활성화하는 것을 권장한다.

---

## 4. 활동 후기 통계 조회

```http
GET /api/clubs/reviews/{clubId}
```

### 응답

```json
{
  "options": [
    {
      "reviewTag": "BUILD_RESUME",
      "percentage": 42.86
    },
    {
      "reviewTag": "ACADEMIC_PASSION",
      "percentage": 28.57
    },
    {
      "reviewTag": "ENJOY_HOBBY",
      "percentage": 0.00
    }
  ]
}
```

`options`에는 25개 enum 항목이 항상 전부 반환된다.

```text
percentage = 해당 태그 선택 수 ÷ 전체 태그 선택 수 × 100
```

- 소수 둘째 자리까지 반올림한다.
- 선택표가 없으면 모든 비율은 `0.00`이다.
- 프론트엔드는 `reviewTag` enum으로 문구와 이모지를 매핑한다.

---

## 5. 내 활동 후기 상태 조회

```http
GET /api/clubs/reviews/{clubId}/me
Authorization: Bearer {accessToken}
```

### 후기를 작성한 경우

```json
{
  "hasReview": true,
  "reviewTags": [
    "BUILD_RESUME",
    "SOCIALIZING"
  ]
}
```

### 후기를 작성하지 않은 경우

```json
{
  "hasReview": false,
  "reviewTags": []
}
```

- 로그인한 사용자가 해당 동아리에 작성한 후기만 조회한다.
- 다른 사용자의 후기는 `hasReview`와 `reviewTags`에 영향을 주지 않는다.
- 태그는 활동 후기 enum 선언 순서로 반환한다.
- 토큰이 없거나 유효하지 않은 경우: `401 Unauthorized`
- 존재하지 않는 동아리인 경우: `404 Not Found`

---

## 6. 내 활동 후기 등록·수정·제거

```http
PUT /api/clubs/reviews/{clubId}
Authorization: Bearer {accessToken}
Content-Type: application/json
```

### 등록 요청

```json
{
  "reviewTags": [
    "BUILD_RESUME",
    "ACADEMIC_PASSION"
  ]
}
```

1개 이상 5개 이하의 태그를 보내면 로그인한 사용자의 후기를 저장한다.
같은 사용자가 같은 동아리에 다시 요청하면 새 후기를 누적하지 않고 기존 후기를 교체한다.

### 내 후기 제거 요청

```json
{
  "reviewTags": []
}
```

빈 배열, 빈 객체 또는 요청 본문 없이 호출하면 로그인한 사용자가 해당 동아리에 작성한 후기만 제거한다.
다른 사용자의 후기는 변경되지 않는다.

### 등록 응답

```json
{
  "message": "활동 후기가 등록되었습니다."
}
```

### 제거 응답

```json
{
  "message": "활동 후기가 삭제되었습니다."
}
```

별도의 활동 후기 `DELETE` API는 제공하지 않는다.

- 로그인하지 않거나 토큰이 유효하지 않은 경우: `401 Unauthorized`
- 존재하지 않는 동아리인 경우: `404 Not Found`
- 로그인 사용자별로 동아리당 현재 후기 1건만 저장한다.
- 후기 통계와 동아리 상세의 `reviewerCount`는 저장된 로그인 사용자 후기 기준으로 즉시 다시 계산된다.

---

# 동아리관리자 API

`CLUB_ADMIN` 계정으로 로그인하면 로그인 응답과 `GET /api/auth/me`의 `role`이
`CLUB_ADMIN`이다. 이 역할일 때 마이페이지에 **동아리 모집 공고 관리** 메뉴를 표시한다.
일반 동아리 상세 화면은 이 역할로 로그인해도 조회용으로 사용하며,
모집공고 수정 버튼이나 편집 화면으로 이동하는 동작을 제공하지 않는다.

| 역할 | 마이페이지 관리 메뉴 | 모집공고 PUT | `/api/admin/**` |
|---|---|---|---|
| `USER` | 표시하지 않음 | 접근 불가 | 접근 불가 |
| `CLUB_ADMIN` | 표시 | 모든 동아리 관리 가능 | 접근 불가 |
| `ADMIN` | 표시하지 않음 | 접근 불가 | 기존 관리자 API 사용 |

### 화면별 연결 순서

1. 마이페이지에서 `GET /api/auth/me`를 호출하고 `role === "CLUB_ADMIN"`이면 관리 메뉴를 표시한다.
2. 메뉴를 누르면 `GET /api/clubs`로 **전체 동아리 목록**을 조회한다. `category`는 생략하고
   `recruitingOnly`는 생략하거나 `false`를 보낸다. `clubs`로 목록을, `totalCount`로 개수를 표시한다.
   마이페이지의 `likedClubs`는 찜 목록이므로 관리할 동아리 목록으로 사용하지 않는다.
3. 목록의 `id`를 선택하면 `GET /api/clubs/{clubId}`를 호출하여 별도의 **모집공고 관리 상세 화면**을 연다.
4. 관리 상세의 **수정하기**를 누르면 시작일·종료일·내용·신청 링크를 편집한다.
5. 아래 PUT API로 저장하고 `204`를 받으면 상세를 다시 조회한다. 목록으로 돌아갈 때도 목록을
   다시 조회하거나 캐시를 갱신하여 바뀐 모집 상태를 표시한다.

목록과 상세는 기존 공개 조회 API를 재사용한다. 화면 접근과 메뉴 노출은 프론트에서
`CLUB_ADMIN`으로 제한하고, PUT 저장 권한은 서버에서도 검사한다. 관리 목록에 표시할
동아리가 없으면 `totalCount: 0`, `clubs: []`를 반환한다.

### 모집공고 관리 상세 화면

| 화면 항목 | 상세 응답 필드 | 편집 가능 여부 |
|---|---|---|
| 동아리 사진 | `profileImageUrl` (배경이 필요하면 `backgroundImageUrl`) | 조회만 가능 |
| 동아리 이름 | `name` | 조회만 가능 |
| 한 줄 소개 | `shortDescription` | 조회만 가능 |
| 모집 상태 | `recruitmentStatus` | 상시모집 여부와 날짜에 따라 자동 결정 |
| 상시모집 여부 | `recruitmentAlwaysOpen` | true/false 수정 가능 |
| 시작일 | `recruitmentStartDate`, `recruitmentStartDayOfWeek` | 날짜만 수정 가능 |
| 종료일 | `recruitmentEndDate`, `recruitmentEndDayOfWeek` | 날짜만 수정 가능 |
| 모집 내용 | `recruitmentContent` | 수정 가능 |
| 신청 링크 | `recruitmentUrl` | 수정 가능 |

기간 표시에는 위의 요일 매핑을 사용한다. 이미지·한 줄 소개·모집 내용·링크가 `null`이면
각 항목의 빈 상태를 표시하며, 링크가 없으면 신청 링크 버튼을 표시하지 않는다.

관리 화면에서 편집할 동아리를 선택하면 `GET /api/clubs/{clubId}`로 기존 값을 조회하고,
`recruitmentContent`, `recruitmentStartDate`, `recruitmentEndDate`, `recruitmentUrl`, `recruitmentAlwaysOpen`을
입력 필드의 초기값으로 사용한다. 이 조회 API 호출을 위해 일반 상세 화면으로 이동할 필요는 없다.
입력창에서 지운 값은 각각 `null`로 보낸다. 날짜를 비웠을 때는 빈 문자열 대신 `null`을 보낸다.

### 모집공고 작성·수정·삭제

관리 화면은 아래 하나의 `PUT` API로 공고를 작성·수정·삭제한다. 사진·이름·한 줄 소개·모집 상태·요일은
저장 요청에 포함하지 않는다. 최초 동아리 등록은 기존 `ADMIN`의 `POST /api/admin/clubs`를 사용한다.

```http
PUT /api/clubs/{clubId}/recruitment
Authorization: Bearer {accessToken}
Content-Type: application/json

{
  "recruitmentContent": "신입 부원을 모집합니다.",
  "recruitmentStartDate": "2026-10-25",
  "recruitmentEndDate": "2026-10-31",
  "recruitmentUrl": "https://example.com/apply",
  "recruitmentAlwaysOpen": false
}
```

성공 시 `204 No Content`를 반환한다. 내용·시작일·종료일·링크 네 필드는 각각 `null`을 허용하며 링크만 저장하거나
내용 없이 날짜를 저장하는 것도 가능하다. 내용과 링크의 앞뒤 공백은 제거하고 빈 문자열은 `null`로 저장한다.
링크가 있다면 `http` 또는 `https` URL을 보내야 한다.

이 API는 상시모집 여부를 포함한 다섯 값을 모두 대체한다. **내용·날짜·링크는 생략하면 `null`,
상시모집은 생략하거나 `null`이면 `false`로 저장하므로 유지할 값은 함께 보내야 한다.**
날짜 없이 상시모집을 저장하는 요청 예시는 다음과 같다.

```json
{
  "recruitmentContent": "언제든 지원할 수 있습니다.",
  "recruitmentStartDate": null,
  "recruitmentEndDate": null,
  "recruitmentUrl": "https://example.com/apply",
  "recruitmentAlwaysOpen": true
}
```

공고를 삭제하려면 내용·시작일·종료일·링크를 모두 `null`, 상시모집을 `false`로 보내거나 빈 객체 `{}`를 보낸다.
요청 본문 자체는 필요하며, 모두 비우는 요청은 다음과 같다.

```json
{
  "recruitmentContent": null,
  "recruitmentStartDate": null,
  "recruitmentEndDate": null,
  "recruitmentUrl": null,
  "recruitmentAlwaysOpen": false
}
```

전체 삭제 후 상태는 `CLOSED`다. 상시모집이 `false`이고 두 날짜의 순서가 거꾸로이면 `400 Bad Request`다.
날짜 형식·링크가 잘못되면 상시모집 여부와 관계없이 `400 Bad Request`다.

새 공고를 등록하거나 기존 공고의 내용·기간·링크·상시모집 여부 중 하나를 변경하면 해당 동아리를 좋아요한 사용자에게
기존 관리자 수정 API와 동일한 알림을 생성한다. 다섯 값이 모두 같거나 전체 삭제할 때는 알림이 없다.
나머지 네 값이 없어도 상시모집이 `true`이면 공고가 있는 것으로 판단한다.
인증이 없으면 `401`, 다른 역할이면 `403`, 동아리가 없으면 `404`를 반환한다.
서버는 모집공고 내용·기간·링크·상시모집 여부만 수정한다.

동아리관리자 계정은 공개 회원가입으로 만들 수 없다. 운영자가 기존 계정을 확인한 뒤
`user_accounts.role`을 `CLUB_ADMIN`으로 변경한다. 역할 컬럼은 이미 `VARCHAR(20)`이어서
이 역할을 위한 스키마 변경은 필요 없다. 권한 변경 후 사용자는 다시 로그인해야 한다.

---

# 관리자 API

모든 관리자 API 요청에는 ADMIN 역할로 발급받은 JWT가 필요하다.

```http
Authorization: Bearer {accessToken}
```

## 1. 관리자용 동아리 목록 조회

```http
GET /api/admin/clubs
GET /api/admin/clubs?category=ACADEMIC
GET /api/admin/clubs?recruitingOnly=true
GET /api/admin/clubs?category=ACADEMIC&recruitingOnly=true
Authorization: Bearer {accessToken}
```

일반 사용자용 `GET /api/clubs`와 동일한 `category`, `recruitingOnly` 필터와
`{ "totalCount": ..., "clubs": [...] }` 응답 형식을 사용한다.
`totalCount`는 두 조건을 모두 적용한 개수이며 관리자 목록에서도 `clubs` 배열을 사용한다.

- 토큰이 없거나 유효하지 않은 경우: `401 Unauthorized`
- USER 역할인 경우: `403 Forbidden`
- ADMIN 역할인 경우: `200 OK`

---

## 1-1. 동아리 표시 순서 저장

```http
PUT /api/admin/clubs/order
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "clubIds": [3, 1, 2]
}
```

현재 등록된 **전체 동아리 ID**를 원하는 순서대로 한 번씩 전달한다.
위 예시는 전체 동아리가 1, 2, 3일 때 3 → 1 → 2 순서로 저장하며,
성공 응답은 본문 없는 `204 No Content`이다. 표시 순서는 DB 내부에서만 관리하며 목록 응답에는 포함하지 않는다.

- 전체 목록과 분과별 사용자·관리자 목록에 동일하게 반영된다.
- 분과별로 독립된 순서를 저장하지 않는다. 분과 화면에서도 전체 ID 목록을 기준으로 요청한다.
- 중복, 누락, 존재하지 않는 ID, null, 0 이하 ID는 `400 Bad Request`로 거부하며 기존 순서는 유지한다.
- 빈 배열은 등록된 동아리가 없을 때만 허용한다. 요청 본문과 `clubIds` 필드는 필수다.
- 저장 사이에 동아리가 추가·삭제되어 목록이 달라졌다면 전체 목록을 다시 조회한 뒤 저장한다.
- 저장은 트랜잭션으로 처리하며 같은 요청을 반복해도 결과는 같다. 동시 순서 변경은 뒤에 처리된 요청이 최종 순서가 된다.
- 새 동아리는 목록 마지막에 추가된다. 삭제 시 나머지 순서는 유지한다.
- 인증이 없으면 `401`, USER 권한이면 `403`이다.

연동 순서: `GET /api/admin/clubs`로 필터 없이 전체 조회 → `clubs` 배열 재배치 → 전체 `clubIds` 저장 → 목록 재조회.
사용자·관리자 목록은 같은 응답 형식이며 `recruitmentStatus`와 `recruitmentAlwaysOpen`을 포함한다.
순서 변경은 전체 `clubIds` 배열로만 요청한다.

운영 DB에는 `docs/club-display-order-migration-mysql.sql`을 새 백엔드 실행 전에 적용한다.
기존 동아리는 ID 오름차순을 유지하고, 마이그레이션을 재실행해도 관리자가 저장한 순서는 보존된다.
`hsm-deploy`의 배포 스크립트에도 해당 SQL 실행이 포함되어 있다.

---

## 2. 관리자용 동아리 상세 조회

```http
GET /api/admin/clubs/{clubId}
Authorization: Bearer {accessToken}
```

일반 사용자용 `GET /api/clubs/{clubId}`와 동일한 상세 정보를 반환한다.

### 응답

```json
{
  "backgroundImageUrl": "http://localhost:8080/uploads/clubs/background/example.png",
  "profileImageUrl": "http://localhost:8080/uploads/clubs/profile/example.png",
  "likeCount": 20,
  "likedByMe": false,
  "name": "멋쟁이사자처럼 한서대학교",
  "shortDescription": "함께 서비스를 만드는 IT 동아리",
  "topReviewTags": [
    "BUILD_RESUME",
    "ACADEMIC_PASSION",
    "SOCIALIZING"
  ],
  "introduction": "동아리 소개 장문 내용 🎉",
  "activityContent": "동아리 활동 장문 내용 🚀",
  "instagramUrl": "https://instagram.com/example",
  "kakaoTalkUrl": "https://open.kakao.com/o/example",
  "recruitmentContent": "현재 신입 부원을 모집합니다 🙌",
  "recruitmentUrl": "https://example.com/apply",
  "reviewerCount": 3,
  "recruitmentStartDate": "2026-10-25",
  "recruitmentEndDate": "2026-10-31",
  "recruitmentStatus": "UPCOMING",
  "recruitmentStartDayOfWeek": "SUNDAY",
  "recruitmentEndDayOfWeek": "SATURDAY",
  "recruitmentAlwaysOpen": false
}
```

- 토큰이 없거나 유효하지 않은 경우: `401 Unauthorized`
- USER 역할인 경우: `403 Forbidden`
- 존재하지 않는 동아리인 경우: `404 Not Found`

---

## 3. 동아리 등록

```http
POST /api/admin/clubs
Content-Type: application/json
```

### 요청

```json
{
  "name": "멋쟁이사자처럼 한서대학교",
  "category": "ACADEMIC",
  "recruitmentContent": "신입 부원을 모집합니다.",
  "recruitmentStartDate": "2026-10-25",
  "recruitmentEndDate": "2026-10-31",
  "recruitmentUrl": "https://example.com/apply",
  "recruitmentAlwaysOpen": false
}
```

### 응답

```http
201 Created
Location: /api/clubs/1
```

```json
{
  "id": 1
}
```

이름과 분과는 필수다. 모집공고 내용·시작일·종료일·링크는 각각 `null` 또는 생략할 수 있다.
상시모집 여부 `recruitmentAlwaysOpen`도 선택 입력이다. `true`면 날짜에 관계없이 `ALWAYS_OPEN`이다.
생략 또는 `null`이면 `false`로 저장한다. 이름과 분과만 보내면 나머지 모집공고 네 필드가 모두 `null`이고 상태는 `CLOSED`다.
등록 후 반환받은 ID로 나머지 텍스트 정보를 수정하고, 프로필 이미지와 배경 이미지는 각각 업로드한다.
생성 직후 `shortDescription`, 소개·활동, SNS URL과 이미지 URL은 `null`이다.

---

## 4. 배경 이미지 업로드

```http
PUT /api/admin/clubs/background-images/{clubId}
Content-Type: multipart/form-data
```

| Key | Type | Value |
|---|---|---|
| `file` | File | JPG, PNG 또는 GIF 이미지 |

### 응답

```json
{
  "imageUrl": "http://localhost:8080/uploads/clubs/background/uuid.png"
}
```

---

## 5. 프로필 이미지 업로드

```http
PUT /api/admin/clubs/profile-images/{clubId}
Content-Type: multipart/form-data
```

| Key | Type | Value |
|---|---|---|
| `file` | File | JPG, PNG 또는 GIF 이미지 |

### 응답

```json
{
  "imageUrl": "http://localhost:8080/uploads/clubs/profile/uuid.png"
}
```

업로드된 URL은 동아리 데이터에 저장되며 URL로 파일을 바로 조회할 수 있다.

---

## 6. 배경 이미지 삭제

```http
DELETE /api/admin/clubs/background-images/{clubId}
```

동아리의 배경 이미지 URL을 `null`로 변경하고 서버가 관리하는 기존 이미지 파일을 삭제한다.
정상적으로 삭제했거나 이미 배경 이미지가 없는 경우 모두 `204 No Content`를 반환한다.

---

## 7. 프로필 이미지 삭제

```http
DELETE /api/admin/clubs/profile-images/{clubId}
```

동아리의 프로필 이미지 URL을 `null`로 변경하고 서버가 관리하는 기존 이미지 파일을 삭제한다.
정상적으로 삭제했거나 이미 프로필 이미지가 없는 경우 모두 `204 No Content`를 반환한다.

---

## 8. 동아리 정보 통합 수정

```http
PUT /api/admin/clubs/{clubId}
Content-Type: application/json
```

```json
{
  "name": "수정된 동아리명",
  "shortDescription": "수정된 한 줄 소개",
  "introduction": "수정된 동아리 소개 🎉",
  "activityContent": "수정된 활동 내용 🚀",
  "instagramUrl": "https://instagram.com/updated",
  "kakaoTalkUrl": null,
  "recruitmentContent": "수정된 모집공고 내용 🙌",
  "recruitmentStartDate": "2026-10-25",
  "recruitmentEndDate": "2026-10-31",
  "recruitmentUrl": "https://example.com/apply",
  "recruitmentAlwaysOpen": false
}
```

동아리명, 한 줄 소개, 동아리 소개, 활동 내용, 문의 URL과 모집공고를 한 번에 수정한다.
이미지는 각각의 이미지 업로드 API를 사용한다.
성공 시 `204 No Content`를 반환한다.

모집공고 내용·시작일·종료일·링크는 각각 `null` 또는 생략 가능하며 동아리관리자 API와 같은 검증을 적용한다.
상시모집 여부는 `true`일 때 날짜보다 우선하며 생략 또는 `null`이면 `false`다.
이 API도 다섯 값을 대체하므로 유지할 내용·날짜·링크·상시모집 여부를 모두 요청에 포함해야 한다.
내용·날짜·링크·상시모집 여부 중 하나가 새로 입력되거나 변경되면 해당 시점에 동아리를 좋아요한 사용자에게
모집공고 알림을 생성한다. 다섯 값이 모두 같거나 전체 삭제할 때는 알림을 생성하지 않는다.

---

## 9. 동아리 삭제

```http
DELETE /api/admin/clubs/{clubId}
```

동아리와 연결된 좋아요, 활동 후기와 후기 선택 데이터를 함께 삭제한다.
성공 시 `204 No Content`를 반환한다.

---

## 활동 후기 enum

```text
BUILD_RESUME
ACADEMIC_PASSION
ENJOY_HOBBY
SOCIALIZING
CAREER_HELPFUL
SMALL_SCALE
GROUP_ACTIVITY
DEVELOP_SKILL
MANY_SENIORS
MANY_JUNIORS
MANY_GATHERINGS
CALM_ATMOSPHERE
DATING_FRIENDLY
FRIENDLY_MEMBERS
EASY_TO_JOIN_ALONE
SOCIABLE_MEMBERS
LARGE_SCALE
BUSY_SCHEDULE
FLEXIBLE_ATTENDANCE
HAS_FEE
MANDATORY_EVENTS
ATTENDANCE_IMPORTANT
MINIMUM_PERIOD
HAS_CLUB_ROOM
INTERVIEW_IMPORTANT
```

활동 후기 선택 항목은 25개다. 기존 DB의 `STRONG_SENIORITY` 선택 데이터는
`docs/club-review-strong-seniority-removal-mysql.sql`로 정리한다.
후기 쓰기를 중단한 상태에서 SQL을 먼저 실행하고 변경된 서버를 배포해야 한다.
다른 선택 항목은 보존하며, 제거한 항목만 선택했던 후기는 함께 삭제하여
후기 작성 수와 내 후기 목록에 빈 후기가 남지 않게 한다.

---

## 이미지 업로드 설정

```text
UPLOAD_DIRECTORY=uploads
UPLOAD_PUBLIC_BASE_URL=http://localhost:8080
UPLOAD_MAX_IMAGE_BYTES=5242880
```

- 로컬 파일 저장 방식이며 기본 최대 크기는 5 MiB이다.
- 운영 환경에서는 업로드 디렉터리를 영속 볼륨에 연결해야 한다.
- 관리자 API는 ADMIN 역할의 JWT만 접근할 수 있다.

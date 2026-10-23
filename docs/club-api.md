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
| 동아리관리자 | `PUT` | `/api/clubs/{clubId}/recruitment` | 모든 동아리의 모집공고 내용·기간 수정 |
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

모집공고를 작성·수정할 때 `recruitmentStartDate`와 `recruitmentEndDate`를
`YYYY-MM-DD` 형식으로 함께 보낸다. 두 날짜는 모집공고가 있으면 필수이며,
종료일은 시작일과 같거나 이후여야 한다. 과거 기간과 하루짜리 모집도 저장할 수 있다.

목록과 상세에 `recruitmentStatus`를 반환한다. 상태는 한국 시간(`Asia/Seoul`)의
오늘 날짜로 조회 시 계산하며 DB에 별도 저장하지 않는다.

| 값 | 화면 표시 | 조건 |
|---|---|---|
| `UPCOMING` | 모집전 | 오늘 < 시작일 |
| `RECRUITING` | 모집중 | 시작일 ≤ 오늘 ≤ 종료일 |
| `CLOSED` | 모집완료 | 오늘 > 종료일 또는 공고/기간 없음 |

시작일 00:00부터 종료일 23:59:59까지 모집중이다. 기존에 기간이 없는 공고는
내용을 보존하고 모집완료로 표시한다. 프론트는 이 값을 화면 문구로 매핑하고,
상세 응답의 두 날짜를 편집 화면 초기값으로 사용한다. 직접 상태를 지정하는 요청 필드는 없다.
예제의 상태는 한국 날짜 2026-10-23을 기준으로 한다.

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
| 전체 | ON | `/api/clubs?recruitingOnly=true` | 전체 동아리 중 모집중인 동아리 |
| 학술 | OFF | `/api/clubs?category=ACADEMIC&recruitingOnly=false` | 학술 동아리 전체 |
| 학술 | ON | `/api/clubs?category=ACADEMIC&recruitingOnly=true` | 학술 동아리 중 모집중인 동아리 |

두 조건은 **AND**로 적용한다. `true`일 때 한국 날짜 기준 `RECRUITING`만 반환하며,
모집전·모집완료·공고나 기간이 없는 동아리는 제외한다.
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
      "recruitmentStatus": "RECRUITING"
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

`recruitmentStatus`가 추가되며 `displayOrder`는 응답에 포함하지 않는다.
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
  "reviewerCount": 3,
  "recruitmentStartDate": "2026-10-25",
  "recruitmentEndDate": "2026-10-31",
  "recruitmentStatus": "UPCOMING"
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
`CLUB_ADMIN`이다. 앱은 이 역할일 때 각 동아리 상세의 모집공고에 수정 버튼을 표시한다.
공개 상세 조회 `GET /api/clubs/{clubId}`의 `recruitmentContent`,
`recruitmentStartDate`, `recruitmentEndDate`를 편집 화면의 초기값으로 사용한다.

```http
PUT /api/clubs/{clubId}/recruitment
Authorization: Bearer {accessToken}
Content-Type: application/json

{
  "recruitmentContent": "신입 부원을 모집합니다.",
  "recruitmentStartDate": "2026-10-25",
  "recruitmentEndDate": "2026-10-31"
}
```

성공 시 `204 No Content`를 반환한다. 공고를 지우려면 내용은 공백 또는 `null`로,
두 날짜는 모두 `null`로 보낸다. 삭제 후 날짜도 비워지고 상태는 `CLOSED`가 된다.
본문이 있는데 날짜를 빠뜨리거나, 날짜 순서/형식이 잘못됐거나,
본문이 없는데 날짜를 보내면 `400 Bad Request`다.
새 공고를 등록하거나 기존 공고의 내용 또는 기간을 변경하면 해당 동아리를 좋아요한 사용자에게
기존 관리자 수정 API와 동일한 알림을 생성한다. 내용과 기간이 모두 같거나 지울 때는 알림이 없다.
인증이 없으면 `401`, 다른 역할이면 `403`, 동아리가 없으면 `404`를 반환한다.
서버는 모집공고 내용과 기간만 수정한다.

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
사용자·관리자 목록은 같은 응답 형식이며 `recruitmentStatus`를 포함한다.
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
  "reviewerCount": 3,
  "recruitmentStartDate": "2026-10-25",
  "recruitmentEndDate": "2026-10-31",
  "recruitmentStatus": "UPCOMING"
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
  "category": "ACADEMIC"
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

동아리 등록 단계에서는 이름과 분과만 저장한다. 등록 후 반환받은 ID로
나머지 텍스트 정보를 한 번에 수정하고, 프로필 이미지와 배경 이미지는 각각 업로드한다.
따라서 생성 직후에는 `shortDescription`, 소개·활동·모집공고, SNS URL과
이미지 URL이 모두 `null`이다. 모집 시작일·종료일도 `null`이며 상태는 `CLOSED`다.

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
  "recruitmentEndDate": "2026-10-31"
}
```

동아리명, 한 줄 소개, 동아리 소개, 활동 내용, 문의 URL과 모집공고를 한 번에 수정한다.
이미지는 각각의 이미지 업로드 API를 사용한다.
성공 시 `204 No Content`를 반환한다.

모집공고 내용이 있으면 두 날짜가 필수이며 동아리관리자 API와 같은 날짜 검증을 적용한다.
기존 프론트의 전체 수정 요청도 두 날짜를 함께 보내도록 변경해야 한다.
`recruitmentContent`가 새로 작성되거나 내용 또는 기간이 수정되면 해당 시점에
동아리를 좋아요한 로그인 사용자에게만 모집공고 알림을 생성한다.
모집공고 내용과 기간이 모두 동일하거나 다른 동아리 정보만 변경된 경우에는 알림을 생성하지 않는다.

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

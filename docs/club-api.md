# 동아리 API 명세서

## 공통 정보

```text
Base URL: http://localhost:8080
JSON Content-Type: application/json
Image Content-Type: multipart/form-data
```

JWT 로그인과 역할 기반 권한 검사가 적용되어 있으며 `/api/admin/**`는 ADMIN 역할만 접근할 수 있다.
활동 후기 조회는 공개 API이고, 활동 후기 등록·수정·제거에는 로그인 JWT가 필요하다.
좋아요 요청에는 아직 별도의 사용자 식별 정보를 사용하지 않는다.

---

## API 목록

| 구분 | Method | URL | 기능 |
|---|---|---|---|
| 사용자 | `GET` | `/api/clubs` | 전체 또는 분과별 동아리 목록 조회 |
| 사용자 | `GET` | `/api/clubs/{clubId}` | 동아리 전체 상세 정보 조회 |
| 사용자 | `PUT` | `/api/clubs/likes/{clubId}` | 익명 요청 단위 좋아요 수 변경 |
| 사용자 | `GET` | `/api/clubs/reviews/{clubId}` | 활동 후기 비율 조회 |
| 사용자 | `PUT` | `/api/clubs/reviews/{clubId}` | 로그인 사용자의 활동 후기 등록·수정·제거 |
| 관리자 | `GET` | `/api/admin/clubs` | 전체 또는 분과별 동아리 목록 조회 |
| 관리자 | `POST` | `/api/admin/clubs` | 동아리 등록 |
| 관리자 | `PUT` | `/api/admin/clubs/background-images/{clubId}` | 배경 이미지 파일 업로드 |
| 관리자 | `DELETE` | `/api/admin/clubs/background-images/{clubId}` | 배경 이미지 삭제 |
| 관리자 | `PUT` | `/api/admin/clubs/profile-images/{clubId}` | 프로필 이미지 파일 업로드 |
| 관리자 | `DELETE` | `/api/admin/clubs/profile-images/{clubId}` | 프로필 이미지 삭제 |
| 관리자 | `PUT` | `/api/admin/clubs/{clubId}` | 동아리 텍스트 정보 통합 수정 |
| 관리자 | `DELETE` | `/api/admin/clubs/{clubId}` | 동아리 삭제 |

---

# 사용자 API

## 1. 동아리 목록 조회

```http
GET /api/clubs
GET /api/clubs?category=ACADEMIC
```

### 응답

```json
[
  {
    "id": 1,
    "name": "멋쟁이사자처럼 한서대학교",
    "category": "ACADEMIC",
    "profileImageUrl": "http://localhost:8080/uploads/clubs/profile/example.png",
    "shortDescription": "함께 서비스를 만드는 IT 동아리",
    "likeCount": 20,
    "topReviewTags": [
      "BUILD_RESUME",
      "ACADEMIC_PASSION"
    ]
  }
]
```

`topReviewTags`는 누적 선택 수가 많은 순서대로 최대 2개를 반환한다.

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
  "reviewerCount": 3
}
```

`topReviewTags`는 누적 선택 수가 많은 순서대로 최대 3개를 반환한다.
`reviewerCount`는 현재 해당 동아리에 활동 후기를 남긴 로그인 사용자 수다.

---

## 3. 좋아요 수 변경

```http
PUT /api/clubs/likes/{clubId}
Content-Type: application/json
```

### 증가 요청

```json
{
  "liked": true
}
```

### 감소 요청

```json
{
  "liked": false
}
```

### 응답

```json
{
  "clubId": 1,
  "liked": true,
  "likeCount": 21
}
```

로그인 전 테스트 방식이므로 `true` 요청마다 1건 증가하고 `false` 요청마다 최근 1건을 감소시킨다.

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

`options`에는 26개 enum 항목이 항상 전부 반환된다.

```text
percentage = 해당 태그 선택 수 ÷ 전체 태그 선택 수 × 100
```

- 소수 둘째 자리까지 반올림한다.
- 선택표가 없으면 모든 비율은 `0.00`이다.
- 프론트엔드는 `reviewTag` enum으로 문구와 이모지를 매핑한다.

---

## 5. 내 활동 후기 등록·수정·제거

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

# 관리자 API

모든 관리자 API 요청에는 ADMIN 역할로 발급받은 JWT가 필요하다.

```http
Authorization: Bearer {accessToken}
```

## 1. 관리자용 동아리 목록 조회

```http
GET /api/admin/clubs
GET /api/admin/clubs?category=ACADEMIC
Authorization: Bearer {accessToken}
```

일반 사용자용 `GET /api/clubs`와 동일한 목록과 필터 결과를 반환한다.

- 토큰이 없거나 유효하지 않은 경우: `401 Unauthorized`
- USER 역할인 경우: `403 Forbidden`
- ADMIN 역할인 경우: `200 OK`

---

## 2. 동아리 등록

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
이미지 URL이 모두 `null`이다.

---

## 3. 배경 이미지 업로드

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

## 4. 프로필 이미지 업로드

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

## 5. 배경 이미지 삭제

```http
DELETE /api/admin/clubs/background-images/{clubId}
```

동아리의 배경 이미지 URL을 `null`로 변경하고 서버가 관리하는 기존 이미지 파일을 삭제한다.
정상적으로 삭제했거나 이미 배경 이미지가 없는 경우 모두 `204 No Content`를 반환한다.

---

## 6. 프로필 이미지 삭제

```http
DELETE /api/admin/clubs/profile-images/{clubId}
```

동아리의 프로필 이미지 URL을 `null`로 변경하고 서버가 관리하는 기존 이미지 파일을 삭제한다.
정상적으로 삭제했거나 이미 프로필 이미지가 없는 경우 모두 `204 No Content`를 반환한다.

---

## 7. 동아리 정보 통합 수정

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
  "recruitmentContent": "수정된 모집공고 내용 🙌"
}
```

동아리명, 한 줄 소개, 동아리 소개, 활동 내용, 문의 URL과 모집공고를 한 번에 수정한다.
이미지는 각각의 이미지 업로드 API를 사용한다.
성공 시 `204 No Content`를 반환한다.

---

## 8. 동아리 삭제

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
STRONG_SENIORITY
BUSY_SCHEDULE
FLEXIBLE_ATTENDANCE
HAS_FEE
MANDATORY_EVENTS
ATTENDANCE_IMPORTANT
MINIMUM_PERIOD
HAS_CLUB_ROOM
INTERVIEW_IMPORTANT
```

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

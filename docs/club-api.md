# 동아리 API 명세서

## 공통 정보

```text
Base URL: http://localhost:8080
JSON Content-Type: application/json
Image Content-Type: multipart/form-data
```

현재 로그인, 사용자 인증과 관리자 권한 검사는 구현하지 않았다.
좋아요와 활동 후기 요청에 별도의 사용자 식별 헤더를 사용하지 않는다.

---

## API 목록

| 구분 | Method | URL | 기능 |
|---|---|---|---|
| 사용자 | `GET` | `/api/clubs` | 전체 또는 분과별 동아리 목록 조회 |
| 사용자 | `GET` | `/api/clubs/{clubId}` | 동아리 상단 상세 조회 |
| 사용자 | `GET` | `/api/clubs/information/{clubId}` | 동아리 소개·활동 내용·문의 링크 조회 |
| 사용자 | `GET` | `/api/clubs/recruitments/{clubId}` | 모집공고 조회 |
| 사용자 | `PUT` | `/api/clubs/likes/{clubId}` | 익명 요청 단위 좋아요 수 변경 |
| 사용자 | `GET` | `/api/clubs/reviews/{clubId}` | 활동 후기 비율 조회 |
| 사용자 | `PUT` | `/api/clubs/reviews/{clubId}` | 익명 활동 후기 등록 또는 최근 후기 제거 |
| 관리자 | `POST` | `/api/admin/clubs` | 동아리 등록 |
| 관리자 | `PUT` | `/api/admin/clubs/background-images/{clubId}` | 배경 이미지 파일 업로드 |
| 관리자 | `PUT` | `/api/admin/clubs/profile-images/{clubId}` | 프로필 이미지 파일 업로드 |
| 관리자 | `PUT` | `/api/admin/clubs/basic-info/{clubId}` | 동아리명·한 줄 소개 수정 |
| 관리자 | `PUT` | `/api/admin/clubs/information/{clubId}` | 동아리 소개·활동 내용·문의 링크 수정 |
| 관리자 | `PUT` | `/api/admin/clubs/recruitments/{clubId}` | 모집공고 수정 |
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
  "id": 1,
  "name": "멋쟁이사자처럼 한서대학교",
  "profileImageUrl": "http://localhost:8080/uploads/clubs/profile/example.png",
  "backgroundImageUrl": "http://localhost:8080/uploads/clubs/background/example.png",
  "shortDescription": "함께 서비스를 만드는 IT 동아리",
  "likeCount": 20,
  "topReviewTags": [
    "BUILD_RESUME",
    "ACADEMIC_PASSION",
    "SOCIALIZING"
  ]
}
```

`topReviewTags`는 누적 선택 수가 많은 순서대로 최대 3개를 반환한다.

---

## 3. 동아리 정보 조회

```http
GET /api/clubs/information/{clubId}
```

### 응답

```json
{
  "introduction": "동아리 소개 장문 내용 🎉",
  "activityContent": "동아리 활동 장문 내용 🚀",
  "instagramUrl": "https://instagram.com/example",
  "kakaoTalkUrl": "https://open.kakao.com/o/example"
}
```

---

## 4. 모집공고 조회

```http
GET /api/clubs/recruitments/{clubId}
```

### 응답

```json
{
  "recruitmentContent": "현재 신입 부원을 모집합니다 🙌"
}
```

---

## 5. 좋아요 수 변경

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

## 6. 활동 후기 조회

```http
GET /api/clubs/reviews/{clubId}
```

### 응답

```json
{
  "clubId": 1,
  "reviewerCount": 3,
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

## 7. 활동 후기 등록 또는 최근 후기 제거

```http
PUT /api/clubs/reviews/{clubId}
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

1개 이상 5개 이하의 태그를 보내면 익명 후기 1건으로 누적한다.

### 최근 후기 제거 요청

```json
{
  "reviewTags": []
}
```

빈 배열, 빈 객체 또는 요청 본문 없이 호출하면 해당 동아리의 최근 후기 1건을 제거한다.

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

---

# 관리자 API

## 1. 동아리 등록

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
한 줄 소개, 상세 정보, 모집공고, 프로필 이미지와 배경 이미지를 각각 수정한다.
따라서 생성 직후에는 `shortDescription`, 소개·활동·모집공고, SNS URL과
이미지 URL이 모두 `null`이다.

---

## 2. 배경 이미지 업로드

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

## 3. 프로필 이미지 업로드

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

## 4. 동아리명과 한 줄 소개 수정

```http
PUT /api/admin/clubs/basic-info/{clubId}
```

```json
{
  "name": "수정된 동아리명",
  "shortDescription": "수정된 한 줄 소개"
}
```

성공 시 `204 No Content`를 반환한다.

---

## 5. 동아리 정보 수정

```http
PUT /api/admin/clubs/information/{clubId}
```

```json
{
  "introduction": "수정된 동아리 소개 🎉",
  "activityContent": "수정된 활동 내용 🚀",
  "instagramUrl": "https://instagram.com/updated",
  "kakaoTalkUrl": null
}
```

성공 시 `204 No Content`를 반환한다.

---

## 6. 모집공고 수정

```http
PUT /api/admin/clubs/recruitments/{clubId}
```

```json
{
  "recruitmentContent": "수정된 모집공고 내용 🙌"
}
```

성공 시 `204 No Content`를 반환한다.

---

## 7. 동아리 삭제

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
- 로그인과 관리자 인증을 추가하기 전에는 인터넷에 공개하지 않는다.

# 마이페이지 API 명세서

## 1. 기능 개요

로그인한 사용자의 기본 계정 정보와 본인이 작성한 동아리 활동 후기 목록을 한 번에 조회합니다.

- Bearer JWT 필수
- 다른 사용자의 후기는 반환하지 않음
- 후기 미작성 시 빈 배열 반환
- 비밀번호, 비밀번호 해시 및 새 Access Token은 응답하지 않음
- DB 스키마 변경 없음

---

## 2. 기본 정보

| 항목 | 내용 |
| --- | --- |
| Method | `GET` |
| URL | `/api/auth/me` |
| 요청 형식 | 요청 본문 없음 |
| 응답 형식 | `application/json` |
| 인증 | `Authorization: Bearer {accessToken}` |

## 요청 예시

```http
GET /api/auth/me
Authorization: Bearer {accessToken}
```

Query Parameter와 Request Body는 사용하지 않습니다.

---

## 3. 성공 응답

```http
200 OK
```

```json
{
  "userId": 1,
  "loginId": "user01",
  "role": "USER",
  "createdAt": "2026-08-11T10:00:00",
  "updatedAt": "2026-08-11T10:00:00",
  "clubReviews": [
    {
      "clubId": 3,
      "clubName": "멋쟁이사자처럼",
      "reviewTags": [
        "BUILD_RESUME",
        "SOCIALIZING"
      ]
    }
  ]
}
```

## 응답 필드

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `userId` | Number | 로그인 사용자 ID |
| `loginId` | String | 로그인 아이디 |
| `role` | String | 사용자 권한, `USER` 또는 `ADMIN` |
| `createdAt` | String | 계정 생성 일시 |
| `updatedAt` | String | 계정 수정 일시 |
| `clubReviews` | Array | 본인이 현재 작성한 동아리 후기 목록 |
| `clubReviews[].clubId` | Number | 후기 작성 대상 동아리 ID |
| `clubReviews[].clubName` | String | 동아리 이름 |
| `clubReviews[].reviewTags` | Array | 본인이 선택한 후기 태그 코드 목록 |

후기 목록은 최근 등록된 후기부터 후기 ID 내림차순으로 정렬합니다.
각 후기의 태그는 서버의 후기 태그 Enum 선언 순서로 정렬됩니다.

후기를 작성하지 않은 경우에도 오류가 아니라 다음과 같이 빈 배열을 반환합니다.

```json
{
  "userId": 1,
  "loginId": "user01",
  "role": "USER",
  "createdAt": "2026-08-11T10:00:00",
  "updatedAt": "2026-08-11T10:00:00",
  "clubReviews": []
}
```

---

## 4. 후기 수정 및 삭제 연동

마이페이지 응답의 `clubId`를 기존 동아리 후기 API에 사용합니다.

```http
PUT /api/clubs/reviews/{clubId}
Authorization: Bearer {accessToken}
Content-Type: application/json
```

- 1~5개의 태그를 전달하면 해당 동아리의 본인 후기를 등록하거나 수정합니다.
- 빈 배열 또는 빈 요청을 전달하면 본인 후기를 삭제합니다.
- 수정 후 마이페이지를 다시 조회하면 변경된 태그가 반환됩니다.
- 삭제 후 마이페이지를 다시 조회하면 해당 동아리 후기가 목록에서 제외됩니다.

---

## 5. 인증 오류

다음 경우 `401 Unauthorized`를 반환합니다.

- Authorization Header 누락
- 잘못되거나 만료된 JWT
- 숫자가 아닌 JWT subject
- JWT가 가리키는 사용자가 DB에 존재하지 않음

```json
{
  "status": 401,
  "message": "로그인이 필요합니다.",
  "path": "/api/auth/me",
  "timestamp": "2026-08-11T01:00:00Z"
}
```

---

## 6. API 요약

| Method | URL | 인증 | 기능 |
| --- | --- | --- | --- |
| `GET` | `/api/auth/me` | Bearer JWT 필수 | 내 계정 정보와 작성한 동아리 후기 조회 |

> 현재 회원 정보에는 이름, 닉네임, 학번, 학과, 이메일, 프로필 이미지가 저장되어 있지 않습니다. 해당 정보를 마이페이지에 추가하려면 회원 DB 모델을 별도로 확장해야 합니다.

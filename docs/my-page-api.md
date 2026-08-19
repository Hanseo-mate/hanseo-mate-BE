# 마이페이지 API 명세서

## 1. 기능 개요

로그인한 사용자의 기본 계정 정보, 본인이 작성한 동아리 활동 후기, 좋아요한 동아리 목록을 한 번에 조회하고, 비밀번호 확인 후 계정과 회원 관련 데이터를 영구 삭제할 수 있습니다.

- Bearer JWT 필수
- 다른 사용자의 후기는 반환하지 않음
- 다른 사용자의 좋아요는 반환하지 않음
- 후기 미작성 시 빈 배열 반환
- 좋아요한 동아리가 없을 때 빈 배열 반환
- 비밀번호, 비밀번호 해시 및 새 Access Token은 응답하지 않음
- 회원탈퇴는 복구할 수 없는 물리 삭제 방식
- 운영 DB는 시간표·푸시 데이터의 연쇄 삭제 FK를 배포 전에 보강해야 함

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
  ],
  "likedClubs": [
    {
      "clubId": 5,
      "clubName": "총학생회"
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
| `likedClubs` | Array | 본인이 현재 좋아요한 동아리 목록 |
| `likedClubs[].clubId` | Number | 좋아요한 동아리 ID |
| `likedClubs[].clubName` | String | 좋아요한 동아리 이름 |

후기 목록은 최근 등록된 후기부터 후기 ID 내림차순으로 정렬합니다.
각 후기의 태그는 서버의 후기 태그 Enum 선언 순서로 정렬됩니다.
좋아요한 동아리는 최근에 좋아요한 순서로 반환됩니다.

후기를 작성하지 않았거나 좋아요한 동아리가 없는 경우에도 오류가 아니라 각 필드에 빈 배열을 반환합니다.

```json
{
  "userId": 1,
  "loginId": "user01",
  "role": "USER",
  "createdAt": "2026-08-11T10:00:00",
  "updatedAt": "2026-08-11T10:00:00",
  "clubReviews": [],
  "likedClubs": []
}
```

---

## 4. 후기 및 좋아요 연동

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

좋아요는 기존 동아리 좋아요 API로 변경합니다.

```http
POST /api/clubs/likes/{clubId}
Authorization: Bearer {accessToken}
```

- 좋아요하지 않은 동아리에 호출하면 좋아요가 등록됩니다.
- 이미 좋아요한 동아리에 호출하면 좋아요가 취소됩니다.
- 등록 후 마이페이지를 다시 조회하면 해당 동아리가 `likedClubs`에 포함됩니다.
- 취소 후 마이페이지를 다시 조회하면 해당 동아리가 `likedClubs`에서 제외됩니다.

---

## 5. 회원탈퇴

| 항목 | 내용 |
| --- | --- |
| Method | `DELETE` |
| URL | `/api/auth/me` |
| 요청 형식 | `application/json` |
| 응답 형식 | 응답 본문 없음 |
| 인증 | `Authorization: Bearer {accessToken}` |

```http
DELETE /api/auth/me
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "password": "current-password"
}
```

현재 비밀번호가 일치하면 다음 데이터를 하나의 트랜잭션에서 영구 삭제합니다.

- 회원 계정
- 개인 일정
- 동아리 좋아요
- 동아리 후기와 선택한 후기 태그
- 개인 시간표와 시간표에 담은 과목
- 현재 계정에 연결된 푸시 기기와 해당 기기의 발송 티켓

공용 강좌, 동아리, 공지, 공용 알림 Outbox와 다른 사용자의 데이터는 삭제하지 않습니다.

```http
204 No Content
```

- 응답 본문은 없습니다.
- Soft Delete나 계정 잠금이 아니므로 탈퇴 데이터는 복구할 수 없습니다.
- 탈퇴에 사용한 Access Token은 즉시 무효가 됩니다.
- 같은 `loginId`로 다시 가입할 수 있지만 새 계정으로 생성되며 과거 데이터는 복원되지 않습니다.

기존 운영 DB에는 코드 배포 전에
[`account-withdrawal-migration-mysql.sql`](account-withdrawal-migration-mysql.sql)의
고아 데이터 확인 쿼리와 FK 추가 DDL을 적용해야 합니다.

---

## 6. 오류 응답

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

회원탈퇴 요청의 `password`가 누락되거나 공백이면 `400 Bad Request`를 반환합니다.
현재 비밀번호와 일치하지 않으면 `401 Unauthorized`를 반환하며, 계정과 기존 데이터는 삭제되지 않습니다.

```json
{
  "status": 401,
  "message": "아이디 또는 비밀번호가 올바르지 않습니다.",
  "path": "/api/auth/me",
  "timestamp": "2026-08-18T01:00:00Z"
}
```

---

## 7. API 요약

| Method | URL | 인증 | 기능 |
| --- | --- | --- | --- |
| `GET` | `/api/auth/me` | Bearer JWT 필수 | 내 계정 정보, 작성한 동아리 후기 및 좋아요한 동아리 조회 |
| `DELETE` | `/api/auth/me` | Bearer JWT 필수 | 비밀번호 확인 후 계정과 회원 관련 데이터 영구 삭제 |

> 현재 회원 정보에는 이름, 닉네임, 학번, 학과, 이메일, 프로필 이미지가 저장되어 있지 않습니다. 해당 정보를 마이페이지에 추가하려면 회원 DB 모델을 별도로 확장해야 합니다.

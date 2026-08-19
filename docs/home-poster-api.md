# 관리자 홈 포스터 API 명세서

## 1. 기능 개요

관리자가 메인 화면에 노출할 포스터 이미지와 클릭 시 이동할 선택 링크를 등록,
조회, 교체, 삭제하는 기능입니다.

- 포스터 한 장을 하나의 독립 데이터로 저장합니다.
- `linkUrl`은 선택값이며 `null`을 허용합니다.
- 등록 가능한 포스터 개수에는 애플리케이션 제한이 없습니다.
- 관리자 목록은 등록 순서인 ID 오름차순으로 반환합니다.
- 일반 사용자는 `GET /api/home`의 `posters`에서 이미지와 링크를 함께 조회합니다.
- 기존 클라이언트 호환을 위해 `/api/home`의 `posterImageUrls`도 유지합니다.

## 2. 공통 정보

```text
Base URL: http://localhost:8080
Authorization: Bearer {ADMIN_ACCESS_TOKEN}
Upload Content-Type: multipart/form-data
Response Content-Type: application/json
```

관리자 포스터 API는 JWT의 `role`이 `ADMIN`인 사용자만 호출할 수 있습니다.

| 상황 | 상태 코드 |
|---|---:|
| JWT 없음, 잘못된 JWT, 만료된 JWT | `401 Unauthorized` |
| 로그인했지만 `USER` 역할 | `403 Forbidden` |
| `ADMIN` 역할 | 요청 처리 |

## 3. 포스터 등록

```http
POST /api/admin/home-posters
Authorization: Bearer {ADMIN_ACCESS_TOKEN}
Content-Type: multipart/form-data
```

### form-data

| Key | Type | 필수 | 설명 |
|---|---|---:|---|
| `file` | File | 필수 | JPG, PNG 또는 GIF 이미지 |
| `linkUrl` | Text | 선택 | 클릭 시 이동할 HTTP 또는 HTTPS URL, 최대 2,048자 |

`linkUrl` 파트를 생략하거나 빈 문자열 또는 공백으로 보내면 DB와 응답에
`null`로 저장됩니다. 문자열 `"null"`은 실제 null이 아니므로 올바른 URL로 인정되지 않습니다.

### 링크가 있는 요청

```text
file      poster.png
linkUrl   https://www.hanseo.ac.kr/event/1
```

### 링크가 없는 요청

```text
file      poster.png
linkUrl   파트 생략 또는 빈 값
```

### 성공 응답

```http
201 Created
Location: /api/admin/home-posters/1
```

```json
{
  "id": 1,
  "imageUrl": "http://localhost:8080/uploads/home-posters/poster.png",
  "linkUrl": "https://www.hanseo.ac.kr/event/1",
  "createdAt": "2026-08-19T17:30:00",
  "updatedAt": "2026-08-19T17:30:00"
}
```

링크가 없으면 필드는 생략되지 않고 명시적으로 `null`을 반환합니다.

```json
{
  "id": 2,
  "imageUrl": "http://localhost:8080/uploads/home-posters/poster-2.png",
  "linkUrl": null,
  "createdAt": "2026-08-19T17:31:00",
  "updatedAt": "2026-08-19T17:31:00"
}
```

## 4. 포스터 목록 조회

```http
GET /api/admin/home-posters
Authorization: Bearer {ADMIN_ACCESS_TOKEN}
```

### 성공 응답

```http
200 OK
```

```json
[
  {
    "id": 1,
    "imageUrl": "http://localhost:8080/uploads/home-posters/first.png",
    "linkUrl": "https://www.hanseo.ac.kr/event/1",
    "createdAt": "2026-08-19T17:30:00",
    "updatedAt": "2026-08-19T17:30:00"
  },
  {
    "id": 2,
    "imageUrl": "http://localhost:8080/uploads/home-posters/second.png",
    "linkUrl": null,
    "createdAt": "2026-08-19T17:31:00",
    "updatedAt": "2026-08-19T17:31:00"
  }
]
```

포스터가 없으면 `[]`를 반환합니다.

## 5. 포스터 이미지와 링크 교체

포스터 ID는 유지하면서 이미지 파일과 선택 링크를 함께 교체합니다.

```http
PUT /api/admin/home-posters/{posterId}
Authorization: Bearer {ADMIN_ACCESS_TOKEN}
Content-Type: multipart/form-data
```

### Path Variable

| 이름 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `posterId` | Number | 필수 | 교체할 포스터 ID, 1 이상 |

### form-data

| Key | Type | 필수 | 설명 |
|---|---|---:|---|
| `file` | File | 필수 | 새 JPG, PNG 또는 GIF 이미지 |
| `linkUrl` | Text | 선택 | 새 클릭 링크, 최대 2,048자 |

- `linkUrl`에 값을 보내면 기존 링크를 새 링크로 교체합니다.
- `linkUrl`을 생략하거나 빈 값으로 보내면 기존 링크를 제거하고 `null`로 저장합니다.
- 이미지 변경 없이 링크만 수정하는 별도 API는 제공하지 않습니다.

```json
{
  "id": 1,
  "imageUrl": "http://localhost:8080/uploads/home-posters/replaced.png",
  "linkUrl": "https://www.hanseo.ac.kr/event/2",
  "createdAt": "2026-08-19T17:30:00",
  "updatedAt": "2026-08-19T18:00:00"
}
```

교체 트랜잭션이 커밋된 뒤 기존 이미지 파일을 삭제합니다. DB 처리가 실패하거나
새 이미지 또는 링크 검증이 실패하면 기존 이미지, 링크, 파일을 유지합니다.

## 6. 포스터 삭제

```http
DELETE /api/admin/home-posters/{posterId}
Authorization: Bearer {ADMIN_ACCESS_TOKEN}
```

성공하면 `204 No Content`를 반환하며 응답 본문은 없습니다. 포스터 데이터와
서버가 관리하는 이미지 파일을 함께 삭제합니다.

## 7. 일반 사용자 메인 응답

```http
GET /api/home
```

기존 `posterImageUrls`를 유지하면서 이미지와 링크가 결합된 `posters`를 추가합니다.

아래 예시는 전체 메인 응답 중 포스터 관련 필드만 발췌한 것입니다.

```json
{
  "posterImageUrls": [
    "http://localhost:8080/uploads/home-posters/first.png",
    "http://localhost:8080/uploads/home-posters/second.png"
  ],
  "posters": [
    {
      "id": 1,
      "imageUrl": "http://localhost:8080/uploads/home-posters/first.png",
      "linkUrl": "https://www.hanseo.ac.kr/event/1"
    },
    {
      "id": 2,
      "imageUrl": "http://localhost:8080/uploads/home-posters/second.png",
      "linkUrl": null
    }
  ]
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `posterImageUrls` | String[] 또는 null | 기존 호환용 이미지 URL 배열 |
| `posters` | Object[] 또는 null | 새 포스터 객체 배열 |
| `posters[].id` | Number | 포스터 ID |
| `posters[].imageUrl` | String | 이미지 표시 URL |
| `posters[].linkUrl` | String 또는 null | 클릭 이동 URL |

포스터가 없으면 `posterImageUrls`와 `posters`를 모두 `null`로 반환합니다.

## 8. 링크 및 이미지 검증

### 이미지

- JPG, PNG, GIF만 지원합니다.
- 확장자나 요청 MIME만 믿지 않고 실제 이미지 내용을 검사합니다.
- 기본 이미지 한 장의 최대 크기는 5 MiB입니다.
- 서버가 UUID 기반 파일명을 생성합니다.

### linkUrl

- 선택값이며 생략 또는 공백을 허용합니다.
- 저장 전 앞뒤 공백을 제거합니다.
- 최대 2,048자입니다.
- `http://` 또는 `https://`와 유효한 host가 있어야 합니다.
- `javascript:`, `data:`, `ftp:`, 상대 경로는 허용하지 않습니다.
- 서버는 `linkUrl`로 외부 요청을 보내지 않고 문자열만 저장합니다.

## 9. 오류 응답

```json
{
  "status": 404,
  "message": "홈 포스터를 찾을 수 없습니다. posterId=999",
  "path": "/api/admin/home-posters/999",
  "timestamp": "2026-08-19T09:00:00Z"
}
```

| 상태 코드 | 발생 상황 |
|---:|---|
| `400` | 파일 누락, 잘못된 이미지, 잘못된 링크, 링크 길이 초과, 잘못된 ID |
| `401` | JWT 없음, 잘못된 JWT, 만료된 JWT |
| `403` | `USER` 역할로 관리자 API 호출 |
| `404` | 교체하거나 삭제할 포스터가 없음 |
| `413` | 운영 프록시·웹 서버 등 외부 업로드 제한 초과 |

## 10. API 요약

| Method | URL | 기능 |
|---|---|---|
| `POST` | `/api/admin/home-posters` | 이미지와 선택 링크 등록 |
| `GET` | `/api/admin/home-posters` | 관리자용 전체 포스터 조회 |
| `PUT` | `/api/admin/home-posters/{posterId}` | 이미지와 선택 링크 교체 |
| `DELETE` | `/api/admin/home-posters/{posterId}` | 포스터 삭제 |
| `GET` | `/api/home` | 일반 사용자 메인 포스터 조회 |

## 11. 운영 DB 적용

기존 운영 DB에는 코드 배포 전에 nullable 컬럼을 추가해야 합니다.

```sql
ALTER TABLE home_posters
    ADD COLUMN link_url VARCHAR(2048) NULL AFTER image_url;
```

운영 증분 파일:

```text
docs/home-poster-link-url-migration-mysql.sql
```

1. DB를 백업합니다.
2. 파일의 `information_schema.columns` 조회로 `link_url` 존재 여부를 확인합니다.
3. 컬럼이 없을 때만 `ALTER TABLE`을 실행합니다.
4. `SHOW COLUMNS FROM home_posters`로 `link_url`이 nullable인지 확인합니다.
5. 코드를 배포하고 등록·조회 smoke test를 수행합니다.

기존 행은 `link_url=null`로 유지되므로 별도 데이터 보정은 필요 없습니다.
`docs/database-schema-mysql.sql` 전체는 빈 DB 전용이므로 기존 운영 DB에 실행하지 않습니다.

추가 properties와 인덱스는 필요하지 않습니다.

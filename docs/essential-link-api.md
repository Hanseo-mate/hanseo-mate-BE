# 필수 링크 API 명세서

학교생활 필수 링크는 이름과 URL만 입력받습니다. 카테고리 필드와 카테고리 조회 필터는 제거했습니다.

## API와 권한

| Method | Endpoint | 기능 | 권한 |
|---|---|---|---|
| GET | `/api/links` | 전체 목록 조회 | 로그인 없이 가능 |
| GET | `/api/links/{linkId}` | 상세 조회 | 로그인 없이 가능 |
| GET | `/api/admin/links` | 관리자 전체 목록 조회 | ADMIN JWT 필요 |
| GET | `/api/admin/links/{linkId}` | 관리자 상세 조회 | ADMIN JWT 필요 |
| POST | `/api/admin/links` | 등록 | ADMIN JWT 필요 |
| PUT | `/api/admin/links/{linkId}` | 전체 수정 | ADMIN JWT 필요 |
| DELETE | `/api/admin/links/{linkId}` | 삭제 | ADMIN JWT 필요 |

관리자 목록은 `GET /api/admin/links`로 조회할 수 있습니다. 일반 사용자 목록과 응답 구조·정렬이 동일하며 ADMIN JWT가 필요합니다. 관리자 상세 조회에는 `GET /api/admin/links/{linkId}`를 사용합니다. 목록은 ID 오름차순이며 데이터가 없으면 빈 배열을 반환합니다. 목록 조회에는 필터와 페이지네이션이 없습니다.

## 관리자 상세 조회

```http
GET /api/admin/links/1
Authorization: Bearer {accessToken}
```

요청 본문은 없습니다. `linkId`는 1 이상의 정수입니다. 성공 시 `200 OK`와 아래 링크 객체를 반환합니다.

```json
{
  "id": 1,
  "name": "한서포탈",
  "url": "https://portal.hanseo.ac.kr",
  "createdAt": "2026-10-06T10:00:00.123456",
  "updatedAt": "2026-10-06T10:00:00.123456"
}
```

잘못된 ID는 `400`, 인증이 없거나 유효하지 않으면 `401`, ADMIN 권한이 없으면 `403`, 링크가 없으면 `404`입니다. 관리자 상세 화면은 이 경로를 사용합니다. 공통 관리자 CORS 설정이 적용되며, 운영 허용 Origin에 프론트 주소가 등록되어 있어야 합니다.

## 등록·수정 양식

요청 형식은 `application/json`입니다. 관리자 요청에는 `Authorization: Bearer <accessToken>` 헤더를 보냅니다.

| 필드 | 필수 | 조건 |
|---|---|---|
| `name` | O | 공백 불가, 최대 100자 |
| `url` | O | 공백 불가, 최대 2048자, 호스트가 있는 http 또는 https 주소 |

```json
{
  "name": "한서포탈",
  "url": "https://portal.hanseo.ac.kr"
}
```

이름과 URL은 앞뒤 공백을 제거해 저장합니다. 수정 시에도 두 필드를 모두 전달합니다.

## 응답

등록은 `201 Created`와 `Location: /api/links/{id}` 헤더, 수정·상세 조회는 `200 OK`로 아래 객체를 반환합니다. 목록 조회는 같은 객체의 배열을 반환합니다.

```json
{
  "id": 1,
  "name": "한서포탈",
  "url": "https://portal.hanseo.ac.kr",
  "createdAt": "2026-09-30T14:00:00.123456",
  "updatedAt": "2026-09-30T14:00:00.123456"
}
```

ID와 생성일·수정일은 서버가 관리합니다. 수정 시 ID와 생성일을 유지하고 수정일을 갱신합니다. 삭제는 DB에서 실제로 삭제하며 `204 No Content`를 반환합니다.

## 오류

| 상태 | 조건 |
|---|---|
| 400 | 필수값 누락, 길이 초과, 잘못된 URL·JSON·링크 ID |
| 401 | 관리자 API 요청 시 인증 없음 또는 유효하지 않은 JWT |
| 403 | 관리자 API 요청 시 ADMIN 권한 없음 |
| 404 | 상세 조회·수정·삭제 대상 링크 없음 |

오류 응답은 `status`, `message`, `path`, `timestamp` 필드를 반환합니다. 링크 ID는 1 이상의 정수입니다.

## 기존 DB 변경

기존 DB에는 배포 전에 `docs/essential-link-category-removal-mysql.sql`을 적용합니다. 기존 `category` 컬럼은 NOT NULL이므로 남겨 두면 이름과 URL만 등록할 때 INSERT가 실패할 수 있습니다.

SQL을 적용하면 기존 카테고리 데이터는 삭제됩니다. 기존 링크의 ID, 이름, URL, 생성일·수정일은 유지됩니다. 새 DB는 `docs/database-schema-mysql.sql`을 사용합니다.

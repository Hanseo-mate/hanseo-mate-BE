# 필수 링크 API 명세서

## 1. 문서 정보

| 항목 | 내용 |
|---|---|
| 서비스 | 한서 메이트 백엔드 |
| 기능 | 학교생활 필수 링크 조회 및 관리 |
| 문서 버전 | v0.1 |
| 작성일 | 2026-07-17 |
| 구현 상태 | 구현 및 통합 테스트 완료 |

## 2. 기본 정보

| 항목 | 내용 |
|---|---|
| 로컬 Base URL | `http://localhost:8080` |
| 요청 형식 | `application/json` |
| 응답 형식 | `application/json` |
| 문자 인코딩 | UTF-8 |
| 인증 | 현재 미적용 |

> 현재 `/api/admin/**`에도 인증이 적용되어 있지 않습니다. 정식 공개 전에 관리자 인증과 권한 검사가 필요합니다.

## 3. 전체 API 목록

| 구분 | Method | Endpoint | 설명 |
|---|---|---|---|
| 사용자 | `GET` | `/api/links` | 전체 링크 목록 조회 |
| 사용자 | `GET` | `/api/links?category={category}` | 카테고리별 링크 목록 조회 |
| 사용자 | `GET` | `/api/links/{linkId}` | 링크 상세 조회 |
| 관리자 | `POST` | `/api/admin/links` | 링크 등록 |
| 관리자 | `PUT` | `/api/admin/links/{linkId}` | 링크 전체 수정 |
| 관리자 | `DELETE` | `/api/admin/links/{linkId}` | 링크 삭제 |

관리자 페이지의 목록 및 상세 조회는 사용자 조회 API를 동일하게 사용합니다.

## 4. 데이터 모델

### 4.1 링크 등록·수정 요청

| 필드 | 타입 | 필수 | 제한사항 | 설명 |
|---|---|---|---|---|
| `name` | String | O | 공백 불가, 최대 100자 | 화면에 표시할 링크 이름 |
| `url` | String | O | 최대 2048자, `http` 또는 `https`, 호스트 필수 | 이동할 외부 사이트 주소 |
| `category` | String | O | 공백 불가, 정규화 후 최대 50자 | 링크 분류 |

### 4.2 링크 응답

| 필드 | 타입 | Nullable | 설명 |
|---|---|---|---|
| `id` | Long | X | 링크 고유 ID |
| `name` | String | X | 링크 이름 |
| `url` | String | X | 외부 사이트 주소 |
| `category` | String | X | 정규화된 링크 카테고리 |
| `createdAt` | LocalDateTime | X | 생성 일시 |
| `updatedAt` | LocalDateTime | X | 마지막 수정 일시 |

### 4.3 카테고리 처리 규칙

- 카테고리는 Enum이 아닌 문자열입니다.
- 입력값 앞뒤 공백을 제거합니다.
- 영문은 대문자로 변환합니다.
- 한글은 그대로 저장합니다.
- 새로운 카테고리를 코드 수정 없이 사용할 수 있습니다.
- 필터 조회에도 동일한 정규화 규칙을 적용합니다.

```text
입력: " remote_class "
저장 및 응답: "REMOTE_CLASS"
```

## 5. 사용자 API

### 5.1 링크 목록 조회

```http
GET /api/links
```

등록된 모든 링크를 ID 오름차순으로 조회합니다. 등록된 링크가 없으면 빈 배열을 반환합니다.

#### 성공 응답

```http
HTTP/1.1 200 OK
Content-Type: application/json
```

```json
[
  {
    "id": 1,
    "name": "한서포탈",
    "url": "https://portal.hanseo.ac.kr",
    "category": "ACADEMIC",
    "createdAt": "2026-07-17T14:00:00.123456",
    "updatedAt": "2026-07-17T14:00:00.123456"
  },
  {
    "id": 2,
    "name": "e클래스",
    "url": "https://eclass.hanseo.ac.kr",
    "category": "REMOTE_CLASS",
    "createdAt": "2026-07-17T14:01:00.123456",
    "updatedAt": "2026-07-17T14:01:00.123456"
  }
]
```

#### 빈 목록 응답

```json
[]
```

### 5.2 카테고리별 링크 목록 조회

```http
GET /api/links?category=REMOTE_CLASS
```

#### Query Parameter

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `category` | String | X | 조회할 카테고리. 전달하면 공백 제거 및 대문자 변환 후 검색 |

#### 성공 응답

```http
HTTP/1.1 200 OK
```

```json
[
  {
    "id": 2,
    "name": "e클래스",
    "url": "https://eclass.hanseo.ac.kr",
    "category": "REMOTE_CLASS",
    "createdAt": "2026-07-17T14:01:00.123456",
    "updatedAt": "2026-07-17T14:01:00.123456"
  }
]
```

#### 오류 응답

| 상태 코드 | 발생 조건 |
|---|---|
| `400 Bad Request` | `category`를 전달했지만 값이 공백인 경우 |
| `400 Bad Request` | 정규화된 카테고리가 50자를 초과한 경우 |

### 5.3 링크 상세 조회

```http
GET /api/links/{linkId}
```

#### Path Parameter

| 이름 | 타입 | 필수 | 제한사항 |
|---|---|---|---|
| `linkId` | Long | O | 1 이상의 정수 |

#### 성공 응답

```http
HTTP/1.1 200 OK
```

```json
{
  "id": 1,
  "name": "한서포탈",
  "url": "https://portal.hanseo.ac.kr",
  "category": "ACADEMIC",
  "createdAt": "2026-07-17T14:00:00.123456",
  "updatedAt": "2026-07-17T14:00:00.123456"
}
```

#### 오류 응답

| 상태 코드 | 발생 조건 |
|---|---|
| `400 Bad Request` | ID가 숫자가 아니거나 1보다 작은 경우 |
| `404 Not Found` | 해당 ID의 링크가 존재하지 않는 경우 |

## 6. 관리자 API

### 6.1 링크 등록

```http
POST /api/admin/links
Content-Type: application/json
```

#### 요청 본문

```json
{
  "name": "e클래스",
  "url": "https://eclass.hanseo.ac.kr",
  "category": "remote_class"
}
```

#### 성공 응답

```http
HTTP/1.1 201 Created
Location: /api/links/1
Content-Type: application/json
```

```json
{
  "id": 1,
  "name": "e클래스",
  "url": "https://eclass.hanseo.ac.kr",
  "category": "REMOTE_CLASS",
  "createdAt": "2026-07-17T14:00:00.123456",
  "updatedAt": "2026-07-17T14:00:00.123456"
}
```

#### 오류 응답

| 상태 코드 | 발생 조건 |
|---|---|
| `400 Bad Request` | 이름, URL 또는 카테고리가 누락된 경우 |
| `400 Bad Request` | 필드별 최대 길이를 초과한 경우 |
| `400 Bad Request` | URL이 올바른 `http` 또는 `https` 주소가 아닌 경우 |
| `400 Bad Request` | JSON 형식이 올바르지 않은 경우 |

### 6.2 링크 전체 수정

```http
PUT /api/admin/links/{linkId}
Content-Type: application/json
```

이름, URL, 카테고리를 모두 전달하여 기존 링크를 전체 수정합니다. `id`와 `createdAt`은 변경되지 않으며 `updatedAt`은 자동 갱신됩니다.

#### 요청 본문

```json
{
  "name": "한서포탈",
  "url": "https://portal.hanseo.ac.kr",
  "category": "academic"
}
```

#### 성공 응답

```http
HTTP/1.1 200 OK
Content-Type: application/json
```

```json
{
  "id": 1,
  "name": "한서포탈",
  "url": "https://portal.hanseo.ac.kr",
  "category": "ACADEMIC",
  "createdAt": "2026-07-17T14:00:00.123456",
  "updatedAt": "2026-07-17T15:30:00.654321"
}
```

#### 오류 응답

| 상태 코드 | 발생 조건 |
|---|---|
| `400 Bad Request` | ID 또는 요청 본문이 올바르지 않은 경우 |
| `404 Not Found` | 수정할 링크가 존재하지 않는 경우 |

### 6.3 링크 삭제

```http
DELETE /api/admin/links/{linkId}
```

링크를 데이터베이스에서 실제로 삭제합니다.

#### 성공 응답

```http
HTTP/1.1 204 No Content
```

응답 본문은 없습니다.

#### 오류 응답

| 상태 코드 | 발생 조건 |
|---|---|
| `400 Bad Request` | ID가 숫자가 아니거나 1보다 작은 경우 |
| `404 Not Found` | 삭제할 링크가 존재하지 않는 경우 |

## 7. 공통 오류 응답

모든 오류는 다음 구조로 반환합니다.

| 필드 | 타입 | 설명 |
|---|---|---|
| `status` | Integer | HTTP 상태 코드 |
| `message` | String | 오류 설명 |
| `path` | String | 오류가 발생한 요청 경로 |
| `timestamp` | Instant | 오류 발생 시각 |

### 400 Bad Request 예시

```json
{
  "status": 400,
  "message": "url: http 또는 https 형식의 올바른 URL이어야 합니다.",
  "path": "/api/admin/links",
  "timestamp": "2026-07-17T05:00:00.123456Z"
}
```

### 404 Not Found 예시

```json
{
  "status": 404,
  "message": "링크를 찾을 수 없습니다. linkId=999",
  "path": "/api/links/999",
  "timestamp": "2026-07-17T05:00:00.123456Z"
}
```

### 500 Internal Server Error 예시

```json
{
  "status": 500,
  "message": "서버 내부 오류가 발생했습니다.",
  "path": "/api/links",
  "timestamp": "2026-07-17T05:00:00.123456Z"
}
```

내부 스택 트레이스와 데이터베이스 접속 정보는 응답에 포함하지 않습니다.

## 8. Swagger 확인

로컬 서버 실행 후 다음 주소에서 API를 확인하고 직접 요청할 수 있습니다.

```text
Swagger UI: http://localhost:8080/swagger-ui.html
OpenAPI JSON: http://localhost:8080/v3/api-docs
```

운영 프로필에서는 Swagger와 OpenAPI가 기본적으로 비활성화됩니다.

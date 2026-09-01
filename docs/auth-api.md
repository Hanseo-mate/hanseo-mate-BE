# 인증 API 명세서

## 1. 기능 개요

사용자는 아이디와 비밀번호로 회원가입·로그인하며, 성공 시 Access Token과 Refresh Token을 함께 발급받습니다.

- Access Token 기본 유효기간: 3,600초(1시간)
- Refresh Token 기본 유효기간: 로그인 또는 회원가입 시점부터 2,592,000초(30일)
- Access Token은 인증이 필요한 API의 `Authorization` 헤더에 사용
- Refresh Token은 Access Token 재발급과 로그아웃 요청 본문에만 사용
- 재발급 성공 시 기존 Refresh Token을 폐기하고 새 Refresh Token으로 교체
- 탈취된 이전 Refresh Token의 재사용이 감지되면 같은 로그인 세션의 Refresh Token 전체 폐기
- 서버 DB에는 Refresh Token 원문이 아닌 SHA-256 해시만 저장

> Refresh Token의 30일 만료 시각은 재발급해도 연장되지 않습니다. 만료되면 아이디와 비밀번호로 다시 로그인해야 합니다.

## 2. 공통 인증 방식

인증이 필요한 API는 다음 헤더를 사용합니다.

```http
Authorization: Bearer {accessToken}
```

Refresh Token에는 `Bearer` 접두사를 붙이지 않고 JSON 요청 본문으로 전달합니다.

## 3. 회원가입

### 요청

```http
POST /api/auth/signup
Content-Type: application/json
```

```json
{
  "loginId": "user01",
  "password": "password1234"
}
```

### 성공 응답

```http
HTTP/1.1 201 Created
```

```json
{
  "accessToken": "eyJ...",
  "refreshToken": "Qm9...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "refreshTokenExpiresIn": 2592000,
  "userId": 1,
  "loginId": "user01",
  "role": "USER",
  "preferredRestaurantType": "MAIN_STUDENT",
  "createdAt": "2026-09-01T12:00:00",
  "updatedAt": "2026-09-01T12:00:00"
}
```

## 4. 로그인

### 요청

```http
POST /api/auth/login
Content-Type: application/json
```

```json
{
  "loginId": "user01",
  "password": "password1234"
}
```

### 성공 응답

```http
HTTP/1.1 200 OK
```

응답 형식은 회원가입 성공 응답과 같습니다.

## 5. 토큰 재발급

### 요청

```http
POST /api/auth/refresh
Content-Type: application/json
```

```json
{
  "refreshToken": "Qm9..."
}
```

### 성공 응답

```http
HTTP/1.1 200 OK
```

```json
{
  "accessToken": "eyJ...",
  "refreshToken": "새로운-refresh-token",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "refreshTokenExpiresIn": 2588400
}
```

- 응답을 받으면 클라이언트는 기존 Access Token과 Refresh Token을 모두 새 값으로 즉시 교체해야 합니다.
- `refreshTokenExpiresIn`은 최초 로그인 세션의 30일 만료 시각까지 남은 초입니다.
- 같은 Refresh Token으로 두 번 재발급할 수 없습니다.

## 6. 로그아웃

### 요청

```http
POST /api/auth/logout
Content-Type: application/json
```

```json
{
  "refreshToken": "Qm9..."
}
```

### 성공 응답

```http
HTTP/1.1 204 No Content
```

- 존재하지 않거나 이미 폐기된 Refresh Token도 보안상 동일하게 `204`를 응답합니다.
- 로그아웃한 Refresh Token으로는 더 이상 재발급할 수 없습니다.
- 이미 발급된 Access Token은 별도로 서버에 저장하지 않으므로 남은 유효기간(최대 1시간) 동안 유효할 수 있습니다. 클라이언트는 로그아웃 즉시 두 토큰을 모두 삭제해야 합니다.

## 7. 오류 응답

### 유효하지 않은 Refresh Token

```http
HTTP/1.1 401 Unauthorized
```

```json
{
  "status": 401,
  "message": "유효하지 않거나 만료된 Refresh Token입니다.",
  "path": "/api/auth/refresh",
  "timestamp": "2026-09-01T03:00:00Z"
}
```

| HTTP 상태 | 발생 조건 |
|---:|---|
| 400 | 필수 필드 누락, 빈 문자열, 허용 길이 초과 |
| 401 | 로그인 정보 불일치 또는 Refresh Token이 유효하지 않음·만료됨·폐기됨 |
| 409 | 이미 사용 중인 로그인 아이디 |

## 8. 프론트엔드 처리 순서

1. 회원가입 또는 로그인 성공 시 `accessToken`, `refreshToken`을 저장합니다.
2. 인증 API에는 Access Token만 `Authorization` 헤더로 보냅니다.
3. Access Token 만료로 `401`이 발생하면 Refresh Token으로 `/api/auth/refresh`를 한 번 호출합니다.
4. 성공하면 두 토큰을 모두 새 값으로 교체하고 원래 요청을 한 번 재시도합니다.
5. 재발급도 `401`이면 저장된 토큰을 모두 삭제하고 로그인 화면으로 이동합니다.
6. 로그아웃 시 `/api/auth/logout` 호출 후 성공 여부와 관계없이 로컬 토큰을 삭제합니다.

> 동시에 여러 API가 `401`을 반환해도 재발급 요청은 한 번만 실행하고, 나머지 요청은 그 결과를 기다리도록 구현해야 합니다. 같은 Refresh Token을 동시에 여러 번 보내면 회전 정책에 따라 해당 로그인 세션이 폐기될 수 있습니다.

## 9. 서버 설정 및 배포 전 작업

운영 DB는 `ddl-auto=validate`이므로 새 코드를 배포하기 전에 `docs/refresh-token-migration-mysql.sql`을 한 번 실행해야 합니다.

선택 환경변수:

```text
JWT_ACCESS_TOKEN_EXPIRATION_SECONDS=3600
JWT_REFRESH_TOKEN_EXPIRATION_SECONDS=2592000
```

값을 설정하지 않으면 위 기본값을 사용합니다. `JWT_SECRET`과 토큰 원문은 로그·문서·Git에 기록하지 않습니다.

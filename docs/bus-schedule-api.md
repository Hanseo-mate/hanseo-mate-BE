# 버스 시간표 이미지 API 명세서

## 1. 기능 개요

버스 시간표 이미지를 대분류(`mainCategory`)와 소분류(`subCategory`) 조합으로 관리합니다.

- 분류 조합당 이미지를 하나만 유지합니다.
- 동일 분류로 이미지를 다시 업로드하면 서버 물리 파일과 DB 레코드가 함께 교체됩니다.
- 파일명에 타임스탬프를 포함해 클라이언트 캐싱을 방지합니다.
- 조회 API는 비로그인 사용자를 포함해 누구나 호출할 수 있습니다.
- 업로드/교체 API는 `ADMIN` 역할을 가진 사용자만 호출할 수 있습니다.

---

## 2. 공통 정보

```text
Base URL: http://localhost:8080
Response Content-Type: application/json
```

### 인증 및 권한

| API | 접근 권한 |
|---|---|
| `GET /api/bus-schedules` | 누구나 (비로그인 포함) |
| `POST /api/admin/bus-schedules` | `ADMIN` 역할 JWT 필수 |

`ADMIN` API 호출 시 헤더:

```http
Authorization: Bearer {ADMIN_ACCESS_TOKEN}
```

| 상황 | 상태 코드 |
|---|---:|
| JWT 없음, 잘못된 JWT, 만료된 JWT | `401 Unauthorized` |
| 로그인했지만 `USER` 역할 | `403 Forbidden` |
| `ADMIN` 역할 | 요청 처리 |

---

## 3. Enum 정의

### MainCategory (대분류)

| 값 | 설명 |
|---|---|
| `CITY_BUS` | 시내버스 |
| `COMMUTER_BUS` | 통근버스 |
| `SCHOOL_BUS` | 학교 셔틀 |

### SubCategory (소분류)

| 값 | 소속 MainCategory | 설명 |
|---|---|---|
| `TAEAN_TO_AIRFIELD` | `CITY_BUS` | 태안 → 비행장 |
| `AIRFIELD_TO_TAEAN` | `CITY_BUS` | 비행장 → 태안 |
| `HANSEO_TO_SEOSAN` | `CITY_BUS` | 한서대 → 서산 |
| `COMMUTE_TO_SCHOOL` | `COMMUTER_BUS` | 통근 등교 |
| `COMMUTE_FROM_SCHOOL` | `COMMUTER_BUS` | 통근 하교 |
| `NEARBY_TO_SCHOOL` | `SCHOOL_BUS` | 인근 등교 |
| `NEARBY_FROM_SCHOOL` | `SCHOOL_BUS` | 인근 하교 |
| `CAMPUS_SHUTTLE` | `SCHOOL_BUS` | 캠퍼스 셔틀 |
| `SEOSAN_CIRCULAR` | `SCHOOL_BUS` | 서산 순환 |
| `NAEPO_SAPGYO_CIRCULAR` | `SCHOOL_BUS` | 내포·삽교 순환 |

> `mainCategory`와 `subCategory` 조합이 DB 식별 키로 사용됩니다.  
> 논리적으로 유효하지 않은 조합(예: `CITY_BUS` + `CAMPUS_SHUTTLE`)도 서버는 거부하지 않으며, 클라이언트가 올바른 조합을 전달해야 합니다.

---

## 4. 버스 시간표 전체 조회

```http
GET /api/bus-schedules
```

### 요청

파라미터 없음. 인증 불필요.

### 성공 응답

```http
200 OK
```

```json
[
  {
    "id": 1,
    "mainCategory": "CITY_BUS",
    "subCategory": "HANSEO_TO_SEOSAN",
    "imageUrl": "http://localhost:8080/home/images/bus/HANSEO_TO_SEOSAN_20260823_234400.png",
    "updatedAt": "2026-08-23T23:44:00"
  },
  {
    "id": 2,
    "mainCategory": "SCHOOL_BUS",
    "subCategory": "CAMPUS_SHUTTLE",
    "imageUrl": "http://localhost:8080/home/images/bus/CAMPUS_SHUTTLE_20260823_235500.png",
    "updatedAt": "2026-08-23T23:55:00"
  }
]
```

등록된 시간표가 없으면 `[]`를 반환합니다.

### 응답 필드

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Number | 레코드 고유 ID |
| `mainCategory` | String (Enum) | 대분류 (`MainCategory` 값) |
| `subCategory` | String (Enum) | 소분류 (`SubCategory` 값) |
| `imageUrl` | String | 클라이언트가 이미지를 요청할 공개 URL |
| `updatedAt` | String (ISO 8601) | 마지막 업데이트 일시 (`yyyy-MM-ddTHH:mm:ss`) |

> `serverFilePath`(서버 물리 경로)는 보안상 응답에 포함되지 않습니다.

---

## 5. 버스 시간표 이미지 업로드 / 교체

```http
POST /api/admin/bus-schedules
Authorization: Bearer {ADMIN_ACCESS_TOKEN}
Content-Type: multipart/form-data
```

### 요청 form-data

| Key | Type | 필수 | 설명 |
|---|---|:---:|---|
| `image` | File | ✅ | 업로드할 이미지 파일 (JPG, PNG, GIF) |
| `mainCategory` | Text | ✅ | 대분류 Enum 값 (예: `CITY_BUS`) |
| `subCategory` | Text | ✅ | 소분류 Enum 값 (예: `HANSEO_TO_SEOSAN`) |

#### 요청 예시 (신규 등록)

```text
image         [binary: HANSEO_TO_SEOSAN.png]
mainCategory  CITY_BUS
subCategory   HANSEO_TO_SEOSAN
```

#### 요청 예시 (기존 이미지 교체)

동일한 `mainCategory` + `subCategory` 조합으로 다시 요청하면 기존 파일을 삭제하고 새 이미지로 교체합니다.

```text
image         [binary: HANSEO_TO_SEOSAN_new.png]
mainCategory  CITY_BUS
subCategory   HANSEO_TO_SEOSAN
```

### 성공 응답

```http
200 OK
```

```json
{
  "id": 1,
  "mainCategory": "CITY_BUS",
  "subCategory": "HANSEO_TO_SEOSAN",
  "imageUrl": "http://localhost:8080/home/images/bus/HANSEO_TO_SEOSAN_20260824_001500.png",
  "updatedAt": "2026-08-24T00:15:00"
}
```

### 응답 필드

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Number | 레코드 고유 ID |
| `mainCategory` | String (Enum) | 대분류 |
| `subCategory` | String (Enum) | 소분류 |
| `imageUrl` | String | 새로 저장된 이미지의 공개 URL |
| `updatedAt` | String (ISO 8601) | 업데이트 일시 |

---

## 6. 파일 저장 규칙

### 저장 경로

| 항목 | 값 |
|---|---|
| 서버 물리 저장 경로 | `/home/images/bus/{파일명}` |
| 공개 이미지 URL | `{app.upload.public-base-url}/home/images/bus/{파일명}` |

### 파일명 생성 규칙

```
{SubCategory이름}_{yyyyMMdd}_{HHmmss}.{확장자}
```

| 예시 SubCategory | 업로드 시각 | 생성된 파일명 |
|---|---|---|
| `HANSEO_TO_SEOSAN` | 2026-08-23 23:44:00 | `HANSEO_TO_SEOSAN_20260823_234400.png` |
| `CAMPUS_SHUTTLE` | 2026-08-24 00:15:30 | `CAMPUS_SHUTTLE_20260824_001530.jpg` |
| `NAEPO_SAPGYO_CIRCULAR` | 2026-08-24 09:00:00 | `NAEPO_SAPGYO_CIRCULAR_20260824_090000.png` |

타임스탬프를 파일명에 포함해 클라이언트 캐시가 자동으로 무효화됩니다.

### 교체 시 처리 흐름

1. DB에서 동일 `mainCategory` + `subCategory` 레코드 조회
2. 레코드가 존재하면 `serverFilePath`로 `Files.deleteIfExists()` 호출 → 기존 파일 삭제
3. 새 파일을 `/home/images/bus/{새파일명}` 경로에 저장
4. DB의 `imageUrl`, `serverFilePath`, `updatedAt` 업데이트
5. 레코드가 없으면 새로 생성

---

## 7. 오류 응답

```json
{
  "status": 400,
  "message": "업로드할 이미지 파일이 없습니다.",
  "path": "/api/admin/bus-schedules",
  "timestamp": "2026-08-24T00:00:00Z"
}
```

| 상태 코드 | 발생 상황 |
|---:|---|
| `400` | `image` 파트 누락, 빈 파일, 지원하지 않는 이미지 형식, 알 수 없는 Enum 값 |
| `401` | JWT 없음, 잘못된 JWT, 만료된 JWT |
| `403` | `USER` 역할로 관리자 API 호출 |
| `500` | 서버 파일 저장 실패 (디스크 권한 문제 등) |

---

## 8. API 요약

| Method | URL | 인증 | 기능 |
|---|---|---|---|
| `GET` | `/api/bus-schedules` | 불필요 | 전체 버스 시간표 목록 조회 |
| `POST` | `/api/admin/bus-schedules` | ADMIN JWT | 버스 시간표 이미지 업로드 / 교체 |

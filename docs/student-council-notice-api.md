# 학생회 공지 API 명세서

## 공통 안내

- 학생회 공지는 크롤링 공지와 별도인 `student_council_notices` 테이블에서 관리합니다.
- 본문은 일반 텍스트이며 줄바꿈과 이모티콘을 원문 그대로 저장·반환합니다.
- 본문에는 애플리케이션 글자 수 제한이 없고 MySQL `LONGTEXT`를 사용합니다.
- 목록은 최신 작성순으로 10개씩 조회합니다.
- 로그인과 관리자 권한 검사는 아직 적용하지 않았습니다.

## 사용자 API

### 학생회 공지 목록 조회

```http
GET /api/notices/categories/admin?page=0
```

```json
{
  "items": [
    {
      "id": 1,
      "title": "학생회 행사 안내",
      "createdAt": "2026-07-25T16:00:00",
      "updatedAt": "2026-07-25T16:00:00"
    }
  ],
  "page": 0,
  "size": 10,
  "totalPages": 1,
  "totalElements": 1,
  "hasNext": false
}
```

### 학생회 공지 상세 조회

```http
GET /api/notices/categories/admin/{noticeId}
```

```json
{
  "id": 1,
  "title": "학생회 행사 안내",
  "content": "학생회 행사 내용을 안내드립니다. 📢\n많은 참여 부탁드립니다.",
  "createdAt": "2026-07-25T16:00:00",
  "updatedAt": "2026-07-25T16:00:00"
}
```

## 관리자 API

### 학생회 공지 등록

```http
POST /api/admin/notices
Content-Type: application/json
```

```json
{
  "title": "학생회 행사 안내",
  "content": "학생회 행사 내용을 안내드립니다. 📢\n많은 참여 부탁드립니다."
}
```

- 성공 상태: `201 Created`
- `Location`: `/api/notices/categories/admin/{noticeId}`
- 응답 본문: 생성된 학생회 공지 상세 정보

### 학생회 공지 수정

```http
PUT /api/admin/notices/{noticeId}
Content-Type: application/json
```

```json
{
  "title": "수정된 학생회 행사 안내",
  "content": "변경된 행사 내용입니다. ✅"
}
```

- 성공 상태: `200 OK`
- 제목과 내용을 모두 전달하는 전체 수정 방식입니다.
- 응답 본문: 수정된 학생회 공지 상세 정보

### 학생회 공지 삭제

```http
DELETE /api/admin/notices/{noticeId}
```

- 성공 상태: `204 No Content`
- 응답 본문은 없습니다.

## 요청값

| 필드 | 필수 | 조건 |
|---|---:|---|
| `title` | 필수 | 공백 불가, 최대 500자 |
| `content` | 필수 | 공백 불가, 애플리케이션 글자 수 제한 없음 |

## 오류 상태

| 상태 코드 | 발생 상황 |
|---:|---|
| `400` | 잘못된 ID·페이지 번호, 빈 제목·내용, 500자를 초과한 제목 |
| `404` | 학생회 공지가 존재하지 않음 |
| `500` | 예상하지 못한 서버 오류 |

```json
{
  "status": 404,
  "message": "학생회 공지를 찾을 수 없습니다. noticeId=999",
  "path": "/api/admin/notices/999",
  "timestamp": "2026-07-25T07:00:00Z"
}
```

> 현재 `/api/admin/notices/**`에는 인증이 없습니다. 정식 배포 전 관리자 인증과 권한 검사를 적용해야 합니다.

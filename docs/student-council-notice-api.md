# 학생회 공지 API 명세서

## 1. 기능 안내

학생회 공지는 학교 홈페이지에서 수집하는 크롤링 공지와 분리하여
`student_council_notices` 테이블에서 관리한다.

관리자는 제목·작성자·본문과 함께 이미지 및 일반 첨부파일을 등록하고 수정·삭제할 수
있다. 사용자는 로그인 없이 학생회 공지 목록과 상세 내용을 조회하고 일반 첨부파일을
다운로드할 수 있다.

### 주요 특징

- 기존 `application/json` 등록·수정 API를 그대로 지원한다.
- 파일을 함께 전송할 때는 같은 URL에 `multipart/form-data` 요청을 사용한다.
- 이미지는 공개 URL로 제공하고, 일반 첨부파일은 다운로드 API를 통해 제공한다.
- 공지당 이미지와 일반 첨부파일 개수에는 애플리케이션 제한이 없다.
- 파일이 없는 공지는 `images`, `attachments`를 각각 빈 배열 `[]`로 반환한다.
- 사용자용 상세 조회는 성공할 때마다 조회수가 1 증가한다.
- 관리자용 상세 조회는 조회수를 증가시키지 않는다.
- 사용자 조회·다운로드 API는 공개되며 `/api/admin/notices/**`는 `ADMIN` 권한이 필요하다.

---

## 2. 공통 정보

| 항목 | 내용 |
|---|---|
| 사용자 경로 | `/api/notices/categories/admin/**` |
| 관리자 경로 | `/api/admin/notices/**` |
| JSON 요청 형식 | `application/json` |
| 파일 포함 요청 형식 | `multipart/form-data` |
| 응답 형식 | `application/json` |
| 관리자 인증 | `Authorization: Bearer {accessToken}`, `ADMIN` 권한 |

### 이미지 응답 객체

```json
{
  "id": 11,
  "fileName": "행사 포스터.png",
  "imageUrl": "https://api.example.com/uploads/student-council-notices/1/images/uuid.png",
  "contentType": "image/png",
  "fileSize": 245810,
  "createdAt": "2026-08-12T18:00:00",
  "updatedAt": "2026-08-12T18:00:00"
}
```

### 일반 첨부파일 응답 객체

```json
{
  "id": 21,
  "fileName": "행사 안내문.pdf",
  "downloadUrl": "https://api.example.com/api/notices/categories/admin/1/attachments/21/download",
  "contentType": "application/pdf",
  "fileSize": 781230,
  "createdAt": "2026-08-12T18:00:00",
  "updatedAt": "2026-08-12T18:00:00"
}
```

- `fileName`은 사용자가 업로드한 원본 파일의 표시용 이름이며 최대 500자로 저장한다.
- `imageUrl`은 공개 이미지 URL이다.
- `downloadUrl`은 `UPLOAD_PUBLIC_BASE_URL`을 포함한 일반 첨부파일 공개 다운로드 URL이다.
- `fileSize`의 단위는 byte이다.
- 이미지와 첨부파일 배열은 등록 ID 오름차순으로 반환한다.

---

## 3. 사용자 API

### 3-1. 학생회 공지 목록 조회

```http
GET /api/notices/categories/admin?page=0
```

`page`는 `0`부터 시작하며 기본값은 `0`이다. 페이지 크기는 10개로 고정된다.

```json
{
  "items": [
    {
      "id": 1,
      "title": "학생회 행사 안내",
      "author": "총학생회",
      "content": "학생회 행사 내용을 안내드립니다.",
      "viewCount": 3,
      "createdAt": "2026-08-12T18:00:00",
      "updatedAt": "2026-08-12T18:00:00",
      "images": [
        {
          "id": 11,
          "fileName": "행사 포스터.png",
          "imageUrl": "https://api.example.com/uploads/student-council-notices/1/images/uuid.png",
          "contentType": "image/png",
          "fileSize": 245810,
          "createdAt": "2026-08-12T18:00:00",
          "updatedAt": "2026-08-12T18:00:00"
        }
      ],
      "attachments": [
        {
          "id": 21,
          "fileName": "행사 안내문.pdf",
          "downloadUrl": "https://api.example.com/api/notices/categories/admin/1/attachments/21/download",
          "contentType": "application/pdf",
          "fileSize": 781230,
          "createdAt": "2026-08-12T18:00:00",
          "updatedAt": "2026-08-12T18:00:00"
        }
      ]
    }
  ],
  "page": 0,
  "size": 10,
  "totalPages": 1,
  "totalElements": 1,
  "hasNext": false
}
```

목록 조회는 조회수를 증가시키지 않는다.

### 3-2. 학생회 공지 상세 조회

```http
GET /api/notices/categories/admin/{noticeId}
```

```json
{
  "id": 1,
  "title": "학생회 행사 안내",
  "author": "총학생회",
  "content": "학생회 행사 내용을 안내드립니다. 📢\n많은 참여 부탁드립니다.",
  "viewCount": 4,
  "createdAt": "2026-08-12T18:00:00",
  "updatedAt": "2026-08-12T18:00:00",
  "images": [],
  "attachments": []
}
```

- 상세 조회가 성공할 때마다 `viewCount`가 1 증가한다.
- 사용자나 기기를 구분하지 않으므로 같은 사용자의 반복 조회도 모두 반영된다.
- 존재하지 않는 공지는 조회수를 변경하지 않고 `404 Not Found`를 반환한다.

### 3-3. 일반 첨부파일 다운로드

```http
GET /api/notices/categories/admin/{noticeId}/attachments/{attachmentId}/download
```

- 로그인 없이 다운로드할 수 있다.
- 파일은 공개 정적 디렉터리에 노출하지 않고 서버의 비공개 저장소에서 읽어 반환한다.
- 응답의 `Content-Type`은 안전한 다운로드를 위해 항상 `application/octet-stream`이다.
- `Content-Disposition: attachment` 헤더와 원본 `fileName`을 사용해 다운로드한다.
- `X-Content-Type-Options: nosniff` 헤더를 함께 반환한다.
- URL의 `attachmentId`가 해당 `noticeId`의 첨부파일이 아니거나 파일이 존재하지 않으면
  `404 Not Found`를 반환한다.

---

## 4. 관리자 조회 API

모든 관리자 API 요청에는 `ADMIN` 권한의 JWT가 필요하다.

```http
Authorization: Bearer {accessToken}
```

### 4-1. 관리자용 학생회 공지 목록 조회

```http
GET /api/admin/notices?page=0
Authorization: Bearer {accessToken}
```

- 성공 상태는 `200 OK`이다.
- 사용자용 목록과 동일한 페이지 응답 및 `images`, `attachments` 배열을 반환한다.
- 조회수를 증가시키지 않는다.

### 4-2. 관리자용 학생회 공지 상세 조회

```http
GET /api/admin/notices/{noticeId}
Authorization: Bearer {accessToken}
```

- 성공 상태는 `200 OK`이다.
- 사용자용 상세와 동일한 필드를 반환한다.
- 현재 `viewCount`를 반환하지만 조회수를 증가시키지 않는다.

---

## 5. 학생회 공지 등록

### 5-1. 파일 없이 JSON으로 등록

기존 JSON 요청 계약을 그대로 지원한다.

```http
POST /api/admin/notices
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "title": "학생회 행사 안내",
  "author": "총학생회",
  "content": "학생회 행사 내용을 안내드립니다. 📢\n많은 참여 부탁드립니다."
}
```

생성된 공지는 `images: []`, `attachments: []`를 반환한다.

### 5-2. 이미지·첨부파일과 함께 등록

```http
POST /api/admin/notices
Authorization: Bearer {accessToken}
Content-Type: multipart/form-data
```

| Part 이름 | 형식 | 필수 | 설명 |
|---|---|---:|---|
| `request` | `application/json` | 필수 | 제목·작성자·본문 JSON |
| `images` | File, 반복 가능 | 선택 | 새로 등록할 이미지 |
| `attachments` | File, 반복 가능 | 선택 | 새로 등록할 일반 첨부파일 |

`request` part:

```json
{
  "title": "학생회 행사 안내",
  "author": "총학생회",
  "content": "학생회 행사 내용을 안내드립니다."
}
```

동일한 part 이름을 반복하여 여러 파일을 전송한다.

```text
request      application/json  {"title":"학생회 행사 안내",...}
images       image/png         poster-1.png
images       image/jpeg        poster-2.jpg
attachments  application/pdf   행사-안내문.pdf
attachments  application/zip   신청서.zip
```

### 등록 성공 응답

- 상태: `201 Created`
- `Location`: `/api/notices/categories/admin/{noticeId}`
- 응답 본문: 생성된 공지 상세 정보와 `images`, `attachments`
- 새 공지의 `viewCount`는 `0`이다.

---

## 6. 학생회 공지 수정

수정은 제목·작성자·본문을 모두 전달하는 전체 수정 방식이다.

### 6-1. 파일을 변경하지 않고 JSON으로 수정

```http
PUT /api/admin/notices/{noticeId}
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "title": "수정된 학생회 행사 안내",
  "author": "제42대 총학생회",
  "content": "변경된 행사 내용입니다. ✅"
}
```

기존 JSON 수정 요청은 기존 이미지와 일반 첨부파일을 그대로 보존한다.

### 6-2. 이미지·첨부파일을 함께 수정

```http
PUT /api/admin/notices/{noticeId}
Authorization: Bearer {accessToken}
Content-Type: multipart/form-data
```

| Part 이름 | 형식 | 필수 | 설명 |
|---|---|---:|---|
| `request` | `application/json` | 필수 | 공지 내용과 기존 파일 유지 목록 |
| `images` | File, 반복 가능 | 선택 | 새로 추가할 이미지 |
| `attachments` | File, 반복 가능 | 선택 | 새로 추가할 일반 첨부파일 |

`request` part 예시:

```json
{
  "title": "수정된 학생회 행사 안내",
  "author": "제42대 총학생회",
  "content": "변경된 행사 내용입니다. ✅",
  "retainedImageIds": [11, 12],
  "retainedAttachmentIds": [21]
}
```

### 기존 파일 유지 규칙

| 값 | 동작 |
|---|---|
| 필드 생략 또는 `null` | 해당 종류의 기존 파일을 모두 보존 |
| 빈 배열 `[]` | 해당 종류의 기존 파일을 모두 삭제 |
| ID 배열 | 배열에 포함된 해당 공지의 기존 파일만 보존하고 나머지는 삭제 |

- `retainedImageIds`는 기존 이미지에만 적용된다.
- `retainedAttachmentIds`는 기존 일반 첨부파일에만 적용된다.
- `images`, `attachments` part로 함께 보낸 새 파일은 유지 목록과 별도로 추가된다.
- 다른 공지에 속한 ID 또는 존재하지 않는 ID를 유지 목록에 전달할 수 없다.
- JSON 방식의 기존 수정 API에는 유지 목록이 없으므로 모든 기존 파일을 보존한다.

성공 상태는 `200 OK`이며 수정 이후의 전체 `images`, `attachments` 배열을 반환한다.

---

## 7. 학생회 공지 삭제

```http
DELETE /api/admin/notices/{noticeId}
Authorization: Bearer {accessToken}
```

- 성공 상태는 `204 No Content`이며 응답 본문은 없다.
- 공지에 속한 이미지 및 일반 첨부파일 DB 데이터도 함께 삭제한다.
- 서버가 관리하는 실제 파일도 DB 커밋 후 정리를 시도한다. 파일 시스템 삭제 실패는 API의
  DB 삭제 결과를 되돌리지 않으므로 운영 환경에서는 디스크 모니터링과 정기 정리가 필요하다.

---

## 8. 요청값과 파일 제한

### 공지 JSON 필드

| 필드 | 필수 | 조건 |
|---|---:|---|
| `title` | 필수 | 공백 불가, 최대 500자 |
| `author` | 필수 | 공백 불가, 최대 100자 |
| `content` | 필수 | 공백 불가, 애플리케이션 글자 수 제한 없음 |
| `retainedImageIds` | 수정 multipart만 선택 | `null`이면 모두 보존, 빈 배열이면 모두 삭제 |
| `retainedAttachmentIds` | 수정 multipart만 선택 | `null`이면 모두 보존, 빈 배열이면 모두 삭제 |

### 이미지

- 실제 파일 내용을 검사하여 JPG, PNG, GIF만 허용한다.
- 파일 확장자나 요청 `Content-Type`만으로 이미지라고 판단하지 않는다.
- 이미지 한 장의 애플리케이션 제한은 `UPLOAD_MAX_IMAGE_BYTES`이며 기본값은
  `5,242,880` byte(5 MiB)이다.

### 일반 첨부파일

- 일반 첨부파일의 종류와 개수에는 별도의 애플리케이션 제한이 없다.
- 일반 첨부파일 원본 이름은 표시 및 다운로드에만 사용하며 실제 저장 파일명으로 사용하지
  않는다.
- 경로와 제어 문자를 제거한 원본 이름은 표시용 메타데이터로 최대 500자까지 저장한다.
- 일반 첨부파일은 UUID 기반 저장 키로 비공개 디렉터리에 보관한다.

### 개수 및 서버 한계

공지당 이미지와 일반 첨부파일 개수에는 별도의 애플리케이션 하드 제한이 없다. 다만
무한한 저장 용량을 의미하지는 않는다.

현재 Spring Boot와 내장 Tomcat의 multipart 개수·크기 설정은 다음과 같이 별도의
애플리케이션 하드 제한을 두지 않는다.

```properties
spring.servlet.multipart.max-file-size=-1
spring.servlet.multipart.max-request-size=-1
server.tomcat.max-part-count=-1
```

- 운영 환경의 reverse proxy, load balancer, 웹 서버에도 더 작은 요청 제한이나 timeout이
  설정되어 있을 수 있다.
- 실제 저장 가능 개수와 총용량은 서버 디스크, inode, DB, 네트워크 및 응답 크기에 의해
  제한된다. 운영 환경에서는 디스크 사용량과 백업 정책을 별도로 관리해야 한다.
- 애플리케이션 제한이 없더라도 매우 큰 파일이나 많은 파일을 한 요청에 보내면 메모리,
  임시 디스크 및 처리 시간을 과도하게 사용할 수 있으므로 운영 프록시와 인프라에서
  허용 크기·timeout을 관리해야 한다.

---

## 9. 오류 응답

| 상태 코드 | 발생 상황 |
|---:|---|
| `400` | 잘못된 ID·페이지, 빈 필수값, 길이 초과, 파일 누락·빈 파일·잘못된 이미지, 잘못된 유지 ID |
| `401` | 관리자 API 요청에 유효한 JWT가 없음 |
| `403` | `USER` 권한으로 관리자 API 요청 |
| `404` | 공지 또는 해당 공지에 속한 첨부파일이 존재하지 않음 |
| `413` | 운영 프록시·웹 서버 등 인프라의 업로드 크기 제한 초과 |
| `415` | 지원하지 않는 요청 `Content-Type` |
| `500` | 예상하지 못한 서버 오류 |

```json
{
  "status": 404,
  "message": "학생회 공지를 찾을 수 없습니다. noticeId=999",
  "path": "/api/admin/notices/999",
  "timestamp": "2026-08-12T09:00:00Z"
}
```

---

## 10. API 요약

| Method | URL | 인증 | 기능 |
|---|---|---|---|
| `GET` | `/api/notices/categories/admin?page=0` | 불필요 | 학생회 공지 목록 조회 |
| `GET` | `/api/notices/categories/admin/{noticeId}` | 불필요 | 학생회 공지 상세 조회 및 조회수 증가 |
| `GET` | `/api/notices/categories/admin/{noticeId}/attachments/{attachmentId}/download` | 불필요 | 일반 첨부파일 다운로드 |
| `GET` | `/api/admin/notices?page=0` | `ADMIN` | 관리자용 목록 조회 |
| `GET` | `/api/admin/notices/{noticeId}` | `ADMIN` | 관리자용 상세 조회, 조회수 불변 |
| `POST` | `/api/admin/notices` | `ADMIN` | JSON 또는 multipart 공지 등록 |
| `PUT` | `/api/admin/notices/{noticeId}` | `ADMIN` | JSON 또는 multipart 공지 전체 수정 |
| `DELETE` | `/api/admin/notices/{noticeId}` | `ADMIN` | 공지와 모든 파일 삭제 |

---

## 11. 저장소 환경변수

```text
UPLOAD_DIRECTORY=/srv/hanseomate/uploads
UPLOAD_PUBLIC_BASE_URL=https://api.example.com
UPLOAD_MAX_IMAGE_BYTES=5242880
NOTICE_ATTACHMENT_DIRECTORY=/srv/hanseomate/private-uploads/student-council-notices
```

- `UPLOAD_DIRECTORY`: 학생회 공지 이미지를 포함한 공개 이미지 저장 루트
- `UPLOAD_PUBLIC_BASE_URL`: `imageUrl` 생성에 사용하는 외부 API 주소
- `UPLOAD_MAX_IMAGE_BYTES`: 이미지 한 장의 애플리케이션 최대 크기
- `NOTICE_ATTACHMENT_DIRECTORY`: 정적 리소스로 공개하지 않는 일반 첨부파일 저장 루트

운영에서는 두 저장 디렉터리를 재배포 후에도 유지되는 영속 디스크 또는 볼륨에 연결하고
애플리케이션 프로세스에 읽기·쓰기 권한을 부여해야 한다. 여러 애플리케이션 인스턴스를
사용하면 모든 인스턴스가 같은 파일을 읽을 수 있는 공유 저장소가 필요하다.

### 브라우저 CORS

`/api/notices/**`의 공개 GET·OPTIONS 요청은 `CORS_ALLOWED_ORIGINS`에 등록된 Origin에
허용된다. 첨부파일 다운로드 응답에서는 브라우저 JavaScript가 파일명을 읽을 수 있도록
`Content-Disposition`, `Content-Length`, `X-Content-Type-Options` 헤더를 노출한다.

---

## 12. 운영 DB 배포

운영 프로필은 `spring.jpa.hibernate.ddl-auto=validate`이고 프로젝트는 Flyway나
Liquibase를 사용하지 않는다. 따라서 기존 운영 DB에는 새 애플리케이션을 시작하기 전에
다음 증분 DDL을 한 번 적용해야 한다.

```sql
CREATE TABLE student_council_notice_images (
    id BIGINT NOT NULL AUTO_INCREMENT,
    notice_id BIGINT NOT NULL,
    image_url VARCHAR(2048) NOT NULL,
    original_file_name VARCHAR(500) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_student_council_notice_images_notice (notice_id, id),
    CONSTRAINT fk_student_council_notice_images_notice
        FOREIGN KEY (notice_id)
        REFERENCES student_council_notices (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE student_council_notice_attachments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    notice_id BIGINT NOT NULL,
    storage_key VARCHAR(255) NOT NULL,
    original_file_name VARCHAR(500) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_student_council_notice_attachment_storage_key UNIQUE (storage_key),
    INDEX idx_student_council_notice_attachments_notice (notice_id, id),
    CONSTRAINT fk_student_council_notice_attachments_notice
        FOREIGN KEY (notice_id)
        REFERENCES student_council_notices (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

`docs/database-schema-mysql.sql`은 완전히 비어 있는 새 DB용 전체 스키마이다. 기존 운영
DB에는 전체 스키마 파일을 다시 실행하지 말고 위의 증분 `CREATE TABLE` 문만 적용한다.
DDL 적용 후 운영 프로필로 애플리케이션을 시작하여 Hibernate schema validation을
통과하는지 확인한다.

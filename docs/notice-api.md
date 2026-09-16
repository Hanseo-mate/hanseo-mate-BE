# 공지 API 명세서

## 1. 기본 정보

| 항목 | 내용                           |
|---|------------------------------|
| 서비스 | 한서 메이트 백엔드                   |
| Base URL | `http://34.64.250.12:8080`   |
| 요청/응답 형식 | `application/json`           |
| 페이징 기준 | `page`는 0부터 시작, 페이지 크기 기본 10 (통합 검색은 20) |
| 정렬 공통 규칙 | `isHot=true`가 항상 우선 노출 (통합 검색 제외) |

## 2. 공지 카테고리 ENUM

| ENUM 값 | 의미 |
|---|---|
| `academic` | 학사공지 |
| `general` | 일반공지 |
| `scholarship` | 장학공지 |
| `graduate` | 대학원공지 |
| `STUDENT_COUNCIL` | 총학생회 공지 (통합 API 전용) |

## 3. API 목록

| Method | Endpoint | 설명 |
|---|---|---|
| `GET` | `/api/notices/categories/{noticeType}?page=0` | 카테고리별 공지 목록 조회 |
| `GET` | `/api/notices?page=0` | 전체 공지 목록 조회(대학원 제외) |
| `GET` | `/api/notices/search?keyword=수강신청&page=0` | 일반 공지 검색(제목/내용/작성자, 띄어쓰기 무시) |
| `GET` | `/api/notices/unified?keyword=수강신청&page=0&size=20` | **[NEW]** 일반/학생회 공지 통합 목록 및 검색 (제목 전용, 띄어쓰기 무시) |
| `GET` | `/api/notices/{noticeId}` | 공지 상세 조회(첨부파일 포함) |

## 4. 목록 조회 규칙

### 4.1 카테고리별 목록 & 4.2 전체 목록
- 페이지당 10건 반환
- 정렬: `isHot DESC` 우선, 이후 카테고리 우선순위 `academic → general → scholarship`, 이후 `postDate DESC`, `id DESC`
- 전체 목록에서 `graduate`(대학원)는 제외됨

### 4.3 일반/학생회 공지 통합 목록 및 검색 (`/unified`)
- 페이지당 기본 20건 반환 (size 파라미터로 조절 가능)
- `keyword` 생략 시 전체 공지(일반+학생회)를 최신순으로 반환
- 검색 시 일반 공지와 학생회 공지의 **제목(title)**을 띄어쓰기 무시(`REPLACE`)하여 통합 검색
- 정렬: 작성일(`postDate` / `createdAt`) 기준 내림차순(최신순)

## 5. 상세 조회 규칙

- 공지 단건 조회 시 첨부파일 목록(`attachments`)을 함께 반환
- 공지 상세를 조회할 때마다 `viewCount`가 1씩 증가 **(목록 조회 시에는 증가하지 않음)**
- 존재하지 않는 `noticeId`는 `404 Not Found`

## 6. 요청/응답 스펙

### 6.1 카테고리별 공지 목록 조회 & 6.2 전체 목록 조회

```http
GET /api/notices/categories/academic?page=0
```

#### 성공 응답 예시 (`200 OK`)

```json
{
  "items": [
    {
      "id": 101,
      "noticeType": "academic",
      "originNoticeId": "38142",
      "title": "2026학년도 2학기 수강신청 안내",
      "sourceUrl": "[https://www.hanseo.ac.kr/](https://www.hanseo.ac.kr/)...",
      "author": "교무처",
      "postDate": "2026-07-22",
      "isHot": true,
      "viewCount": 128
    }
  ],
  "page": 0,
  "size": 10,
  "totalPages": 37,
  "totalElements": 366,
  "hasNext": true
}
```

#### 응답 필드 (items 배열 내부)

| 필드 | 타입 | Nullable | 설명 |
|---|---|---|---|
| `id` | Long | X | 공지 ID |
| `noticeType` | String | X | 공지 카테고리 ENUM |
| `originNoticeId` | String | X | 원본 공지 ID |
| `title` | String | X | 공지 제목 |
| `sourceUrl` | String | X | 원문 링크 |
| `author` | String | X | 작성자 |
| `postDate` | String(`yyyy-MM-dd`) | X | 게시일 |
| `isHot` | Boolean | X | HOT 여부 |
| `viewCount` | Long | X | 현재 조회수 (목록 조회 시 증가 안 함) |

### 6.3 일반/학생회 공지 통합 목록 및 검색 [신규]

```http
GET /api/notices/unified?keyword=수 강신청&page=0&size=20
```

#### Query Parameter

| 이름 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---|---|---|
| `keyword` | String | X | `""` (빈 문자열) | 검색어(공백 무시). 없으면 전체 최신순 조회 |
| `page` | Integer | X | `0` | 0 이상 정수 |
| `size` | Integer | X | `20` | 페이지당 반환 건수 |

#### 성공 응답 예시 (`200 OK`)

```json
[
  {
    "id": 102,
    "noticeType": "STUDENT_COUNCIL",
    "originNoticeId": null,
    "title": "총학생회 주관 수강신청 간식 행사 안내",
    "sourceUrl": null,
    "author": null,
    "postDate": "2026-07-30",
    "isHot": false,
    "viewCount": 45
  },
  {
    "id": 101,
    "noticeType": "academic",
    "originNoticeId": "38142",
    "title": "2026학년도 2학기 수강신청 안내",
    "sourceUrl": "[https://www.hanseo.ac.kr/](https://www.hanseo.ac.kr/)...",
    "author": "교무처",
    "postDate": "2026-07-22",
    "isHot": true,
    "viewCount": 128
  }
]
```
> **참고:** 페이징 객체(`PageResponse`)가 아닌 `List` 형태로 반환되며, 학생회 공지의 경우 `originNoticeId`, `sourceUrl`, `author`는 `null`로 응답됩니다.

### 6.4 공지 상세 조회

```http
GET /api/notices/101
```

#### 성공 응답 예시 (`200 OK`)

```json
{
  "id": 101,
  "noticeType": "academic",
  "originNoticeId": "38142",
  "title": "2026학년도 2학기 수강신청 안내",
  "sourceUrl": "[https://www.hanseo.ac.kr/](https://www.hanseo.ac.kr/)...",
  "contentHtml": "<p>공지 본문</p>",
  "author": "교무처",
  "postDate": "2026-07-22",
  "isHot": true,
  "viewCount": 129,
  "attachments": [
    {
      "id": 501,
      "fileName": "수강신청_안내문.pdf",
      "fileUrl": "[https://www.hanseo.ac.kr/cmm/fms/FileDown.do](https://www.hanseo.ac.kr/cmm/fms/FileDown.do)?..."
    }
  ]
}
```

## 7. 오류 응답

오류 응답 공통 구조:

```json
{
  "status": 400,
  "message": "오류 메시지",
  "path": "/api/notices/categories/unknown",
  "timestamp": "2026-07-30T14:00:00.000000Z"
}
```

주요 케이스:

| HTTP Status | 상황 |
|---|---|
| `400 Bad Request` | 잘못된 `noticeType`, 잘못된 `page`, 잘못된 `noticeId` 형식 |
| `404 Not Found` | 존재하지 않는 `noticeId` 조회 |

## 8. 운영 크롤링 스케줄

- Spring Boot 스케줄러가 FastAPI 운영 제어 API(`/crawl/run`)를 `mode=background`로 호출
- 실행 시각: 매일 `12:00`, `18:00` (Asia/Seoul)

## 9. 변경 사항

| 일자 | 변경 내용 |
|---|---|
| 2026-07-30 | 공지 통합 검색 API(`/api/notices/search`) 명세 추가 |
| 2026-07-30 | 상세 응답 `viewCount` 필드 및 조회 시 증가 규칙 추가 |
| 2026-07-30 | 응답 필드 정의 표(목록/상세) 추가 |
| 2026-07-30 | 운영 크롤링 스케줄 항목 추가 |
| **2026-07-30** | **목록 조회 응답 모델(`items`)에 `viewCount` 필드 노출 추가** |
| **2026-07-30** | **일반/학생회 공지 통합 검색 및 목록 조회 API(`/api/notices/unified`) 명세 추가** |
| **2026-07-30** | **공지 카테고리 ENUM에 `STUDENT_COUNCIL` 추가 및 Nullable 필드 규칙 명시** |
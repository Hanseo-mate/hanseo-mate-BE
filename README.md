# 한서 메이트 백엔드

한서대학교 학생들이 학교생활에 필요한 정보를 한곳에서 확인할 수 있도록 제공하는 REST API 서버입니다.

현재 구현된 기능은 학교생활 필수 링크 관리, 학기별 강좌 일괄 수입·조회,
중앙동아리 정보 관리, 로그인 사용자 기반 좋아요와 선택형 활동 후기, 동아리 이미지 업로드,
학생회 공지 CRUD와 관리자용 홈 포스터 이미지 관리입니다.

## 기술 스택

- Java 17
- Spring Boot 4.1.0
- Gradle 9.5.1
- Spring Web MVC
- Spring Data JPA
- MySQL
- Apache POI
- Bean Validation
- Springdoc OpenAPI
- H2 테스트 데이터베이스

## 시간표 관련 패키지 구조

```text
domain
├── courseenrichment       # 동일교과목·타학과 전공인정 엑셀 수입 및 상세조회 연동
├── course                  # 강좌·개설 강좌·수업 시간 등 공용 강좌 데이터
├── courseimport            # 관리자용 전공·교양 엑셀 수입
└── timetable
    └── search              # 사용자용 강좌 검색 API·서비스·DTO·검색 조건
```

시간표 검색 기능은 `domain.timetable.search`에서 독립적으로 관리한다.
향후 개인 시간표 편성, 시간 중복 검사 등은 `domain.timetable` 아래에 별도 기능 패키지로 추가하고,
공용 강좌 정보는 `domain.course`를 참조한다.

## 로컬 실행 준비

JDK 17과 MySQL 8 이상이 필요합니다. 데이터베이스는 한글 저장을 보장하도록 `utf8mb4`로 생성합니다.

```sql
CREATE DATABASE hanseo_mate
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
```

로컬 환경은 `ddl-auto=update`를 사용하고 운영 환경은 `ddl-auto=validate`를 사용합니다.
운영에서는 JPA가 테이블을 임의로 변경하지 않으며, 애플리케이션 시작 시 엔티티와 실제 DB 구조가 일치하는지만 확인합니다.

로컬 프로필은 기본적으로 다음 접속 정보를 사용합니다.

```text
URL: jdbc:mysql://localhost:3306/hanseo_mate
Username: root
```

비밀번호는 코드에 저장하지 않고 환경변수로 전달합니다.

```powershell
$env:DB_PASSWORD="MySQL 비밀번호"
.\gradlew.bat bootRun
```

기본값과 다른 데이터베이스를 사용하면 다음 환경변수도 설정합니다.

```text
DB_URL
DB_USERNAME
DB_PASSWORD
```

운영 환경에서는 `SPRING_PROFILES_ACTIVE=prod`를 사용하며 세 가지 DB 환경변수가 모두 필요합니다.

동아리·홈 포스터·학생회 공지 이미지 파일 저장 위치와 반환 URL, 학생회 공지 일반
첨부파일의 비공개 저장 위치는 다음 환경변수로 설정합니다.

```text
UPLOAD_DIRECTORY=uploads
UPLOAD_PUBLIC_BASE_URL=http://localhost:8080
UPLOAD_MAX_IMAGE_BYTES=5242880
NOTICE_ATTACHMENT_DIRECTORY=private-uploads/student-council-notices
```

운영 환경에서는 `UPLOAD_DIRECTORY`와 `NOTICE_ATTACHMENT_DIRECTORY`를 영속 디스크나
Docker 볼륨에 연결해야 합니다. 일반 첨부파일은 공개 정적 경로가 아니라 API를 통해
다운로드합니다.

새 DB에는 전체 엔티티를 기준으로 정리한 단일 스키마 파일을 한 번 적용합니다.
이 파일은 기존 테이블을 변경하거나 삭제하지 않는 **빈 데이터베이스 전용** 파일입니다.

```powershell
mysql --default-character-set=utf8mb4 -u 사용자명 -p hanseo_mate --execute="source docs/database-schema-mysql.sql"
```

테이블이 하나라도 남아 있으면 스키마 실행이 중단되도록 구성했습니다. 기존 DB를 백업하고
완전히 비운 뒤 실행해야 구버전 컬럼이 섞이지 않습니다.

## API

로그인 사용자 마이페이지 조회 계약은 [마이페이지 API 명세서](docs/my-page-api.md)에서 확인할 수 있습니다.

요청·응답 예시와 오류 형식은 [필수 링크 API 명세서](docs/essential-link-api.md)에서 확인할 수 있습니다.

강좌 수입·조회 계약은 [강좌 수입·조회 API 명세서](docs/course-import-api.md)에서 확인할 수 있습니다.

동일교과목·타학과 전공인정 수입과 강좌 상세 응답 계약은
[과목 정책 보강 API 명세서](docs/course-enrichment-api.md)에서 확인할 수 있습니다.

동아리 기능의 Postman 테스트 순서와 요청·응답 계약은 [동아리 API 명세서](docs/club-api.md)에서 확인할 수 있습니다.

학생회 공지 조회와 관리 계약은 [학생회 공지 API 명세서](docs/student-council-notice-api.md)에서 확인할 수 있습니다.

관리자 홈 포스터 관리 계약은 [홈 포스터 API 명세서](docs/home-poster-api.md)에서 확인할 수 있습니다.

학생회 캘린더 조회와 관리 계약은 [학생회 캘린더 API 명세서](docs/calendar-api.md)에서 확인할 수 있습니다.

로그인 사용자 개인 일정 계약은 [개인 일정 API 명세서](docs/personal-calendar-api.md)에서 확인할 수 있습니다.

학교 공식 일정 관리 계약은 [학교 공식 일정 API 명세서](docs/school-calendar-api.md)에서 확인할 수 있습니다.

세 종류의 일정을 합치는 조회 계약은 [통합 일정 API 명세서](docs/unified-calendar-api.md)에서 확인할 수 있습니다.

| Method | Endpoint | 설명 |
|---|---|---|
| `GET` | `/api/auth/me` | 로그인 사용자의 계정 정보, 작성한 동아리 후기 및 좋아요한 동아리 조회 |
| `GET` | `/api/calendars` | 로그인 없이 학생회 일정 전체 조회 |
| `GET` | `/api/admin/calendars` | 관리자용 학생회 일정 전체 조회 |
| `POST` | `/api/admin/calendars` | 학생회 일정 등록 |
| `PUT` | `/api/admin/calendars/{calendarId}` | 학생회 일정 전체 수정 |
| `DELETE` | `/api/admin/calendars/{calendarId}` | 학생회 일정 삭제 |
| `GET` | `/api/calendars/me` | 로그인 사용자의 개인 일정 전체 조회 |
| `POST` | `/api/calendars/me` | 개인 일정 등록 |
| `PUT` | `/api/calendars/me/{calendarId}` | 본인 개인 일정 전체 수정 |
| `DELETE` | `/api/calendars/me/{calendarId}` | 본인 개인 일정 삭제 |
| `GET` | `/api/calendars/school` | 로그인 없이 학교 공식 일정 전체 조회 |
| `GET` | `/api/admin/school-calendars` | 관리자용 학교 일정 전체 조회 |
| `POST` | `/api/admin/school-calendars` | 학교 일정 등록 |
| `PUT` | `/api/admin/school-calendars/{calendarId}` | 학교 일정 전체 수정 |
| `DELETE` | `/api/admin/school-calendars/{calendarId}` | 학교 일정 삭제 |
| `GET` | `/api/calendars/all` | 학교·학생회·개인 일정 통합 조회 |
| `GET` | `/api/links` | 링크 목록을 ID 오름차순으로 조회 |
| `GET` | `/api/links?category=REMOTE_CLASS` | 카테고리별 링크 조회 |
| `GET` | `/api/links/{linkId}` | 링크 상세 조회 |
| `POST` | `/api/admin/links` | 링크 등록 |
| `PUT` | `/api/admin/links/{linkId}` | 링크 전체 수정 |
| `DELETE` | `/api/admin/links/{linkId}` | 링크 삭제 |
| `POST` | `/api/v1/timetables/major` | 관리자 전용 전공 시간표 엑셀 분석 및 일괄 저장 |
| `POST` | `/api/v1/timetables/general-education` | 관리자 전용 교양 시간표 엑셀 분석 및 일괄 저장 |
| `POST` | `/api/admin/course-enrichments/equivalent-courses/imports` | 관리자 전용 동일교과목 엑셀 자동 감지 및 스냅샷 저장 |
| `POST` | `/api/admin/course-enrichments/cross-major-recognitions/imports` | 관리자 전용 타학과 전공인정 엑셀 자동 감지 및 연간 정책 저장 |
| `GET` | `/api/courses` | 전공·영역, 검색어, 정렬, 시간, 학년, 학점 조건으로 강좌를 페이지 조회 |
| `GET` | `/api/clubs` | 전체 또는 분과별 동아리 목록 조회 |
| `GET` | `/api/clubs/{clubId}` | 동아리 전체 상세 정보와 후기 작성 수 조회 |
| `POST` | `/api/clubs/likes/{clubId}` | 로그인 사용자의 좋아요 상태 토글 |
| `GET` | `/api/clubs/reviews/{clubId}` | 전체 활동 후기 키워드별 선택 비율 조회 |
| `PUT` | `/api/clubs/reviews/{clubId}` | 로그인 사용자의 후기 등록·수정·제거 |
| `GET` | `/api/admin/clubs` | 관리자용 전체 또는 분과별 동아리 목록 조회 |
| `GET` | `/api/admin/clubs/{clubId}` | 관리자용 동아리 상세 조회 |
| `POST` | `/api/admin/clubs` | 동아리 등록 |
| `PUT` | `/api/admin/clubs/background-images/{clubId}` | 배경 이미지 파일 업로드 |
| `DELETE` | `/api/admin/clubs/background-images/{clubId}` | 배경 이미지 삭제 |
| `PUT` | `/api/admin/clubs/profile-images/{clubId}` | 프로필 이미지 파일 업로드 |
| `DELETE` | `/api/admin/clubs/profile-images/{clubId}` | 프로필 이미지 삭제 |
| `PUT` | `/api/admin/clubs/{clubId}` | 동아리 텍스트 정보 통합 수정 |
| `DELETE` | `/api/admin/clubs/{clubId}` | 동아리와 좋아요·후기 데이터 삭제 |
| `POST` | `/api/admin/home-posters` | 홈 포스터 이미지 추가 |
| `GET` | `/api/admin/home-posters` | 관리자용 홈 포스터 전체 조회 |
| `PUT` | `/api/admin/home-posters/{posterId}` | 홈 포스터 이미지 교체 |
| `DELETE` | `/api/admin/home-posters/{posterId}` | 홈 포스터 삭제 |
| `GET` | `/api/notices/categories/admin` | 학생회 공지 목록 조회 |
| `GET` | `/api/notices/categories/admin/{noticeId}` | 학생회 공지 상세 조회 |
| `POST` | `/api/admin/notices` | 학생회공지 등록 |
| `PUT` | `/api/admin/notices/{noticeId}` | 학생회공지 제목·내용 수정 |
| `DELETE` | `/api/admin/notices/{noticeId}` | 학생회공지 삭제 |

링크 데이터는 `id`, `name`, `url`, `category`, `created_at`, `updated_at` 여섯 컬럼만 사용합니다.

### 사용자용 강좌 검색

`GET /api/courses`는 에브리타임 형태의 검색 조건을 조합하여 강좌를 조회합니다.

- 전공·영역: 전공 학과 또는 교양필수·1/2/3영역·원격수업 제공기관
- 검색어: 과목명·교수명·과목코드·장소 중 하나
- 정렬: 기본·과목코드·과목명
- 시간: `0`~`30`교시 사이의 시작·종료 범위
- 학년: 1·2·3·4학년·기타 다중 선택
- 학점: 1·2·3·4학점 이상 다중 선택
- 페이지: `page` 기본값 `0`, `size` 기본값 `20`·최댓값 `100`

같은 필터 그룹에서 선택한 값은 `OR`, 서로 다른 필터 그룹은 `AND`로 적용합니다.
선택값이 없거나 해당 그룹의 모든 값을 선택하면 그 그룹은 필터링하지 않습니다.

```http
GET /api/courses?academicYear=2026&semester=1&curriculumType=MAJOR&academicUnits=항공소프트웨어공학과&searchField=COURSE_NAME&keyword=프로그래밍&sort=COURSE_NAME&startPeriod=0&endPeriod=6&grades=GRADE_1,GRADE_2&credits=CREDIT_3&page=0&size=20
```

응답은 다음 페이지 형식을 사용합니다.

```json
{
  "items": [
    {
      "offeringId": "7da5b546-d431-4b4d-9992-0a50d97399d5",
      "courseName": "웹프로그래밍",
      "sectionNo": "01",
      "credit": 3,
      "cyber": false,
      "instructorName": "홍길동",
      "curriculumType": "MAJOR",
      "targetGrade": 2,
      "originalAcademicUnitName": "항공소프트웨어공학과",
      "eligibleDepartmentNames": [],
      "generalCategory": null,
      "schedules": [
        {
          "dayOfWeek": "MONDAY",
          "periods": [1, 2, 3],
          "buildingName": "공학관",
          "roomNumber": "302"
        }
      ]
    }
  ],
  "page": 0,
  "size": 20,
  "totalPages": 1,
  "totalElements": 1,
  "hasNext": false
}
```

`offeringId`는 검색 결과의 강좌를 시간표에 추가하거나 상세 조회할 때 사용하는 식별자입니다.
교양 강좌는 `generalCategory` 하나로 `REQUIRED`, `AREA_1`, `AREA_2`, `AREA_3`,
`E_CLASS`, `HSU_CYBER`, `OCU`, `CHUNGNAM_ELEARNING`, `SDU`, `OTHER` 중 하나를
반환합니다. 상세 조회 `GET /api/courses/{offeringId}`는 같은 정보에 엑셀의 비고를
`note`로 추가하여 반환합니다.
`eligibleDepartmentNames`는 엑셀에 저장된 수강대상 학과 목록이며, 제한 정보가 없으면
빈 배열을 반환합니다. 이는 전공 엑셀의 원본 학과·전공을 나타내는
`originalAcademicUnitName`과는 다른 정보이며, 교양의 `originalAcademicUnitName`은
`null`입니다.

전체 Query parameter, Enum 값과 검색 조합 규칙은
[강좌 수입·조회 API 명세서](docs/course-import-api.md)에서 확인할 수 있습니다.

## API 문서

로컬 실행 후 다음 주소에서 확인합니다.

```text
Swagger UI: http://localhost:8080/swagger-ui.html
OpenAPI JSON: http://localhost:8080/v3/api-docs
Health Check: http://localhost:8080/actuator/health
```

운영 프로필에서는 Swagger와 OpenAPI가 기본적으로 비활성화됩니다. 필요한 제한 환경에서만 다음 환경변수로 활성화합니다.

```text
SWAGGER_UI_ENABLED=true
SWAGGER_API_DOCS_ENABLED=true
```

## 테스트

테스트는 실제 MySQL이 아닌 H2의 MySQL 호환 모드에서 실행됩니다.

```powershell
.\gradlew.bat test
.\gradlew.bat build
```

## 인증 및 권한 적용 범위

- 회원가입과 로그인 성공 시 JWT Access Token을 발급합니다.
- `/api/admin/**`는 `ADMIN` 역할만 접근할 수 있습니다.
- 전공·교양 엑셀 수입 API는 `ADMIN` 역할만 접근할 수 있습니다.
- 시간표 구성 API, 동아리 좋아요 및 활동 후기 작성에는 로그인 JWT가 필요합니다.
- 동아리 목록·상세와 활동 후기 통계 조회는 로그인 없이 사용할 수 있습니다.

동아리 좋아요는 로그인 사용자별로 동아리당 한 건만 저장하며 토글 API를 호출할 때마다 등록 또는 취소됩니다.
활동 후기는 로그인 사용자별로 동아리당 한 건만 저장하고, 빈 요청은 본인의 후기만 제거합니다.

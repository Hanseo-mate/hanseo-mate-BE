# 동일교과목·타학과 전공인정 저장 구조 전환

## 변경 범위

프론트 요청과 응답은 변경하지 않는다. 두 관리자 업로드의 URL, multipart `file`,
인증, 상태 코드, `STORED / DUPLICATE / REVIEW_REQUIRED`, 과목 상세의 두 배열을 유지한다.

- 동일교과목은 기존처럼 **학년도 + 학기**별로 적용한다. 연간 정책으로 바꾸지 않는다.
- 타학과 전공인정은 기존처럼 **정책연도**별로 적용한다. 각 규칙의 적용년도·학기 필터도 유지한다.
- 조회 대상 연도에 업로드가 없으면 다른 연도의 정책을 임의로 이어받지 않는다.
- 과목 상세의 매칭 기준과 배열 순서도 바꾸지 않는다.

## 저장 방식

변하지 않는 내용과 해당 내용이 포함된 업로드 이력의 연결을 분리한다.

| 테이블 | 역할 |
| --- | --- |
| `equivalent_course_contents` | 동일교과목의 과목코드·과목명 조합을 한 번 저장 |
| `equivalent_course_memberships` | 업로드 이력·그룹·과목 내용 연결, 원본 위치와 표시 순서 |
| `cross_major_rule_contents` | 학생/개설 조직·과목·적용 시점이 같은 인정 규칙을 한 번 저장 |
| `cross_major_rule_memberships` | 정책 업로드 이력·인정 규칙 연결, 원본 위치 |

기존 이력 테이블 두 개와 동일교과목 그룹 테이블은 계속 사용한다.
기존 `equivalent_course_members`, `cross_major_recognition_rules`는 이관 원본으로 보존하되,
전환 후 신규 업로드와 조회에서는 사용하지 않는다. 삭제하지 말아야 한다.

1. 업로드 전체 파일을 기존 규칙대로 검증한다.
2. 같은 범위의 현재 활성 내용과 같으면 `DUPLICATE`로 종료한다.
3. 각 행의 내용 키를 최대 500개씩 묶어 기존 데이터와 비교한다.
4. 같은 내용은 재사용하고, 새로운 내용만 삽입한다.
5. 새 업로드에 포함된 행 전체에 대해 연결 정보를 생성한다.
6. 기존 활성 이력의 비활성화, 새 이력·내용·연결 저장을 한 트랜잭션으로 처리한다.

동일교과목 내용 키는 UTF-8의 `과목코드 + U+001F + 과목명`에 대한 SHA-256이다.
타학과 전공인정은 기존 파서의 `ruleKey`를 그대로 사용한다.
내용 엔티티는 변경하지 않는다. 과목명이나 규칙이 달라지면 다른 키의 새 내용이 된다.
이전에 사용한 내용으로 돌아가면 기존 내용을 다시 참조한다.

### 업로드 예시

2025년 파일에 규칙 A/B/C가 있고, 2026년 파일이 A/B/D라면 내용은 A/B/C/D만 저장한다.

- 2025년 이력은 A/B/C를 연결한다.
- 2026년 이력은 A/B/D를 연결한다.
- 2026년 파일에서 C가 빠져도 2025년 C는 유지한다.
- 같은 2026년 파일을 재업로드하면 새 이력이나 연결도 추가하지 않는다.
- 다른 연도에 내용이 모두 같아도 해당 연도의 이력과 연결은 필요하므로 `STORED`다.

`memberCount`, `groupCount`, `ruleCount`는 기존처럼 파일/스냅샷 전체의 수량이며
이번에 새로 삽입한 내용 행 수가 아니다.

### 보존 비용

모든 데이터를 한 테이블에 넣거나 연도 목록을 문자열로 관리하지 않는다.
그 방식으로는 과목 그룹, 원본 순서, 삭제된 규칙, 같은 연도 내 재업로드 이력을
안전하게 표현하기 어렵다.

내용 본문은 재사용하지만 이력과 연결 행, 감사용 원본 JSON은 업로드별로 보존한다.
따라서 중복이 완전히 0이 되는 것은 아니다. 이관 직후에는 기존 테이블도 남겨두므로
DB 용량이 일시적으로 늘어난다. 이후 반복 업로드에서 구조화된 내용의 중복 증가를 줄이는 변경이다.

## 기존 운영 DB 배포 순서

1. DB 전체 백업을 확보하고 복구 가능 여부를 확인한다.
2. **두 관리자 업로드 API를 모든 서버 인스턴스에서 일시 중지**한다.
   이관 중 구버전 서버가 새 업로드를 저장하면 신규 연결이 누락될 수 있다.
   차단할 수 없다면 점검 시간 동안 앱을 중지한다.
3. 올바른 운영 DB가 선택됐는지 확인한다.

```sql
SELECT DATABASE(), VERSION();
SHOW TABLES LIKE 'equivalent_course%';
SHOW TABLES LIKE 'cross_major%';
```

4. `docs/course-enrichment-content-reuse-migration-mysql.sql` 전체를 **같은 DB 세션**에서 실행한다.
   MySQL의 `DELIMITER`와 저장 프로시저를 지원하는 SQL 클라이언트를 사용한다.
   테이블 생성·조회·삽입과 임시 이관 프로시저 생성/실행/삭제 권한이 필요하다.
   오류를 무시하고 계속 실행하는 옵션을 사용하지 않는다.
5. 오류 없이 완료됐는지 확인한다. 스크립트는 ACTIVE뿐 아니라 SUPERSEDED 원본 행도 이관하고,
   내용·소속·원본 위치·순서와 이력별 수량을 비교한다. 불일치하면 이관 DML을 롤백하고 오류를 낸다.
6. 아래 누락 검사가 모두 0인지 확인하고 신버전을 배포한다.
7. 구버전 인스턴스가 남아 있지 않은지 확인한 뒤 업로드를 다시 허용한다.
8. 과거/현재 강좌 상세조회, 동일 파일 재업로드, 일부 변경 파일 업로드를 확인한다.

```sql
SELECT COUNT(*) AS missing_equivalent_rows
FROM equivalent_course_members old
LEFT JOIN equivalent_course_memberships new ON new.id = old.id
WHERE new.id IS NULL;

SELECT COUNT(*) AS missing_cross_major_rows
FROM cross_major_recognition_rules old
LEFT JOIN cross_major_rule_memberships new ON new.id = old.id
WHERE new.id IS NULL;

SELECT h.academic_year, h.semester, h.member_count, COUNT(n.id) AS linked_members
FROM equivalent_course_import_histories h
LEFT JOIN equivalent_course_memberships n ON n.import_history_id = h.id
WHERE h.history_status = 'ACTIVE'
GROUP BY h.id, h.academic_year, h.semester, h.member_count
HAVING h.member_count <> COUNT(n.id);

SELECT h.policy_year, h.rule_count, COUNT(n.id) AS linked_rules
FROM cross_major_recognition_import_histories h
LEFT JOIN cross_major_rule_memberships n ON n.import_history_id = h.id
WHERE h.status = 'ACTIVE'
GROUP BY h.id, h.policy_year, h.rule_count
HAVING h.rule_count <> COUNT(n.id);
```

뒤의 두 수량 검사 쿼리는 **결과 행이 없어야** 한다.

### 재실행 및 롤백 주의

- 테이블이 존재하면 생성은 건너뛰고, 이미 옮겨진 내용 키/원본 행 ID는 다시 삽입하지 않는다.
- 불일치 데이터를 덮어쓰거나 원본을 삭제하지 않는다.
- DDL은 암묵적 커밋이 있으므로 전체 파일을 하나의 트랜잭션으로 롤백할 수는 없다.
  DML 실패 시 새 빈 테이블이나 이관 프로시저는 남을 수 있다. 원인을 해결한 후 전체 재실행한다.
- 신버전에서 업로드하기 전에는 기존 행과 활성 이력을 그대로 유지하므로 구버전 조회가 가능하다.
- **신버전에서 새 업로드한 뒤 코드만 구버전으로 되돌리면 안 된다.**
  새 이력에는 신규 연결만 있으므로 구버전이 내용을 찾지 못한다.
  이 경우 업로드 중지 후 기존 구조로 역이관하거나 백업 복구 여부를 판단해야 한다.
- 자동 fallback이나 이중 쓰기를 사용하지 않는다. 이관 누락을 숨기지 않기 위해서다.

### 설정

properties 추가·변경은 필요 없다. 기존 `ddl-auto=validate`를 유지한다.
새 테이블 생성과 데이터 이관을 **코드 배포 전에** 끝내야 한다.
빈 DB 신규 설치에는 갱신된 `docs/database-schema-mysql.sql`을 사용한다.
기존 DB에 전체 스키마 파일을 실행하지 않는다.

## 검증 항목

- 기존 관리자 권한, 업로드 응답과 OpenAPI, 상세조회 배열 호환성
- 연도/학기 범위 분리, 내용 재사용, 이름 변경, 그룹 변경, 제외/재등장
- 검토 필요 파일이 활성 데이터를 변경하지 않는지
- 저장 실패 시 활성 이력·내용·연결이 함께 롤백되는지
- 내용 키 충돌/활성 범위 충돌/잠금 경합 시 최대 3회 새 트랜잭션 재시도
- 배치 조회 후 누락 내용만 INSERT하는지
- MySQL 이관의 과거 이력 보존, Java와 SQL의 내용 키 일치, 재실행, 불일치 롤백

실제 MySQL 검증은 Docker가 실행된 환경의
`CourseEnrichmentMigrationMySqlContainerTest`로 실행한다.
Docker가 없으면 이 테스트는 건너뛰므로 일반 테스트 통과만으로 SQL 실행 검증을 대신할 수 없다.

### 이번 변경의 로컬 검증

2026-09-07에 운영 DB와 분리된 임시 MySQL 8.0.41에서도 실제 이관 SQL을 실행했다.
동일교과목 기존 4행은 연결 4행·내용 3행으로, 동일한 타학과 규칙 2행은 연결 2행·내용 1행으로
이관됐으며, 같은 SQL 재실행 후 수량이 유지됐다. 과거/현재 과목명과 순서, Java와 SQL의
SHA-256 일치도 확인했다. 내용 불일치를 주입한 별도 임시 DB에서는 오류와 DML 롤백을 확인했다.
운영 MySQL 8.4 환경에 적용하거나 배포한 것은 아니므로 운영 적용 전 백업·사전 검사는 별도로 필요하다.

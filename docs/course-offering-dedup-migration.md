# 과목 공통 데이터·학기 매핑 증분 마이그레이션

## 1. 목적

기존 구조는 과목코드와 이름만 `courses`에 두고, 교수·분반·학점·시간·강의실·분류 등
실제 강좌 데이터를 학기별 `course_offerings`에 반복 저장했다. 변경 후에는 다음 책임으로
분리한다.

| 테이블 | 책임 |
|---|---|
| `courses` | 최초 수입한 공통 과목 데이터 한 건 |
| `course_offerings` | `Course`가 개설된 학기, 수입 범위와 원본 위치 |
| `course_schedules`, `offering_allowed_grades`, `offering_eligible_departments`, `offering_general_education` | 공통 `Course`의 상세 데이터 |
| `course_source_cells` | 학기별 Offering을 만든 엑셀 원본 셀 증거 |
| `timetable_courses` | 기존과 같이 학기별 Offering UUID 참조 |

과목 identity는 공백이 아닌 `courseCode` 하나뿐이다. 같은 코드이면 이름이나 교수·시간이
달라도 최초 저장된 공통 데이터를 계속 사용한다. 이름이 같아도 코드가 다르면 별도 과목이고,
코드가 없으면 이름으로 합치지 않고 각 수입 행을 별도 과목으로 저장한다.

## 2. 빈 DB와 기존 DB 구분

- 새 빈 DB에는 최종 구조가 반영된 `docs/database-schema-mysql.sql`만 적용한다.
- 기존 DB에는 빈 DB용 파일을 실행하지 않고
  `docs/course-offering-dedup-migration-mysql.sql`을 한 번 적용한다.
- 운영 프로필은 `ddl-auto=validate`이므로 기존 DB 마이그레이션을 완료한 뒤 새 애플리케이션을
  배포한다.

## 3. 마이그레이션의 데이터 선택 규칙

기존 DB에는 같은 코드의 학기별 데이터가 서로 다를 수 있다. 증분 스크립트는
`course_import_histories.created_at`, `source_sheet`, `source_row`, Offering UUID 순으로 가장
이른 행을 최초 데이터로 선택한다.

- 코드가 있는 Offering은 같은 코드끼리 하나의 기존 `Course`로 모은다.
- 코드가 없는 Offering은 각각 새로운 `Course`를 만든다.
- 공통 일정·허용 학년·대상 학과·교양 분류는 최초 Offering의 데이터만 남긴다.
- 기존 `course_offerings.id`는 바꾸지 않으므로 `timetable_courses` 참조도 유지된다.
- 기존 Offering은 모두 현재 스냅샷이므로 최초 `active` 값은 `true`로 채운다.

과목코드는 앞뒤 공백을 제거하고 영문을 대문자로 정규화한 뒤, 빈 문자열이면 코드 없음으로
판단한다. 현재 DB collation도 `utf8mb4_unicode_ci`이므로 대소문자만 다른 코드는 유니크
제약에서 같은 값이다. 사전 조회에서 공백·대소문자 이외의 코드 변형이 발견되면 학교
과목코드 정책에 맞게 먼저 정리해야 한다.

## 4. 실행 전 필수 조건

1. 운영 애플리케이션과 관리자 강좌 수입을 모두 중지한다.
2. DB 전체 백업과 복원 연습을 완료한다.
3. 운영 DB 복제본에서 스크립트와 새 애플리케이션 기동을 먼저 검증한다.
4. 실행 계정에 대상 테이블의 `SELECT`, `INSERT`, `UPDATE`, `DELETE`, `CREATE`, `ALTER`,
   `DROP`, `INDEX`, `REFERENCES` 권한과 사전 검증용 임시 프로시저를 위한 `CREATE ROUTINE`
   권한이 있는지 확인한다.
5. 다음 결과를 보관한다.

```sql
SELECT COUNT(*) AS courses_before FROM courses;
SELECT COUNT(*) AS offerings_before FROM course_offerings;
SELECT COUNT(*) AS timetable_courses_before FROM timetable_courses;

SELECT s.academic_year, s.semester,
       NULLIF(UPPER(TRIM(o.course_code_snapshot)), '') AS course_code,
       COUNT(*) AS duplicate_count
FROM course_offerings o
JOIN semesters s ON s.id = o.semester_id
WHERE NULLIF(UPPER(TRIM(o.course_code_snapshot)), '') IS NOT NULL
GROUP BY s.academic_year, s.semester,
         NULLIF(UPPER(TRIM(o.course_code_snapshot)), '')
HAVING COUNT(*) > 1;

SHOW CREATE TABLE courses;
SHOW CREATE TABLE course_offerings;
SHOW CREATE TABLE course_schedules;
SHOW CREATE TABLE offering_allowed_grades;
SHOW CREATE TABLE offering_eligible_departments;
SHOW CREATE TABLE offering_general_education;
```

동일 학기·동일 코드가 여러 Offering으로 이미 저장된 경우 자동으로 어느 Offering UUID를
남길지 결정하면 사용자 시간표 계약이 바뀔 수 있으므로 스크립트가 변경 전에 중단된다. 해당
데이터는 수입 원본과 시간표 참조를 확인해 수동 정리한 뒤 다시 실행한다.

증분 스크립트는 `docs/database-schema-mysql.sql`에 기록된 기존 FK와 인덱스 이름을 기준으로
한다. `ddl-auto=update`로 생성해 제약 이름이 다르면 `SHOW CREATE TABLE` 결과에 맞춰 `DROP
FOREIGN KEY`와 `DROP INDEX` 이름을 조정해야 한다.

## 5. 배포 순서

1. 애플리케이션 중지 및 백업
2. 사전 조회와 중복 코드 검토
3. `docs/course-offering-dedup-migration-mysql.sql` 실행
4. 스크립트 마지막 검증 조회가 모두 통과하는지 확인
5. 새 애플리케이션 배포
6. `ddl-auto=validate` 기동 성공 확인
7. 강좌 검색·상세·시간표 조회 smoke test
8. 관리자 엑셀 재수입 후 Offering UUID와 시간표 선택 유지 확인
9. 애플리케이션 쓰기 재개

MySQL DDL은 암시적으로 commit되므로 이 스크립트 전체를 하나의 트랜잭션으로 되돌릴 수 없다.
중간 실패 시 임의로 역방향 DDL을 실행하지 말고 애플리케이션을 중지한 상태에서 백업을
복원한다.

스크립트는 실제 엔티티 컬럼을 바꾸기 전에 `course_dedup_migration_ranked`와
`course_dedup_migration_map` 작업 테이블을 만든 뒤 생성 ID 충돌을 한 번 더 검사한다. 이 두
작업 테이블을 만드는 단계에서만 중단됐다면 도메인 스키마는 아직 바뀌지 않았다. 원인을
확인한 뒤 두 작업 테이블을 삭제하고 처음부터 다시 실행할 수 있다. 첫 `ALTER TABLE courses`
이후에 실패했다면 부분 적용 상태이므로 반드시 백업 복원을 우선한다. 마지막 검증 프로시저가
실패해도 조사할 수 있도록 작업 테이블을 삭제하지 않고 중단한다.

## 6. 적용 후 검증

다음을 모두 만족해야 한다.

- migration 전후 `course_offerings` 행 수가 같다.
- migration 전후 `timetable_courses` 행 수와 Offering UUID가 같다.
- 모든 Offering이 존재하는 `Course`를 참조한다.
- 공백이 아닌 같은 과목코드를 가진 `Course`가 둘 이상 없다.
- 코드 없는 각 Offering은 서로 다른 `Course`를 참조한다.
- 공통 상세 테이블에 orphan이 없다.
- 모든 기존 Offering의 `active`가 `true`이다.
- 같은 학기·같은 Course 매핑이 중복되지 않는다.

새 코드 배포 후에는 다음 동작도 확인한다.

- 같은 코드를 다른 학기에 수입하면 `courses` 행은 늘지 않고 Offering만 추가된다.
- 같은 코드의 이름·교수·시간을 바꿔 재수입해도 최초 공통 데이터가 유지된다.
- 같은 학기·같은 코드를 재수입하면 Offering UUID가 유지된다.
- 재수입에서 누락된 Offering은 `active=false`가 되고 검색에서는 제외된다.
- 비활성 Offering을 참조하던 기존 개인 시간표는 그대로 조회된다.
- 코드 없는 같은 이름 두 행은 서로 다른 Course로 저장된다.

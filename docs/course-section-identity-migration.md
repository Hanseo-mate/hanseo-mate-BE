# 과목코드·분반 강좌 식별 마이그레이션

## 목적

기존 구조는 `courseCode`만 같으면 분반이 달라도 하나의 강좌로 합쳤다. 변경 후에는
`courseCode + sectionNo` 조합을 강좌 identity로 사용한다.

```text
웹프로그래밍 / 001분반 / 월 1,2,3교시
웹프로그래밍 / 002분반 / 화 4,5,6교시
```

두 행은 과목명과 과목코드가 같아도 서로 다른 `Course`와 학기별 `CourseOffering`을 갖는다.
따라서 강좌 검색에는 두 항목이 각각 표시되고, 교수·수업 시간·강의실·수강 대상과
`offeringId`도 분반별로 구분된다.

## 기존 데이터 보존

`docs/course-section-identity-migration-mysql.sql`은 기존 행을 삭제하거나 UUID를 변경하지
않는다.

- 기존 `courses.id`와 `course_offerings.id` 유지
- 기존 개인 시간표의 `timetable_courses` 참조 유지
- 기존 과목코드 유일 제약만 일반 인덱스로 변경
- 기존 Course의 `master_key`를 과목코드·현재 분반 기준으로 갱신

현재 구조에서 이미 제거된 다른 분반은 정규 테이블에 남아 있지 않으므로 SQL만으로
복구하지 않는다. 새 코드 배포 후 해당 학기의 전공·교양 엑셀을 다시 업로드해야 다른
분반이 새 Offering으로 생성된다.

## 적용 순서

1. 애플리케이션과 관리자 엑셀 업로드 중지
2. 운영 DB 전체 백업 및 복원 가능 여부 확인
3. 운영 DB 복제본에서 SQL 실행 및 새 애플리케이션 기동 검증
4. 운영 DB에 `docs/course-section-identity-migration-mysql.sql` 한 번 실행
5. 마지막 검증 결과 확인
   - `courses_before = courses_after`
   - `offerings_before = offerings_after`
   - `timetable_courses_before = timetable_courses_after`
   - `invalid_master_key_count = 0`
   - `uk_course_code` 없음
   - `ix_course_code` 존재하고 `non_unique = 1`
6. 새 애플리케이션 배포 및 `ddl-auto=validate` 기동 확인
7. 전공·교양 시간표 엑셀 재업로드
8. 같은 과목코드의 분반별 검색·상세·시간표 추가·충돌 검사 확인

MySQL DDL은 암시적으로 commit되므로 중간 실패 시 임의로 다시 실행하지 않는다. 실패한
구문과 `SHOW CREATE TABLE courses`, 마지막으로 완료된 검증 결과를 확인한 뒤 백업 복원을
우선한다.

## 재업로드 후 확인 SQL

```sql
SELECT
    semester.academic_year,
    semester.semester,
    course.course_code,
    course.course_name,
    course.section_no,
    offering.id AS offering_id,
    offering.active
FROM course_offerings offering
JOIN semesters semester ON semester.id = offering.semester_id
JOIN courses course ON course.id = offering.course_id
WHERE course.course_code = '확인할 과목코드'
ORDER BY semester.academic_year, semester.semester, course.section_no;
```


-- 캠퍼스맵 교내시설 운영시간 필드 제거
-- 애플리케이션 배포 전에 운영 DB에서 실행합니다.

ALTER TABLE campus_lecture_building_details
    DROP COLUMN operating_hours;

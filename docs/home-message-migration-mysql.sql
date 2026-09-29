-- 기존 DB에 애플리케이션 배포 전에 실행합니다.
CREATE TABLE IF NOT EXISTS home_messages (
    id BIGINT NOT NULL,
    message VARCHAR(500) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 미설정 상태는 행이 없어도 됩니다. 애플리케이션이 id=1 한 행을 저장합니다.
-- 재실행 시 기존 문구는 변경하지 않습니다.

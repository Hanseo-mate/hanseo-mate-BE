-- 기존 운영 DB에 회원탈퇴용 참조 무결성을 보강하는 증분 DDL입니다.
-- 실행 전 반드시 백업하고, 아래 orphan 조회 결과가 모두 0건인지 확인하세요.
-- docs/database-schema-mysql.sql 전체는 빈 DB 전용이므로 운영 DB에 실행하지 않습니다.

SELECT timetable.id, timetable.owner_id
FROM timetables timetable
LEFT JOIN user_accounts user_account ON user_account.id = timetable.owner_id
WHERE user_account.id IS NULL;

SELECT device.id, device.user_id
FROM push_devices device
LEFT JOIN user_accounts user_account ON user_account.id = device.user_id
WHERE device.user_id IS NOT NULL
  AND user_account.id IS NULL;

SELECT ticket.id, ticket.push_device_id
FROM push_tickets ticket
LEFT JOIN push_devices device ON device.id = ticket.push_device_id
WHERE device.id IS NULL;

-- 위 조회가 모두 0건일 때만 다음 ALTER TABLE을 실행합니다.
ALTER TABLE timetables
    ADD CONSTRAINT fk_timetables_owner
        FOREIGN KEY (owner_id)
        REFERENCES user_accounts (id)
        ON DELETE CASCADE;

ALTER TABLE push_devices
    ADD INDEX idx_push_devices_user (user_id),
    ADD CONSTRAINT fk_push_devices_user
        FOREIGN KEY (user_id)
        REFERENCES user_accounts (id)
        ON DELETE CASCADE;

ALTER TABLE push_tickets
    ADD INDEX idx_push_tickets_push_device (push_device_id),
    ADD CONSTRAINT fk_push_tickets_push_device
        FOREIGN KEY (push_device_id)
        REFERENCES push_devices (id)
        ON DELETE CASCADE;

-- 기존 운영 DB에 서산·태안 캠퍼스 및 주변 장소 좌표를 추가하는 증분 SQL입니다.
-- 운영 애플리케이션 배포 전에 실행하며, 기존 수업 건물 테이블과는 별도로 관리합니다.
-- 창업3관 위도는 36.592307, 태안GS25 뒤쪽 위도는 36.590894로 보정했습니다.
-- MySQL DDL은 암시적으로 커밋되므로 먼저 [0-A]만 실행하고 결과를 확인합니다.
--
-- campus_place_table_count=0: [1]부터 [3]까지 실행합니다.
-- campus_place_table_count=1: [0-B]에서 구조와 행 수를 확인합니다.
--   row_count=0이고 구조가 일치할 때만 [2]와 [3]을 실행합니다.
--   데이터가 한 건이라도 있으면 중단하고 이 파일을 재실행하지 않습니다.

SET NAMES utf8mb4;

-- ============================================================================
-- [0-A] 항상 먼저 단독 실행
-- ============================================================================
SELECT DATABASE() AS selected_database,
       COUNT(*) AS campus_place_table_count,
       GROUP_CONCAT(table_name ORDER BY table_name) AS existing_tables,
       CASE COUNT(*)
           WHEN 0 THEN 'RUN_FROM_SECTION_1'
           WHEN 1 THEN 'RUN_SECTION_0_B_AND_INSPECT'
           ELSE 'STOP_AND_INSPECT'
       END AS next_action
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name = 'campus_places';

-- ============================================================================
-- [1] campus_place_table_count=0일 때만 실행
-- ============================================================================
CREATE TABLE campus_places (
    id BIGINT NOT NULL AUTO_INCREMENT,
    campus_code VARCHAR(20) COLLATE utf8mb4_bin NOT NULL,
    place_name VARCHAR(255) NOT NULL,
    place_name_key VARCHAR(255) NOT NULL,
    category VARCHAR(40) COLLATE utf8mb4_bin NULL,
    one_line_description VARCHAR(255) NULL,
    address VARCHAR(255) NULL,
    image_url VARCHAR(2048) NULL,
    latitude DECIMAL(12, 9) NOT NULL,
    longitude DECIMAL(12, 9) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_campus_place_campus_name_key
        UNIQUE (campus_code, place_name_key),
    INDEX ix_campus_place_campus (campus_code),
    CONSTRAINT ck_campus_place_campus_code
        CHECK (campus_code IN ('SEOSAN', 'TAEAN')),
    CONSTRAINT ck_campus_place_category
        CHECK (
            category IS NULL OR category IN (
                'RESTAURANT',
                'CAFE',
                'LECTURE_BUILDING',
                'CONVENIENCE_FACILITY'
            )
        ),
    CONSTRAINT ck_campus_place_latitude
        CHECK (latitude BETWEEN -90.000000000 AND 90.000000000),
    CONSTRAINT ck_campus_place_longitude
        CHECK (longitude BETWEEN -180.000000000 AND 180.000000000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- [0-B] 테이블 생성 직후 또는 기존 테이블이 있을 때 구조·행 수 확인
-- ============================================================================
SHOW CREATE TABLE campus_places;

SELECT COUNT(*) AS campus_place_row_count
FROM campus_places;

-- ============================================================================
-- [2] campus_places가 비어 있을 때만 실행
-- --force를 사용하지 않습니다. INSERT 실패 시 COMMIT하지 말고 ROLLBACK합니다.
-- ============================================================================
START TRANSACTION;

SET @campus_place_seeded_at = CURRENT_TIMESTAMP(6);

INSERT INTO campus_places (
    id, campus_code, place_name, place_name_key,
    latitude, longitude, created_at, updated_at
) VALUES
    (1, 'SEOSAN', '신선대 식당', '신선대식당',
     36.692232000, 126.574652000, @campus_place_seeded_at, @campus_place_seeded_at),
    (2, 'SEOSAN', '가배앤빈', '가배앤빈',
     36.691166000, 126.574659000, @campus_place_seeded_at, @campus_place_seeded_at),
    (3, 'SEOSAN', '가시버시', '가시버시',
     36.690613000, 126.575891000, @campus_place_seeded_at, @campus_place_seeded_at),
    (4, 'SEOSAN', '한서마당', '한서마당',
     36.690527000, 126.576010000, @campus_place_seeded_at, @campus_place_seeded_at),
    (5, 'SEOSAN', '파비 기사식당', '파비기사식당',
     36.689521000, 126.576797000, @campus_place_seeded_at, @campus_place_seeded_at),
    (6, 'SEOSAN', '시골밥상', '시골밥상',
     36.689385000, 126.576913000, @campus_place_seeded_at, @campus_place_seeded_at),
    (7, 'SEOSAN', '가야산우리지리', '가야산우리지리',
     36.689303000, 126.576962000, @campus_place_seeded_at, @campus_place_seeded_at),
    (8, 'SEOSAN', '홍은이닭지그리', '홍은이닭지그리',
     36.689338000, 126.576970000, @campus_place_seeded_at, @campus_place_seeded_at),
    (9, 'SEOSAN', '다담', '다담',
     36.689245000, 126.577037000, @campus_place_seeded_at, @campus_place_seeded_at),
    (10, 'SEOSAN', '자라용봉탕', '자라용봉탕',
     36.689027000, 126.577268000, @campus_place_seeded_at, @campus_place_seeded_at),
    (11, 'SEOSAN', '블루플레이스', '블루플레이스',
     36.690180000, 126.576728000, @campus_place_seeded_at, @campus_place_seeded_at),
    (12, 'SEOSAN', '바니스피자', '바니스피자',
     36.689970000, 126.577191000, @campus_place_seeded_at, @campus_place_seeded_at),
    (13, 'SEOSAN', '호랑', '호랑',
     36.689997000, 126.577296000, @campus_place_seeded_at, @campus_place_seeded_at),
    (14, 'SEOSAN', '38고기', '38고기',
     36.690259000, 126.577179000, @campus_place_seeded_at, @campus_place_seeded_at),
    (15, 'SEOSAN', 'xoxo', 'XOXO',
     36.690303000, 126.577189000, @campus_place_seeded_at, @campus_place_seeded_at),
    (16, 'SEOSAN', '38고기 옆', '38고기옆',
     36.690235000, 126.577113000, @campus_place_seeded_at, @campus_place_seeded_at),
    (17, 'SEOSAN', '대곡리포장마차', '대곡리포장마차',
     36.690379000, 126.577525000, @campus_place_seeded_at, @campus_place_seeded_at),
    (18, 'SEOSAN', '먹통', '먹통',
     36.690406000, 126.577661000, @campus_place_seeded_at, @campus_place_seeded_at),
    (19, 'SEOSAN', '대정문 세븐일레븐', '대정문세븐일레븐',
     36.692093000, 126.574737000, @campus_place_seeded_at, @campus_place_seeded_at),
    (20, 'SEOSAN', '대정문 GS', '대정문GS',
     36.690226000, 126.576512000, @campus_place_seeded_at, @campus_place_seeded_at),
    (21, 'SEOSAN', '대정문 버스정류장 서울방향', '대정문버스정류장서울방향',
     36.690270000, 126.575981000, @campus_place_seeded_at, @campus_place_seeded_at),
    (22, 'SEOSAN', '대정문 CU', '대정문CU',
     36.689474000, 126.576247000, @campus_place_seeded_at, @campus_place_seeded_at),
    (23, 'SEOSAN', '대정문 버스정류장 홍성방향', '대정문버스정류장홍성방향',
     36.689448000, 126.576381000, @campus_place_seeded_at, @campus_place_seeded_at),
    (24, 'SEOSAN', 'BBQ', 'BBQ',
     36.690105000, 126.578093000, @campus_place_seeded_at, @campus_place_seeded_at),
    (25, 'SEOSAN', '정문CU', '정문CU',
     36.690534000, 126.577811000, @campus_place_seeded_at, @campus_place_seeded_at),
    (26, 'SEOSAN', '헤커PC', '헤커PC',
     36.690476000, 126.577860000, @campus_place_seeded_at, @campus_place_seeded_at),
    (27, 'SEOSAN', '워시엔조이', '워시엔조이',
     36.690595000, 126.577723000, @campus_place_seeded_at, @campus_place_seeded_at),
    (28, 'SEOSAN', '최초장집', '최초장집',
     36.690488000, 126.578154000, @campus_place_seeded_at, @campus_place_seeded_at),
    (29, 'SEOSAN', '이모네', '이모네',
     36.690517000, 126.578243000, @campus_place_seeded_at, @campus_place_seeded_at),
    (30, 'SEOSAN', '투다리', '투다리',
     36.690541000, 126.578415000, @campus_place_seeded_at, @campus_place_seeded_at),
    (31, 'SEOSAN', '먹거리대장', '먹거리대장',
     36.690556000, 126.578538000, @campus_place_seeded_at, @campus_place_seeded_at),
    (32, 'SEOSAN', '음주다방', '음주다방',
     36.690598000, 126.578461000, @campus_place_seeded_at, @campus_place_seeded_at),
    (33, 'SEOSAN', '빠사시', '빠사시',
     36.690572000, 126.578697000, @campus_place_seeded_at, @campus_place_seeded_at),
    (34, 'SEOSAN', '파티', '파티',
     36.690591000, 126.578831000, @campus_place_seeded_at, @campus_place_seeded_at),
    (35, 'SEOSAN', '제이제이당구', '제이제이당구',
     36.690668000, 126.579218000, @campus_place_seeded_at, @campus_place_seeded_at),
    (36, 'SEOSAN', '콩닭콩닭', '콩닭콩닭',
     36.690343000, 126.579256000, @campus_place_seeded_at, @campus_place_seeded_at),
    (37, 'SEOSAN', '맘스터치', '맘스터치',
     36.690357000, 126.579381000, @campus_place_seeded_at, @campus_place_seeded_at),
    (38, 'SEOSAN', '그리스', '그리스',
     36.690271000, 126.579340000, @campus_place_seeded_at, @campus_place_seeded_at),
    (39, 'SEOSAN', '대학복사', '대학복사',
     36.690476000, 126.579618000, @campus_place_seeded_at, @campus_place_seeded_at),
    (40, 'SEOSAN', '메가', '메가',
     36.690543000, 126.579795000, @campus_place_seeded_at, @campus_place_seeded_at),
    (41, 'SEOSAN', '엄청난파닭', '엄청난파닭',
     36.690598000, 126.580053000, @campus_place_seeded_at, @campus_place_seeded_at),
    (42, 'SEOSAN', '이마트24 정문', '이마트24정문',
     36.690632000, 126.580182000, @campus_place_seeded_at, @campus_place_seeded_at),
    (43, 'SEOSAN', '무인탁구', '무인탁구',
     36.690594000, 126.580193000, @campus_place_seeded_at, @campus_place_seeded_at),
    (44, 'SEOSAN', '공차', '공차',
     36.690607000, 126.580324000, @campus_place_seeded_at, @campus_place_seeded_at),
    (45, 'SEOSAN', '감동까스/59쌀피자', '감동까스/59쌀피자',
     36.690919000, 126.580052000, @campus_place_seeded_at, @campus_place_seeded_at),
    (46, 'SEOSAN', '엽떡', '엽떡',
     36.690848000, 126.580258000, @campus_place_seeded_at, @campus_place_seeded_at),
    (47, 'SEOSAN', '세이커피', '세이커피',
     36.690871000, 126.580333000, @campus_place_seeded_at, @campus_place_seeded_at),
    (48, 'SEOSAN', '정문GS', '정문GS',
     36.690931000, 126.580491000, @campus_place_seeded_at, @campus_place_seeded_at),
    (49, 'SEOSAN', '스피드카피', '스피드카피',
     36.690980000, 126.580660000, @campus_place_seeded_at, @campus_place_seeded_at),
    (50, 'SEOSAN', '주당', '주당',
     36.690100000, 126.580493000, @campus_place_seeded_at, @campus_place_seeded_at),
    (51, 'SEOSAN', '86PC', '86PC',
     36.691048000, 126.580612000, @campus_place_seeded_at, @campus_place_seeded_at),
    (52, 'SEOSAN', '정문 코인노래방', '정문코인노래방',
     36.690992000, 126.580634000, @campus_place_seeded_at, @campus_place_seeded_at),
    (53, 'SEOSAN', '토프레소', '토프레소',
     36.691065000, 126.580743000, @campus_place_seeded_at, @campus_place_seeded_at),
    (54, 'SEOSAN', '정문버스정류장', '정문버스정류장',
     36.690638000, 126.580530000, @campus_place_seeded_at, @campus_place_seeded_at),
    (55, 'SEOSAN', '이마트24 재민이형집쪽', '이마트24재민이형집쪽',
     36.691145000, 126.579770000, @campus_place_seeded_at, @campus_place_seeded_at),
    (56, 'SEOSAN', '도스마스', '도스마스',
     36.691151000, 126.579935000, @campus_place_seeded_at, @campus_place_seeded_at),
    (57, 'SEOSAN', '더테이블', '더테이블',
     36.691291000, 126.579982000, @campus_place_seeded_at, @campus_place_seeded_at),
    (58, 'SEOSAN', '아이스크림할인점', '아이스크림할인점',
     36.691293000, 126.580028000, @campus_place_seeded_at, @campus_place_seeded_at),
    (59, 'SEOSAN', '마라탕집', '마라탕집',
     36.691143000, 126.580095000, @campus_place_seeded_at, @campus_place_seeded_at),
    (60, 'SEOSAN', '나사', '나사',
     36.691370000, 126.580412000, @campus_place_seeded_at, @campus_place_seeded_at),
    (61, 'SEOSAN', '더큰', '더큰',
     36.691243000, 126.580607000, @campus_place_seeded_at, @campus_place_seeded_at),
    (62, 'SEOSAN', '이삭토스트', '이삭토스트',
     36.691234000, 126.580741000, @campus_place_seeded_at, @campus_place_seeded_at),
    (63, 'SEOSAN', '하이마트', '하이마트',
     36.691215000, 126.580907000, @campus_place_seeded_at, @campus_place_seeded_at),
    (64, 'SEOSAN', '에잇어클락', '에잇어클락',
     36.691211000, 126.581088000, @campus_place_seeded_at, @campus_place_seeded_at),
    (65, 'SEOSAN', '이것이국밥이다', '이것이국밥이다',
     36.691231000, 126.581179000, @campus_place_seeded_at, @campus_place_seeded_at),
    (66, 'SEOSAN', '학사반점', '학사반점',
     36.691269000, 126.581438000, @campus_place_seeded_at, @campus_place_seeded_at),
    (67, 'SEOSAN', '후문CU', '후문CU',
     36.692174000, 126.583623000, @campus_place_seeded_at, @campus_place_seeded_at),
    (68, 'SEOSAN', '영춘원', '영춘원',
     36.692208000, 126.583662000, @campus_place_seeded_at, @campus_place_seeded_at),
    (69, 'SEOSAN', '잇또라멘', '잇또라멘',
     36.692799000, 126.585949000, @campus_place_seeded_at, @campus_place_seeded_at),
    (70, 'SEOSAN', '킹코인', '킹코인',
     36.692819000, 126.586027000, @campus_place_seeded_at, @campus_place_seeded_at),
    (71, 'SEOSAN', '후문GS', '후문GS',
     36.692676000, 126.586133000, @campus_place_seeded_at, @campus_place_seeded_at),
    (72, 'SEOSAN', '스타PC', '스타PC',
     36.692662000, 126.585986000, @campus_place_seeded_at, @campus_place_seeded_at),
    (73, 'SEOSAN', '봉주르대곡리', '봉주르대곡리',
     36.692632000, 126.586513000, @campus_place_seeded_at, @campus_place_seeded_at),
    (74, 'SEOSAN', '한뚝배기', '한뚝배기',
     36.692788000, 126.587084000, @campus_place_seeded_at, @campus_place_seeded_at),
    (75, 'SEOSAN', '해미막국수', '해미막국수',
     36.693140000, 126.587067000, @campus_place_seeded_at, @campus_place_seeded_at),
    (76, 'SEOSAN', '이학관/카페드림', '이학관/카페드림',
     36.690669000, 126.581760000, @campus_place_seeded_at, @campus_place_seeded_at),
    (77, 'SEOSAN', '보건관', '보건관',
     36.690237000, 126.581944000, @campus_place_seeded_at, @campus_place_seeded_at),
    (78, 'SEOSAN', '여긱', '여긱',
     36.689868000, 126.582259000, @campus_place_seeded_at, @campus_place_seeded_at),
    (79, 'SEOSAN', '영암관', '영암관',
     36.691341000, 126.582453000, @campus_place_seeded_at, @campus_place_seeded_at),
    (80, 'SEOSAN', '학생회관', '학생회관',
     36.691486000, 126.583172000, @campus_place_seeded_at, @campus_place_seeded_at),
    (81, 'SEOSAN', '건축관', '건축관',
     36.691361000, 126.583607000, @campus_place_seeded_at, @campus_place_seeded_at),
    (82, 'SEOSAN', '도서관', '도서관',
     36.690021000, 126.584036000, @campus_place_seeded_at, @campus_place_seeded_at),
    (83, 'SEOSAN', '인곡관', '인곡관',
     36.691789000, 126.584722000, @campus_place_seeded_at, @campus_place_seeded_at),
    (84, 'SEOSAN', '학군단', '학군단',
     36.690958000, 126.585458000, @campus_place_seeded_at, @campus_place_seeded_at),
    (85, 'SEOSAN', '인문사회관', '인문사회관',
     36.690100000, 126.585907000, @campus_place_seeded_at, @campus_place_seeded_at),
    (86, 'SEOSAN', '공학관', '공학관',
     36.690884000, 126.585761000, @campus_place_seeded_at, @campus_place_seeded_at),
    (87, 'SEOSAN', '상상공작소', '상상공작소',
     36.691625000, 126.586317000, @campus_place_seeded_at, @campus_place_seeded_at),
    (88, 'SEOSAN', '예술관', '예술관',
     36.689406000, 126.587976000, @campus_place_seeded_at, @campus_place_seeded_at),
    (89, 'SEOSAN', '대운동장', '대운동장',
     36.690743000, 126.588396000, @campus_place_seeded_at, @campus_place_seeded_at),
    (90, 'SEOSAN', '영암체육관', '영암체육관',
     36.691580000, 126.588189000, @campus_place_seeded_at, @campus_place_seeded_at),
    (91, 'SEOSAN', '자악관', '자악관',
     36.691490000, 126.588935000, @campus_place_seeded_at, @campus_place_seeded_at),
    (92, 'SEOSAN', '농구장', '농구장',
     36.692188000, 126.586706000, @campus_place_seeded_at, @campus_place_seeded_at),
    (93, 'SEOSAN', '테니스장', '테니스장',
     36.691951000, 126.586864000, @campus_place_seeded_at, @campus_place_seeded_at),
    (94, 'SEOSAN', '이학관 전기차충전소', '이학관전기차충전소',
     36.690548000, 126.581160000, @campus_place_seeded_at, @campus_place_seeded_at),
    (95, 'SEOSAN', '연암도서관 전기차충전소', '연암도서관전기차충전소',
     36.690061000, 126.583477000, @campus_place_seeded_at, @campus_place_seeded_at),
    (96, 'SEOSAN', '빵곡관', '빵곡관',
     36.691897000, 126.584103000, @campus_place_seeded_at, @campus_place_seeded_at),
    (97, 'SEOSAN', '서점', '서점',
     36.691887000, 126.584237000, @campus_place_seeded_at, @campus_place_seeded_at),
    (98, 'TAEAN', '항공기술교육원', '항공기술교육원',
     36.596492000, 126.292215000, @campus_place_seeded_at, @campus_place_seeded_at),
    (99, 'TAEAN', '창업2관', '창업2관',
     36.595669000, 126.293328000, @campus_place_seeded_at, @campus_place_seeded_at),
    (100, 'TAEAN', '태안본관', '태안본관',
     36.594581000, 126.294056000, @campus_place_seeded_at, @campus_place_seeded_at),
    (101, 'TAEAN', '실습2동', '실습2동',
     36.593520000, 126.294879000, @campus_place_seeded_at, @campus_place_seeded_at),
    (102, 'TAEAN', '창업3관', '창업3관',
     36.592307000, 126.295738000, @campus_place_seeded_at, @campus_place_seeded_at),
    (103, 'TAEAN', '태안기숙사구관', '태안기숙사구관',
     36.594001000, 126.292797000, @campus_place_seeded_at, @campus_place_seeded_at),
    (104, 'TAEAN', '태안기숙사신관', '태안기숙사신관',
     36.593186000, 126.293383000, @campus_place_seeded_at, @campus_place_seeded_at),
    (105, 'TAEAN', '기숙사CU', '기숙사CU',
     36.593503000, 126.293142000, @campus_place_seeded_at, @campus_place_seeded_at),
    (106, 'TAEAN', '태안세븐일레븐', '태안세븐일레븐',
     36.594412000, 126.292178000, @campus_place_seeded_at, @campus_place_seeded_at),
    (107, 'TAEAN', '태안GS25', '태안GS25',
     36.595482000, 126.291524000, @campus_place_seeded_at, @campus_place_seeded_at),
    (108, 'TAEAN', '태안코노', '태안코노',
     36.596202000, 126.290875000, @campus_place_seeded_at, @campus_place_seeded_at),
    (109, 'TAEAN', '피굽남', '피굽남',
     36.596110000, 126.290957000, @campus_place_seeded_at, @campus_place_seeded_at),
    (110, 'TAEAN', '막리단길', '막리단길',
     36.596069000, 126.290991000, @campus_place_seeded_at, @campus_place_seeded_at),
    (111, 'TAEAN', '경아두마리치킨', '경아두마리치킨',
     36.595994000, 126.291036000, @campus_place_seeded_at, @campus_place_seeded_at),
    (112, 'TAEAN', '태안GS25 뒤쪽', '태안GS25뒤쪽',
     36.590894000, 126.295726000, @campus_place_seeded_at, @campus_place_seeded_at),
    (113, 'TAEAN', '태안캠해양교육원', '태안캠해양교육원',
     36.593851000, 126.300619000, @campus_place_seeded_at, @campus_place_seeded_at),
    (114, 'TAEAN', '태안마슬랜', '태안마슬랜',
     36.590423000, 126.295288000, @campus_place_seeded_at, @campus_place_seeded_at);

COMMIT;

-- ============================================================================
-- [3] 실행 후 검증
-- ============================================================================
SELECT campus_code, COUNT(*) AS place_count
FROM campus_places
GROUP BY campus_code
ORDER BY campus_code;

SELECT COUNT(*) AS duplicate_place_count
FROM (
    SELECT campus_code, place_name_key
    FROM campus_places
    GROUP BY campus_code, place_name_key
    HAVING COUNT(*) > 1
) duplicates;

SELECT COUNT(*) AS invalid_coordinate_count
FROM campus_places
WHERE latitude NOT BETWEEN -90.000000000 AND 90.000000000
   OR longitude NOT BETWEEN -180.000000000 AND 180.000000000;

SELECT campus_code, place_name, latitude, longitude
FROM campus_places
WHERE (campus_code = 'TAEAN' AND place_name IN ('창업3관', '태안GS25 뒤쪽'))
   OR (campus_code = 'SEOSAN' AND place_name IN (
       '공학관', '인문사회관', '자악관', '영암체육관'
   ))
ORDER BY campus_code, place_name;

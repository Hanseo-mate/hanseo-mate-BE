-- STRONG_SENIORITY만 제거하는 기존 MySQL DB용 증분 데이터 정리입니다.
-- 후기 쓰기를 중단하고 이 SQL 전체를 같은 연결에서 실행한 뒤 새 서버를 배포합니다.
-- 다른 선택 항목과 후기는 보존합니다. 이 항목만 선택한 후기는 함께 삭제합니다.
-- 오류 발생 시 COMMIT하지 말고 ROLLBACK하세요.

CREATE TEMPORARY TABLE removed_seniority_only_reviews (
    id BIGINT NOT NULL PRIMARY KEY
);

START TRANSACTION;

INSERT INTO removed_seniority_only_reviews (id)
SELECT review.id
FROM club_reviews review
WHERE EXISTS (
    SELECT 1 FROM club_review_selections selection
    WHERE selection.club_review_id = review.id
      AND selection.review_option = 'STRONG_SENIORITY'
)
AND NOT EXISTS (
    SELECT 1 FROM club_review_selections selection
    WHERE selection.club_review_id = review.id
      AND selection.review_option <> 'STRONG_SENIORITY'
);

DELETE FROM club_review_selections
WHERE review_option = 'STRONG_SENIORITY';

DELETE FROM club_reviews
WHERE id IN (SELECT id FROM removed_seniority_only_reviews);

COMMIT;

DROP TEMPORARY TABLE removed_seniority_only_reviews;

-- 결과는 0이어야 합니다.
SELECT COUNT(*) AS remaining_strong_seniority_selections
FROM club_review_selections
WHERE review_option = 'STRONG_SENIORITY';

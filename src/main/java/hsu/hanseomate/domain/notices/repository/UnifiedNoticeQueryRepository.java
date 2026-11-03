package hsu.hanseomate.domain.notices.repository;

import hsu.hanseomate.domain.notices.dto.UnifiedNoticeListItemResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UnifiedNoticeQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    // 두 테이블을 합친 전체 결과를 정렬한 후에만 페이지를 자른다.
    // 학생회는 기존 응답의 postDate와 동일하게 created_at의 날짜 부분으로 정렬한다.
    private static final String SEARCH_SQL = """
            SELECT id, notice_type, origin_notice_id, title, source_url, author,
                   post_date, is_hot, view_count
            FROM (
                SELECT n.id, UPPER(TRIM(n.notice_type)) AS notice_type,
                       n.origin_notice_id, n.title, n.source_url, n.author,
                       n.post_date, n.is_hot, n.view_count,
                       CASE
                           WHEN n.is_hot = FALSE THEN 5
                           WHEN LOWER(TRIM(n.notice_type)) = 'academic' THEN 1
                           WHEN LOWER(TRIM(n.notice_type)) = 'general' THEN 2
                           WHEN LOWER(TRIM(n.notice_type)) = 'scholarship' THEN 3
                           WHEN LOWER(TRIM(n.notice_type)) = 'graduate' THEN 4
                           ELSE 99
                       END AS group_priority
                FROM notices n
                WHERE REPLACE(n.title, ' ', '') LIKE ? ESCAPE '!'
                UNION ALL
                SELECT s.id, 'STUDENT_COUNCIL' AS notice_type,
                       NULL AS origin_notice_id, s.title, NULL AS source_url, s.author,
                       CAST(s.created_at AS DATE) AS post_date, FALSE AS is_hot, s.view_count,
                       0 AS group_priority
                FROM student_council_notices s
                WHERE REPLACE(s.title, ' ', '') LIKE ? ESCAPE '!'
            ) unified_notices
            ORDER BY group_priority ASC, post_date DESC, id DESC
            LIMIT ? OFFSET ?
            """;

    public List<UnifiedNoticeListItemResponse> searchByTitleIgnoringSpaces(
            String keyword, Pageable pageable
    ) {
        // 기존 Spring Data의 부분 일치 검색처럼 사용자 입력의 와일드카드는 문자로 취급한다.
        String pattern = "%" + keyword.replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_") + "%";

        return jdbcTemplate.query(SEARCH_SQL, (rs, rowNum) -> new UnifiedNoticeListItemResponse(
                rs.getLong("id"),
                rs.getString("notice_type"),
                rs.getString("origin_notice_id"),
                rs.getString("title"),
                rs.getString("source_url"),
                rs.getString("author"),
                rs.getDate("post_date").toLocalDate(),
                rs.getBoolean("is_hot"),
                rs.getLong("view_count")
        ), pattern, pattern, pageable.getPageSize(), pageable.getOffset());
    }
}

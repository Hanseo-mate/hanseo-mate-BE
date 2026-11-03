package hsu.hanseomate.domain.notices.repository;

import hsu.hanseomate.domain.notices.dto.UnifiedNoticeListItemResponse;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UnifiedNoticeQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    // 크롤러와 스프링이 만든 테이블은 collation이 다를 수 있어 문자열을 UNION하지 않는다.
    // 학생회가 항상 첫 그룹이므로 개수로 페이지 경계를 계산하고 각 테이블에서 정렬·제한한다.
    private static final String COUNCIL_COUNT_SQL = """
            SELECT COUNT(*) FROM student_council_notices
            WHERE REPLACE(title, ' ', '') LIKE ? ESCAPE '!'
            """;

    private static final String COUNCIL_SQL = """
            SELECT id, 'STUDENT_COUNCIL' AS notice_type, NULL AS origin_notice_id,
                   title, NULL AS source_url, author, CAST(created_at AS DATE) AS post_date,
                   FALSE AS is_hot, view_count
            FROM student_council_notices
            WHERE REPLACE(title, ' ', '') LIKE ? ESCAPE '!'
            ORDER BY CAST(created_at AS DATE) DESC, id DESC
            LIMIT ? OFFSET ?
            """;

    private static final String NOTICE_SQL = """
            SELECT id, UPPER(TRIM(notice_type)) AS notice_type, origin_notice_id,
                   title, source_url, author, post_date, is_hot, view_count
            FROM notices
            WHERE REPLACE(title, ' ', '') LIKE ? ESCAPE '!'
            ORDER BY CASE
                         WHEN is_hot = FALSE THEN 5
                         WHEN LOWER(TRIM(notice_type)) = 'academic' THEN 1
                         WHEN LOWER(TRIM(notice_type)) = 'general' THEN 2
                         WHEN LOWER(TRIM(notice_type)) = 'scholarship' THEN 3
                         WHEN LOWER(TRIM(notice_type)) = 'graduate' THEN 4
                         ELSE 99
                     END ASC,
                     post_date DESC, id DESC
            LIMIT ? OFFSET ?
            """;

    private static final RowMapper<UnifiedNoticeListItemResponse> ROW_MAPPER = (rs, rowNum) ->
            new UnifiedNoticeListItemResponse(
                    rs.getLong("id"), rs.getString("notice_type"), rs.getString("origin_notice_id"),
                    rs.getString("title"), rs.getString("source_url"), rs.getString("author"),
                    rs.getDate("post_date").toLocalDate(), rs.getBoolean("is_hot"), rs.getLong("view_count")
            );

    public List<UnifiedNoticeListItemResponse> searchByTitleIgnoringSpaces(String keyword, Pageable pageable) {
        String pattern = "%" + keyword.replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_") + "%";
        long councilCount = jdbcTemplate.queryForObject(COUNCIL_COUNT_SQL, Long.class, pattern);
        long offset = pageable.getOffset();
        int remaining = pageable.getPageSize();
        List<UnifiedNoticeListItemResponse> result = new ArrayList<>();

        if (offset < councilCount) {
            int councilLimit = (int) Math.min(remaining, councilCount - offset);
            result.addAll(jdbcTemplate.query(COUNCIL_SQL, ROW_MAPPER, pattern, councilLimit, offset));
            remaining -= result.size();
        }
        if (remaining > 0) {
            long noticeOffset = Math.max(0L, offset - councilCount);
            result.addAll(jdbcTemplate.query(NOTICE_SQL, ROW_MAPPER, pattern, remaining, noticeOffset));
        }
        return result;
    }
}

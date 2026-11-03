package hsu.hanseomate.domain.notices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UnifiedNoticeApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM notice_files");
        jdbcTemplate.update("DELETE FROM notices");
        jdbcTemplate.update("DELETE FROM student_council_notice_images");
        jdbcTemplate.update("DELETE FROM student_council_notice_attachments");
        jdbcTemplate.update("DELETE FROM student_council_notices");
    }

    @Test
    void includesLatestNoticeEvenWhenInsertedAfterMoreThanOnePageOfOldNotices() throws Exception {
        for (int i = 0; i < 45; i++) {
            insertNotice("academic", "old-" + i, LocalDate.of(2025, 1, 1), false);
        }
        insertNotice("general", "latest", LocalDate.of(2026, 11, 3), false);

        mockMvc.perform(get("/api/notices/categories/general"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].originNoticeId").value("latest"));
        mockMvc.perform(get("/api/notices/unified").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(20))
                .andExpect(jsonPath("$[0].originNoticeId").value("latest"))
                .andExpect(jsonPath("$[0].noticeType").value("GENERAL"))
                .andExpect(jsonPath("$[0].sourceUrl").value("https://example.com/latest"))
                .andExpect(jsonPath("$[0].author").value("관리자"))
                .andExpect(jsonPath("$[0].postDate").value("2026-11-03"))
                .andExpect(jsonPath("$[0].isHot").value(false))
                .andExpect(jsonPath("$[0].viewCount").value(7));
    }

    @Test
    void scrollsThroughEveryNoticeOnceInGlobalPriorityOrder() throws Exception {
        LocalDate oldDate = LocalDate.of(2025, 1, 1);
        LocalDate newDate = LocalDate.of(2026, 11, 3);
        for (int i = 0; i < 45; i++) {
            insertNotice("academic", "old-" + i, oldDate, false);
        }
        insertNotice("academic", "normal-academic", newDate.minusDays(1), false);
        insertNotice("general", "normal-general", newDate, false);
        insertNotice("graduate", "normal-graduate", newDate, false);
        insertNotice("graduate", "hot-graduate", newDate, true);
        insertNotice("scholarship", "hot-scholarship", newDate, true);
        insertNotice("general", "hot-general", newDate, true);
        insertNotice("academic", "hot-academic-older", oldDate, true);
        insertNotice("academic", "hot-academic-newer", newDate, true);
        // 같은 날짜에는 생성 시각 대신 기존 API의 ID 내림차순을 유지한다.
        insertCouncil("council-first", oldDate.atTime(23, 0));
        insertCouncil("council-second", oldDate.atTime(9, 0));

        List<String> expected = new ArrayList<>(List.of(
                "council-second", "council-first", "hot-academic-newer", "hot-academic-older",
                "hot-general", "hot-scholarship", "hot-graduate", "normal-graduate",
                "normal-general", "normal-academic"
        ));
        for (int i = 44; i >= 0; i--) {
            expected.add("old-" + i);
        }

        List<String> actual = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            List<String> titles = titles(page, 20, "");
            assertThat(titles).hasSize(page == 2 ? 15 : 20);
            actual.addAll(titles);
        }
        assertThat(actual).containsExactlyElementsOf(expected).doesNotHaveDuplicates();
        assertThat(titles(3, 20, "")).isEmpty();
    }

    @Test
    void includesNewestCouncilNoticeBeyondOldFetchLimit() throws Exception {
        for (int i = 0; i < 505; i++) {
            insertCouncil("council-" + i, LocalDateTime.of(2025, 1, 1, 0, 0));
        }
        insertCouncil("latest-council", LocalDateTime.of(2026, 11, 3, 0, 0));

        mockMvc.perform(get("/api/notices/unified").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("latest-council"))
                .andExpect(jsonPath("$[0].noticeType").value("STUDENT_COUNCIL"))
                .andExpect(jsonPath("$[0].originNoticeId").isEmpty())
                .andExpect(jsonPath("$[0].sourceUrl").isEmpty())
                .andExpect(jsonPath("$[0].isHot").value(false));
    }

    @Test
    void filtersBothSourcesBeforeSortingAndPagingAndIgnoresSpaces() throws Exception {
        LocalDate date = LocalDate.of(2026, 11, 3);
        for (int i = 0; i < 25; i++) {
            insertNotice("academic", "unrelated-" + i, date, true);
        }
        insertNotice("general", "장학신청", date, false);
        insertNotice("scholarship", "장학 신청 HOT", date.minusDays(1), true);
        insertCouncil("장학 신청 학생회", date.minusYears(1).atStartOfDay());
        insertCouncil("검색과 무관한 학생회", date.atStartOfDay());

        assertThat(titles(0, 2, "장 학 신 청")).containsExactly("장학 신청 학생회", "장학 신청 HOT");
        assertThat(titles(1, 2, "장 학 신 청")).containsExactly("장학신청");
        assertThat(titles(2, 2, "장 학 신 청")).isEmpty();
    }

    @Test
    void searchesPercentAndUnderscoreAsLiteralCharacters() throws Exception {
        LocalDate date = LocalDate.of(2026, 11, 3);
        insertNotice("general", "100%_지원!", date, false);
        insertNotice("general", "100퍼센트지원!", date, false);
        insertCouncil("100%_지원! 학생회", date.atStartOfDay());

        assertThat(titles(0, 20, "100%_지원!")).containsExactly("100%_지원! 학생회", "100%_지원!");
    }

    @Test
    void returnsEmptyListForEmptyDataAndVeryLargePage() throws Exception {
        assertThat(titles(0, 20, "")).isEmpty();
        insertNotice("general", "only", LocalDate.of(2026, 11, 3), false);
        assertThat(titles(Integer.MAX_VALUE, 20, "")).isEmpty();
    }

    @Test
    void rejectsInvalidPagination() throws Exception {
        mockMvc.perform(get("/api/notices/unified").param("page", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/notices/unified").param("size", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/notices/unified").param("size", "-1"))
                .andExpect(status().isBadRequest());
    }

    private List<String> titles(int page, int size, String keyword) throws Exception {
        String json = mockMvc.perform(get("/api/notices/unified")
                        .param("page", Integer.toString(page))
                        .param("size", Integer.toString(size))
                        .param("keyword", keyword))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return JsonPath.read(json, "$[*].title");
    }

    private void insertNotice(String type, String title, LocalDate date, boolean hot) {
        jdbcTemplate.update("""
                INSERT INTO notices (notice_type, origin_notice_id, title, source_url,
                    content_html, author, post_date, is_hot, view_count)
                VALUES (?, ?, ?, ?, '<p>본문</p>', '관리자', ?, ?, 7)
                """, type, title, title, "https://example.com/" + title, Date.valueOf(date), hot);
    }

    private void insertCouncil(String title, LocalDateTime createdAt) {
        jdbcTemplate.update("""
                INSERT INTO student_council_notices (title, author, content, view_count, created_at, updated_at)
                VALUES (?, '학생회', '본문', 0, ?, ?)
                """, title, Timestamp.valueOf(createdAt), Timestamp.valueOf(createdAt));
    }
}

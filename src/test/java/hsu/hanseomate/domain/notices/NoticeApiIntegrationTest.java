package hsu.hanseomate.domain.notices;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Date;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NoticeApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM notice_files");
        jdbcTemplate.update("DELETE FROM notices");
    }

    @Test
    void returnsCategoryNoticesWithHotFirstAndPaginationSizeTen() throws Exception {
        insertNotice("academic", "n-hot", "HOT 공지", LocalDate.of(2026, 7, 20), true);
        insertNotice("academic", "n-normal", "일반 공지", LocalDate.of(2026, 7, 21), false);

        for (int i = 0; i < 9; i++) {
            insertNotice("academic", "n-extra-" + i, "추가 공지 " + i, LocalDate.of(2026, 7, 10), false);
        }

        mockMvc.perform(get("/api/notices/categories/{noticeType}", "academic")
                        .param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(10))
                .andExpect(jsonPath("$.items[0].originNoticeId").value("n-hot"))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(11))
                .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    void returnsAllNoticesWithoutGraduateWithRequestedOrdering() throws Exception {
        insertNotice("scholarship", "s-hot", "장학 HOT", LocalDate.of(2026, 7, 22), true);
        insertNotice("general", "g-hot", "일반 HOT", LocalDate.of(2026, 7, 22), true);
        insertNotice("academic", "a-hot", "학사 HOT", LocalDate.of(2026, 7, 22), true);
        insertNotice("graduate", "gr-hot", "대학원 HOT", LocalDate.of(2026, 7, 22), true);

        mockMvc.perform(get("/api/notices").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].noticeType").value("academic"))
                .andExpect(jsonPath("$.items[1].noticeType").value("general"))
                .andExpect(jsonPath("$.items[2].noticeType").value("scholarship"));
    }

    @Test
    void returnsNoticeDetailWithAttachments() throws Exception {
        Long noticeId = insertNotice("general", "detail-1", "상세 공지", LocalDate.of(2026, 7, 21), false);
        insertAttachment(noticeId, "첨부1.pdf", "https://example.com/attach1");
        insertAttachment(noticeId, "첨부2.pdf", "https://example.com/attach2");

        mockMvc.perform(get("/api/notices/{noticeId}", noticeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originNoticeId").value("detail-1"))
                .andExpect(jsonPath("$.attachments.length()").value(2))
                .andExpect(jsonPath("$.attachments[0].fileName").value("첨부1.pdf"));
    }

    @Test
    void rejectsUnknownNoticeType() throws Exception {
        mockMvc.perform(get("/api/notices/categories/{noticeType}", "unknown"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    private Long insertNotice(
            String noticeType,
            String originNoticeId,
            String title,
            LocalDate postDate,
            boolean isHot
    ) {
        jdbcTemplate.update("""
                INSERT INTO notices (
                    notice_type,
                    origin_notice_id,
                    title,
                    source_url,
                    content_html,
                    author,
                    post_date,
                    is_hot
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                noticeType,
                originNoticeId,
                title,
                "https://www.hanseo.ac.kr/detail/" + originNoticeId,
                "<p>content</p>",
                "관리자",
                Date.valueOf(postDate),
                isHot
        );

        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM notices", Long.class);
    }

    private void insertAttachment(Long noticeId, String fileName, String fileUrl) {
        jdbcTemplate.update("""
                INSERT INTO notice_files (
                    notice_id,
                    file_name,
                    file_url
                ) VALUES (?, ?, ?)
                """,
                noticeId,
                fileName,
                fileUrl
        );
    }
}

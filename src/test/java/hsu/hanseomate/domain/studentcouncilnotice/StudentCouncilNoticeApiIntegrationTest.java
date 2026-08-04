package hsu.hanseomate.domain.studentcouncilnotice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hsu.hanseomate.domain.studentcouncilnotice.entity.StudentCouncilNotice;
import hsu.hanseomate.domain.studentcouncilnotice.repository.StudentCouncilNoticeRepository;
import hsu.hanseomate.support.AdminMockMvcConfiguration;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AdminMockMvcConfiguration.class)
class StudentCouncilNoticeApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentCouncilNoticeRepository studentCouncilNoticeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanUp() {
        studentCouncilNoticeRepository.deleteAll();
    }

    @Test
    void jpaCreatesOnlyStudentCouncilNoticeColumns() {
        var columns = jdbcTemplate.queryForList("""
                SELECT column_name
                FROM information_schema.columns
                WHERE LOWER(table_name) = 'student_council_notices'
                ORDER BY ordinal_position
                """, String.class);

        assertThat(columns).containsExactlyInAnyOrder(
                "id", "title", "content", "created_at", "updated_at"
        );
    }

    @Test
    void createsAndReadsStudentCouncilNoticeWithEmojiAndLineBreaks() throws Exception {
        mockMvc.perform(post("/api/admin/notices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "  학생회 행사 안내  ",
                                  "content": "행사 내용을 안내드립니다. 📢\\n많은 참여 부탁드립니다."
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        org.hamcrest.Matchers.matchesPattern(
                                "/api/notices/categories/admin/\\d+"
                        )
                ))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("학생회 행사 안내"))
                .andExpect(jsonPath("$.content")
                        .value("행사 내용을 안내드립니다. 📢\n많은 참여 부탁드립니다."))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());

        StudentCouncilNotice saved = studentCouncilNoticeRepository.findAll().get(0);

        mockMvc.perform(get(
                        "/api/notices/categories/admin/{noticeId}",
                        saved.getId()
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.content")
                        .value("행사 내용을 안내드립니다. 📢\n많은 참여 부탁드립니다."));
    }

    @Test
    void returnsNoticesTenAtATimeInNewestOrder() throws Exception {
        for (int i = 1; i <= 11; i++) {
            studentCouncilNoticeRepository.save(
                    StudentCouncilNotice.create("공지 " + i, "내용 " + i)
            );
        }
        studentCouncilNoticeRepository.flush();

        mockMvc.perform(get("/api/notices/categories/admin").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(10))
                .andExpect(jsonPath("$.items[0].title").value("공지 11"))
                .andExpect(jsonPath("$.items[0].content").value("내용 11"))
                .andExpect(jsonPath("$.items[9].title").value("공지 2"))
                .andExpect(jsonPath("$.items[9].content").value("내용 2"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(11))
                .andExpect(jsonPath("$.hasNext").value(true));

        mockMvc.perform(get("/api/notices/categories/admin").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("공지 1"))
                .andExpect(jsonPath("$.items[0].content").value("내용 1"))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void updatesStudentCouncilNoticeWithoutChangingCreatedAt() throws Exception {
        StudentCouncilNotice notice = studentCouncilNoticeRepository.saveAndFlush(
                StudentCouncilNotice.create("수정 전", "수정 전 내용")
        );
        LocalDateTime createdAt = jdbcTemplate.queryForObject(
                "SELECT created_at FROM student_council_notices WHERE id = ?",
                LocalDateTime.class,
                notice.getId()
        );

        mockMvc.perform(put("/api/admin/notices/{noticeId}", notice.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "수정 후",
                                  "content": "수정된 내용입니다. ✅"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(notice.getId()))
                .andExpect(jsonPath("$.title").value("수정 후"))
                .andExpect(jsonPath("$.content").value("수정된 내용입니다. ✅"));

        StudentCouncilNotice updated =
                studentCouncilNoticeRepository.findById(notice.getId()).orElseThrow();
        LocalDateTime persistedCreatedAt = jdbcTemplate.queryForObject(
                "SELECT created_at FROM student_council_notices WHERE id = ?",
                LocalDateTime.class,
                notice.getId()
        );
        LocalDateTime persistedUpdatedAt = jdbcTemplate.queryForObject(
                "SELECT updated_at FROM student_council_notices WHERE id = ?",
                LocalDateTime.class,
                notice.getId()
        );
        assertThat(updated.getTitle()).isEqualTo("수정 후");
        assertThat(persistedCreatedAt).isEqualTo(createdAt);
        assertThat(persistedUpdatedAt).isAfterOrEqualTo(createdAt);
    }

    @Test
    void deletesStudentCouncilNotice() throws Exception {
        StudentCouncilNotice notice = studentCouncilNoticeRepository.saveAndFlush(
                StudentCouncilNotice.create("삭제 대상", "삭제할 내용")
        );

        mockMvc.perform(delete("/api/admin/notices/{noticeId}", notice.getId()))
                .andExpect(status().isNoContent());

        assertThat(studentCouncilNoticeRepository.existsById(notice.getId())).isFalse();
    }

    @Test
    void returnsNotFoundForMissingStudentCouncilNotice() throws Exception {
        mockMvc.perform(get("/api/notices/categories/admin/{noticeId}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path")
                        .value("/api/notices/categories/admin/999999"));

        mockMvc.perform(put("/api/admin/notices/{noticeId}", 999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/admin/notices/{noticeId}", 999999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsBlankTitleAndContent() throws Exception {
        mockMvc.perform(post("/api/admin/notices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": " ",
                                  "content": " "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void storesMoreThanOneHundredThousandCharactersAndEmoji() throws Exception {
        String longContent = "학생회 장문 공지 🚀\n".repeat(10_000);
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "title", "장문 학생회 공지",
                "content", longContent
        ));

        mockMvc.perform(post("/api/admin/notices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value(longContent));

        String savedContent = jdbcTemplate.queryForObject(
                "SELECT content FROM student_council_notices",
                String.class
        );
        assertThat(savedContent)
                .hasSizeGreaterThan(100_000)
                .contains("🚀")
                .isEqualTo(longContent);
    }

    @Test
    void existingCrawledNoticeCategoryEndpointStillWorksIndependently() throws Exception {
        mockMvc.perform(post("/api/admin/notices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/notices/categories/academic").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber());
    }

    private String validRequest() {
        return """
                {
                  "title": "학생회 공지",
                  "content": "학생회 공지 내용입니다."
                }
                """;
    }
}

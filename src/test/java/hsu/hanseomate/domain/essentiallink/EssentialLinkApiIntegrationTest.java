package hsu.hanseomate.domain.essentiallink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hsu.hanseomate.domain.essentiallink.entity.EssentialLink;
import hsu.hanseomate.domain.essentiallink.repository.EssentialLinkRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EssentialLinkApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EssentialLinkRepository essentialLinkRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        essentialLinkRepository.deleteAll();
    }

    @Test
    void jpaCreatesExactlyTheRequiredColumns() {
        var columns = jdbcTemplate.queryForList("""
                SELECT column_name
                FROM information_schema.columns
                WHERE LOWER(table_name) = 'essential_links'
                ORDER BY ordinal_position
                """, String.class);

        assertThat(columns).containsExactlyInAnyOrder(
                "id", "name", "url", "category", "created_at", "updated_at"
        );
    }

    @Test
    void createsLinkAndNormalizesInput() throws Exception {
        mockMvc.perform(post("/api/admin/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "  e클래스  ",
                                  "url": "  https://eclass.hanseo.ac.kr  ",
                                  "category": "  remote_class  "
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("e클래스"))
                .andExpect(jsonPath("$.url").value("https://eclass.hanseo.ac.kr"))
                .andExpect(jsonPath("$.category").value("REMOTE_CLASS"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void returnsEmptyListWhenNoLinksExist() throws Exception {
        mockMvc.perform(get("/api/links"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void returnsAllLinksInIdOrder() throws Exception {
        EssentialLink first = saveLink("한서포탈", "https://portal.hanseo.ac.kr", "ACADEMIC");
        EssentialLink second = saveLink("도서관", "https://library.hanseo.ac.kr", "CAMPUS");

        mockMvc.perform(get("/api/links"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(first.getId()))
                .andExpect(jsonPath("$[1].id").value(second.getId()));
    }

    @Test
    void filtersLinksByNormalizedCategory() throws Exception {
        saveLink("OCU", "https://cons.ocu.ac.kr", "REMOTE_CLASS");
        saveLink("도서관", "https://library.hanseo.ac.kr", "CAMPUS");

        mockMvc.perform(get("/api/links").param("category", " remote_class "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("OCU"))
                .andExpect(jsonPath("$[0].category").value("REMOTE_CLASS"));
    }

    @Test
    void returnsLinkDetails() throws Exception {
        EssentialLink link = saveLink("전자출결", "https://attendance.hanseo.ac.kr", "ACADEMIC");

        mockMvc.perform(get("/api/links/{linkId}", link.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(link.getId()))
                .andExpect(jsonPath("$.name").value("전자출결"));
    }

    @Test
    void updatesEntireLink() throws Exception {
        EssentialLink link = saveLink("포탈", "https://old.hanseo.ac.kr", "academic");
        EssentialLink persisted = essentialLinkRepository.findById(link.getId()).orElseThrow();
        LocalDateTime createdAt = persisted.getCreatedAt();
        LocalDateTime updatedAt = persisted.getUpdatedAt();

        mockMvc.perform(put("/api/admin/links/{linkId}", link.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "한서포탈",
                                  "url": "https://portal.hanseo.ac.kr",
                                  "category": " school_service "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(link.getId()))
                .andExpect(jsonPath("$.name").value("한서포탈"))
                .andExpect(jsonPath("$.category").value("SCHOOL_SERVICE"));

        EssentialLink updated = essentialLinkRepository.findById(link.getId()).orElseThrow();
        assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(updatedAt);
    }

    @Test
    void deletesLink() throws Exception {
        EssentialLink link = saveLink("삭제 대상", "https://delete.example.com", "ETC");

        mockMvc.perform(delete("/api/admin/links/{linkId}", link.getId()))
                .andExpect(status().isNoContent());

        assertThat(essentialLinkRepository.existsById(link.getId())).isFalse();
    }

    @Test
    void returnsNotFoundForMissingLinkDetails() throws Exception {
        mockMvc.perform(get("/api/links/{linkId}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/api/links/999999"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void returnsNotFoundWhenUpdatingMissingLink() throws Exception {
        mockMvc.perform(put("/api/admin/links/{linkId}", 999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void returnsNotFoundWhenDeletingMissingLink() throws Exception {
        mockMvc.perform(delete("/api/admin/links/{linkId}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void rejectsBlankName() throws Exception {
        mockMvc.perform(post("/api/admin/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "   ",
                                  "url": "https://portal.hanseo.ac.kr",
                                  "category": "ACADEMIC"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/admin/links"));
    }

    @Test
    void rejectsUnsafeUrlScheme() throws Exception {
        mockMvc.perform(post("/api/admin/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "위험한 링크",
                                  "url": "javascript:alert(1)",
                                  "category": "ETC"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void rejectsMissingCategory() throws Exception {
        mockMvc.perform(post("/api/admin/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "한서포탈",
                                  "url": "https://portal.hanseo.ac.kr"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void rejectsCategoryThatExceedsLimitAfterNormalization() throws Exception {
        String category = "ß".repeat(50);
        String request = """
                {
                  "name": "한서포탈",
                  "url": "https://portal.hanseo.ac.kr",
                  "category": "%s"
                }
                """.formatted(category);

        mockMvc.perform(post("/api/admin/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void rejectsBlankCategoryFilter() throws Exception {
        mockMvc.perform(get("/api/links").param("category", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void rejectsNonPositiveLinkId() throws Exception {
        mockMvc.perform(get("/api/links/{linkId}", 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void rejectsNonNumericLinkId() throws Exception {
        mockMvc.perform(get("/api/links/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/admin/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    private EssentialLink saveLink(String name, String url, String category) {
        return essentialLinkRepository.saveAndFlush(EssentialLink.create(name, url, category));
    }

    private String validRequestJson() {
        return """
                {
                  "name": "한서포탈",
                  "url": "https://portal.hanseo.ac.kr",
                  "category": "ACADEMIC"
                }
                """;
    }
}

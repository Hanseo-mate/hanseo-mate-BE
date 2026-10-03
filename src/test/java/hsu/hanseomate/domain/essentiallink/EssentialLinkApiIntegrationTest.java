package hsu.hanseomate.domain.essentiallink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import hsu.hanseomate.domain.essentiallink.entity.EssentialLink;
import hsu.hanseomate.domain.essentiallink.repository.EssentialLinkRepository;
import hsu.hanseomate.support.AdminMockMvcConfiguration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AdminMockMvcConfiguration.class)
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
                "id", "name", "url", "created_at", "updated_at"
        );
    }

    @Test
    void createsLinkAndNormalizesInput() throws Exception {
        mockMvc.perform(post("/api/admin/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "  e클래스  ",
                                  "url": "  https://eclass.hanseo.ac.kr  "
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("e클래스"))
                .andExpect(jsonPath("$.url").value("https://eclass.hanseo.ac.kr"))
                .andExpect(jsonPath("$.category").doesNotExist())
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
        EssentialLink first = saveLink("한서포탈", "https://portal.hanseo.ac.kr");
        EssentialLink second = saveLink("도서관", "https://library.hanseo.ac.kr");

        mockMvc.perform(get("/api/links"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(first.getId()))
                .andExpect(jsonPath("$[1].id").value(second.getId()))
                .andExpect(jsonPath("$[0].category").doesNotExist());
    }

    @Test
    void adminReturnsAllLinksInIdOrder() throws Exception {
        saveLink("OCU", "https://cons.ocu.ac.kr");
        saveLink("도서관", "https://library.hanseo.ac.kr");

        mockMvc.perform(get("/api/admin/links"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("OCU"))
                .andExpect(jsonPath("$[1].name").value("도서관"))
                .andExpect(jsonPath("$[0].category").doesNotExist());
    }

    @Test
    void protectsAdminLinkListWithAdminRole() throws Exception {
        mockMvc.perform(get("/api/admin/links").with(anonymous()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/links")
                        .with(jwt().authorities(
                                new SimpleGrantedAuthority("ROLE_USER")
                        )))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsLinkDetails() throws Exception {
        EssentialLink link = saveLink("전자출결", "https://attendance.hanseo.ac.kr");

        mockMvc.perform(get("/api/links/{linkId}", link.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(link.getId()))
                .andExpect(jsonPath("$.name").value("전자출결"))
                .andExpect(jsonPath("$.category").doesNotExist());
    }

    @Test
    void updatesEntireLink() throws Exception {
        EssentialLink link = saveLink("포탈", "https://old.hanseo.ac.kr");
        EssentialLink persisted = essentialLinkRepository.findById(link.getId()).orElseThrow();
        LocalDateTime createdAt = persisted.getCreatedAt();
        LocalDateTime updatedAt = persisted.getUpdatedAt();

        mockMvc.perform(put("/api/admin/links/{linkId}", link.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "한서포탈",
                                  "url": "https://portal.hanseo.ac.kr"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(link.getId()))
                .andExpect(jsonPath("$.name").value("한서포탈"))
                .andExpect(jsonPath("$.url").value("https://portal.hanseo.ac.kr"))
                .andExpect(jsonPath("$.category").doesNotExist());

        EssentialLink updated = essentialLinkRepository.findById(link.getId()).orElseThrow();
        assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(updatedAt);
    }

    @Test
    void deletesLink() throws Exception {
        EssentialLink link = saveLink("삭제 대상", "https://delete.example.com");

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
                                  "url": "https://portal.hanseo.ac.kr"
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
                                  "url": "javascript:alert(1)"
                                }
                                """))
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

    private EssentialLink saveLink(String name, String url) {
        return essentialLinkRepository.saveAndFlush(EssentialLink.create(name, url));
    }

    private String validRequestJson() {
        return """
                {
                  "name": "한서포탈",
                  "url": "https://portal.hanseo.ac.kr"
                }
                """;
    }
}

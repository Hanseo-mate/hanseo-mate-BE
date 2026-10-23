package hsu.hanseomate.domain.club;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hsu.hanseomate.domain.club.entity.Club;
import hsu.hanseomate.domain.club.repository.ClubRepository;
import hsu.hanseomate.domain.club.type.ClubCategory;
import hsu.hanseomate.domain.user.entity.UserAccount;
import hsu.hanseomate.domain.user.repository.UserAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClubRecruitmentPeriodApiIntegrationTest {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    @Autowired private MockMvc mockMvc;
    @Autowired private ClubRepository clubRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private Clock clock;

    private long clubId;
    private long editorId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        for (String table : List.of("club_review_selections", "club_reviews", "club_likes", "clubs",
                "notifications", "notification_outbox", "refresh_tokens", "user_accounts")) {
            jdbcTemplate.execute("DELETE FROM " + table);
        }
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
        clubId = clubRepository.saveAndFlush(Club.create("모집 기간 동아리", ClubCategory.ACADEMIC)).getId();
        editorId = userAccountRepository.saveAndFlush(UserAccount.create("period-editor", "hash")).getId();
        jdbcTemplate.update("UPDATE user_accounts SET role = 'CLUB_ADMIN' WHERE id = ?", editorId);
        at("2026-10-24T14:59:59Z");
    }

    @Test
    void savedPeriodChangesStatusAtSeoulMidnightWithoutAnotherWrite() throws Exception {
        saveRecruitment(body("모집합니다", "2026-10-25", "2026-10-31"));
        assertStatus("UPCOMING");
        at("2026-10-24T15:00:00Z"); // 10월 25일 00:00 KST
        assertStatus("RECRUITING");
        at("2026-10-31T14:59:59Z"); // 종료일 23:59:59 KST
        assertStatus("RECRUITING");
        at("2026-10-31T15:00:00Z");
        assertStatus("CLOSED");

        Club saved = clubRepository.findById(clubId).orElseThrow();
        assertThat(saved.getRecruitmentStartDate()).isEqualTo(LocalDate.of(2026, 10, 25));
        assertThat(saved.getRecruitmentEndDate()).isEqualTo(LocalDate.of(2026, 10, 31));
    }

    @Test
    void singleDayPeriodIsAllowedAndClearingNoticeClearsDates() throws Exception {
        at("2026-10-24T15:00:00Z");
        saveRecruitment(body("하루 모집", "2026-10-25", "2026-10-25"));
        assertStatus("RECRUITING");
        at("2026-10-25T15:00:00Z");
        assertStatus("CLOSED");
        saveRecruitment(body("  ", null, null));
        mockMvc.perform(get("/api/clubs/{id}", clubId))
                .andExpect(jsonPath("$.recruitmentContent").value((Object) null))
                .andExpect(jsonPath("$.recruitmentStartDate").value((Object) null))
                .andExpect(jsonPath("$.recruitmentEndDate").value((Object) null))
                .andExpect(jsonPath("$.recruitmentStatus").value("CLOSED"));
    }

    @Test
    void newAndLegacyUndatedClubsAreClosedWithoutLosingTheirContent() throws Exception {
        assertStatus("CLOSED");
        jdbcTemplate.update("UPDATE clubs SET recruitment_content = ? WHERE id = ?", "기존 공고", clubId);
        assertStatus("CLOSED");
        mockMvc.perform(get("/api/clubs/{id}", clubId))
                .andExpect(jsonPath("$.recruitmentContent").value("기존 공고"));
    }

    @Test
    void rejectsInvalidPeriodsWithoutChangingExistingNotice() throws Exception {
        saveRecruitment(body("기존 공고", "2026-10-25", "2026-10-31"));
        for (Map<String, Object> invalid : List.of(
                body("수정", null, null),
                body("수정", "2026-10-25", null),
                body("수정", null, "2026-10-31"),
                body("수정", "2026-11-01", "2026-10-31"),
                body("수정", "2026-02-30", "2026-10-31"),
                body("", "2026-10-25", "2026-10-31")
        )) {
            mockMvc.perform(put("/api/clubs/{id}/recruitment", clubId)
                            .with(editor()).contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/api/clubs/{id}", clubId))
                .andExpect(jsonPath("$.recruitmentContent").value("기존 공고"))
                .andExpect(jsonPath("$.recruitmentStartDate").value("2026-10-25"))
                .andExpect(jsonPath("$.recruitmentEndDate").value("2026-10-31"));
    }

    @Test
    void adminFullUpdateUsesSamePeriodValidation() throws Exception {
        Map<String, Object> request = body("관리자 공고", "2026-10-25", "2026-10-31");
        request.put("name", "모집 기간 동아리");
        request.put("shortDescription", "소개");
        mockMvc.perform(put("/api/admin/clubs/{id}", clubId).with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());
        assertStatus("UPCOMING");
        request.put("recruitmentEndDate", null);
        request.put("name", "실패하면 반영되지 않을 이름");
        mockMvc.perform(put("/api/admin/clubs/{id}", clubId).with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        assertThat(clubRepository.findById(clubId).orElseThrow().getName()).isEqualTo("모집 기간 동아리");
    }

    @Test
    void recruitingToggleCombinesWithCategoryAndCountsOnlyMatchingClubsInDisplayOrder() throws Exception {
        at("2026-10-24T15:00:00Z");
        long laterAcademic = createClub("학술 모집 둘째", ClubCategory.ACADEMIC, 50,
                "2026-10-25", "2026-10-31");
        long sports = createClub("체육 모집", ClubCategory.SPORTS, 20,
                "2026-10-20", "2026-10-25");
        long earlierAcademic = createClub("학술 모집 첫째", ClubCategory.ACADEMIC, 10,
                "2026-10-25", "2026-10-25");
        long upcoming = createClub("학술 모집 예정", ClubCategory.ACADEMIC, 30,
                "2026-10-26", "2026-10-31");
        long closed = createClub("체육 모집 완료", ClubCategory.SPORTS, 40,
                "2026-10-01", "2026-10-24");

        for (String path : List.of("/api/clubs", "/api/admin/clubs")) {
            assertList(path, null, null, clubId, earlierAcademic, sports, upcoming, closed, laterAcademic);
            assertList(path, null, "false", clubId, earlierAcademic, sports, upcoming, closed, laterAcademic);
            assertList(path, null, "true", earlierAcademic, sports, laterAcademic);
            assertList(path, "ACADEMIC", "false", clubId, earlierAcademic, upcoming, laterAcademic);
            assertList(path, " academic ", "true", earlierAcademic, laterAcademic);
            assertList(path, "SPORTS", "true", sports);
            assertList(path, "HOBBY", "true");
            assertList(path, "HOBBY", "false");
            assertList(path, " ", "true", earlierAcademic, sports, laterAcademic);
        }
    }

    @Test
    void filteredListPreservesLikeCountsAndCurrentUsersLikeState() throws Exception {
        at("2026-10-24T15:00:00Z");
        saveRecruitment(body("모집중 공고", "2026-10-25", "2026-10-31"));
        mockMvc.perform(post("/api/clubs/likes/{id}", clubId).with(editor()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/clubs").param("category", "ACADEMIC")
                        .param("recruitingOnly", "true").with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.clubs[0].likeCount").value(1))
                .andExpect(jsonPath("$.clubs[0].likedByMe").value(false));
        mockMvc.perform(get("/api/clubs").param("category", "ACADEMIC")
                        .param("recruitingOnly", "true").with(editor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.clubs[0].likeCount").value(1))
                .andExpect(jsonPath("$.clubs[0].likedByMe").value(true));
    }

    @Test
    void rejectsMalformedToggleEvenWhenCategoryHasNoClubs() throws Exception {
        for (String path : List.of("/api/clubs", "/api/admin/clubs")) {
            for (String invalid : List.of("invalid", "RECRUITING", "null")) {
                mockMvc.perform(get(path).param("category", "HOBBY")
                                .param("recruitingOnly", invalid).with(admin()))
                        .andExpect(status().isBadRequest());
            }
        }
    }

    private long createClub(String name, ClubCategory category, int order, String start, String end) {
        Club club = Club.create(name, category);
        club.updateDisplayOrder(order);
        club.updateRecruitment("모집공고", LocalDate.parse(start), LocalDate.parse(end));
        return clubRepository.saveAndFlush(club).getId();
    }

    private void assertList(String path, String category, String recruitingOnly, long... expectedIds)
            throws Exception {
        MockHttpServletRequestBuilder request = get(path)
                .with(path.startsWith("/api/admin/") ? admin() : anonymous());
        if (category != null) {
            request.param("category", category);
        }
        if (recruitingOnly != null) {
            request.param("recruitingOnly", recruitingOnly);
        }
        ResultActions result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(expectedIds.length))
                .andExpect(jsonPath("$.clubs.length()").value(expectedIds.length));
        for (int index = 0; index < expectedIds.length; index++) {
            result.andExpect(jsonPath("$.clubs[" + index + "].id").value(expectedIds[index]));
            if ("true".equals(recruitingOnly)) {
                result.andExpect(jsonPath("$.clubs[" + index + "].recruitmentStatus").value("RECRUITING"));
            }
        }
    }

    private void at(String instant) {
        when(clock.withZone(SEOUL)).thenReturn(Clock.fixed(Instant.parse(instant), SEOUL));
    }

    private void assertStatus(String expected) throws Exception {
        for (String path : List.of("/api/clubs", "/api/admin/clubs")) {
            mockMvc.perform(get(path).param("category", "ACADEMIC").with(admin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCount").value(1))
                    .andExpect(jsonPath("$.clubs[0].recruitmentStatus").value(expected));
            if ("RECRUITING".equals(expected)) {
                assertList(path, "ACADEMIC", "true", clubId);
            } else {
                assertList(path, "ACADEMIC", "true");
            }
            mockMvc.perform(get(path + "/{id}", clubId).with(admin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.recruitmentStatus").value(expected));
        }
    }

    private void saveRecruitment(Map<String, Object> request) throws Exception {
        mockMvc.perform(put("/api/clubs/{id}/recruitment", clubId).with(editor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());
    }

    private Map<String, Object> body(String content, String start, String end) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("recruitmentContent", content);
        body.put("recruitmentStartDate", start);
        body.put("recruitmentEndDate", end);
        return body;
    }

    private RequestPostProcessor editor() {
        return jwt().jwt(token -> token.subject(Long.toString(editorId)).claim("role", "CLUB_ADMIN"))
                .authorities(new SimpleGrantedAuthority("ROLE_CLUB_ADMIN"));
    }

    private RequestPostProcessor admin() {
        return jwt().jwt(token -> token.subject(Long.toString(editorId)).claim("role", "ADMIN"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}

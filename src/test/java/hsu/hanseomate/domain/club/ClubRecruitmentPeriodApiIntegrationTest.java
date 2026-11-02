package hsu.hanseomate.domain.club;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.in;
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
    void clubAdminCanSelectAnyClubFromMyPageAndManageItsRecruitment() throws Exception {
        long sportsClubId = clubRepository.saveAndFlush(Club.create("운동 동아리", ClubCategory.SPORTS)).getId();
        jdbcTemplate.update("UPDATE clubs SET profile_image_url = ?, short_description = ? WHERE id = ?",
                "https://example.com/sports.png", "함께 운동하는 동아리", sportsClubId);

        mockMvc.perform(get("/api/auth/me").with(editor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CLUB_ADMIN"))
                .andExpect(jsonPath("$.likedClubs").isEmpty());
        mockMvc.perform(get("/api/clubs").with(editor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.clubs[0].id").value(clubId))
                .andExpect(jsonPath("$.clubs[1].id").value(sportsClubId))
                .andExpect(jsonPath("$.clubs[1].profileImageUrl").value("https://example.com/sports.png"))
                .andExpect(jsonPath("$.clubs[1].shortDescription").value("함께 운동하는 동아리"));

        Map<String, Object> request = body("운동할 부원을 모집합니다", "2026-10-26", "2026-11-01");
        request.put("recruitmentUrl", "https://example.com/apply");
        mockMvc.perform(put("/api/clubs/{id}/recruitment", sportsClubId).with(editor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        // The same saved calendar dates and weekdays are returned to every viewing screen.
        for (String path : List.of("/api/clubs/{id}", "/api/admin/clubs/{id}")) {
            mockMvc.perform(get(path, sportsClubId).with(path.startsWith("/api/admin/") ? admin() : editor()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.profileImageUrl").value("https://example.com/sports.png"))
                    .andExpect(jsonPath("$.name").value("운동 동아리"))
                    .andExpect(jsonPath("$.shortDescription").value("함께 운동하는 동아리"))
                    .andExpect(jsonPath("$.recruitmentStatus").value("UPCOMING"))
                    .andExpect(jsonPath("$.recruitmentContent").value("운동할 부원을 모집합니다"))
                    .andExpect(jsonPath("$.recruitmentUrl").value("https://example.com/apply"))
                    .andExpect(jsonPath("$.recruitmentStartDate").value("2026-10-26"))
                    .andExpect(jsonPath("$.recruitmentStartDayOfWeek").value("MONDAY"))
                    .andExpect(jsonPath("$.recruitmentEndDate").value("2026-11-01"))
                    .andExpect(jsonPath("$.recruitmentEndDayOfWeek").value("SUNDAY"));
        }
        assertRecruitment(clubId, nullableRecruitment(0));

        mockMvc.perform(put("/api/clubs/{id}/recruitment", sportsClubId).with(editor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nullableRecruitment(0))))
                .andExpect(status().isNoContent());
        assertRecruitment(sportsClubId, nullableRecruitment(0));
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
                body("수정", "2026-11-01", "2026-10-31"),
                body("수정", "2026-02-30", "2026-10-31"),
                body(null, "2026-11-01", "2026-10-31")
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
        request.put("recruitmentEndDate", "2026-10-24");
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
        long alwaysAcademic = createClub("학술 상시모집", ClubCategory.ACADEMIC, 15,
                "2026-10-01", "2026-10-24");
        long alwaysSports = createClub("체육 상시모집", ClubCategory.SPORTS, 25,
                "2026-11-01", "2026-11-30");
        jdbcTemplate.update("UPDATE clubs SET recruitment_always_open = TRUE WHERE id IN (?, ?)",
                alwaysAcademic, alwaysSports);

        for (String path : List.of("/api/clubs", "/api/admin/clubs")) {
            assertList(path, null, null, clubId, earlierAcademic, alwaysAcademic, sports, alwaysSports,
                    upcoming, closed, laterAcademic);
            assertList(path, null, "false", clubId, earlierAcademic, alwaysAcademic, sports, alwaysSports,
                    upcoming, closed, laterAcademic);
            assertList(path, null, "true", earlierAcademic, alwaysAcademic, sports, alwaysSports, laterAcademic);
            assertList(path, "ACADEMIC", "false", clubId, earlierAcademic, alwaysAcademic, upcoming, laterAcademic);
            assertList(path, " academic ", "true", earlierAcademic, alwaysAcademic, laterAcademic);
            assertList(path, "SPORTS", "true", sports, alwaysSports);
            assertList(path, "HOBBY", "true");
            assertList(path, "HOBBY", "false");
            assertList(path, " ", "true", earlierAcademic, alwaysAcademic, sports, alwaysSports, laterAcademic);
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

    @Test
    void adminCanCreateClubsWithEveryCombinationOfNullableRecruitmentFields() throws Exception {
        at("2026-10-24T15:00:00Z");
        for (int mask = 0; mask < 16; mask++) {
            Map<String, Object> request = nullableRecruitment(mask);
            request.put("name", "선택 입력 동아리 " + mask);
            request.put("category", "ACADEMIC");
            String response = mockMvc.perform(post("/api/admin/clubs").with(admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            long createdId = objectMapper.readTree(response).get("id").asLong();
            assertRecruitment(createdId, request);
        }
    }

    @Test
    void bothPutEndpointsAcceptEveryNullableCombinationAndClearAllFields() throws Exception {
        at("2026-10-24T15:00:00Z");
        for (String path : List.of("/api/clubs/{id}/recruitment", "/api/admin/clubs/{id}")) {
            for (int mask = 15; mask >= 0; mask--) {
                Map<String, Object> request = nullableRecruitment(mask);
                request.put("name", "모집 기간 동아리");
                request.put("shortDescription", "소개");
                mockMvc.perform(put(path, clubId).with(path.startsWith("/api/admin/") ? admin() : editor())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isNoContent());
                assertRecruitment(clubId, request);
                assertStatus((mask & 6) == 6 ? "RECRUITING" : "CLOSED");
            }
        }
    }

    @Test
    void putReplacesOmittedFieldsAndNormalizesBlankContentAndUrl() throws Exception {
        Map<String, Object> request = nullableRecruitment(15);
        request.put("recruitmentUrl", "  https://example.com/apply  ");
        saveRecruitment(request);
        assertThat(clubRepository.findById(clubId).orElseThrow().getRecruitmentUrl())
                .isEqualTo("https://example.com/apply");
        saveRecruitment(Map.of("recruitmentUrl", "https://example.com/only-link"));
        Map<String, Object> onlyLink = nullableRecruitment(0);
        onlyLink.put("recruitmentUrl", "https://example.com/only-link");
        assertRecruitment(clubId, onlyLink);

        saveRecruitment(Map.of("recruitmentContent", "  ", "recruitmentUrl", "  "));
        assertRecruitment(clubId, nullableRecruitment(0));
        saveRecruitment(nullableRecruitment(15));
        saveRecruitment(Map.of());
        assertRecruitment(clubId, nullableRecruitment(0));
    }

    @Test
    void allWriteEndpointsRejectInvalidUrlsAndDateOrderWithoutPersistingChanges() throws Exception {
        at("2026-10-24T15:00:00Z");
        Map<String, Object> existing = nullableRecruitment(15);
        saveRecruitment(existing);
        long initialCount = clubRepository.count();
        for (String invalidUrl : List.of("javascript:alert(1)", "ftp://example.com/apply", "not-a-url",
                "https://example.com/" + "a".repeat(2030))) {
            Map<String, Object> request = new LinkedHashMap<>(existing);
            request.put("recruitmentUrl", invalidUrl);
            assertRejectedByAllWriteEndpoints(request);
        }
        Map<String, Object> reversed = new LinkedHashMap<>(existing);
        reversed.put("recruitmentStartDate", "2026-11-01");
        assertRejectedByAllWriteEndpoints(reversed);
        assertThat(clubRepository.count()).isEqualTo(initialCount);
        assertRecruitment(clubId, existing);
        assertThat(clubRepository.findById(clubId).orElseThrow().getName()).isEqualTo("모집 기간 동아리");
    }

    @Test
    void allWriteEndpointsPersistAlwaysOpenRegardlessOfMissingOrOrderedDates() throws Exception {
        int index = 0;
        for (Map<String, Object> request : List.of(
                body(null, null, null),
                body(null, "2026-10-25", null),
                body(null, null, "2026-10-31"),
                body("향후 모집", "2026-11-01", "2026-11-30"),
                body("지난 모집", "2026-09-01", "2026-09-30"),
                body("기간 내 모집", "2026-10-01", "2026-10-31"),
                body("역순 날짜도 보존", "2026-11-01", "2026-10-01")
        )) {
            request.put("recruitmentAlwaysOpen", true);
            request.put("name", "상시모집 동아리 " + index++);
            request.put("category", "SPORTS");
            request.put("shortDescription", "소개");
            String response = mockMvc.perform(post("/api/admin/clubs").with(admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            long createdId = objectMapper.readTree(response).get("id").asLong();
            assertRecruitment(createdId, request, "ALWAYS_OPEN");

            request.put("name", "모집 기간 동아리");
            for (String path : List.of("/api/clubs/{id}/recruitment", "/api/admin/clubs/{id}")) {
                mockMvc.perform(put(path, clubId).with(path.startsWith("/api/admin/") ? admin() : editor())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isNoContent());
                assertRecruitment(clubId, request, "ALWAYS_OPEN");
                assertStatus("ALWAYS_OPEN");
            }
        }
        // Always-open does not bypass parsing or link validation.
        Map<String, Object> invalid = body(null, "2026-02-30", null);
        invalid.put("recruitmentAlwaysOpen", true);
        assertRejectedByAllWriteEndpoints(invalid);
        invalid = body(null, null, null);
        invalid.put("recruitmentAlwaysOpen", true);
        invalid.put("recruitmentUrl", "javascript:alert(1)");
        assertRejectedByAllWriteEndpoints(invalid);
    }

    @Test
    void disablingAlwaysOpenRestoresDateStatusesAndValidationForBothPutEndpoints() throws Exception {
        at("2026-10-24T15:00:00Z");
        List<Map<String, Object>> periods = List.of(
                body(null, null, null),
                body(null, "2026-10-25", null),
                body(null, null, "2026-10-31"),
                body(null, "2026-11-01", "2026-11-30"),
                body(null, "2026-10-25", "2026-10-31"),
                body(null, "2026-10-01", "2026-10-24")
        );
        List<String> statuses = List.of("CLOSED", "CLOSED", "CLOSED", "UPCOMING", "RECRUITING", "CLOSED");
        for (String path : List.of("/api/clubs/{id}/recruitment", "/api/admin/clubs/{id}")) {
            for (int index = 0; index < periods.size(); index++) {
                Map<String, Object> request = new LinkedHashMap<>(periods.get(index));
                request.put("recruitmentAlwaysOpen", true);
                saveRecruitment(request);
                request.put("name", "모집 기간 동아리");
                request.put("shortDescription", "소개");
                request.put("recruitmentAlwaysOpen", false);
                mockMvc.perform(put(path, clubId).with(path.startsWith("/api/admin/") ? admin() : editor())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isNoContent());
                assertRecruitment(clubId, request, statuses.get(index));
                assertStatus(statuses.get(index));
            }
            Map<String, Object> reversed = body("상시모집", "2026-11-01", "2026-10-01");
            reversed.put("recruitmentAlwaysOpen", true);
            saveRecruitment(reversed);
            reversed.put("recruitmentAlwaysOpen", false);
            reversed.put("name", "모집 기간 동아리");
            reversed.put("shortDescription", "소개");
            mockMvc.perform(put(path, clubId).with(path.startsWith("/api/admin/") ? admin() : editor())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(reversed)))
                    .andExpect(status().isBadRequest());
            reversed.put("recruitmentAlwaysOpen", true);
            assertRecruitment(clubId, reversed, "ALWAYS_OPEN");
        }
    }

    @Test
    void omittedOrNullAlwaysOpenDefaultsToFalseAndPutCanDeleteAlwaysOpenNotice() throws Exception {
        for (boolean explicitNull : List.of(false, true)) {
            Map<String, Object> request = new LinkedHashMap<>();
            if (explicitNull) {
                request.put("recruitmentAlwaysOpen", null);
            }
            for (String path : List.of("/api/clubs/{id}/recruitment", "/api/admin/clubs/{id}")) {
                saveRecruitment(Map.of("recruitmentAlwaysOpen", true));
                if (path.startsWith("/api/admin/")) {
                    request.put("name", "모집 기간 동아리");
                    request.put("shortDescription", "소개");
                }
                mockMvc.perform(put(path, clubId).with(path.startsWith("/api/admin/") ? admin() : editor())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isNoContent());
                assertRecruitment(clubId, request, "CLOSED");
            }
            request.put("name", "기본값 동아리 " + explicitNull);
            request.put("category", "SPORTS");
            String response = mockMvc.perform(post("/api/admin/clubs").with(admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            assertRecruitment(objectMapper.readTree(response).get("id").asLong(), request, "CLOSED");
        }
    }

    private void assertRejectedByAllWriteEndpoints(Map<String, Object> request) throws Exception {
        request.put("name", "실패한 요청의 이름");
        request.put("category", "ACADEMIC");
        request.put("shortDescription", "소개");
        String json = objectMapper.writeValueAsString(request);
        mockMvc.perform(post("/api/admin/clubs").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/admin/clubs/{id}", clubId).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/clubs/{id}/recruitment", clubId).with(editor())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
    }

    private Map<String, Object> nullableRecruitment(int mask) {
        Map<String, Object> request = body((mask & 1) != 0 ? "모집 내용" : null,
                (mask & 2) != 0 ? "2026-10-25" : null,
                (mask & 4) != 0 ? "2026-10-31" : null);
        request.put("recruitmentUrl", (mask & 8) != 0 ? "https://example.com/apply" : null);
        return request;
    }

    private void assertRecruitment(long id, Map<String, Object> expected) throws Exception {
        assertRecruitment(id, expected,
                expected.get("recruitmentStartDate") != null && expected.get("recruitmentEndDate") != null
                        ? "RECRUITING" : "CLOSED");
    }

    private void assertRecruitment(long id, Map<String, Object> expected, String expectedStatus) throws Exception {
        Club saved = clubRepository.findById(id).orElseThrow();
        assertThat(saved.isRecruitmentAlwaysOpen()).isEqualTo(Boolean.TRUE.equals(expected.get("recruitmentAlwaysOpen")));
        assertThat(saved.getRecruitmentContent()).isEqualTo(expected.get("recruitmentContent"));
        assertThat(saved.getRecruitmentUrl()).isEqualTo(expected.get("recruitmentUrl"));
        assertThat(saved.getRecruitmentStartDate()).isEqualTo(expected.get("recruitmentStartDate") == null
                ? null : LocalDate.parse((String) expected.get("recruitmentStartDate")));
        assertThat(saved.getRecruitmentEndDate()).isEqualTo(expected.get("recruitmentEndDate") == null
                ? null : LocalDate.parse((String) expected.get("recruitmentEndDate")));
        for (String path : List.of("/api/clubs/{id}", "/api/admin/clubs/{id}")) {
            ResultActions result = mockMvc.perform(get(path, id).with(admin()))
                    .andExpect(status().isOk());
            for (String field : List.of("recruitmentContent", "recruitmentStartDate",
                    "recruitmentEndDate", "recruitmentUrl")) {
                result.andExpect(jsonPath("$." + field).hasJsonPath())
                        .andExpect(jsonPath("$." + field).value(expected.get(field)));
            }
            result.andExpect(jsonPath("$.recruitmentStatus").value(expectedStatus))
                    .andExpect(jsonPath("$.recruitmentAlwaysOpen").value(Boolean.TRUE.equals(expected.get("recruitmentAlwaysOpen"))))
                    .andExpect(jsonPath("$.recruitmentStartDayOfWeek").hasJsonPath())
                    .andExpect(jsonPath("$.recruitmentStartDayOfWeek").value(
                            expected.get("recruitmentStartDate") == null ? null
                                    : LocalDate.parse((String) expected.get("recruitmentStartDate")).getDayOfWeek().name()))
                    .andExpect(jsonPath("$.recruitmentEndDayOfWeek").hasJsonPath())
                    .andExpect(jsonPath("$.recruitmentEndDayOfWeek").value(
                            expected.get("recruitmentEndDate") == null ? null
                                    : LocalDate.parse((String) expected.get("recruitmentEndDate")).getDayOfWeek().name()));
        }
    }

    private long createClub(String name, ClubCategory category, int order, String start, String end) {
        Club club = Club.create(name, category);
        club.updateDisplayOrder(order);
        club.updateRecruitment("모집공고", LocalDate.parse(start), LocalDate.parse(end), null, false);
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
                result.andExpect(jsonPath("$.clubs[" + index + "].recruitmentStatus",
                        in(List.of("RECRUITING", "ALWAYS_OPEN"))));
            }
            result.andExpect(jsonPath("$.clubs[" + index + "].recruitmentAlwaysOpen").value(
                    clubRepository.findById(expectedIds[index]).orElseThrow().isRecruitmentAlwaysOpen()));
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
            if ("RECRUITING".equals(expected) || "ALWAYS_OPEN".equals(expected)) {
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

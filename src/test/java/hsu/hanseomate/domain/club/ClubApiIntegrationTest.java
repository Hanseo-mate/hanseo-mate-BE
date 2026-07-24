package hsu.hanseomate.domain.club;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClubApiIntegrationTest {

    private static final Path TEST_UPLOAD_ROOT = Path.of("build", "test-uploads")
            .toAbsolutePath()
            .normalize();

    private static final byte[] TINY_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUB"
                    + "AScY42YAAAAASUVORK5CYII="
    );

    private static final List<String> ALL_REVIEW_TAGS = List.of(
            "BUILD_RESUME",
            "ACADEMIC_PASSION",
            "ENJOY_HOBBY",
            "SOCIALIZING",
            "CAREER_HELPFUL",
            "SMALL_SCALE",
            "GROUP_ACTIVITY",
            "DEVELOP_SKILL",
            "MANY_SENIORS",
            "MANY_JUNIORS",
            "MANY_GATHERINGS",
            "CALM_ATMOSPHERE",
            "DATING_FRIENDLY",
            "FRIENDLY_MEMBERS",
            "EASY_TO_JOIN_ALONE",
            "SOCIABLE_MEMBERS",
            "LARGE_SCALE",
            "STRONG_SENIORITY",
            "BUSY_SCHEDULE",
            "FLEXIBLE_ATTENDANCE",
            "HAS_FEE",
            "MANDATORY_EVENTS",
            "ATTENDANCE_IMPORTANT",
            "MINIMUM_PERIOD",
            "HAS_CLUB_ROOM",
            "INTERVIEW_IMPORTANT"
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() throws Exception {
        cleanTestUploads();

        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        List<String> clubTables = jdbcTemplate.queryForList(
                """
                        SELECT TABLE_NAME
                        FROM INFORMATION_SCHEMA.TABLES
                        WHERE LOWER(TABLE_SCHEMA) = 'public'
                          AND LOWER(TABLE_NAME) LIKE 'club%'
                        """,
                String.class
        );
        for (String tableName : clubTables) {
            jdbcTemplate.execute("DELETE FROM " + tableName);
        }
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }

    @Test
    void createsClubWithOnlyNameAndCategoryAndInitializesOptionalFieldsAsNull()
            throws Exception {
        Map<String, Object> request = clubRequest("  멋쟁이사자처럼 한서대학교  ", " academic ");

        assertThat(request).containsOnlyKeys("name", "category");

        MvcResult result = mockMvc.perform(post("/api/admin/clubs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$", aMapWithSize(1)))
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn();

        long clubId = responseId(result);
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/api/clubs/" + clubId);

        mockMvc.perform(get("/api/clubs/{clubId}", clubId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(12)))
                .andExpect(jsonPath("$.name").value("멋쟁이사자처럼 한서대학교"))
                .andExpect(jsonPath("$.shortDescription").value((Object) null))
                .andExpect(jsonPath("$.profileImageUrl").value((Object) null))
                .andExpect(jsonPath("$.backgroundImageUrl").value((Object) null))
                .andExpect(jsonPath("$.likeCount").value(0))
                .andExpect(jsonPath("$.topReviewTags").isEmpty())
                .andExpect(jsonPath("$.introduction").value((Object) null))
                .andExpect(jsonPath("$.activityContent").value((Object) null))
                .andExpect(jsonPath("$.instagramUrl").value((Object) null))
                .andExpect(jsonPath("$.kakaoTalkUrl").value((Object) null))
                .andExpect(jsonPath("$.recruitmentContent").value((Object) null))
                .andExpect(jsonPath("$.reviewerCount").value(0))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.category").doesNotExist());
    }

    @Test
    void listsAllClubsAndFiltersByCategoryWithDesignFields() throws Exception {
        long first = createClub("첫 번째 학술 동아리", "ACADEMIC");
        long second = createClub("운동 동아리", "SPORTS");
        long third = createClub("두 번째 학술 동아리", "ACADEMIC");

        mockMvc.perform(get("/api/clubs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(first))
                .andExpect(jsonPath("$[1].id").value(second))
                .andExpect(jsonPath("$[2].id").value(third))
                .andExpect(jsonPath("$[0].profileImageUrl").value((Object) null))
                .andExpect(jsonPath("$[0].shortDescription")
                        .value("함께 서비스를 만드는 IT 동아리"))
                .andExpect(jsonPath("$[0].liked").doesNotExist())
                .andExpect(jsonPath("$[0].likeCount").value(0))
                .andExpect(jsonPath("$[0].topReviewTags").isEmpty())
                .andExpect(jsonPath("$[0].backgroundImageUrl").doesNotExist())
                .andExpect(jsonPath("$[0].introduction").doesNotExist());

        mockMvc.perform(get("/api/clubs").param("category", " academic "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(first))
                .andExpect(jsonPath("$[0].category").value("ACADEMIC"))
                .andExpect(jsonPath("$[1].id").value(third));
    }

    @Test
    void returnsUnifiedClubDetailWithInformationRecruitmentAndReviewerCount()
            throws Exception {
        long clubId = createClub("상세 조회 동아리", "HOBBY");
        setLike(clubId, true);
        putReview(clubId, List.of("BUILD_RESUME", "ACADEMIC_PASSION"));

        mockMvc.perform(get("/api/clubs/{clubId}", clubId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(12)))
                .andExpect(jsonPath("$.name").value("상세 조회 동아리"))
                .andExpect(jsonPath("$.profileImageUrl").value((Object) null))
                .andExpect(jsonPath("$.backgroundImageUrl").value((Object) null))
                .andExpect(jsonPath("$.shortDescription")
                        .value("함께 서비스를 만드는 IT 동아리"))
                .andExpect(jsonPath("$.liked").doesNotExist())
                .andExpect(jsonPath("$.likeCount").value(1))
                .andExpect(jsonPath("$.topReviewTags.length()").value(2))
                .andExpect(jsonPath("$.topReviewTags[0]").value("BUILD_RESUME"))
                .andExpect(jsonPath("$.topReviewTags[1]").value("ACADEMIC_PASSION"))
                .andExpect(jsonPath("$.introduction").value("동아리 상세 소개 🦁"))
                .andExpect(jsonPath("$.activityContent").value("프로젝트와 스터디 활동 🚀"))
                .andExpect(jsonPath("$.instagramUrl").value("https://instagram.com/example"))
                .andExpect(jsonPath("$.kakaoTalkUrl").value("https://open.kakao.com/o/example"))
                .andExpect(jsonPath("$.recruitmentContent")
                        .value("현재 신입 부원을 모집합니다 🙌"))
                .andExpect(jsonPath("$.reviewerCount").value(1))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.category").doesNotExist())
                .andExpect(jsonPath("$.createdAt").doesNotExist())
                .andExpect(jsonPath("$.updatedAt").doesNotExist());
    }

    @Test
    void uploadsClubImagesStoresUrlsAndServesUploadedFiles() throws Exception {
        long clubId = createClub("이미지 업로드 동아리", "ACADEMIC");

        String backgroundUrl = uploadImage(
                "/api/admin/clubs/background-images/{clubId}",
                clubId,
                "background.png"
        );
        String profileUrl = uploadImage(
                "/api/admin/clubs/profile-images/{clubId}",
                clubId,
                "profile.png"
        );

        assertThat(backgroundUrl).startsWith("http://localhost/uploads/clubs/background/");
        assertThat(profileUrl).startsWith("http://localhost/uploads/clubs/profile/");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT background_image_url FROM clubs WHERE id = ?",
                String.class,
                clubId
        )).isEqualTo(backgroundUrl);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT profile_image_url FROM clubs WHERE id = ?",
                String.class,
                clubId
        )).isEqualTo(profileUrl);

        mockMvc.perform(get(URI.create(backgroundUrl).getPath()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(TINY_PNG));
        mockMvc.perform(get(URI.create(profileUrl).getPath()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(TINY_PNG));

        mockMvc.perform(get("/api/clubs/{clubId}", clubId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backgroundImageUrl").value(backgroundUrl))
                .andExpect(jsonPath("$.profileImageUrl").value(profileUrl));

        String replacedProfileUrl = uploadImage(
                "/api/admin/clubs/profile-images/{clubId}",
                clubId,
                "replaced-profile.png"
        );
        assertThat(replacedProfileUrl).isNotEqualTo(profileUrl);
        mockMvc.perform(get(URI.create(profileUrl).getPath()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(URI.create(replacedProfileUrl).getPath()))
                .andExpect(status().isOk())
                .andExpect(content().bytes(TINY_PNG));
        mockMvc.perform(get("/api/clubs/{clubId}", clubId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileImageUrl").value(replacedProfileUrl));
    }

    @Test
    void deletesProfileAndBackgroundImagesIndependentlyAndIdempotently() throws Exception {
        long clubId = createClub("이미지 삭제 동아리", "ACADEMIC");
        String backgroundUrl = uploadImage(
                "/api/admin/clubs/background-images/{clubId}",
                clubId,
                "background.png"
        );
        String profileUrl = uploadImage(
                "/api/admin/clubs/profile-images/{clubId}",
                clubId,
                "profile.png"
        );

        mockMvc.perform(delete("/api/admin/clubs/profile-images/{clubId}", clubId))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT profile_image_url FROM clubs WHERE id = ?",
                String.class,
                clubId
        )).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT background_image_url FROM clubs WHERE id = ?",
                String.class,
                clubId
        )).isEqualTo(backgroundUrl);
        mockMvc.perform(get(URI.create(profileUrl).getPath()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(URI.create(backgroundUrl).getPath()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/clubs/{clubId}", clubId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileImageUrl").value((Object) null))
                .andExpect(jsonPath("$.backgroundImageUrl").value(backgroundUrl));

        // 같은 삭제 요청을 반복해도 이미 삭제된 상태를 성공으로 처리한다.
        mockMvc.perform(delete("/api/admin/clubs/profile-images/{clubId}", clubId))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/admin/clubs/background-images/{clubId}", clubId))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT background_image_url FROM clubs WHERE id = ?",
                String.class,
                clubId
        )).isNull();
        mockMvc.perform(get(URI.create(backgroundUrl).getPath()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/clubs/{clubId}", clubId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileImageUrl").value((Object) null))
                .andExpect(jsonPath("$.backgroundImageUrl").value((Object) null));
    }

    @Test
    void updatesClubTextSectionsWithoutRemovingInteractionData() throws Exception {
        long clubId = createClub("수정 전 동아리", "RELIGION");
        setLike(clubId, true);
        putReview(clubId, List.of("BUILD_RESUME"));

        String longIntroduction = "아주 긴 동아리 소개와 이모지 🦁\n".repeat(700);
        String longActivityContent = "아주 긴 활동 내용과 이모지 🚀\n".repeat(700);
        String longRecruitment = "아주 긴 모집공고와 이모지 🙌\n".repeat(700);
        Map<String, Object> updateRequest = clubUpdateRequest(
                "수정된 동아리",
                "수정된 한 줄 소개",
                longIntroduction,
                longActivityContent,
                "https://instagram.com/updated",
                null,
                longRecruitment
        );

        mockMvc.perform(put("/api/admin/clubs/{clubId}", clubId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateRequest)))
                .andExpect(status().isNoContent());

        MvcResult detailResult = mockMvc.perform(get("/api/clubs/{clubId}", clubId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("수정된 동아리"))
                .andExpect(jsonPath("$.shortDescription").value("수정된 한 줄 소개"))
                .andExpect(jsonPath("$.likeCount").value(1))
                .andExpect(jsonPath("$.liked").doesNotExist())
                .andExpect(jsonPath("$.topReviewTags[0]").value("BUILD_RESUME"))
                .andExpect(jsonPath("$.reviewerCount").value(1))
                .andExpect(jsonPath("$.instagramUrl").value("https://instagram.com/updated"))
                .andExpect(jsonPath("$.kakaoTalkUrl").value((Object) null))
                .andReturn();
        Map<String, Object> detail = responseBody(detailResult);
        assertThat(detail.get("introduction")).isEqualTo(longIntroduction.strip());
        assertThat(detail.get("activityContent")).isEqualTo(longActivityContent.strip());
        assertThat(detail.get("recruitmentContent")).isEqualTo(longRecruitment.strip());
    }

    @Test
    void incrementsAndDecrementsLikeCountPerRequestWithoutClientIdentity() throws Exception {
        long clubId = createClub("좋아요 동아리", "ACADEMIC");

        setLikeAndExpect(clubId, true, true, 1);
        setLikeAndExpect(clubId, true, true, 2);
        setLikeAndExpect(clubId, true, true, 3);
        setLikeAndExpect(clubId, false, false, 2);
        setLikeAndExpect(clubId, false, false, 1);
        setLikeAndExpect(clubId, false, false, 0);
        setLikeAndExpect(clubId, false, false, 0);

        mockMvc.perform(get("/api/clubs/{clubId}", clubId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").doesNotExist())
                .andExpect(jsonPath("$.likeCount").value(0));
    }

    @Test
    void returnsAllReviewOptionsUsingTotalSelectedTagCount() throws Exception {
        long clubId = createClub("활동 후기 동아리", "ACADEMIC");

        mockMvc.perform(put("/api/clubs/reviews/{clubId}", clubId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewRequest(List.of(
                                "BUILD_RESUME",
                                "ACADEMIC_PASSION"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(1)))
                .andExpect(jsonPath("$.message").value("활동 후기가 등록되었습니다."));

        mockMvc.perform(get("/api/clubs/reviews/{clubId}", clubId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(1)))
                .andExpect(jsonPath("$.clubId").doesNotExist())
                .andExpect(jsonPath("$.reviewerCount").doesNotExist())
                .andExpect(jsonPath("$.selectedReviewTags").doesNotExist())
                .andExpect(jsonPath("$.options.length()").value(26))
                .andExpect(jsonPath("$.options[0]", aMapWithSize(2)))
                .andExpect(jsonPath("$.options[0].reviewTag").value("BUILD_RESUME"))
                .andExpect(jsonPath("$.options[0].percentage").value(50.0))
                .andExpect(jsonPath("$.options[0].count").doesNotExist())
                .andExpect(jsonPath("$.options[0].selected").doesNotExist())
                .andExpect(jsonPath("$.options[0].label").doesNotExist())
                .andExpect(jsonPath("$.options[0].emoji").doesNotExist())
                .andExpect(jsonPath("$.options[25].reviewTag").value("INTERVIEW_IMPORTANT"))
                .andExpect(jsonPath("$.options[25].percentage").value(0.0));
        expectReviewerCount(clubId, 1);
    }

    @Test
    void calculatesReviewPercentagesAcrossAllAnonymousSubmissions() throws Exception {
        long clubId = createClub("후기 비율 동아리", "ACADEMIC");
        putReview(clubId, List.of("BUILD_RESUME", "ACADEMIC_PASSION"));
        putReview(clubId, List.of("BUILD_RESUME", "ACADEMIC_PASSION", "ENJOY_HOBBY"));
        putReview(clubId, List.of("BUILD_RESUME"));

        mockMvc.perform(get("/api/clubs/reviews/{clubId}", clubId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(1)))
                .andExpect(jsonPath("$.reviewerCount").doesNotExist())
                .andExpect(jsonPath("$.options.length()").value(26))
                .andExpect(jsonPath(
                        "$.options[?(@.reviewTag == 'BUILD_RESUME')].percentage"
                ).value(hasItem(50.0)))
                .andExpect(jsonPath(
                        "$.options[?(@.reviewTag == 'ACADEMIC_PASSION')].percentage"
                ).value(hasItem(33.33)))
                .andExpect(jsonPath(
                        "$.options[?(@.reviewTag == 'ENJOY_HOBBY')].percentage"
                ).value(hasItem(16.67)))
                .andExpect(jsonPath(
                        "$.options[?(@.reviewTag == 'INTERVIEW_IMPORTANT')].percentage"
                        ).value(hasItem(0.0)));
        expectReviewerCount(clubId, 3);
    }

    @Test
    void rejectsInvalidReviewSelectionsButAcceptsEmptySelection() throws Exception {
        long clubId = createClub("후기 검증 동아리", "ACADEMIC");

        expectInvalidReview(clubId, ALL_REVIEW_TAGS.subList(0, 6));
        expectInvalidReview(clubId, List.of("BUILD_RESUME", "BUILD_RESUME"));
        expectInvalidReview(clubId, List.of("UNKNOWN_REVIEW_TAG"));

        mockMvc.perform(put("/api/clubs/reviews/{clubId}", clubId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewTags\":[null]}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/clubs/reviews/{clubId}", clubId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewRequest(List.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(1)))
                .andExpect(jsonPath("$.message").value("활동 후기가 삭제되었습니다."));
    }

    @Test
    void emptyReviewRequestsRemoveOnlyTheMostRecentSubmissionAndDeleteIsNotSupported()
            throws Exception {
        long clubId = createClub("후기 누적 동아리", "ACADEMIC");
        putReview(clubId, List.of("BUILD_RESUME", "ACADEMIC_PASSION"));
        putReview(clubId, List.of("ENJOY_HOBBY"));
        putReview(clubId, List.of("ENJOY_HOBBY"));

        mockMvc.perform(get("/api/clubs/reviews/{clubId}", clubId))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.options[?(@.reviewTag == 'ENJOY_HOBBY')].percentage"
                        ).value(hasItem(50.0)));
        expectReviewerCount(clubId, 3);

        mockMvc.perform(put("/api/clubs/reviews/{clubId}", clubId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewRequest(List.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(1)))
                .andExpect(jsonPath("$.message").value("활동 후기가 삭제되었습니다."));

        mockMvc.perform(get("/api/clubs/reviews/{clubId}", clubId))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.options[?(@.reviewTag == 'ENJOY_HOBBY')].percentage"
                        ).value(hasItem(33.33)));
        expectReviewerCount(clubId, 2);

        mockMvc.perform(put("/api/clubs/reviews/{clubId}", clubId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(1)))
                .andExpect(jsonPath("$.message").value("활동 후기가 삭제되었습니다."));

        mockMvc.perform(get("/api/clubs/reviews/{clubId}", clubId))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.options[?(@.reviewTag == 'ENJOY_HOBBY')].percentage"
                        ).value(hasItem(0.0)))
                .andExpect(jsonPath(
                        "$.options[?(@.reviewTag == 'BUILD_RESUME')].percentage"
                        ).value(hasItem(50.0)));
        expectReviewerCount(clubId, 1);

        mockMvc.perform(delete("/api/clubs/reviews/{clubId}", clubId))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void returnsTopTwoReviewTagsInListAndTopThreeInDetail() throws Exception {
        long clubId = createClub("후기 상위 동아리", "PERFORMANCE");
        putReview(
                clubId,
                List.of("BUILD_RESUME", "ACADEMIC_PASSION", "ENJOY_HOBBY")
        );
        putReview(
                clubId,
                List.of("BUILD_RESUME", "ACADEMIC_PASSION", "SOCIALIZING")
        );
        putReview(clubId, List.of("BUILD_RESUME", "DEVELOP_SKILL"));

        mockMvc.perform(get("/api/clubs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].topReviewTags.length()").value(2))
                .andExpect(jsonPath("$[0].topReviewTags[0]").value("BUILD_RESUME"))
                .andExpect(jsonPath("$[0].topReviewTags[1]").value("ACADEMIC_PASSION"));

        mockMvc.perform(get("/api/clubs/{clubId}", clubId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topReviewTags.length()").value(3))
                .andExpect(jsonPath("$.topReviewTags[0]").value("BUILD_RESUME"))
                .andExpect(jsonPath("$.topReviewTags[1]").value("ACADEMIC_PASSION"))
                .andExpect(jsonPath("$.topReviewTags[2]").value("ENJOY_HOBBY"));
    }

    @Test
    void isolatesLikesAndReviewsByClub() throws Exception {
        long firstClubId = createClub("첫 번째 동아리", "ACADEMIC");
        long secondClubId = createClub("두 번째 동아리", "SPORTS");
        setLike(firstClubId, true);
        putReview(firstClubId, List.of("BUILD_RESUME"));

        mockMvc.perform(get("/api/clubs/{clubId}", firstClubId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").doesNotExist())
                .andExpect(jsonPath("$.likeCount").value(1))
                .andExpect(jsonPath("$.topReviewTags.length()").value(1));

        mockMvc.perform(get("/api/clubs/{clubId}", secondClubId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").doesNotExist())
                .andExpect(jsonPath("$.likeCount").value(0))
                .andExpect(jsonPath("$.topReviewTags").isEmpty());
    }

    @Test
    void deletesClubTogetherWithLikesReviewsAndManagedImages() throws Exception {
        long clubId = createClub("삭제할 동아리", "RELIGION");
        setLike(clubId, true);
        setLike(clubId, true);
        putReview(clubId, List.of("BUILD_RESUME", "ACADEMIC_PASSION"));
        putReview(clubId, List.of("BUILD_RESUME"));
        String profileUrl = uploadImage(
                "/api/admin/clubs/profile-images/{clubId}",
                clubId,
                "profile.png"
        );

        mockMvc.perform(delete("/api/admin/clubs/{clubId}", clubId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/clubs/{clubId}", clubId))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/clubs/reviews/{clubId}", clubId))
                .andExpect(status().isNotFound());

        // 동아리를 삭제하면 서버가 관리하는 이미지 파일도 함께 정리한다.
        mockMvc.perform(get(URI.create(profileUrl).getPath()))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsBadRequestAndNotFoundForInvalidClubRequests() throws Exception {
        Map<String, Object> blankName = clubRequest("   ", "ACADEMIC");
        mockMvc.perform(post("/api/admin/clubs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(blankName)))
                .andExpect(status().isBadRequest());

        long invalidUrlClubId = createClub("잘못된 URL 동아리", "ACADEMIC");
        mockMvc.perform(put("/api/admin/clubs/{clubId}", invalidUrlClubId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(clubUpdateRequest(
                                "잘못된 URL 동아리",
                                "한 줄 소개",
                                "동아리 소개",
                                "동아리 활동",
                                "javascript:alert(1)",
                                "https://open.kakao.com/o/example",
                                "모집공고"
                        ))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/clubs").param("category", "ALL"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/clubs/{clubId}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        mockMvc.perform(put("/api/clubs/likes/{clubId}", 999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("liked", true))))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/clubs/reviews/{clubId}", 999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewRequest(List.of("BUILD_RESUME"))))
                .andExpect(status().isNotFound());

        MockMultipartFile image = pngFile("image.png");
        mockMvc.perform(multipart("/api/admin/clubs/background-images/{clubId}", 999999L)
                        .file(image)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        }))
                .andExpect(status().isNotFound());
        mockMvc.perform(multipart("/api/admin/clubs/profile-images/{clubId}", 999999L)
                        .file(pngFile("image.png"))
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        }))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/admin/clubs/background-images/{clubId}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        mockMvc.perform(delete("/api/admin/clubs/profile-images/{clubId}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        mockMvc.perform(put("/api/admin/clubs/{clubId}", 999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(clubUpdateRequest(
                                "없는 동아리",
                                "한 줄 소개",
                                "소개",
                                "활동",
                                null,
                                null,
                                "모집공고"
                        ))))
                .andExpect(status().isNotFound());

        long clubId = createClub("이미지 검증 동아리", "ACADEMIC");
        MockMultipartFile invalidImage = new MockMultipartFile(
                "file",
                "not-image.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "not an image".getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/admin/clubs/background-images/{clubId}", clubId)
                        .file(invalidImage)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        }))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsDuplicateNameInUnifiedUpdate() throws Exception {
        createClub("이미 존재하는 동아리", "ACADEMIC");
        long targetClubId = createClub("수정 대상 동아리", "SPORTS");

        mockMvc.perform(put("/api/admin/clubs/{clubId}", targetClubId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(clubUpdateRequest(
                                "이미 존재하는 동아리",
                                "중복 이름으로 변경",
                                "소개",
                                "활동",
                                null,
                                null,
                                "모집공고"
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exposesCurrentClubEndpointsInOpenApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/clubs'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/clubs/{clubId}'].get.responses['404']")
                        .exists())
                .andExpect(jsonPath(
                        "$.paths['/api/clubs/information/{clubId}']"
                ).doesNotExist())
                .andExpect(jsonPath(
                        "$.paths['/api/clubs/recruitments/{clubId}']"
                ).doesNotExist())
                .andExpect(jsonPath("$.paths['/api/admin/clubs'].post.responses['201']")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.ClubCreateRequest.properties",
                        aMapWithSize(2)))
                .andExpect(jsonPath("$.components.schemas.ClubCreateRequest.properties.name")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.ClubCreateRequest.properties.category")
                        .exists())
                .andExpect(jsonPath(
                        "$.components.schemas.ClubCreateRequest.properties.shortDescription"
                ).doesNotExist())
                .andExpect(jsonPath(
                        "$.paths['/api/admin/clubs/{clubId}'].put.responses['204']"
                ).exists())
                .andExpect(jsonPath("$.components.schemas.ClubUpdateRequest.properties",
                        aMapWithSize(7)))
                .andExpect(jsonPath("$.components.schemas.ClubUpdateRequest.properties.name")
                        .exists())
                .andExpect(jsonPath(
                        "$.components.schemas.ClubUpdateRequest.properties.shortDescription"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.ClubUpdateRequest.properties.introduction"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.ClubUpdateRequest.properties.activityContent"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.ClubUpdateRequest.properties.instagramUrl"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.ClubUpdateRequest.properties.kakaoTalkUrl"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.ClubUpdateRequest.properties.recruitmentContent"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.ClubUpdateRequest.properties.category"
                ).doesNotExist())
                .andExpect(jsonPath(
                        "$.components.schemas.ClubUpdateRequest.properties.profileImageUrl"
                ).doesNotExist())
                .andExpect(jsonPath(
                        "$.components.schemas.ClubUpdateRequest.properties.backgroundImageUrl"
                ).doesNotExist())
                .andExpect(jsonPath(
                        "$.paths['/api/admin/clubs/background-images/{clubId}']"
                                + ".put.responses['200']"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/admin/clubs/background-images/{clubId}']"
                                + ".put.responses['200'].content['*/*']"
                                + ".schema['$ref']"
                ).value("#/components/schemas/ClubImageUploadResponse"))
                .andExpect(jsonPath(
                        "$.paths['/api/admin/clubs/background-images/{clubId}']"
                                + ".put.requestBody.content['multipart/form-data']"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/admin/clubs/background-images/{clubId}']"
                                + ".delete.responses['204']"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/admin/clubs/background-images/{clubId}']"
                                + ".delete.responses['404']"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/admin/clubs/background-images/{clubId}']"
                                + ".delete.requestBody"
                ).doesNotExist())
                .andExpect(jsonPath(
                        "$.paths['/api/admin/clubs/profile-images/{clubId}']"
                                + ".put.responses['200']"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/admin/clubs/profile-images/{clubId}']"
                                + ".put.responses['200'].content['*/*']"
                                + ".schema['$ref']"
                ).value("#/components/schemas/ClubImageUploadResponse"))
                .andExpect(jsonPath(
                        "$.paths['/api/admin/clubs/profile-images/{clubId}']"
                                + ".put.requestBody.content['multipart/form-data']"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/admin/clubs/profile-images/{clubId}']"
                                + ".delete.responses['204']"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/admin/clubs/profile-images/{clubId}']"
                                + ".delete.responses['404']"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/admin/clubs/profile-images/{clubId}']"
                                + ".delete.requestBody"
                ).doesNotExist())
                .andExpect(jsonPath(
                        "$.components.schemas.ClubImageUploadResponse.properties",
                        aMapWithSize(1)
                ))
                .andExpect(jsonPath(
                        "$.components.schemas.ClubImageUploadResponse.properties.imageId"
                ).doesNotExist())
                .andExpect(jsonPath(
                        "$.components.schemas.ClubImageUploadResponse.properties.imageUrl"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/admin/clubs/basic-info/{clubId}']"
                ).doesNotExist())
                .andExpect(jsonPath(
                        "$.paths['/api/admin/clubs/information/{clubId}']"
                ).doesNotExist())
                .andExpect(jsonPath(
                        "$.paths['/api/admin/clubs/recruitments/{clubId}']"
                ).doesNotExist())
                .andExpect(jsonPath("$.paths['/api/clubs/likes/{clubId}'].put.responses['200']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/clubs/reviews/{clubId}'].get.responses['200']")
                        .exists())
                .andExpect(jsonPath(
                        "$.components.schemas.ClubReviewStatisticsResponse.properties",
                        aMapWithSize(1)
                ))
                .andExpect(jsonPath(
                        "$.components.schemas.ClubReviewStatisticsResponse.properties.options"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.ClubReviewStatisticsResponse.properties.clubId"
                ).doesNotExist())
                .andExpect(jsonPath(
                        "$.components.schemas.ClubReviewStatisticsResponse.properties.reviewerCount"
                ).doesNotExist())
                .andExpect(jsonPath("$.paths['/api/clubs/reviews/{clubId}'].put.responses['200']")
                        .exists())
                .andExpect(jsonPath("$.paths['/api/clubs/reviews/{clubId}'].delete")
                        .doesNotExist())
                .andExpect(jsonPath("$.paths['/api/admin/clubs/activity-photos/{clubId}']")
                        .doesNotExist());
    }

    private long createClub(String name, String category) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/clubs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(clubRequest(name, category))))
                .andExpect(status().isCreated())
                .andReturn();
        long clubId = responseId(result);
        populateClubFixture(clubId, name.strip());
        return clubId;
    }

    private void populateClubFixture(long clubId, String name) throws Exception {
        mockMvc.perform(put("/api/admin/clubs/{clubId}", clubId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(clubUpdateRequest(
                                name,
                                "  함께 서비스를 만드는 IT 동아리  ",
                                "  동아리 상세 소개 🦁  ",
                                "  프로젝트와 스터디 활동 🚀  ",
                                "https://instagram.com/example",
                                "https://open.kakao.com/o/example",
                                "  현재 신입 부원을 모집합니다 🙌  "
                        ))))
                .andExpect(status().isNoContent());
    }

    private void setLike(long clubId, boolean liked) throws Exception {
        mockMvc.perform(put("/api/clubs/likes/{clubId}", clubId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("liked", liked))))
                .andExpect(status().isOk());
    }

    private void setLikeAndExpect(
            long clubId,
            boolean requestedState,
            boolean expectedState,
            long expectedCount
    ) throws Exception {
        mockMvc.perform(put("/api/clubs/likes/{clubId}", clubId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("liked", requestedState))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clubId").value(clubId))
                .andExpect(jsonPath("$.liked").value(expectedState))
                .andExpect(jsonPath("$.likeCount").value(expectedCount));
    }

    private void putReview(long clubId, List<String> tags) throws Exception {
        mockMvc.perform(put("/api/clubs/reviews/{clubId}", clubId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewRequest(tags)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(1)))
                .andExpect(jsonPath("$.message").value("활동 후기가 등록되었습니다."));
    }

    private void expectReviewerCount(long clubId, long expectedCount) throws Exception {
        mockMvc.perform(get("/api/clubs/{clubId}", clubId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewerCount").value(expectedCount));
    }

    private void expectInvalidReview(long clubId, List<String> tags) throws Exception {
        mockMvc.perform(put("/api/clubs/reviews/{clubId}", clubId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewRequest(tags)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    private String uploadImage(String endpoint, long clubId, String originalFileName)
            throws Exception {
        MvcResult result = mockMvc.perform(multipart(endpoint, clubId)
                        .file(pngFile(originalFileName))
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(1)))
                .andExpect(jsonPath("$.imageId").doesNotExist())
                .andExpect(jsonPath("$.imageUrl").isString())
                .andReturn();
        Map<String, Object> response = responseBody(result);
        String imageUrl = (String) response.get("imageUrl");

        assertThat(Path.of(URI.create(imageUrl).getPath()).getFileName().toString())
                .isNotBlank();
        return imageUrl;
    }

    private MockMultipartFile pngFile(String originalFileName) {
        return new MockMultipartFile(
                "file",
                originalFileName,
                MediaType.IMAGE_PNG_VALUE,
                TINY_PNG
        );
    }

    private String reviewRequest(List<String> tags) throws Exception {
        return json(Map.of("reviewTags", tags));
    }

    private Map<String, Object> clubRequest(String name, String category) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", name);
        request.put("category", category);
        return request;
    }

    private Map<String, Object> clubUpdateRequest(
            String name,
            String shortDescription,
            String introduction,
            String activityContent,
            String instagramUrl,
            String kakaoTalkUrl,
            String recruitmentContent
    ) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", name);
        request.put("shortDescription", shortDescription);
        request.put("introduction", introduction);
        request.put("activityContent", activityContent);
        request.put("instagramUrl", instagramUrl);
        request.put("kakaoTalkUrl", kakaoTalkUrl);
        request.put("recruitmentContent", recruitmentContent);
        return request;
    }

    private void cleanTestUploads() throws Exception {
        Path buildRoot = Path.of("build").toAbsolutePath().normalize();
        if (!TEST_UPLOAD_ROOT.startsWith(buildRoot) || TEST_UPLOAD_ROOT.equals(buildRoot)) {
            throw new IllegalStateException("테스트 업로드 경로가 build 하위가 아닙니다.");
        }
        if (!Files.exists(TEST_UPLOAD_ROOT)) {
            Files.createDirectories(TEST_UPLOAD_ROOT);
            return;
        }

        List<Path> descendants;
        try (Stream<Path> paths = Files.walk(TEST_UPLOAD_ROOT)) {
            descendants = paths
                    .filter(path -> !path.equals(TEST_UPLOAD_ROOT))
                    .sorted(java.util.Comparator.reverseOrder())
                    .toList();
        }
        for (Path path : descendants) {
            Files.deleteIfExists(path);
        }
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private long responseId(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        Number id = JsonPath.read(body, "$.id");
        return id.longValue();
    }

    private Map<String, Object> responseBody(MvcResult result) throws Exception {
        return objectMapper.readValue(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8),
                new TypeReference<>() {
                }
        );
    }
}

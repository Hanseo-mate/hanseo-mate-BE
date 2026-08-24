package hsu.hanseomate.domain.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hsu.hanseomate.domain.club.entity.Club;
import hsu.hanseomate.domain.club.entity.ClubReview;
import hsu.hanseomate.domain.club.repository.ClubRepository;
import hsu.hanseomate.domain.club.repository.ClubReviewRepository;
import hsu.hanseomate.domain.club.type.ClubCategory;
import hsu.hanseomate.domain.club.type.ClubReviewOption;
import hsu.hanseomate.domain.user.entity.UserAccount;
import hsu.hanseomate.domain.user.repository.UserAccountRepository;
import hsu.hanseomate.global.security.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MyPageApiIntegrationTest {

    private static final String PATH = "/api/auth/me";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private ClubRepository clubRepository;

    @Autowired
    private ClubReviewRepository clubReviewRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    @BeforeEach
    void cleanDatabaseBeforeTest() {
        cleanDatabase();
    }

    @AfterEach
    void cleanDatabaseAfterTest() {
        cleanDatabase();
    }

    @Test
    void returnsMyAccountAndOnlyMyClubReviewsInNewestOrder() throws Exception {
        AuthSession current = signupAndLogin("mypage-user");
        AuthSession other = signupAndLogin("other-user");
        UserAccount currentUser = userAccountRepository.findById(current.userId()).orElseThrow();
        UserAccount otherUser = userAccountRepository.findById(other.userId()).orElseThrow();

        Club firstClubToSave = Club.create("첫 번째 동아리", ClubCategory.ACADEMIC);
        firstClubToSave.updateProfileImage("https://example.com/clubs/first-profile.png");
        Club firstClub = clubRepository.saveAndFlush(firstClubToSave);

        Club secondClubToSave = Club.create("두 번째 동아리", ClubCategory.HOBBY);
        secondClubToSave.updateProfileImage("https://example.com/clubs/second-profile.png");
        Club secondClub = clubRepository.saveAndFlush(secondClubToSave);
        Club otherClub = clubRepository.saveAndFlush(
                Club.create("다른 사용자 동아리", ClubCategory.SPORTS)
        );

        clubReviewRepository.saveAndFlush(ClubReview.create(
                firstClub,
                currentUser,
                new LinkedHashSet<>(List.of(
                        ClubReviewOption.SOCIALIZING,
                        ClubReviewOption.BUILD_RESUME
                ))
        ));
        clubReviewRepository.saveAndFlush(ClubReview.create(
                secondClub,
                currentUser,
                new LinkedHashSet<>(List.of(ClubReviewOption.ENJOY_HOBBY))
        ));
        clubReviewRepository.saveAndFlush(ClubReview.create(
                otherClub,
                otherUser,
                new LinkedHashSet<>(List.of(ClubReviewOption.LARGE_SCALE))
        ));

        mockMvc.perform(get(PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + current.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(current.userId()))
                .andExpect(jsonPath("$.loginId").value("mypage-user"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.preferredRestaurantType")
                        .value("MAIN_STUDENT"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.clubReviews.length()").value(2))
                .andExpect(jsonPath("$.clubReviews[0].clubId").value(secondClub.getId()))
                .andExpect(jsonPath("$.clubReviews[0].clubName").value("두 번째 동아리"))
                .andExpect(jsonPath("$.clubReviews[0].profileImageUrl")
                        .value("https://example.com/clubs/second-profile.png"))
                .andExpect(jsonPath("$.clubReviews[0].reviewTags[0]")
                        .value("ENJOY_HOBBY"))
                .andExpect(jsonPath("$.clubReviews[1].clubId").value(firstClub.getId()))
                .andExpect(jsonPath("$.clubReviews[1].clubName").value("첫 번째 동아리"))
                .andExpect(jsonPath("$.clubReviews[1].profileImageUrl")
                        .value("https://example.com/clubs/first-profile.png"))
                .andExpect(jsonPath("$.clubReviews[1].reviewTags[0]")
                        .value("BUILD_RESUME"))
                .andExpect(jsonPath("$.clubReviews[1].reviewTags[1]")
                        .value("SOCIALIZING"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void returnsEmptyReviewListWhenCurrentUserHasNoReview() throws Exception {
        AuthSession current = signupAndLogin("no-review-user");

        mockMvc.perform(get(PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + current.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(current.userId()))
                .andExpect(jsonPath("$.clubReviews").isArray())
                .andExpect(jsonPath("$.clubReviews").isEmpty())
                .andExpect(jsonPath("$.likedClubs").isArray())
                .andExpect(jsonPath("$.likedClubs").isEmpty());
    }

    @Test
    void returnsOnlyMyLikedClubsInNewestOrder() throws Exception {
        AuthSession current = signupAndLogin("liked-club-user");
        AuthSession other = signupAndLogin("other-liked-club-user");

        Club firstClub = clubRepository.saveAndFlush(
                Club.create("첫 번째 좋아요 동아리", ClubCategory.ACADEMIC)
        );
        Club secondClub = clubRepository.saveAndFlush(
                Club.create("두 번째 좋아요 동아리", ClubCategory.HOBBY)
        );
        Club otherClub = clubRepository.saveAndFlush(
                Club.create("다른 사용자 좋아요 동아리", ClubCategory.SPORTS)
        );

        toggleLike(firstClub.getId(), current.accessToken());
        toggleLike(secondClub.getId(), current.accessToken());
        toggleLike(otherClub.getId(), other.accessToken());

        mockMvc.perform(get(PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + current.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likedClubs.length()").value(2))
                .andExpect(jsonPath("$.likedClubs[0].clubId").value(secondClub.getId()))
                .andExpect(jsonPath("$.likedClubs[0].clubName")
                        .value("두 번째 좋아요 동아리"))
                .andExpect(jsonPath("$.likedClubs[1].clubId").value(firstClub.getId()))
                .andExpect(jsonPath("$.likedClubs[1].clubName")
                        .value("첫 번째 좋아요 동아리"));
    }

    @Test
    void reflectsReviewChangesMadeThroughClubReviewApi() throws Exception {
        AuthSession current = signupAndLogin("review-api-user");
        Club club = clubRepository.saveAndFlush(
                Club.create("후기 연동 동아리", ClubCategory.VOLUNTEER)
        );

        mockMvc.perform(put("/api/clubs/reviews/{clubId}", club.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + current.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reviewTags",
                                List.of("BUILD_RESUME", "SOCIALIZING")
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(get(PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + current.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clubReviews.length()").value(1))
                .andExpect(jsonPath("$.clubReviews[0].clubId").value(club.getId()))
                .andExpect(jsonPath("$.clubReviews[0].profileImageUrl")
                        .value((Object) null))
                .andExpect(jsonPath("$.clubReviews[0].reviewTags[0]")
                        .value("BUILD_RESUME"))
                .andExpect(jsonPath("$.clubReviews[0].reviewTags[1]")
                        .value("SOCIALIZING"));

        mockMvc.perform(put("/api/clubs/reviews/{clubId}", club.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + current.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reviewTags",
                                List.of()
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(get(PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + current.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clubReviews").isEmpty());
    }

    @Test
    void requiresValidBearerTokenAndExistingUser() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value(PATH));

        mockMvc.perform(get(PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(get(PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredAccessToken(1L)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(get(PATH).with(jwt().jwt(token -> token
                        .subject("999999")
                        .claim("role", "USER"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.path").value(PATH));

        mockMvc.perform(get(PATH).with(jwt().jwt(token -> token
                        .subject("not-a-number")
                        .claim("role", "USER"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.path").value(PATH));
    }

    @Test
    void openApiDocumentsMyPageEndpoint() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/me'].get").exists())
                .andExpect(jsonPath("$.paths['/api/auth/me'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/me'].get.responses['401']").exists())
                .andExpect(jsonPath("$.components.schemas.MyPageResponse").exists())
                .andExpect(jsonPath(
                        "$.components.schemas.MyPageResponse.properties"
                                + ".preferredRestaurantType"
                ).exists())
                .andExpect(jsonPath("$.components.schemas.MyClubReviewResponse").exists())
                .andExpect(jsonPath(
                        "$.components.schemas.MyClubReviewResponse.properties.profileImageUrl"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.MyPageResponse.properties.likedClubs.type"
                ).value("array"))
                .andExpect(jsonPath(
                        "$.components.schemas.MyPageResponse.properties.likedClubs.items['$ref']"
                ).value("#/components/schemas/MyLikedClubResponse"))
                .andExpect(jsonPath(
                        "$.components.schemas.MyLikedClubResponse.properties.clubId"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.MyLikedClubResponse.properties.clubName"
                ).exists());
    }

    private void toggleLike(long clubId, String accessToken) throws Exception {
        mockMvc.perform(post("/api/clubs/likes/{clubId}", clubId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    private AuthSession signupAndLogin(String loginId) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "loginId", loginId,
                "password", "password"
        ));
        MvcResult signupResult = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        long userId = responseBody(signupResult).path("userId").asLong();

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = responseBody(loginResult).path("accessToken").stringValue();
        return new AuthSession(userId, accessToken);
    }

    private JsonNode responseBody(MvcResult result) throws Exception {
        return objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8)
        );
    }

    private String expiredAccessToken(long userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .subject(Long.toString(userId))
                .claim("role", "USER")
                .issuedAt(now.minusSeconds(7_200))
                .expiresAt(now.minusSeconds(3_600))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256)
                .type("JWT")
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();
    }

    private void cleanDatabase() {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        try {
            jdbcTemplate.execute("TRUNCATE TABLE club_review_selections");
            jdbcTemplate.execute("TRUNCATE TABLE club_reviews");
            jdbcTemplate.execute("TRUNCATE TABLE club_likes");
            jdbcTemplate.execute("TRUNCATE TABLE clubs");
            jdbcTemplate.execute("TRUNCATE TABLE user_accounts");
        } finally {
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }

    private record AuthSession(long userId, String accessToken) {
    }
}

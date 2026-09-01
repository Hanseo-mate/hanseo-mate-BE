package hsu.hanseomate.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hsu.hanseomate.domain.cafeteria.entity.RestaurantType;
import hsu.hanseomate.domain.auth.repository.RefreshTokenRepository;
import hsu.hanseomate.domain.user.entity.UserAccount;
import hsu.hanseomate.domain.user.repository.UserAccountRepository;
import hsu.hanseomate.domain.user.type.UserRole;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthApiIntegrationTest {

    private static final String SIGNUP_PATH = "/api/auth/signup";
    private static final String LOGIN_PATH = "/api/auth/login";
    private static final String REFRESH_PATH = "/api/auth/refresh";
    private static final String LOGOUT_PATH = "/api/auth/logout";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabaseBeforeTest() {
        cleanDatabase();
    }

    @AfterEach
    void cleanDatabaseAfterTest() {
        cleanDatabase();
    }

    @Test
    void signupReturnsBearerJwtAndUserInformationAndHashesPassword() throws Exception {
        MvcResult result = mockMvc.perform(post(SIGNUP_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("NewUser", "plain-password")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.refreshTokenExpiresIn").value(2592000))
                .andExpect(jsonPath("$.userId").isNumber())
                .andExpect(jsonPath("$.loginId").value("newuser"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.preferredRestaurantType")
                        .value("MAIN_STUDENT"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        UserAccount saved = userAccountRepository.findByLoginId("newuser").orElseThrow();
        assertThat(saved.getRole()).isEqualTo(UserRole.USER);
        assertThat(saved.getPreferredRestaurantType())
                .isEqualTo(RestaurantType.MAIN_STUDENT);
        assertThat(saved.getPasswordHash()).isNotEqualTo("plain-password");
        assertThat(passwordEncoder.matches("plain-password", saved.getPasswordHash())).isTrue();

        Jwt jwt = jwtDecoder.decode(responseBody(result).path("accessToken").stringValue());
        assertThat(jwt.getSubject()).isEqualTo(saved.getId().toString());
        assertThat(jwt.getClaimAsString("role")).isEqualTo("USER");

        String rawRefreshToken = responseBody(result).path("refreshToken").stringValue();
        String storedHash = jdbcTemplate.queryForObject(
                "SELECT token_hash FROM refresh_tokens WHERE user_id = ?",
                String.class,
                saved.getId()
        );
        assertThat(storedHash).isEqualTo(sha256(rawRefreshToken));
        assertThat(storedHash).isNotEqualTo(rawRefreshToken);
        assertThat(refreshTokenRepository.countByUserAccountId(saved.getId())).isOne();
    }

    @Test
    void duplicateLoginIdReturnsConflict() throws Exception {
        signup("duplicate", "first-password");

        mockMvc.perform(post(SIGNUP_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("duplicate", "second-password")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("이미 사용 중인 아이디입니다."))
                .andExpect(jsonPath("$.path").value(SIGNUP_PATH));

        assertThat(userAccountRepository.count()).isEqualTo(1);
    }

    @Test
    void blankLoginIdOrPasswordReturnsBadRequest() throws Exception {
        mockMvc.perform(post(SIGNUP_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(" ", "password")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(SIGNUP_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("user", " ")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginReturnsBearerJwtWithUserIdSubject() throws Exception {
        long userId = signup("login-user", "correct-password");

        MvcResult result = login("login-user", "correct-password")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.refreshTokenExpiresIn").value(2592000))
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.loginId").value("login-user"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.preferredRestaurantType")
                        .value("MAIN_STUDENT"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        Jwt jwt = jwtDecoder.decode(responseBody(result).path("accessToken").stringValue());
        assertThat(jwt.getSubject()).isEqualTo(Long.toString(userId));
        assertThat(jwt.getIssuer().toString()).isEqualTo("https://hanseomate.test");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("USER");
    }

    @Test
    void refreshRotatesTokenPairAndKeepsTheOriginalSessionExpiry()
            throws Exception {
        signup("refresh-user", "password");
        JsonNode loginBody = responseBody(login("refresh-user", "password")
                .andExpect(status().isOk())
                .andReturn());
        String firstRefreshToken = loginBody.path("refreshToken").stringValue();

        MvcResult firstRefreshResult = refresh(firstRefreshToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.refreshTokenExpiresIn").isNumber())
                .andReturn();
        JsonNode firstRefreshBody = responseBody(firstRefreshResult);
        String secondRefreshToken = firstRefreshBody.path("refreshToken").stringValue();
        assertThat(secondRefreshToken).isNotEqualTo(firstRefreshToken);
        assertThat(firstRefreshBody.path("refreshTokenExpiresIn").asLong())
                .isPositive()
                .isLessThanOrEqualTo(2592000);
        assertThat(jwtDecoder.decode(
                firstRefreshBody.path("accessToken").stringValue()
        ).getSubject()).isEqualTo(Long.toString(loginBody.path("userId").asLong()));

        MvcResult secondRefreshResult = refresh(secondRefreshToken)
                .andExpect(status().isOk())
                .andReturn();
        assertThat(responseBody(secondRefreshResult).path("refreshToken").stringValue())
                .isNotEqualTo(secondRefreshToken);
    }

    @Test
    void reusingRotatedRefreshTokenRevokesItsWholeSession() throws Exception {
        signup("reuse-user", "password");
        String originalToken = responseBody(login("reuse-user", "password")
                        .andExpect(status().isOk())
                        .andReturn())
                .path("refreshToken")
                .stringValue();
        String replacementToken = responseBody(refresh(originalToken)
                        .andExpect(status().isOk())
                        .andReturn())
                .path("refreshToken")
                .stringValue();

        refresh(originalToken)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message")
                        .value("유효하지 않거나 만료된 Refresh Token입니다."))
                .andExpect(jsonPath("$.path").value(REFRESH_PATH));

        refresh(replacementToken)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidExpiredAndAccessTokensCannotRefresh() throws Exception {
        signup("invalid-refresh-user", "password");
        JsonNode loginBody = responseBody(login("invalid-refresh-user", "password")
                .andExpect(status().isOk())
                .andReturn());
        String refreshToken = loginBody.path("refreshToken").stringValue();

        refresh("not-a-refresh-token")
                .andExpect(status().isUnauthorized());
        refresh(loginBody.path("accessToken").stringValue())
                .andExpect(status().isUnauthorized());

        jdbcTemplate.update(
                "UPDATE refresh_tokens SET expires_at = ? WHERE token_hash = ?",
                "2000-01-01 00:00:00",
                sha256(refreshToken)
        );
        refresh(refreshToken)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutIsIdempotentAndDoesNotRevokeAnotherLoginSession()
            throws Exception {
        MvcResult signupResult = mockMvc.perform(post(SIGNUP_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("logout-user", "password")))
                .andExpect(status().isCreated())
                .andReturn();
        String firstSessionToken = responseBody(signupResult)
                .path("refreshToken")
                .stringValue();
        String secondSessionToken = responseBody(login("logout-user", "password")
                        .andExpect(status().isOk())
                        .andReturn())
                .path("refreshToken")
                .stringValue();

        logout(firstSessionToken).andExpect(status().isNoContent());
        logout(firstSessionToken).andExpect(status().isNoContent());
        refresh(firstSessionToken).andExpect(status().isUnauthorized());
        refresh(secondSessionToken).andExpect(status().isOk());
    }

    @Test
    void refreshAndLogoutValidateRequestAndAppearInOpenApi() throws Exception {
        mockMvc.perform(post(REFRESH_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\" \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(LOGOUT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/refresh'].post").exists())
                .andExpect(jsonPath("$.paths['/api/auth/logout'].post").exists())
                .andExpect(jsonPath(
                        "$.components.schemas.RefreshTokenRequest.properties.refreshToken"
                ).exists());
    }

    @Test
    void adminApiRequiresAdminRoleFromNewlyIssuedToken() throws Exception {
        String adminLinkRequest = objectMapper.writeValueAsString(Map.of(
                "name", "관리자 테스트 링크",
                "url", "https://example.com/admin-test",
                "category", "TEST"
        ));

        mockMvc.perform(post("/api/admin/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminLinkRequest))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(get("/api/admin/clubs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        long userId = signup("role-user", "password");
        MvcResult userLoginResult = login("role-user", "password")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"))
                .andReturn();
        String userToken = responseBody(userLoginResult)
                .path("accessToken")
                .stringValue();

        mockMvc.perform(post("/api/admin/links")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminLinkRequest))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("관리자 권한이 필요합니다."));

        mockMvc.perform(get("/api/admin/clubs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        jdbcTemplate.update(
                "UPDATE user_accounts SET role = 'ADMIN' WHERE id = ?",
                userId
        );

        mockMvc.perform(post("/api/admin/links")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminLinkRequest))
                .andExpect(status().isForbidden());

        MvcResult adminLoginResult = login("role-user", "password")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andReturn();
        String adminToken = responseBody(adminLoginResult)
                .path("accessToken")
                .stringValue();
        Jwt adminJwt = jwtDecoder.decode(adminToken);
        assertThat(adminJwt.getClaimAsString("role")).isEqualTo("ADMIN");

        mockMvc.perform(post("/api/admin/links")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminLinkRequest))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/admin/clubs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void unknownLoginIdAndWrongPasswordReturnSameUnauthorizedResponse() throws Exception {
        signup("login-user", "correct-password");

        login("missing-user", "correct-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message")
                        .value("아이디 또는 비밀번호가 올바르지 않습니다."));

        login("login-user", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message")
                        .value("아이디 또는 비밀번호가 올바르지 않습니다."));
    }

    @Test
    void timetableApiRequiresBearerToken() throws Exception {
        mockMvc.perform(get("/api/timetables")
                        .param("year", "2026")
                        .param("semester", "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."))
                .andExpect(jsonPath("$.path").value("/api/timetables"));

        mockMvc.perform(get("/api/timetables")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                        .param("year", "2026")
                        .param("semester", "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));
    }

    @Test
    void validTokenPassesSecurityAndUsesAuthenticatedUser() throws Exception {
        signup("timetable-user", "password");
        String token = responseBody(login("timetable-user", "password")
                        .andExpect(status().isOk())
                        .andReturn())
                .path("accessToken")
                .stringValue();

        mockMvc.perform(get("/api/timetables")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("year", "2026")
                        .param("semester", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TIMETABLE_NOT_FOUND"));
    }

    @Test
    void APIsOtherThanTimetableRemainPublic() throws Exception {
        mockMvc.perform(get("/api/links"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/clubs"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/notices/categories/admin"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/links")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isOk());
    }

    private long signup(String loginId, String password) throws Exception {
        MvcResult result = mockMvc.perform(post(SIGNUP_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(loginId, password)))
                .andExpect(status().isCreated())
                .andReturn();
        return responseBody(result).path("userId").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions login(
            String loginId,
            String password
    ) throws Exception {
        return mockMvc.perform(post(LOGIN_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(loginId, password)));
    }

    private org.springframework.test.web.servlet.ResultActions refresh(
            String refreshToken
    ) throws Exception {
        return mockMvc.perform(post(REFRESH_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody(refreshToken)));
    }

    private org.springframework.test.web.servlet.ResultActions logout(
            String refreshToken
    ) throws Exception {
        return mockMvc.perform(post(LOGOUT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody(refreshToken)));
    }

    private String requestBody(String loginId, String password) {
        return objectMapper.writeValueAsString(Map.of(
                "loginId", loginId,
                "password", password
        ));
    }

    private String refreshBody(String refreshToken) {
        return objectMapper.writeValueAsString(Map.of(
                "refreshToken", refreshToken
        ));
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    value.getBytes(StandardCharsets.UTF_8)
            ));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private JsonNode responseBody(MvcResult result) throws Exception {
        return objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8)
        );
    }

    private void cleanDatabase() {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        try {
            jdbcTemplate.execute("TRUNCATE TABLE timetable_courses");
            jdbcTemplate.execute("TRUNCATE TABLE timetables");
            jdbcTemplate.execute("TRUNCATE TABLE essential_links");
            jdbcTemplate.execute("TRUNCATE TABLE refresh_tokens");
            jdbcTemplate.execute("TRUNCATE TABLE user_accounts");
        } finally {
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }
}

package hsu.hanseomate.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hsu.hanseomate.domain.cafeteria.entity.RestaurantType;
import hsu.hanseomate.domain.user.entity.UserAccount;
import hsu.hanseomate.domain.user.repository.UserAccountRepository;
import java.nio.charset.StandardCharsets;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CafeteriaPreferenceApiIntegrationTest {

    private static final String SIGNUP_PATH = "/api/auth/signup";
    private static final String LOGIN_PATH = "/api/auth/login";
    private static final String MY_PAGE_PATH = "/api/auth/me";
    private static final String PREFERENCE_PATH =
            "/api/auth/me/cafeteria-preference";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanBeforeTest() {
        cleanDatabase();
    }

    @AfterEach
    void cleanAfterTest() {
        cleanDatabase();
    }

    @Test
    void signupLoginAndMyPageExposeMainStudentAsDefault() throws Exception {
        MvcResult signupResult = signup("preference-default")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.preferredRestaurantType")
                        .value("MAIN_STUDENT"))
                .andReturn();
        JsonNode signupBody = responseBody(signupResult);

        UserAccount saved = userAccountRepository.findById(
                signupBody.path("userId").asLong()
        ).orElseThrow();
        assertThat(saved.getPreferredRestaurantType())
                .isEqualTo(RestaurantType.MAIN_STUDENT);

        login("preference-default")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredRestaurantType")
                        .value("MAIN_STUDENT"));

        mockMvc.perform(get(MY_PAGE_PATH)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                bearer(signupBody.path("accessToken").stringValue())
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredRestaurantType")
                        .value("MAIN_STUDENT"));
    }

    @Test
    void updatesPreferenceAndReturnsPersistedValueAfterLogin() throws Exception {
        JsonNode signupBody = responseBody(
                signup("preference-update")
                        .andExpect(status().isCreated())
                        .andReturn()
        );
        String accessToken = signupBody.path("accessToken").stringValue();
        long userId = signupBody.path("userId").asLong();

        mockMvc.perform(put(PREFERENCE_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(preferenceBody("TAEAN_STUDENT")))
                .andExpect(status().isNoContent());

        assertThat(userAccountRepository.findById(userId)
                .orElseThrow()
                .getPreferredRestaurantType())
                .isEqualTo(RestaurantType.TAEAN_STUDENT);

        mockMvc.perform(get(MY_PAGE_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredRestaurantType")
                        .value("TAEAN_STUDENT"));

        login("preference-update")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredRestaurantType")
                        .value("TAEAN_STUDENT"));
    }

    @Test
    void rejectsStaffMissingAndUnknownRestaurantTypes() throws Exception {
        JsonNode signupBody = responseBody(
                signup("preference-validation")
                        .andExpect(status().isCreated())
                        .andReturn()
        );
        String accessToken = signupBody.path("accessToken").stringValue();

        mockMvc.perform(put(PREFERENCE_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(preferenceBody("MAIN_STAFF")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "preferredRestaurantType은 MAIN_STUDENT 또는 "
                                + "TAEAN_STUDENT만 사용할 수 있습니다."
                ));

        mockMvc.perform(put(PREFERENCE_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put(PREFERENCE_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(preferenceBody("UNKNOWN")))
                .andExpect(status().isBadRequest());

        assertThat(userAccountRepository.findById(
                signupBody.path("userId").asLong()
        ).orElseThrow().getPreferredRestaurantType())
                .isEqualTo(RestaurantType.MAIN_STUDENT);
    }

    @Test
    void requiresValidBearerToken() throws Exception {
        mockMvc.perform(put(PREFERENCE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(preferenceBody("TAEAN_STUDENT")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put(PREFERENCE_PATH)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer invalid-token"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(preferenceBody("TAEAN_STUDENT")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void openApiDocumentsPreferenceEndpointAndAllowedValues()
            throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/auth/me/cafeteria-preference'].put"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/auth/me/cafeteria-preference'].put"
                                + ".responses['204']"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.CafeteriaPreferenceUpdateRequest"
                                + ".properties.preferredRestaurantType.enum"
                ).value(contains("MAIN_STUDENT", "TAEAN_STUDENT")))
                .andExpect(jsonPath(
                        "$.components.schemas.MyPageResponse.properties"
                                + ".preferredRestaurantType.enum"
                ).value(contains("MAIN_STUDENT", "TAEAN_STUDENT")))
                .andExpect(jsonPath(
                        "$.components.schemas.AuthResponse.properties"
                                + ".preferredRestaurantType.enum"
                ).value(contains("MAIN_STUDENT", "TAEAN_STUDENT")));
    }

    private org.springframework.test.web.servlet.ResultActions signup(
            String loginId
    ) throws Exception {
        return mockMvc.perform(post(SIGNUP_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentialsBody(loginId)));
    }

    private org.springframework.test.web.servlet.ResultActions login(
            String loginId
    ) throws Exception {
        return mockMvc.perform(post(LOGIN_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentialsBody(loginId)));
    }

    private String credentialsBody(String loginId) {
        return objectMapper.writeValueAsString(Map.of(
                "loginId",
                loginId,
                "password",
                "password"
        ));
    }

    private String preferenceBody(String preferredRestaurantType) {
        return objectMapper.writeValueAsString(Map.of(
                "preferredRestaurantType",
                preferredRestaurantType
        ));
    }

    private JsonNode responseBody(MvcResult result) throws Exception {
        return objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8)
        );
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private void cleanDatabase() {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        try {
            jdbcTemplate.execute("TRUNCATE TABLE refresh_tokens");
            jdbcTemplate.execute("TRUNCATE TABLE user_accounts");
        } finally {
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }
}

package hsu.hanseomate.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hsu.hanseomate.domain.campusmap.type.CampusCode;
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
class CampusPreferenceApiIntegrationTest {

    private static final String SIGNUP_PATH = "/api/auth/signup";
    private static final String LOGIN_PATH = "/api/auth/login";
    private static final String MY_PAGE_PATH = "/api/auth/me";
    private static final String PREFERENCE_PATH =
            "/api/auth/me/campus-preference";

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
    void signupLoginAndMyPageExposeSeosanAsDefault() throws Exception {
        MvcResult signupResult = signup("preference-default")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.preferredCampusCode")
                        .value("SEOSAN"))
                .andReturn();
        JsonNode signupBody = responseBody(signupResult);

        UserAccount saved = userAccountRepository.findById(
                signupBody.path("userId").asLong()
        ).orElseThrow();
        assertThat(saved.getPreferredCampusCode())
                .isEqualTo(CampusCode.SEOSAN);

        login("preference-default")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredCampusCode")
                        .value("SEOSAN"));

        mockMvc.perform(get(MY_PAGE_PATH)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                bearer(signupBody.path("accessToken").stringValue())
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredCampusCode")
                        .value("SEOSAN"));
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
                        .content(preferenceBody("TAEAN")))
                .andExpect(status().isNoContent());

        assertThat(userAccountRepository.findById(userId)
                .orElseThrow()
                .getPreferredCampusCode())
                .isEqualTo(CampusCode.TAEAN);

        mockMvc.perform(get(MY_PAGE_PATH)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredCampusCode")
                        .value("TAEAN"));

        mockMvc.perform(get("/api/cafeteria/menus")
                        .queryParam("menuDate", "2000-01-01")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredCampusCode")
                        .value("TAEAN"))
                .andExpect(jsonPath("$.restaurants.length()").value(2));

        mockMvc.perform(get("/api/campus-map/places")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedCampusCode").value("TAEAN"));

        mockMvc.perform(get("/api/campus-map/places")
                        .queryParam("campusCode", "SEOSAN")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedCampusCode").value("SEOSAN"));

        mockMvc.perform(get("/api/home")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredCampusCode").value("TAEAN"));

        login("preference-update")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredCampusCode")
                        .value("TAEAN"));
    }

    @Test
    void rejectsLegacyMissingAndUnknownCampusCodes() throws Exception {
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
                .andExpect(status().isBadRequest());

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
        ).orElseThrow().getPreferredCampusCode())
                .isEqualTo(CampusCode.SEOSAN);
    }

    @Test
    void requiresValidBearerToken() throws Exception {
        mockMvc.perform(put(PREFERENCE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(preferenceBody("TAEAN")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put(PREFERENCE_PATH)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer invalid-token"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(preferenceBody("TAEAN")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void openApiDocumentsPreferenceEndpointAndAllowedValues()
            throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/auth/me/campus-preference'].put"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/auth/me/campus-preference'].put"
                                + ".responses['204']"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.CampusPreferenceUpdateRequest"
                                + ".properties.preferredCampusCode.enum"
                ).value(contains("SEOSAN", "TAEAN")))
                .andExpect(jsonPath(
                        "$.components.schemas.MyPageResponse.properties"
                                + ".preferredCampusCode.enum"
                ).value(contains("SEOSAN", "TAEAN")))
                .andExpect(jsonPath(
                        "$.components.schemas.AuthResponse.properties"
                                + ".preferredCampusCode.enum"
                ).value(contains("SEOSAN", "TAEAN")));
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

    private String preferenceBody(String preferredCampusCode) {
        return objectMapper.writeValueAsString(Map.of(
                "preferredCampusCode",
                preferredCampusCode
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

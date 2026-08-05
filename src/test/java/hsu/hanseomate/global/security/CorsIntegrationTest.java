package hsu.hanseomate.global.security;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties =
        "app.cors.allowed-origins=https://frontend.hanseomate.test")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorsIntegrationTest {

    private static final String ALLOWED_ORIGIN =
            "https://frontend.hanseomate.test";
    private static final String DISALLOWED_ORIGIN =
            "https://malicious.example";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void adminApiPreflightAllowsConfiguredOrigin()
            throws Exception {
        mockMvc.perform(options("/api/admin/links")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(
                                HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                HttpMethod.POST.name()
                        )
                        .header(
                                HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                "Authorization, Content-Type"
                        ))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        ALLOWED_ORIGIN
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        containsString(HttpMethod.POST.name())
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        containsString(HttpHeaders.AUTHORIZATION)
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        containsString(HttpHeaders.CONTENT_TYPE)
                ));
    }

    @Test
    void loginApiPreflightAllowsConfiguredOrigin() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(
                                HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                HttpMethod.POST.name()
                        )
                        .header(
                                HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                HttpHeaders.CONTENT_TYPE
                        ))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        ALLOWED_ORIGIN
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        containsString(HttpMethod.POST.name())
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        containsString(HttpHeaders.CONTENT_TYPE)
                ));
    }

    @Test
    void clubReviewApiPreflightIsNotCorsEnabled() throws Exception {
        mockMvc.perform(options("/api/clubs/reviews/1")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(
                                HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                HttpMethod.PUT.name()
                        )
                        .header(
                                HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                "Authorization, Content-Type"
                        ))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
                ));
    }

    @Test
    void signupApiPreflightIsNotCorsEnabled() throws Exception {
        mockMvc.perform(options("/api/auth/signup")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(
                                HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                HttpMethod.POST.name()
                        )
                        .header(
                                HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                HttpHeaders.CONTENT_TYPE
                        ))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
                ));
    }

    @Test
    void loginApiPreflightRejectsOriginThatIsNotConfigured()
            throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, DISALLOWED_ORIGIN)
                        .header(
                                HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                HttpMethod.POST.name()
                        ))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
                ));
    }

    @Test
    void adminApiPreflightRejectsOriginThatIsNotConfigured()
            throws Exception {
        mockMvc.perform(options("/api/admin/links")
                        .header(HttpHeaders.ORIGIN, DISALLOWED_ORIGIN)
                        .header(
                                HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                HttpMethod.POST.name()
                        ))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
                ));
    }

    @Test
    void protectedUserApiPreflightIsNotCorsEnabled() throws Exception {
        mockMvc.perform(options("/api/timetables")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(
                                HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                HttpMethod.GET.name()
                        ))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
                ));
    }
}

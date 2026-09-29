package hsu.hanseomate.domain.home;

import static hsu.hanseomate.support.AdminJwtRequestPostProcessor.adminJwt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hsu.hanseomate.domain.home.repository.HomeMessageRepository;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HomeMessageApiIntegrationTest {

    private static final String PATH = "/api/admin/home-message";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private HomeMessageRepository repository;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        repository.deleteAll();
    }

    @Test
    void adminReadsCreatesReplacesAndClearsOnePersistedMessage() throws Exception {
        mockMvc.perform(get(PATH).with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(""));
        setMessage("  환영합니다!\n좋은 하루 보내세요 😊  ", "환영합니다!\n좋은 하루 보내세요 😊");
        setMessage("가".repeat(500), "가".repeat(500));
        mockMvc.perform(get(PATH).with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("가".repeat(500)));
        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findById(1L).orElseThrow().getMessage()).isEqualTo("가".repeat(500));
        setMessage("", "");
        setMessage(" \n ", "");
        mockMvc.perform(get("/api/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(""));
    }

    @Test
    void rejectsMissingNullAndOversizedMessagesWithoutChangingSavedValue() throws Exception {
        setMessage("기존 문구", "기존 문구");
        for (String body : new String[]{"{}", "{\"message\":null}",
                objectMapper.writeValueAsString(Map.of("message", "가".repeat(501)))}) {
            mockMvc.perform(put(PATH).with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(repository.findById(1L).orElseThrow().getMessage()).isEqualTo("기존 문구");
    }

    @Test
    void onlyAdminCanReadAndSetMessage() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mockMvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"변경\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(PATH).with(jwt()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(PATH).with(jwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"변경\"}"))
                .andExpect(status().isForbidden());
        assertThat(repository.count()).isZero();
    }

    @Test
    void documentsAdminEndpointsAndHomeMessageField() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/admin/home-message'].get").exists())
                .andExpect(jsonPath("$.paths['/api/admin/home-message'].put").exists())
                .andExpect(jsonPath("$.components.schemas.HomePageResponse.properties.message").exists());
    }

    private void setMessage(String input, String expected) throws Exception {
        mockMvc.perform(put(PATH).with(adminJwt()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", input))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(expected));
    }
}

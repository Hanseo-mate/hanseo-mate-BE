package hsu.hanseomate.domain.busschedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import hsu.hanseomate.domain.busschedule.repository.BusScheduleRepository;
import hsu.hanseomate.support.AdminMockMvcConfiguration;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Comparator;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AdminMockMvcConfiguration.class)
class BusScheduleApiIntegrationTest {

    private static final Path TEST_BUS_DIRECTORY = Path.of(
            "build",
            "test-uploads",
            "bus"
    ).toAbsolutePath().normalize();

    private static final byte[] TINY_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUB"
                    + "AScY42YAAAAASUVORK5CYII="
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BusScheduleRepository busScheduleRepository;

    @BeforeEach
    void cleanUp() throws Exception {
        busScheduleRepository.deleteAll();
        deleteBusFiles();
    }

    @Test
    void storesAndServesUploadedImageThroughPublicUploadsPath() throws Exception {
        MvcResult result = uploadSchedule("schedule.png");
        String imageUrl = JsonPath.read(responseBody(result), "$.imageUrl");

        assertThat(imageUrl).startsWith("http://localhost/uploads/bus/");
        assertThat(busScheduleRepository.count()).isOne();
        assertThat(busScheduleRepository.findAll().get(0).getServerFilePath())
                .startsWith(TEST_BUS_DIRECTORY.toString());

        mockMvc.perform(get(URI.create(imageUrl).getPath()).with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(TINY_PNG));

        mockMvc.perform(get("/api/bus-schedules").with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].mainCategory").value("CITY_BUS"))
                .andExpect(jsonPath("$[0].subCategory").value("HANSEO_TO_SEOSAN"))
                .andExpect(jsonPath("$[0].imageUrl").value(imageUrl));
    }

    @Test
    void replacementKeepsOneRecordAndDeletesPreviousManagedImageAfterCommit()
            throws Exception {
        MvcResult first = uploadSchedule("first.png");
        Number firstId = JsonPath.read(responseBody(first), "$.id");
        String previousImageUrl = JsonPath.read(responseBody(first), "$.imageUrl");

        MvcResult replacement = uploadSchedule("replacement.png");
        Number replacementId = JsonPath.read(responseBody(replacement), "$.id");
        String replacementImageUrl = JsonPath.read(
                responseBody(replacement),
                "$.imageUrl"
        );

        assertThat(replacementId.longValue()).isEqualTo(firstId.longValue());
        assertThat(replacementImageUrl).isNotEqualTo(previousImageUrl);
        assertThat(busScheduleRepository.count()).isOne();

        mockMvc.perform(get(URI.create(previousImageUrl).getPath()).with(anonymous()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(URI.create(replacementImageUrl).getPath()).with(anonymous()))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsFakeImageWithoutCreatingRecordOrLeavingFile() throws Exception {
        MockMultipartFile fakeImage = new MockMultipartFile(
                "image",
                "fake.png",
                MediaType.IMAGE_PNG_VALUE,
                "not-an-image".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/admin/bus-schedules")
                        .file(fakeImage)
                        .param("mainCategory", "CITY_BUS")
                        .param("subCategory", "HANSEO_TO_SEOSAN"))
                .andExpect(status().isBadRequest());

        assertThat(busScheduleRepository.count()).isZero();
        assertThat(countBusFiles()).isZero();
    }

    private MvcResult uploadSchedule(String originalFileName) throws Exception {
        return mockMvc.perform(multipart("/api/admin/bus-schedules")
                        .file(pngFile(originalFileName))
                        .param("mainCategory", "CITY_BUS")
                        .param("subCategory", "HANSEO_TO_SEOSAN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.mainCategory").value("CITY_BUS"))
                .andExpect(jsonPath("$.subCategory").value("HANSEO_TO_SEOSAN"))
                .andExpect(jsonPath("$.imageUrl").isString())
                .andReturn();
    }

    private MockMultipartFile pngFile(String originalFileName) {
        return new MockMultipartFile(
                "image",
                originalFileName,
                MediaType.IMAGE_PNG_VALUE,
                TINY_PNG
        );
    }

    private String responseBody(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private long countBusFiles() throws Exception {
        if (!Files.exists(TEST_BUS_DIRECTORY)) {
            return 0L;
        }
        try (Stream<Path> paths = Files.list(TEST_BUS_DIRECTORY)) {
            return paths.filter(Files::isRegularFile).count();
        }
    }

    private void deleteBusFiles() throws Exception {
        if (!Files.exists(TEST_BUS_DIRECTORY)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(TEST_BUS_DIRECTORY)) {
            paths.sorted(Comparator.reverseOrder())
                    .filter(path -> !path.equals(TEST_BUS_DIRECTORY))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception exception) {
                            throw new IllegalStateException(exception);
                        }
                    });
        }
    }
}

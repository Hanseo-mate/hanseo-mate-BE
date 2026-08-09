package hsu.hanseomate.domain.homeposter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import hsu.hanseomate.domain.homeposter.repository.HomePosterRepository;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AdminMockMvcConfiguration.class)
class HomePosterApiIntegrationTest {

    private static final Path TEST_POSTER_DIRECTORY = Path.of(
            "build",
            "test-uploads",
            "home-posters"
    ).toAbsolutePath().normalize();

    private static final byte[] TINY_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUB"
                    + "AScY42YAAAAASUVORK5CYII="
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HomePosterRepository homePosterRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() throws Exception {
        homePosterRepository.deleteAll();
        deletePosterFiles();
    }

    @Test
    void jpaCreatesExactlyRequiredColumns() {
        var columns = jdbcTemplate.queryForList(
                """
                        SELECT column_name
                        FROM information_schema.columns
                        WHERE LOWER(table_name) = 'home_posters'
                        ORDER BY ordinal_position
                        """,
                String.class
        );

        assertThat(columns).containsExactlyInAnyOrder(
                "id",
                "image_url",
                "created_at",
                "updated_at"
        );
    }

    @Test
    void createsAnyNumberOfPostersAndReturnsThemInRegistrationOrder() throws Exception {
        MvcResult first = createPoster("first.png");
        MvcResult second = createPoster("second.png");
        MvcResult third = createPoster("third.png");

        long firstId = responseId(first);
        long secondId = responseId(second);
        long thirdId = responseId(third);
        assertThat(firstId).isLessThan(secondId);
        assertThat(secondId).isLessThan(thirdId);
        assertThat(homePosterRepository.count()).isEqualTo(3);

        mockMvc.perform(get("/api/admin/home-posters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(firstId))
                .andExpect(jsonPath("$[1].id").value(secondId))
                .andExpect(jsonPath("$[2].id").value(thirdId));
    }

    @Test
    void returnsEmptyArrayWhenNoPosterExists() throws Exception {
        mockMvc.perform(get("/api/admin/home-posters"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void storesAndServesUploadedPosterImage() throws Exception {
        MvcResult result = createPoster("poster.png");
        long posterId = responseId(result);
        String imageUrl = JsonPath.read(responseBody(result), "$.imageUrl");

        assertThat(result.getResponse().getHeader("Location"))
                .isEqualTo("/api/admin/home-posters/" + posterId);
        assertThat(imageUrl).startsWith("http://localhost/uploads/home-posters/");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT image_url FROM home_posters WHERE id = ?",
                String.class,
                posterId
        )).isEqualTo(imageUrl);

        mockMvc.perform(get(URI.create(imageUrl).getPath()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(TINY_PNG));
    }

    @Test
    void replacesOnlySelectedPosterAndDeletesPreviousManagedFile() throws Exception {
        MvcResult firstPoster = createPoster("first.png");
        MvcResult untouchedPoster = createPoster("untouched.png");
        long firstPosterId = responseId(firstPoster);
        long untouchedPosterId = responseId(untouchedPoster);
        String previousImageUrl = JsonPath.read(responseBody(firstPoster), "$.imageUrl");
        String untouchedImageUrl = JsonPath.read(responseBody(untouchedPoster), "$.imageUrl");

        MvcResult replaced = mockMvc.perform(
                        multipart("/api/admin/home-posters/{posterId}", firstPosterId)
                                .file(pngFile("replacement.png"))
                                .with(request -> {
                                    request.setMethod("PUT");
                                    return request;
                                })
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(4)))
                .andExpect(jsonPath("$.id").value(firstPosterId))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andReturn();
        String replacementUrl = JsonPath.read(responseBody(replaced), "$.imageUrl");

        assertThat(replacementUrl).isNotEqualTo(previousImageUrl);
        assertThat(homePosterRepository.count()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT image_url FROM home_posters WHERE id = ?",
                String.class,
                untouchedPosterId
        )).isEqualTo(untouchedImageUrl);

        mockMvc.perform(get(URI.create(previousImageUrl).getPath()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(URI.create(replacementUrl).getPath()))
                .andExpect(status().isOk());
        mockMvc.perform(get(URI.create(untouchedImageUrl).getPath()))
                .andExpect(status().isOk());
    }

    @Test
    void invalidReplacementKeepsExistingPosterAndFile() throws Exception {
        MvcResult created = createPoster("poster.png");
        long posterId = responseId(created);
        String previousImageUrl = JsonPath.read(responseBody(created), "$.imageUrl");
        MockMultipartFile invalidImage = new MockMultipartFile(
                "file",
                "fake.png",
                MediaType.IMAGE_PNG_VALUE,
                "not-an-image".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/admin/home-posters/{posterId}", posterId)
                        .file(invalidImage)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        }))
                .andExpect(status().isBadRequest());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT image_url FROM home_posters WHERE id = ?",
                String.class,
                posterId
        )).isEqualTo(previousImageUrl);
        mockMvc.perform(get(URI.create(previousImageUrl).getPath()))
                .andExpect(status().isOk());
    }

    @Test
    void deletesSelectedPosterAndManagedFile() throws Exception {
        MvcResult deletedPoster = createPoster("deleted.png");
        MvcResult keptPoster = createPoster("kept.png");
        long deletedPosterId = responseId(deletedPoster);
        long keptPosterId = responseId(keptPoster);
        String deletedImageUrl = JsonPath.read(responseBody(deletedPoster), "$.imageUrl");
        String keptImageUrl = JsonPath.read(responseBody(keptPoster), "$.imageUrl");

        mockMvc.perform(delete("/api/admin/home-posters/{posterId}", deletedPosterId))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(homePosterRepository.existsById(deletedPosterId)).isFalse();
        assertThat(homePosterRepository.existsById(keptPosterId)).isTrue();
        mockMvc.perform(get(URI.create(deletedImageUrl).getPath()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(URI.create(keptImageUrl).getPath()))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsMissingOrInvalidImageAndInvalidPosterId() throws Exception {
        mockMvc.perform(multipart("/api/admin/home-posters"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").doesNotExist());

        MockMultipartFile invalidImage = new MockMultipartFile(
                "file",
                "fake.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "not-an-image".getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/admin/home-posters").file(invalidImage))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/admin/home-posters/{posterId}", 0L))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsNotFoundForMissingPosterReplacementAndDeletion() throws Exception {
        mockMvc.perform(multipart("/api/admin/home-posters/{posterId}", 999999L)
                        .file(pngFile("poster.png"))
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        }))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        mockMvc.perform(delete("/api/admin/home-posters/{posterId}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void requiresAdminRoleForEveryPosterManagementApi() throws Exception {
        long posterId = responseId(createPoster("poster.png"));

        mockMvc.perform(get("/api/admin/home-posters").with(anonymous()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/admin/home-posters")
                        .file(pngFile("anonymous.png"))
                        .with(anonymous()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/admin/home-posters/{posterId}", posterId)
                        .file(pngFile("anonymous.png"))
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .with(anonymous()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/admin/home-posters/{posterId}", posterId)
                        .with(anonymous()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/home-posters").with(userJwt()))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/admin/home-posters")
                        .file(pngFile("user.png"))
                        .with(userJwt()))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/admin/home-posters/{posterId}", posterId)
                        .file(pngFile("user.png"))
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .with(userJwt()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/home-posters/{posterId}", posterId)
                        .with(userJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsConfiguredAdminCorsPreflightForPosterApi() throws Exception {
        mockMvc.perform(options("/api/admin/home-posters")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(
                                HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                "Authorization, Content-Type"
                        ))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "http://localhost:3000"
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        org.hamcrest.Matchers.containsString("POST")
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        org.hamcrest.Matchers.containsStringIgnoringCase("authorization")
                ));
    }

    @Test
    void exposesOnlyAdminPosterManagementEndpointsInOpenApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/admin/home-posters'].post.responses['201']"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/admin/home-posters'].post.requestBody"
                                + ".content['multipart/form-data']"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/admin/home-posters'].get.responses['200']"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/admin/home-posters/{posterId}'].put.responses['200']"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/admin/home-posters/{posterId}'].put.requestBody"
                                + ".content['multipart/form-data']"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/admin/home-posters/{posterId}'].delete.responses['204']"
                ).exists())
                .andExpect(jsonPath("$.components.schemas.HomePosterResponse.properties",
                        aMapWithSize(4)))
                .andExpect(jsonPath("$.components.schemas.HomePosterResponse.properties.id")
                        .exists())
                .andExpect(jsonPath(
                        "$.components.schemas.HomePosterResponse.properties.imageUrl"
                ).exists())
                .andExpect(jsonPath("$.paths['/api/home-posters']").doesNotExist());
    }

    private MvcResult createPoster(String originalFileName) throws Exception {
        return mockMvc.perform(multipart("/api/admin/home-posters")
                        .file(pngFile(originalFileName)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$", aMapWithSize(4)))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.imageUrl").isString())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andReturn();
    }

    private MockMultipartFile pngFile(String originalFileName) {
        return new MockMultipartFile(
                "file",
                originalFileName,
                MediaType.IMAGE_PNG_VALUE,
                TINY_PNG
        );
    }

    private RequestPostProcessor userJwt() {
        return jwt()
                .jwt(jwt -> jwt
                        .subject("1")
                        .claim("role", "USER"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private long responseId(MvcResult result) throws Exception {
        Number id = JsonPath.read(responseBody(result), "$.id");
        return id.longValue();
    }

    private String responseBody(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private void deletePosterFiles() throws Exception {
        if (!Files.exists(TEST_POSTER_DIRECTORY)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(TEST_POSTER_DIRECTORY)) {
            paths.sorted(Comparator.reverseOrder())
                    .filter(path -> !path.equals(TEST_POSTER_DIRECTORY))
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

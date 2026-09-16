package hsu.hanseomate.domain.popup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import hsu.hanseomate.domain.popup.entity.AppPopup;
import hsu.hanseomate.domain.popup.repository.AppPopupRepository;
import hsu.hanseomate.support.AdminMockMvcConfiguration;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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
@Import({
        AdminMockMvcConfiguration.class,
        AppPopupApiIntegrationTest.FixedClockConfiguration.class
})
class AppPopupApiIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(
            2026,
            9,
            3,
            12,
            0
    );
    private static final Path TEST_POPUP_DIRECTORY = Path.of(
            "build",
            "test-uploads",
            "app-popups"
    ).toAbsolutePath().normalize();
    private static final byte[] TINY_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUB"
                    + "AScY42YAAAAASUVORK5CYII="
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppPopupRepository appPopupRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() throws Exception {
        cleanUp();
    }

    @AfterEach
    void tearDown() throws Exception {
        cleanUp();
    }

    @Test
    void jpaCreatesRequiredColumnsAndIndexes() {
        List<String> columns = jdbcTemplate.queryForList(
                """
                        SELECT column_name
                        FROM information_schema.columns
                        WHERE LOWER(table_name) = 'app_popups'
                        ORDER BY ordinal_position
                        """,
                String.class
        );

        assertThat(columns).containsExactlyInAnyOrder(
                "id",
                "title",
                "content",
                "image_url",
                "link_url",
                "navigation_schema_version",
                "navigation_type",
                "navigation_params",
                "enabled",
                "starts_at",
                "ends_at",
                "display_order",
                "revision",
                "created_at",
                "updated_at"
        );

        List<String> indexes = jdbcTemplate.queryForList(
                        """
                                SELECT index_name
                                FROM information_schema.indexes
                                WHERE LOWER(table_name) = 'app_popups'
                                """,
                        String.class
                ).stream()
                .map(String::toLowerCase)
                .toList();
        assertThat(indexes).contains(
                "idx_app_popups_exposure",
                "idx_app_popups_created_at"
        );
    }

    @Test
    void publicApiReturnsOnlyActivePopupsInDisplayOrderWithoutLogin() throws Exception {
        AppPopup later = savePopup(
                "두 번째",
                true,
                NOW.minusHours(1),
                NOW.plusHours(1),
                20
        );
        AppPopup first = savePopup(
                "첫 번째",
                true,
                null,
                null,
                10
        );
        savePopup("예정", true, NOW.plusSeconds(1), null, 0);
        savePopup("종료", true, null, NOW, 0);
        savePopup("비활성", false, null, null, 0);

        mockMvc.perform(get("/api/popups/active").with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")
                ))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(first.getId()))
                .andExpect(jsonPath("$[0].title").value("첫 번째"))
                .andExpect(jsonPath("$[0].revision").value(1))
                .andExpect(jsonPath("$[1].id").value(later.getId()))
                .andExpect(jsonPath("$[0].enabled").doesNotExist())
                .andExpect(jsonPath("$[0].status").doesNotExist());

        mockMvc.perform(get("/api/popups/active").with(userJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/popups/active")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                        .with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void publicApiReturnsEmptyArrayWhenNoPopupIsActive() throws Exception {
        mockMvc.perform(get("/api/popups/active").with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void adminCreatesTextPopupAndNormalizesExternalNavigation() throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/admin/popups")
                        .file(requestPart(createRequestJson(
                                "  새 팝업  ",
                                "팝업 내용\n두 번째 줄",
                                externalNavigationJson(
                                        "  https://www.hanseo.ac.kr/notice/1  "
                                ),
                                true,
                                null,
                                null,
                                5
                        ))))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.title").value("새 팝업"))
                .andExpect(jsonPath("$.content").value("팝업 내용\n두 번째 줄"))
                .andExpect(jsonPath("$.imageUrl").value(nullValue()))
                .andExpect(jsonPath("$.navigation.schemaVersion").value(1))
                .andExpect(jsonPath("$.navigation.type").value("EXTERNAL_URL"))
                .andExpect(jsonPath("$.navigation.params.url")
                        .value("https://www.hanseo.ac.kr/notice/1"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.revision").value(1))
                .andReturn();

        long popupId = responseId(result);
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
                .isEqualTo("/api/admin/popups/" + popupId);
        assertThat(appPopupRepository.findById(popupId).orElseThrow().getImageUrl())
                .isNull();
    }

    @Test
    void adminCreatesAndServesPopupImage() throws Exception {
        MvcResult result = createPopupWithImage("이미지 팝업", "first.png");
        String imageUrl = JsonPath.read(responseBody(result), "$.imageUrl");

        assertThat(imageUrl).startsWith("http://localhost/uploads/app-popups/");
        assertThat(storedFile(imageUrl)).exists();
        mockMvc.perform(get(URI.create(imageUrl).getPath()).with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(TINY_PNG));
    }

    @Test
    void optionalLinkUrlIsReturnedByPublicAndAdminApisAndCanBeCleared() throws Exception {
        MvcResult created = mockMvc.perform(multipart("/api/admin/popups")
                        .file(requestPart(createRequestJson(
                                "링크 팝업",
                                "사진을 누르면 이동합니다.",
                                "  https://www.hanseo.ac.kr/notice/1  ",
                                null,
                                true,
                                null,
                                null,
                                0
                        )))
                        .file(imagePart("linked.png")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageUrl").isString())
                .andExpect(jsonPath("$.linkUrl")
                        .value("https://www.hanseo.ac.kr/notice/1"))
                .andExpect(jsonPath("$.navigation").value(nullValue()))
                .andReturn();
        long popupId = responseId(created);

        mockMvc.perform(get("/api/popups/active").with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].linkUrl")
                        .value("https://www.hanseo.ac.kr/notice/1"));
        mockMvc.perform(get("/api/admin/popups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].linkUrl")
                        .value("https://www.hanseo.ac.kr/notice/1"));
        mockMvc.perform(get("/api/admin/popups/{popupId}", popupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkUrl")
                        .value("https://www.hanseo.ac.kr/notice/1"));

        mockMvc.perform(multipart("/api/admin/popups/{popupId}", popupId)
                        .file(requestPart(updateRequestJson(
                                "링크 제거",
                                "내용",
                                true,
                                0,
                                "KEEP",
                                null,
                                "   "
                        )))
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkUrl").value(nullValue()))
                .andExpect(jsonPath("$.revision").value(2));
    }

    @Test
    void navigationIsReturnedByPublicAndEveryAdminSuccessResponse() throws Exception {
        String noticeNavigation = """
                {
                  "schemaVersion": 1,
                  "type": "NOTICE_DETAIL",
                  "params": {
                    "noticeId": 123,
                    "noticeType": "ACADEMIC"
                  }
                }
                """;
        MvcResult created = mockMvc.perform(multipart("/api/admin/popups")
                        .file(requestPart(createRequestJson(
                                "공지 이동",
                                "내용",
                                noticeNavigation,
                                true,
                                null,
                                null,
                                0
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.navigation.schemaVersion").value(1))
                .andExpect(jsonPath("$.navigation.type").value("NOTICE_DETAIL"))
                .andExpect(jsonPath("$.navigation.params.noticeId").value(123))
                .andExpect(jsonPath("$.navigation.params.noticeType").value("ACADEMIC"))
                .andReturn();
        long popupId = responseId(created);

        mockMvc.perform(get("/api/popups/active").with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].navigation.type").value("NOTICE_DETAIL"));
        mockMvc.perform(get("/api/admin/popups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].navigation.type").value("NOTICE_DETAIL"));
        mockMvc.perform(get("/api/admin/popups/{popupId}", popupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.navigation.type").value("NOTICE_DETAIL"));

        String clubNavigation = """
                {
                  "schemaVersion": 1,
                  "type": "CLUB_DETAIL",
                  "params": {
                    "clubId": 45
                  }
                }
                """;
        mockMvc.perform(multipart("/api/admin/popups/{popupId}", popupId)
                        .file(requestPart(updateRequestJson(
                                "동아리 이동",
                                "수정 내용",
                                true,
                                0,
                                "KEEP",
                                clubNavigation
                        )))
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.navigation.type").value("CLUB_DETAIL"))
                .andExpect(jsonPath("$.navigation.params.clubId").value(45))
                .andExpect(jsonPath("$.revision").value(2));

        mockMvc.perform(patch("/api/admin/popups/{popupId}/enabled", popupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.navigation.type").value("CLUB_DETAIL"))
                .andExpect(jsonPath("$.revision").value(3));
    }

    @Test
    void supportsNullAndStaticNavigationWithoutParams() throws Exception {
        mockMvc.perform(multipart("/api/admin/popups")
                        .file(requestPart(defaultCreateRequestJson("이동 없음"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.navigation").value(nullValue()));

        String homeNavigation = """
                {
                  "schemaVersion": 1,
                  "type": "HOME"
                }
                """;
        mockMvc.perform(multipart("/api/admin/popups")
                        .file(requestPart(createRequestJson(
                                "홈 이동",
                                "내용",
                                homeNavigation,
                                true,
                                null,
                                null,
                                0
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.navigation.schemaVersion").value(1))
                .andExpect(jsonPath("$.navigation.type").value("HOME"))
                .andExpect(jsonPath("$.navigation.params").doesNotExist());
    }

    @Test
    void rejectsMissingOrInvalidNavigationContracts() throws Exception {
        String missingNavigation = """
                {
                  "title": "이동 필드 누락",
                  "content": "내용",
                  "enabled": true,
                  "startsAt": null,
                  "endsAt": null,
                  "displayOrder": 0
                }
                """;
        mockMvc.perform(multipart("/api/admin/popups")
                        .file(requestPart(missingNavigation)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("navigation: 필수 필드")));

        assertNavigationRejected(
                "{\"schemaVersion\":2,\"type\":\"HOME\"}",
                "navigation.schemaVersion"
        );
        assertNavigationRejected(
                "{\"schemaVersion\":1,\"type\":\"UNKNOWN\"}",
                "navigation.type"
        );
        assertNavigationRejected(
                "{\"schemaVersion\":1,\"type\":\"HOME\",\"params\":{}}",
                "navigation.params"
        );
        assertNavigationRejected(
                "{\"schemaVersion\":1,\"type\":\"NOTICE_DETAIL\","
                        + "\"params\":{\"noticeId\":1}}",
                "navigation.params"
        );
        assertNavigationRejected(
                "{\"schemaVersion\":1,\"type\":\"NOTICE_DETAIL\","
                        + "\"params\":{\"noticeId\":1,\"noticeType\":\"GENERAL\","
                        + "\"route\":\"/notices/1\"}}",
                "navigation.params"
        );
        assertNavigationRejected(
                "{\"schemaVersion\":1,\"type\":\"NOTICE_DETAIL\","
                        + "\"params\":{\"noticeId\":1,\"noticeType\":\"INVALID\"}}",
                "navigation.params.noticeType"
        );
        assertNavigationRejected(
                "{\"schemaVersion\":1,\"type\":\"CLUB_DETAIL\","
                        + "\"params\":{\"clubId\":1.5}}",
                "navigation.params.clubId"
        );
        assertNavigationRejected(externalNavigationJson("http://example.com"),
                "navigation.params.url");
        assertNavigationRejected(externalNavigationJson("https://user:password@example.com"),
                "navigation.params.url");
        assertNavigationRejected(externalNavigationJson("javascript:alert(1)"),
                "navigation.params.url");

    }

    @Test
    void adminListAndDetailExposeDerivedStatuses() throws Exception {
        AppPopup active = savePopup("노출 중", true, null, null, 0);
        AppPopup scheduled = savePopup("예정", true, NOW.plusMinutes(1), null, 0);
        AppPopup expired = savePopup("종료", true, null, NOW, 0);
        AppPopup inactive = savePopup("비활성", false, null, null, 0);

        mockMvc.perform(get("/api/admin/popups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));
        assertAdminStatus(active.getId(), "ACTIVE");
        assertAdminStatus(scheduled.getId(), "SCHEDULED");
        assertAdminStatus(expired.getId(), "EXPIRED");
        assertAdminStatus(inactive.getId(), "INACTIVE");
    }

    @Test
    void adminCanKeepReplaceAndRemoveImageWithoutLeavingOrphans() throws Exception {
        MvcResult created = createPopupWithImage("원본", "original.png");
        long popupId = responseId(created);
        String originalUrl = JsonPath.read(responseBody(created), "$.imageUrl");
        Path originalFile = storedFile(originalUrl);

        mockMvc.perform(multipart("/api/admin/popups/{popupId}", popupId)
                        .file(requestPart(updateRequestJson(
                                "글만 수정",
                                "수정 내용",
                                true,
                                5,
                                "KEEP"
                        )))
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value(originalUrl))
                .andExpect(jsonPath("$.revision").value(2));
        assertThat(originalFile).exists();

        MvcResult replaced = mockMvc.perform(
                        multipart("/api/admin/popups/{popupId}", popupId)
                                .file(requestPart(updateRequestJson(
                                        "이미지 교체",
                                        "수정 내용",
                                        true,
                                        5,
                                        "REPLACE"
                                )))
                                .file(imagePart("replacement.png"))
                                .with(request -> {
                                    request.setMethod("PUT");
                                    return request;
                                })
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(3))
                .andReturn();
        String replacementUrl = JsonPath.read(responseBody(replaced), "$.imageUrl");
        Path replacementFile = storedFile(replacementUrl);
        assertThat(replacementUrl).isNotEqualTo(originalUrl);
        assertThat(originalFile).doesNotExist();
        assertThat(replacementFile).exists();

        mockMvc.perform(multipart("/api/admin/popups/{popupId}", popupId)
                        .file(requestPart(updateRequestJson(
                                "이미지 제거",
                                "수정 내용",
                                true,
                                5,
                                "REMOVE"
                        )))
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value(nullValue()))
                .andExpect(jsonPath("$.revision").value(4));
        assertThat(replacementFile).doesNotExist();
    }

    @Test
    void adminCanEnablePopupAndRevisionChangesOnlyWhenValueChanges() throws Exception {
        AppPopup popup = savePopup("비활성", false, null, null, 0);

        mockMvc.perform(patch("/api/admin/popups/{popupId}/enabled", popup.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.revision").value(2));

        mockMvc.perform(patch("/api/admin/popups/{popupId}/enabled", popup.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(2));

        mockMvc.perform(get("/api/popups/active").with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(popup.getId()));
    }

    @Test
    void adminDeleteRemovesPopupAndManagedImage() throws Exception {
        MvcResult created = createPopupWithImage("삭제", "delete.png");
        long popupId = responseId(created);
        String imageUrl = JsonPath.read(responseBody(created), "$.imageUrl");
        Path imageFile = storedFile(imageUrl);

        mockMvc.perform(delete("/api/admin/popups/{popupId}", popupId))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(appPopupRepository.findById(popupId)).isEmpty();
        assertThat(imageFile).doesNotExist();
    }

    @Test
    void rejectsInvalidScheduleLinkImageAndUpdateCombination() throws Exception {
        mockMvc.perform(multipart("/api/admin/popups")
                        .file(requestPart(createRequestJson(
                                "잘못된 기간",
                                "내용",
                                null,
                                true,
                                "2026-09-04T10:00:00",
                                "2026-09-04T10:00:00",
                                0
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("노출 종료 시각은 시작 시각보다 뒤여야 합니다."));

        mockMvc.perform(multipart("/api/admin/popups")
                        .file(requestPart(createRequestJson(
                                "링크 오류",
                                "내용",
                                externalNavigationJson("javascript:alert(1)"),
                                true,
                                null,
                                null,
                                0
                        ))))
                .andExpect(status().isBadRequest());

        assertLinkUrlRejected("http://example.com");
        assertLinkUrlRejected("https://user:password@example.com");
        assertLinkUrlRejected("javascript:alert(1)");

        AppPopup popup = savePopup("수정 대상", true, null, null, 0);
        mockMvc.perform(multipart("/api/admin/popups/{popupId}", popup.getId())
                        .file(requestPart(updateRequestJson(
                                "교체",
                                "내용",
                                true,
                                0,
                                "REPLACE"
                        )))
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        }))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("교체할 이미지 파일이 필요합니다."));

        mockMvc.perform(multipart("/api/admin/popups/{popupId}", popup.getId())
                        .file(requestPart(updateRequestJson(
                                "유지",
                                "내용",
                                true,
                                0,
                                "KEEP"
                        )))
                        .file(imagePart("unexpected.png"))
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        }))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "이미지를 업로드하려면 imageAction을 REPLACE로 지정해야 합니다."
                ));

        MockMultipartFile invalidImage = new MockMultipartFile(
                "image",
                "fake.png",
                MediaType.IMAGE_PNG_VALUE,
                "not-an-image".getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/admin/popups")
                        .file(requestPart(createRequestJson(
                                "이미지 오류",
                                "내용",
                                null,
                                true,
                                null,
                                null,
                                0
                        )))
                        .file(invalidImage))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("올바른 이미지 파일이 아닙니다."));
    }

    @Test
    void returnsBadRequestAndNotFoundForInvalidPopupIds() throws Exception {
        mockMvc.perform(get("/api/admin/popups/{popupId}", 0L))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/admin/popups/{popupId}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("앱 팝업을 찾을 수 없습니다. popupId=999999"));
        mockMvc.perform(delete("/api/admin/popups/{popupId}", 999999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void everyAdminPopupApiRequiresAdminRole() throws Exception {
        AppPopup popup = savePopup("권한", false, null, null, 0);

        mockMvc.perform(get("/api/admin/popups").with(anonymous()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/popups/{popupId}", popup.getId())
                        .with(anonymous()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/admin/popups")
                        .file(requestPart(defaultCreateRequestJson("등록")))
                        .with(anonymous()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/admin/popups/{popupId}", popup.getId())
                        .file(requestPart(updateRequestJson(
                                "수정",
                                "내용",
                                true,
                                0,
                                "KEEP"
                        )))
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .with(anonymous()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/admin/popups/{popupId}/enabled", popup.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}")
                        .with(anonymous()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/admin/popups/{popupId}", popup.getId())
                        .with(anonymous()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/popups").with(userJwt()))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/admin/popups")
                        .file(requestPart(defaultCreateRequestJson("등록")))
                        .with(userJwt()))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/admin/popups/{popupId}/enabled", popup.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}")
                        .with(userJwt()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/popups/{popupId}", popup.getId())
                        .with(userJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void corsAllowsPublicReadAndAdminManagementPreflights() throws Exception {
        mockMvc.perform(options("/api/popups/active")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "http://localhost:3000"
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        containsString("GET")
                ));

        mockMvc.perform(options("/api/admin/popups")
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
                        containsString("POST")
                ));
    }

    @Test
    void openApiExposesPublicAndAdminPopupContracts() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/popups/active'].get.responses['200']"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/admin/popups'].get.responses['200']"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/admin/popups'].post.responses['201']"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/admin/popups/{popupId}'].get.responses['200']"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/admin/popups/{popupId}'].put.responses['200']"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/admin/popups/{popupId}'].delete.responses['204']"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/admin/popups/{popupId}/enabled']"
                                + ".patch.responses['200']"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.ActiveAppPopupResponse.properties.revision"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.ActiveAppPopupResponse.properties.navigation"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.AppPopupUpdateRequest.properties.imageAction"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.AppPopupCreateRequest.properties.navigation"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.AppPopupCreateRequest.required"
                ).value(hasItem("navigation")))
                .andExpect(jsonPath(
                        "$.components.schemas.AppPopupCreateRequest.properties.linkUrl"
                ).exists())
                .andExpect(jsonPath(
                        "$.components.schemas.ActiveAppPopupResponse.properties.linkUrl"
                ).exists());
    }

    private AppPopup savePopup(
            String title,
            boolean enabled,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            int displayOrder
    ) {
        return appPopupRepository.saveAndFlush(AppPopup.create(
                title,
                "내용",
                null,
                null,
                null,
                enabled,
                startsAt,
                endsAt,
                displayOrder
        ));
    }

    private void assertAdminStatus(Long popupId, String expectedStatus) throws Exception {
        mockMvc.perform(get("/api/admin/popups/{popupId}", popupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(expectedStatus));
    }

    private MvcResult createPopupWithImage(
            String title,
            String originalFileName
    ) throws Exception {
        return mockMvc.perform(multipart("/api/admin/popups")
                        .file(requestPart(defaultCreateRequestJson(title)))
                        .file(imagePart(originalFileName)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.imageUrl").isString())
                .andReturn();
    }

    private MockMultipartFile requestPart(String json) {
        return new MockMultipartFile(
                "request",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                json.getBytes(StandardCharsets.UTF_8)
        );
    }

    private MockMultipartFile imagePart(String originalFileName) {
        return new MockMultipartFile(
                "image",
                originalFileName,
                MediaType.IMAGE_PNG_VALUE,
                TINY_PNG
        );
    }

    private String defaultCreateRequestJson(String title) {
        return createRequestJson(title, "내용", null, true, null, null, 0);
    }

    private String createRequestJson(
            String title,
            String content,
            String navigationJson,
            boolean enabled,
            String startsAt,
            String endsAt,
            int displayOrder
    ) {
        return createRequestJson(
                title,
                content,
                null,
                navigationJson,
                enabled,
                startsAt,
                endsAt,
                displayOrder
        );
    }

    private String createRequestJson(
            String title,
            String content,
            String linkUrl,
            String navigationJson,
            boolean enabled,
            String startsAt,
            String endsAt,
            int displayOrder
    ) {
        return """
                {
                  "title": %s,
                  "content": %s,
                  "linkUrl": %s,
                  "navigation": %s,
                  "enabled": %s,
                  "startsAt": %s,
                  "endsAt": %s,
                  "displayOrder": %d
                }
                """.formatted(
                jsonString(title),
                jsonString(content),
                jsonNullableString(linkUrl),
                navigationJson == null ? "null" : navigationJson,
                enabled,
                jsonNullableString(startsAt),
                jsonNullableString(endsAt),
                displayOrder
        );
    }

    private String updateRequestJson(
            String title,
            String content,
            boolean enabled,
            int displayOrder,
            String imageAction
    ) {
        return updateRequestJson(
                title,
                content,
                enabled,
                displayOrder,
                imageAction,
                null
        );
    }

    private String updateRequestJson(
            String title,
            String content,
            boolean enabled,
            int displayOrder,
            String imageAction,
            String navigationJson
    ) {
        return updateRequestJson(
                title,
                content,
                enabled,
                displayOrder,
                imageAction,
                navigationJson,
                null
        );
    }

    private String updateRequestJson(
            String title,
            String content,
            boolean enabled,
            int displayOrder,
            String imageAction,
            String navigationJson,
            String linkUrl
    ) {
        return """
                {
                  "title": %s,
                  "content": %s,
                  "linkUrl": %s,
                  "navigation": %s,
                  "enabled": %s,
                  "startsAt": null,
                  "endsAt": null,
                  "displayOrder": %d,
                  "imageAction": %s
                }
                """.formatted(
                jsonString(title),
                jsonString(content),
                jsonNullableString(linkUrl),
                navigationJson == null ? "null" : navigationJson,
                enabled,
                displayOrder,
                jsonString(imageAction)
        );
    }

    private void assertNavigationRejected(
            String navigationJson,
            String expectedMessagePart
    ) throws Exception {
        mockMvc.perform(multipart("/api/admin/popups")
                        .file(requestPart(createRequestJson(
                                "잘못된 이동",
                                "내용",
                                navigationJson,
                                true,
                                null,
                                null,
                                0
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString(expectedMessagePart)));
    }

    private void assertLinkUrlRejected(String linkUrl) throws Exception {
        mockMvc.perform(multipart("/api/admin/popups")
                        .file(requestPart(createRequestJson(
                                "잘못된 링크",
                                "내용",
                                linkUrl,
                                null,
                                true,
                                null,
                                null,
                                0
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("linkUrl")));
    }

    private String jsonNullableString(String value) {
        return value == null ? "null" : jsonString(value);
    }

    private String externalNavigationJson(String url) {
        return """
                {
                  "schemaVersion": 1,
                  "type": "EXTERNAL_URL",
                  "params": {
                    "url": %s
                  }
                }
                """.formatted(jsonString(url));
    }

    private String jsonString(String value) {
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n") + "\"";
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

    private Path storedFile(String imageUrl) {
        String fileName = Path.of(URI.create(imageUrl).getPath())
                .getFileName()
                .toString();
        return TEST_POPUP_DIRECTORY.resolve(fileName);
    }

    private void cleanUp() throws Exception {
        appPopupRepository.deleteAll();
        if (!Files.exists(TEST_POPUP_DIRECTORY)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(TEST_POPUP_DIRECTORY)) {
            paths.sorted(Comparator.reverseOrder())
                    .filter(path -> !path.equals(TEST_POPUP_DIRECTORY))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception exception) {
                            throw new IllegalStateException(exception);
                        }
                    });
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock popupTestClock() {
            return Clock.fixed(
                    Instant.parse("2026-09-03T03:00:00Z"),
                    ZoneOffset.UTC
            );
        }
    }
}

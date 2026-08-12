package hsu.hanseomate.domain.studentcouncilnotice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import hsu.hanseomate.domain.studentcouncilnotice.entity.StudentCouncilNoticeAttachment;
import hsu.hanseomate.domain.studentcouncilnotice.entity.StudentCouncilNoticeImage;
import hsu.hanseomate.domain.studentcouncilnotice.repository.StudentCouncilNoticeAttachmentRepository;
import hsu.hanseomate.domain.studentcouncilnotice.repository.StudentCouncilNoticeImageRepository;
import hsu.hanseomate.domain.studentcouncilnotice.repository.StudentCouncilNoticeRepository;
import hsu.hanseomate.global.storage.LocalAttachmentStorageService;
import hsu.hanseomate.global.storage.LocalImageStorageService;
import hsu.hanseomate.support.AdminMockMvcConfiguration;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
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
class StudentCouncilNoticeFileApiIntegrationTest {

    private static final byte[] TINY_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUB"
                    + "AScY42YAAAAASUVORK5CYII="
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentCouncilNoticeRepository noticeRepository;

    @Autowired
    private StudentCouncilNoticeImageRepository imageRepository;

    @Autowired
    private StudentCouncilNoticeAttachmentRepository attachmentRepository;

    @Autowired
    private LocalImageStorageService imageStorageService;

    @Autowired
    private LocalAttachmentStorageService attachmentStorageService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        imageRepository.findAll().forEach(
                image -> imageStorageService.deleteIfManaged(image.getImageUrl())
        );
        attachmentRepository.findAll().forEach(
                attachment -> attachmentStorageService.delete(attachment.getStorageKey())
        );
        imageRepository.deleteAll();
        attachmentRepository.deleteAll();
        noticeRepository.deleteAll();
    }

    @Test
    void jpaCreatesStudentCouncilNoticeAssetTables() {
        List<String> imageColumns = columnsOf("student_council_notice_images");
        List<String> attachmentColumns = columnsOf("student_council_notice_attachments");

        assertThat(imageColumns).containsExactlyInAnyOrder(
                "id", "notice_id", "image_url", "original_file_name", "content_type",
                "file_size", "created_at", "updated_at"
        );
        assertThat(attachmentColumns).containsExactlyInAnyOrder(
                "id", "notice_id", "storage_key", "original_file_name", "content_type",
                "file_size", "created_at", "updated_at"
        );
    }

    @Test
    void multipartCreateExposesAllImagesAndAttachmentsToPublicReaders() throws Exception {
        MvcResult created = mockMvc.perform(multipart("/api/admin/notices")
                        .file(requestPart(createRequest(null, null)))
                        .file(imagePart("첫 번째.png"))
                        .file(imagePart("두 번째.png"))
                        .file(imagePart("세 번째.png"))
                        .file(attachmentPart("안내.html", "<script>alert(1)</script>"))
                        .file(attachmentPart("신청서.hwp", "attachment-body")))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        HttpHeaders.LOCATION,
                        org.hamcrest.Matchers.matchesPattern(
                                "/api/notices/categories/admin/\\d+"
                        )
                ))
                .andExpect(jsonPath("$.images.length()").value(3))
                .andExpect(jsonPath("$.images[0].contentType").value("image/png"))
                .andExpect(jsonPath("$.attachments.length()").value(2))
                .andExpect(jsonPath("$.attachments[0].fileName").value("안내.html"))
                .andReturn();

        long noticeId = numberAt(created, "$.id").longValue();
        StudentCouncilNoticeImage image =
                imageRepository.findAllByNoticeIdOrderByIdAsc(noticeId).get(0);
        StudentCouncilNoticeAttachment attachment =
                attachmentRepository.findAllByNoticeIdOrderByIdAsc(noticeId).get(0);

        mockMvc.perform(get("/api/notices/categories/admin")
                        .param("page", "0")
                        .with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].images.length()").value(3))
                .andExpect(jsonPath("$.items[0].attachments.length()").value(2));

        mockMvc.perform(get(
                        "/api/notices/categories/admin/{noticeId}",
                        noticeId
                ).with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewCount").value(1))
                .andExpect(jsonPath("$.images.length()").value(3))
                .andExpect(jsonPath("$.attachments.length()").value(2))
                .andExpect(jsonPath("$.attachments[0].downloadUrl")
                        .value("http://localhost/api/notices/categories/admin/"
                                + noticeId
                                + "/attachments/"
                                + attachment.getId()
                                + "/download"));

        mockMvc.perform(get(URI.create(image.getImageUrl()).getPath()).with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(TINY_PNG));

        mockMvc.perform(get(
                        "/api/notices/categories/admin/{noticeId}/attachments/{attachmentId}/download",
                        noticeId,
                        attachment.getId()
                ).with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        containsString("attachment")
                ))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes("<script>alert(1)</script>".getBytes(
                        StandardCharsets.UTF_8
                )));
    }

    @Test
    void multipartUpdateRetainsSelectedFilesRemovesOthersAndAddsNewFiles()
            throws Exception {
        MvcResult created = mockMvc.perform(multipart("/api/admin/notices")
                        .file(requestPart(createRequest(null, null)))
                        .file(imagePart("keep.png"))
                        .file(imagePart("remove.png"))
                        .file(attachmentPart("keep.txt", "keep"))
                        .file(attachmentPart("remove.txt", "remove")))
                .andExpect(status().isCreated())
                .andReturn();
        long noticeId = numberAt(created, "$.id").longValue();
        List<StudentCouncilNoticeImage> previousImages =
                imageRepository.findAllByNoticeIdOrderByIdAsc(noticeId);
        List<StudentCouncilNoticeAttachment> previousAttachments =
                attachmentRepository.findAllByNoticeIdOrderByIdAsc(noticeId);
        String removedImagePath = URI.create(previousImages.get(1).getImageUrl()).getPath();
        Long removedAttachmentId = previousAttachments.get(1).getId();

        String updateJson = createRequest(
                List.of(previousImages.get(0).getId()),
                List.of(previousAttachments.get(0).getId())
        );
        mockMvc.perform(multipart(
                        "/api/admin/notices/{noticeId}",
                        noticeId
                )
                        .file(requestPart(updateJson))
                        .file(imagePart("new.png"))
                        .file(attachmentPart("new.txt", "new"))
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("파일 포함 공지"))
                .andExpect(jsonPath("$.images.length()").value(2))
                .andExpect(jsonPath("$.attachments.length()").value(2));

        mockMvc.perform(get(removedImagePath).with(anonymous()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(
                        "/api/notices/categories/admin/{noticeId}/attachments/{attachmentId}/download",
                        noticeId,
                        removedAttachmentId
                ).with(anonymous()))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/admin/notices/{noticeId}", noticeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "JSON 수정",
                                  "author": "총학생회",
                                  "content": "기존 파일을 보존합니다."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images.length()").value(2))
                .andExpect(jsonPath("$.attachments.length()").value(2));

        List<String> remainingImagePaths = imageRepository
                .findAllByNoticeIdOrderByIdAsc(noticeId).stream()
                .map(StudentCouncilNoticeImage::getImageUrl)
                .map(url -> URI.create(url).getPath())
                .toList();
        List<Long> remainingAttachmentIds = attachmentRepository
                .findAllByNoticeIdOrderByIdAsc(noticeId).stream()
                .map(StudentCouncilNoticeAttachment::getId)
                .toList();

        mockMvc.perform(delete("/api/admin/notices/{noticeId}", noticeId))
                .andExpect(status().isNoContent());
        for (String imagePath : remainingImagePaths) {
            mockMvc.perform(get(imagePath).with(anonymous()))
                    .andExpect(status().isNotFound());
        }
        for (Long attachmentId : remainingAttachmentIds) {
            mockMvc.perform(get(
                            "/api/notices/categories/admin/{noticeId}/attachments/{attachmentId}/download",
                            noticeId,
                            attachmentId
                    ).with(anonymous()))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void rejectsInvalidImageAndProtectsMultipartMutationsWithAdminRole()
            throws Exception {
        MockMultipartFile invalidImage = new MockMultipartFile(
                "images",
                "fake.png",
                MediaType.IMAGE_PNG_VALUE,
                "not-an-image".getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/admin/notices")
                        .file(requestPart(createRequest(null, null)))
                        .file(invalidImage))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("올바른 이미지 파일이 아닙니다."));
        assertThat(noticeRepository.count()).isZero();

        mockMvc.perform(multipart("/api/admin/notices")
                        .file(requestPart(createRequest(null, null)))
                        .file(imagePart("anonymous.png"))
                        .with(anonymous()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/admin/notices")
                        .file(requestPart(createRequest(null, null)))
                        .file(imagePart("user.png"))
                        .with(userJwt()))
                .andExpect(status().isForbidden());
        assertThat(noticeRepository.count()).isZero();
    }

    @Test
    void rejectsForeignRetainedIdsAndEmptyRetainedListsRemoveEveryFile()
            throws Exception {
        MvcResult first = mockMvc.perform(multipart("/api/admin/notices")
                        .file(requestPart(createRequest(null, null)))
                        .file(imagePart("first.png"))
                        .file(attachmentPart("first.txt", "first")))
                .andExpect(status().isCreated())
                .andReturn();
        MvcResult second = mockMvc.perform(multipart("/api/admin/notices")
                        .file(requestPart(createRequest(null, null)))
                        .file(imagePart("second.png"))
                        .file(attachmentPart("second.txt", "second")))
                .andExpect(status().isCreated())
                .andReturn();
        long firstNoticeId = numberAt(first, "$.id").longValue();
        long secondNoticeId = numberAt(second, "$.id").longValue();
        StudentCouncilNoticeImage firstImage =
                imageRepository.findAllByNoticeIdOrderByIdAsc(firstNoticeId).get(0);
        StudentCouncilNoticeAttachment firstAttachment = attachmentRepository
                .findAllByNoticeIdOrderByIdAsc(firstNoticeId).get(0);
        StudentCouncilNoticeImage secondImage =
                imageRepository.findAllByNoticeIdOrderByIdAsc(secondNoticeId).get(0);
        StudentCouncilNoticeAttachment secondAttachment = attachmentRepository
                .findAllByNoticeIdOrderByIdAsc(secondNoticeId).get(0);

        mockMvc.perform(multipart(
                        "/api/admin/notices/{noticeId}",
                        firstNoticeId
                )
                        .file(requestPart(createRequest(
                                List.of(secondImage.getId()),
                                List.of(firstAttachment.getId())
                        )))
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        }))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("유지할 이미지 ID가 해당 공지에 속하지 않습니다."));
        assertThat(imageRepository.findAllByNoticeIdOrderByIdAsc(firstNoticeId))
                .extracting(StudentCouncilNoticeImage::getId)
                .containsExactly(firstImage.getId());

        mockMvc.perform(get(
                        "/api/notices/categories/admin/{noticeId}/attachments/{attachmentId}/download",
                        firstNoticeId,
                        secondAttachment.getId()
                ).with(anonymous()))
                .andExpect(status().isNotFound());

        String firstImagePath = URI.create(firstImage.getImageUrl()).getPath();
        mockMvc.perform(multipart(
                        "/api/admin/notices/{noticeId}",
                        firstNoticeId
                )
                        .file(requestPart(createRequest(List.of(), List.of())))
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images").isEmpty())
                .andExpect(jsonPath("$.attachments").isEmpty());
        mockMvc.perform(get(firstImagePath).with(anonymous()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(
                        "/api/notices/categories/admin/{noticeId}/attachments/{attachmentId}/download",
                        firstNoticeId,
                        firstAttachment.getId()
                ).with(anonymous()))
                .andExpect(status().isNotFound());
    }

    @Test
    void openApiDocumentsJsonAndMultipartNoticeRequests() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/admin/notices'].post.requestBody.content['application/json']"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/admin/notices'].post.requestBody.content['multipart/form-data']"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/admin/notices/{noticeId}'].put.requestBody.content['application/json']"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/admin/notices/{noticeId}'].put.requestBody.content['multipart/form-data']"
                ).exists())
                .andExpect(jsonPath(
                        "$.paths['/api/notices/categories/admin/{noticeId}/attachments/{attachmentId}/download'].get"
                ).exists());
    }

    private List<String> columnsOf(String tableName) {
        return jdbcTemplate.queryForList("""
                SELECT column_name
                FROM information_schema.columns
                WHERE LOWER(table_name) = ?
                ORDER BY ordinal_position
                """, String.class, tableName);
    }

    private String createRequest(
            List<Long> retainedImageIds,
            List<Long> retainedAttachmentIds
    ) {
        String retainedImages = retainedImageIds == null
                ? ""
                : ",\"retainedImageIds\":" + retainedImageIds;
        String retainedAttachments = retainedAttachmentIds == null
                ? ""
                : ",\"retainedAttachmentIds\":" + retainedAttachmentIds;
        return """
                {
                  "title": "파일 포함 공지",
                  "author": "총학생회",
                  "content": "이미지와 첨부파일을 확인해 주세요."
                %s%s}
                """.formatted(retainedImages, retainedAttachments);
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
                "images",
                originalFileName,
                MediaType.IMAGE_PNG_VALUE,
                TINY_PNG
        );
    }

    private MockMultipartFile attachmentPart(String originalFileName, String content) {
        return new MockMultipartFile(
                "attachments",
                originalFileName,
                MediaType.TEXT_PLAIN_VALUE,
                content.getBytes(StandardCharsets.UTF_8)
        );
    }

    private Number numberAt(MvcResult result, String path) throws Exception {
        return JsonPath.read(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8),
                path
        );
    }

    private RequestPostProcessor userJwt() {
        return jwt()
                .jwt(token -> token
                        .subject("1")
                        .claim("role", "USER"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }
}

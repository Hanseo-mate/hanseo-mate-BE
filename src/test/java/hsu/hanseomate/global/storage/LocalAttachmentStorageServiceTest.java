package hsu.hanseomate.global.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hsu.hanseomate.global.config.AttachmentStorageProperties;
import hsu.hanseomate.global.config.UploadProperties;
import hsu.hanseomate.global.exception.BadRequestException;
import hsu.hanseomate.global.exception.ResourceNotFoundException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class LocalAttachmentStorageServiceTest {

    @TempDir
    Path storageRoot;

    @Test
    void storesWithUuidKeyAndSanitizesOriginalFileName() throws Exception {
        LocalAttachmentStorageService service = service();
        byte[] content = "attachment-content".getBytes(StandardCharsets.UTF_8);

        var stored = service.store(new MockMultipartFile(
                "file",
                "../../report\r\n.txt",
                "text/plain",
                content
        ));

        assertThat(UUID.fromString(stored.storageKey()).toString())
                .isEqualTo(stored.storageKey());
        assertThat(stored.originalFileName()).isEqualTo("report.txt");
        assertThat(stored.contentType()).isEqualTo("text/plain");
        assertThat(stored.size()).isEqualTo(content.length);
        assertThat(stored.path().getParent()).isEqualTo(storageRoot.toAbsolutePath().normalize());
        assertThat(Files.readAllBytes(stored.path())).isEqualTo(content);

        var loaded = service.load(stored.storageKey());
        assertThat(loaded.size()).isEqualTo(content.length);
        assertThat(loaded.resource().getInputStream().readAllBytes()).isEqualTo(content);

        service.delete(stored.storageKey());
        assertThat(Files.exists(stored.path())).isFalse();
        assertThatThrownBy(() -> service.load(stored.storageKey()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void limitsSanitizedOriginalFileNameToFiveHundredCodePoints() {
        LocalAttachmentStorageService service = service();
        String longName = "한".repeat(501);

        var stored = service.store(new MockMultipartFile(
                "file",
                longName,
                null,
                new byte[]{1}
        ));

        assertThat(stored.originalFileName().codePointCount(
                0,
                stored.originalFileName().length()
        )).isEqualTo(500);
        assertThat(stored.contentType()).isEqualTo("application/octet-stream");
    }

    @Test
    void rejectsNonUuidStorageKeysWithoutAccessingOutsideRoot() {
        LocalAttachmentStorageService service = service();

        assertThatThrownBy(() -> service.load("../outside"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.delete("not-a-uuid"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsPrivateStorageInsidePublicUploadRoot() {
        Path publicRoot = storageRoot.resolve("public");

        assertThatThrownBy(() -> new LocalAttachmentStorageService(
                new AttachmentStorageProperties(
                        publicRoot.resolve("private").toString()
                ),
                new UploadProperties(publicRoot.toString(), "http://localhost", 1024L)
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("공개 이미지 디렉터리 밖");
    }

    @Test
    void replacesOverlongContentTypeWithOctetStream() {
        LocalAttachmentStorageService service = service();
        String contentType = "application/octet-stream;note=" + "a".repeat(300);

        var stored = service.store(new MockMultipartFile(
                "file",
                "file.bin",
                contentType,
                new byte[]{1}
        ));

        assertThat(stored.contentType()).isEqualTo("application/octet-stream");
    }

    private LocalAttachmentStorageService service() {
        return new LocalAttachmentStorageService(
                new AttachmentStorageProperties(storageRoot.toString()),
                new UploadProperties(
                        storageRoot.resolveSibling("public-uploads").toString(),
                        "http://localhost",
                        1024L
                )
        );
    }
}

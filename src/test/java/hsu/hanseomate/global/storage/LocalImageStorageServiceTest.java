package hsu.hanseomate.global.storage;

import static org.assertj.core.api.Assertions.assertThat;

import hsu.hanseomate.global.config.UploadProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Base64;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class LocalImageStorageServiceTest {

    private static final byte[] TINY_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9Z8YAAAAAASUVORK5CYII="
    );

    @TempDir
    Path uploadRoot;

    @Test
    void reportsDetectedContentTypeAndStoredByteSize() {
        LocalImageStorageService service = new LocalImageStorageService(
                new UploadProperties(uploadRoot.toString(), "http://localhost", 1024L)
        );

        var stored = service.store(new MockMultipartFile(
                "file",
                "forged.txt",
                "text/plain",
                TINY_PNG
        ), "images");

        assertThat(stored.contentType()).isEqualTo("image/png");
        assertThat(stored.size()).isEqualTo(TINY_PNG.length);
        assertThat(stored.path()).hasExtension("png");
    }

    @Test
    void storesPublicImageWithWebServerReadablePermissions() throws IOException {
        Assumptions.assumeTrue(
                Files.getFileAttributeView(uploadRoot, PosixFileAttributeView.class) != null
        );
        LocalImageStorageService service = new LocalImageStorageService(
                new UploadProperties(uploadRoot.toString(), "http://localhost", 1024L)
        );

        var stored = service.store(new MockMultipartFile(
                "file",
                "image.png",
                "image/png",
                TINY_PNG
        ), "images");

        assertThat(Files.getPosixFilePermissions(stored.path()))
                .containsExactlyInAnyOrderElementsOf(
                        PosixFilePermissions.fromString("rw-r--r--")
                );
    }

    @Test
    void deletesManagedImageEvenAfterPublicBaseUrlChanges() {
        LocalImageStorageService oldService = new LocalImageStorageService(
                new UploadProperties(uploadRoot.toString(), "https://old.example", 1024L)
        );
        var stored = oldService.store(new MockMultipartFile(
                "file",
                "image.png",
                "image/png",
                TINY_PNG
        ), "images");
        LocalImageStorageService newService = new LocalImageStorageService(
                new UploadProperties(uploadRoot.toString(), "https://new.example", 1024L)
        );

        assertThat(newService.currentPublicUrl(stored.url()))
                .startsWith("https://new.example/uploads/");

        newService.deleteIfManaged(stored.url());

        assertThat(stored.path()).doesNotExist();
    }
}

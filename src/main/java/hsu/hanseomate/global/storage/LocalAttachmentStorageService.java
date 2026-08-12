package hsu.hanseomate.global.storage;

import hsu.hanseomate.global.config.AttachmentStorageProperties;
import hsu.hanseomate.global.config.UploadProperties;
import hsu.hanseomate.global.exception.BadRequestException;
import hsu.hanseomate.global.exception.ResourceNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
public class LocalAttachmentStorageService {

    private static final int MAX_ORIGINAL_FILE_NAME_LENGTH = 500;
    private static final int MAX_CONTENT_TYPE_LENGTH = 255;
    private static final String FALLBACK_FILE_NAME = "attachment";

    private final Path storageRoot;

    public LocalAttachmentStorageService(
            AttachmentStorageProperties properties,
            UploadProperties uploadProperties
    ) {
        if (properties.directory() == null || properties.directory().isBlank()) {
            throw new IllegalStateException(
                    "app.attachment-storage.directory 설정이 필요합니다."
            );
        }
        Path configuredStorageRoot = Path.of(properties.directory())
                .toAbsolutePath()
                .normalize();
        createDirectories(configuredStorageRoot);
        Path publicUploadRoot = Path.of(uploadProperties.directory())
                .toAbsolutePath()
                .normalize();
        createDirectories(publicUploadRoot);
        this.storageRoot = realPath(configuredStorageRoot);
        Path realPublicUploadRoot = realPath(publicUploadRoot);
        if (storageRoot.equals(realPublicUploadRoot)
                || storageRoot.startsWith(realPublicUploadRoot)) {
            throw new IllegalStateException(
                    "첨부파일 저장 디렉터리는 공개 이미지 디렉터리 밖에 있어야 합니다."
            );
        }
    }

    public StoredAttachment store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("업로드할 첨부파일이 없습니다.");
        }

        String originalFileName = sanitizeOriginalFileName(file.getOriginalFilename());
        String contentType = normalizeContentType(file.getContentType());
        Path temporaryFile = null;
        Path storedPath = null;
        try {
            temporaryFile = Files.createTempFile(storageRoot, ".upload-", ".tmp");
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, temporaryFile, StandardCopyOption.REPLACE_EXISTING);
            }

            String storageKey = UUID.randomUUID().toString();
            storedPath = resolveStorageKey(storageKey);
            move(temporaryFile, storedPath);
            return new StoredAttachment(
                    storageKey,
                    originalFileName,
                    contentType,
                    Files.size(storedPath),
                    storedPath
            );
        } catch (IOException exception) {
            deleteQuietly(temporaryFile);
            deleteQuietly(storedPath);
            throw new IllegalStateException("첨부파일을 저장할 수 없습니다.", exception);
        } catch (RuntimeException exception) {
            deleteQuietly(temporaryFile);
            deleteQuietly(storedPath);
            throw exception;
        }
    }

    public LoadedAttachment load(String storageKey) {
        Path storedPath = resolveStorageKey(storageKey);
        if (!Files.isRegularFile(storedPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new ResourceNotFoundException("첨부파일을 찾을 수 없습니다.");
        }
        try {
            return new LoadedAttachment(
                    storageKey,
                    new FileSystemResource(storedPath),
                    Files.size(storedPath)
            );
        } catch (IOException exception) {
            throw new IllegalStateException("첨부파일을 읽을 수 없습니다.", exception);
        }
    }

    public void delete(String storageKey) {
        deleteQuietly(resolveStorageKey(storageKey));
    }

    public void delete(StoredAttachment storedAttachment) {
        if (storedAttachment != null) {
            delete(storedAttachment.storageKey());
        }
    }

    private String sanitizeOriginalFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return FALLBACK_FILE_NAME;
        }

        String fileName = originalFileName.replace('\\', '/');
        fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
        fileName = Normalizer.normalize(fileName, Normalizer.Form.NFC);

        StringBuilder sanitized = new StringBuilder(fileName.length());
        fileName.codePoints()
                .filter(codePoint -> codePoint != '/' && codePoint != '\\')
                .filter(codePoint -> !Character.isISOControl(codePoint))
                .limit(MAX_ORIGINAL_FILE_NAME_LENGTH)
                .forEach(sanitized::appendCodePoint);

        String result = sanitized.toString().trim();
        return result.isBlank() ? FALLBACK_FILE_NAME : result;
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        try {
            String normalized = MediaType.parseMediaType(contentType)
                    .toString()
                    .toLowerCase(Locale.ROOT);
            return normalized.length() <= MAX_CONTENT_TYPE_LENGTH
                    ? normalized
                    : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        } catch (IllegalArgumentException exception) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
    }

    private Path resolveStorageKey(String storageKey) {
        String canonicalKey;
        try {
            canonicalKey = UUID.fromString(storageKey).toString();
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BadRequestException("잘못된 첨부파일 식별값입니다.");
        }
        if (!canonicalKey.equals(storageKey.toLowerCase(Locale.ROOT))) {
            throw new BadRequestException("잘못된 첨부파일 식별값입니다.");
        }

        Path resolved = storageRoot.resolve(canonicalKey).normalize();
        if (!resolved.startsWith(storageRoot)) {
            throw new BadRequestException("잘못된 첨부파일 저장 경로입니다.");
        }
        return resolved;
    }

    private void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
    }

    private void createDirectories(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            throw new IllegalStateException("첨부파일 저장 디렉터리를 만들 수 없습니다.", exception);
        }
    }

    private Path realPath(Path directory) {
        try {
            return directory.toRealPath();
        } catch (IOException exception) {
            throw new IllegalStateException("파일 저장 디렉터리를 확인할 수 없습니다.", exception);
        }
    }

    private void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException exception) {
            log.warn("첨부파일을 삭제하지 못했습니다. path={}", file, exception);
        }
    }

    public record StoredAttachment(
            String storageKey,
            String originalFileName,
            String contentType,
            long size,
            Path path
    ) {
    }

    public record LoadedAttachment(
            String storageKey,
            Resource resource,
            long size
    ) {
    }
}

package hsu.hanseomate.global.storage;

import hsu.hanseomate.global.config.UploadProperties;
import hsu.hanseomate.global.exception.BadRequestException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
public class LocalImageStorageService {

    private static final Map<String, String> ALLOWED_EXTENSIONS = Map.of(
            "JPEG", "jpg",
            "JPG", "jpg",
            "PNG", "png",
            "GIF", "gif"
    );

    private final UploadProperties uploadProperties;
    private final Path uploadRoot;
    private final String publicBaseUrl;

    public LocalImageStorageService(UploadProperties uploadProperties) {
        this.uploadProperties = uploadProperties;
        this.uploadRoot = Path.of(uploadProperties.directory()).toAbsolutePath().normalize();
        this.publicBaseUrl = normalizeBaseUrl(uploadProperties.publicBaseUrl());
        createDirectories(uploadRoot);
    }

    public StoredImage store(MultipartFile file, String subdirectory) {
        validateFile(file);

        Path targetDirectory = resolveInsideRoot(subdirectory);
        createDirectories(targetDirectory);

        Path temporaryFile = null;
        Path storedPath = null;
        try {
            temporaryFile = Files.createTempFile(targetDirectory, ".upload-", ".tmp");
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, temporaryFile, StandardCopyOption.REPLACE_EXISTING);
            }

            ImageMetadata imageMetadata = detectImageMetadata(temporaryFile);
            UUID imageId = UUID.randomUUID();
            String fileName = imageId + "." + imageMetadata.extension();
            storedPath = targetDirectory.resolve(fileName).normalize();
            move(temporaryFile, storedPath);
            makePubliclyReadable(storedPath);

            String relativePath = uploadRoot.relativize(storedPath)
                    .toString()
                    .replace('\\', '/');
            String imageUrl = publicBaseUrl + "/uploads/" + relativePath;
            return new StoredImage(
                    imageUrl,
                    storedPath,
                    imageMetadata.contentType(),
                    Files.size(storedPath)
            );
        } catch (IOException exception) {
            deleteQuietly(temporaryFile);
            deleteQuietly(storedPath);
            throw new IllegalStateException("이미지 파일을 저장할 수 없습니다.", exception);
        } catch (RuntimeException exception) {
            deleteQuietly(temporaryFile);
            deleteQuietly(storedPath);
            throw exception;
        }
    }

    public void delete(StoredImage storedImage) {
        if (storedImage != null) {
            deleteQuietly(storedImage.path());
        }
    }

    public void deleteIfManaged(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }

        String relativePath = managedRelativePath(imageUrl);
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        Path managedPath = uploadRoot.resolve(relativePath).normalize();
        if (managedPath.startsWith(uploadRoot)) {
            deleteQuietly(managedPath);
        }
    }

    public String currentPublicUrl(String storedImageUrl) {
        if (storedImageUrl == null || storedImageUrl.isBlank()) {
            return storedImageUrl;
        }
        String relativePath = managedRelativePath(storedImageUrl);
        if (relativePath == null || relativePath.isBlank()) {
            return storedImageUrl;
        }
        return publicBaseUrl + "/uploads/" + relativePath.replace('\\', '/');
    }

    private String managedRelativePath(String imageUrl) {
        String managedPrefix = publicBaseUrl + "/uploads/";
        if (imageUrl.startsWith(managedPrefix)) {
            return imageUrl.substring(managedPrefix.length());
        }
        try {
            String path = new URI(imageUrl).getPath();
            String uploadPrefix = "/uploads/";
            if (path != null && path.startsWith(uploadPrefix)) {
                return path.substring(uploadPrefix.length());
            }
        } catch (URISyntaxException ignored) {
            // 관리 URL 형식이 아니면 로컬 파일을 삭제하지 않는다.
        }
        return null;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("업로드할 이미지 파일이 없습니다.");
        }
        if (file.getSize() > uploadProperties.maxImageBytes()) {
            throw new BadRequestException(
                    "이미지 파일은 " + uploadProperties.maxImageBytes() + "바이트 이하여야 합니다."
            );
        }
    }

    private ImageMetadata detectImageMetadata(Path file) throws IOException {
        try (ImageInputStream imageInput = ImageIO.createImageInputStream(file.toFile())) {
            if (imageInput == null) {
                throw invalidImage();
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw invalidImage();
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                reader.getWidth(0);
                reader.getHeight(0);
                String format = reader.getFormatName().toUpperCase(Locale.ROOT);
                String extension = ALLOWED_EXTENSIONS.get(format);
                if (extension == null) {
                    throw new BadRequestException("JPG, PNG, GIF 이미지 파일만 업로드할 수 있습니다.");
                }
                return new ImageMetadata(extension, contentType(extension));
            } finally {
                reader.dispose();
            }
        }
    }

    private String contentType(String extension) {
        return switch (extension) {
            case "jpg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            default -> throw new IllegalArgumentException("지원하지 않는 이미지 확장자입니다.");
        };
    }

    private Path resolveInsideRoot(String subdirectory) {
        Path resolved = uploadRoot.resolve(subdirectory).normalize();
        if (!resolved.startsWith(uploadRoot)) {
            throw new IllegalArgumentException("잘못된 이미지 저장 경로입니다.");
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

    private void makePubliclyReadable(Path file) throws IOException {
        PosixFileAttributeView posixView = Files.getFileAttributeView(
                file,
                PosixFileAttributeView.class
        );
        if (posixView != null) {
            Files.setPosixFilePermissions(
                    file,
                    PosixFilePermissions.fromString("rw-r--r--")
            );
        }
    }

    private void createDirectories(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            throw new IllegalStateException("이미지 저장 디렉터리를 만들 수 없습니다.", exception);
        }
    }

    private void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException exception) {
            log.warn("이미지 파일을 삭제하지 못했습니다. path={}", file, exception);
        }
    }

    private BadRequestException invalidImage() {
        return new BadRequestException("올바른 이미지 파일이 아닙니다.");
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("app.upload.public-base-url 설정이 필요합니다.");
        }
        return baseUrl.replaceAll("/+$", "");
    }

    private record ImageMetadata(String extension, String contentType) {
    }

    public record StoredImage(
            String url,
            Path path,
            String contentType,
            long size
    ) {
    }
}

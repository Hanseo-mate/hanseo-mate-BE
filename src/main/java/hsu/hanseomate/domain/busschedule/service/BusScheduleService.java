package hsu.hanseomate.domain.busschedule.service;

import hsu.hanseomate.domain.busschedule.dto.BusScheduleResponse;
import hsu.hanseomate.domain.busschedule.entity.BusSchedule;
import hsu.hanseomate.domain.busschedule.repository.BusScheduleRepository;
import hsu.hanseomate.domain.busschedule.type.MainCategory;
import hsu.hanseomate.domain.busschedule.type.SubCategory;
import hsu.hanseomate.global.config.UploadProperties;
import hsu.hanseomate.global.exception.BadRequestException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BusScheduleService {

    private static final String BUS_IMAGE_BASE_PATH = "/home/images/bus";
    private static final String BUS_IMAGE_URL_PATH = "/home/images/bus/";
    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final BusScheduleRepository busScheduleRepository;
    private final UploadProperties uploadProperties;

    public List<BusScheduleResponse> getAllSchedules() {
        return busScheduleRepository.findAll().stream()
                .map(BusScheduleResponse::from)
                .toList();
    }

    @Transactional
    public BusScheduleResponse uploadOrUpdateSchedule(
            MultipartFile image,
            MainCategory mainCategory,
            SubCategory subCategory
    ) {
        validateImage(image);

        String extension = extractExtension(image);
        String filename = buildFilename(subCategory, extension);
        Path targetPath = Path.of(BUS_IMAGE_BASE_PATH).resolve(filename).normalize();
        String imageUrl = buildImageUrl(filename);

        Optional<BusSchedule> existing =
                busScheduleRepository.findByMainCategoryAndSubCategory(mainCategory, subCategory);

        if (existing.isPresent()) {
            BusSchedule schedule = existing.get();
            deleteOldFileQuietly(schedule.getServerFilePath());
            saveImageFile(image, targetPath);
            schedule.update(imageUrl, targetPath.toString());
            return BusScheduleResponse.from(schedule);
        }

        saveImageFile(image, targetPath);
        BusSchedule schedule = busScheduleRepository.save(
                BusSchedule.create(mainCategory, subCategory, imageUrl, targetPath.toString())
        );
        return BusScheduleResponse.from(schedule);
    }

    private String buildFilename(SubCategory subCategory, String extension) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        return subCategory.name() + "_" + timestamp + "." + extension;
    }

    private String buildImageUrl(String filename) {
        String baseUrl = uploadProperties.publicBaseUrl().replaceAll("/+$", "");
        return baseUrl + BUS_IMAGE_URL_PATH + filename;
    }

    private void saveImageFile(MultipartFile image, Path targetPath) {
        try {
            Files.createDirectories(targetPath.getParent());
            try (InputStream inputStream = image.getInputStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("버스 시간표 이미지를 저장할 수 없습니다.", e);
        }
    }

    private void deleteOldFileQuietly(String serverFilePath) {
        if (serverFilePath == null || serverFilePath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(serverFilePath));
        } catch (IOException e) {
            log.warn("기존 버스 시간표 이미지를 삭제하지 못했습니다. path={}", serverFilePath, e);
        }
    }

    private String extractExtension(MultipartFile image) {
        String originalFilename = image.getOriginalFilename();
        if (originalFilename != null && originalFilename.contains(".")) {
            return originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
        }
        String contentType = image.getContentType();
        if (contentType != null) {
            return switch (contentType) {
                case "image/jpeg" -> "jpg";
                case "image/png" -> "png";
                case "image/gif" -> "gif";
                default -> throw new BadRequestException("지원하지 않는 이미지 형식입니다: " + contentType);
            };
        }
        throw new BadRequestException("이미지 파일 확장자를 확인할 수 없습니다.");
    }

    private void validateImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new BadRequestException("업로드할 이미지 파일이 없습니다.");
        }
    }
}

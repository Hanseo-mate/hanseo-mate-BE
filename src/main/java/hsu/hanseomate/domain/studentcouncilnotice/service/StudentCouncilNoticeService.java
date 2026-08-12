package hsu.hanseomate.domain.studentcouncilnotice.service;

import hsu.hanseomate.domain.studentcouncilnotice.dto.StudentCouncilNoticeAttachmentDownload;
import hsu.hanseomate.domain.studentcouncilnotice.dto.StudentCouncilNoticeAttachmentResponse;
import hsu.hanseomate.domain.studentcouncilnotice.dto.StudentCouncilNoticeDetailResponse;
import hsu.hanseomate.domain.studentcouncilnotice.dto.StudentCouncilNoticeImageResponse;
import hsu.hanseomate.domain.studentcouncilnotice.dto.StudentCouncilNoticeMultipartRequest;
import hsu.hanseomate.domain.studentcouncilnotice.dto.StudentCouncilNoticePageResponse;
import hsu.hanseomate.domain.studentcouncilnotice.dto.StudentCouncilNoticeRequest;
import hsu.hanseomate.domain.studentcouncilnotice.entity.StudentCouncilNotice;
import hsu.hanseomate.domain.studentcouncilnotice.entity.StudentCouncilNoticeAttachment;
import hsu.hanseomate.domain.studentcouncilnotice.entity.StudentCouncilNoticeImage;
import hsu.hanseomate.domain.studentcouncilnotice.exception.StudentCouncilNoticeAttachmentNotFoundException;
import hsu.hanseomate.domain.studentcouncilnotice.exception.StudentCouncilNoticeNotFoundException;
import hsu.hanseomate.domain.studentcouncilnotice.repository.StudentCouncilNoticeAttachmentRepository;
import hsu.hanseomate.domain.studentcouncilnotice.repository.StudentCouncilNoticeImageRepository;
import hsu.hanseomate.domain.studentcouncilnotice.repository.StudentCouncilNoticeRepository;
import hsu.hanseomate.global.config.UploadProperties;
import hsu.hanseomate.global.exception.BadRequestException;
import hsu.hanseomate.global.storage.LocalAttachmentStorageService;
import hsu.hanseomate.global.storage.LocalAttachmentStorageService.LoadedAttachment;
import hsu.hanseomate.global.storage.LocalAttachmentStorageService.StoredAttachment;
import hsu.hanseomate.global.storage.LocalImageStorageService;
import hsu.hanseomate.global.storage.LocalImageStorageService.StoredImage;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentCouncilNoticeService {

    private static final int PAGE_SIZE = 10;
    private static final int MAX_FILE_NAME_LENGTH = 500;
    private static final String FALLBACK_IMAGE_FILE_NAME = "image";
    private static final String IMAGE_STORAGE_DIRECTORY =
            "student-council-notices/%d/images";

    private final StudentCouncilNoticeRepository studentCouncilNoticeRepository;
    private final StudentCouncilNoticeImageRepository studentCouncilNoticeImageRepository;
    private final StudentCouncilNoticeAttachmentRepository
            studentCouncilNoticeAttachmentRepository;
    private final LocalImageStorageService imageStorageService;
    private final LocalAttachmentStorageService attachmentStorageService;
    private final UploadProperties uploadProperties;

    public StudentCouncilNoticePageResponse getNotices(int page) {
        Page<StudentCouncilNotice> noticePage =
                studentCouncilNoticeRepository.findAllByOrderByCreatedAtDescIdDesc(
                        PageRequest.of(page, PAGE_SIZE)
                );
        List<Long> noticeIds = noticePage.getContent().stream()
                .map(StudentCouncilNotice::getId)
                .toList();
        if (noticeIds.isEmpty()) {
            return StudentCouncilNoticePageResponse.from(noticePage);
        }

        Map<Long, List<StudentCouncilNoticeImageResponse>> imagesByNoticeId =
                studentCouncilNoticeImageRepository.findAllByNoticeIds(noticeIds).stream()
                        .collect(Collectors.groupingBy(
                                image -> image.getNotice().getId(),
                                LinkedHashMap::new,
                                Collectors.mapping(
                                        this::toImageResponse,
                                        Collectors.toList()
                                )
                        ));
        Map<Long, List<StudentCouncilNoticeAttachmentResponse>> attachmentsByNoticeId =
                studentCouncilNoticeAttachmentRepository.findAllByNoticeIds(noticeIds).stream()
                        .collect(Collectors.groupingBy(
                                attachment -> attachment.getNotice().getId(),
                                LinkedHashMap::new,
                                Collectors.mapping(
                                        this::toAttachmentResponse,
                                        Collectors.toList()
                                )
                        ));
        return StudentCouncilNoticePageResponse.from(
                noticePage,
                imagesByNoticeId,
                attachmentsByNoticeId
        );
    }

    public StudentCouncilNoticeDetailResponse getNoticeForAdmin(Long noticeId) {
        return toDetailResponse(findNotice(noticeId));
    }

    @Transactional
    public StudentCouncilNoticeDetailResponse getNoticeAndIncrementViewCount(Long noticeId) {
        int updatedRows = studentCouncilNoticeRepository.incrementViewCount(noticeId);
        if (updatedRows == 0) {
            throw new StudentCouncilNoticeNotFoundException(noticeId);
        }
        return toDetailResponse(findNotice(noticeId));
    }

    public StudentCouncilNoticeAttachmentDownload downloadAttachment(
            Long noticeId,
            Long attachmentId
    ) {
        StudentCouncilNoticeAttachment attachment =
                studentCouncilNoticeAttachmentRepository.findByIdAndNoticeId(
                                attachmentId,
                                noticeId
                        )
                        .orElseThrow(() ->
                                new StudentCouncilNoticeAttachmentNotFoundException(
                                        noticeId,
                                        attachmentId
                                )
                        );
        LoadedAttachment loadedAttachment =
                attachmentStorageService.load(attachment.getStorageKey());
        return new StudentCouncilNoticeAttachmentDownload(
                loadedAttachment.resource(),
                attachment.getOriginalFileName(),
                loadedAttachment.size()
        );
    }

    @Transactional
    public StudentCouncilNoticeDetailResponse createNotice(StudentCouncilNoticeRequest request) {
        StudentCouncilNotice notice = createNoticeEntity(
                request.title(),
                request.author(),
                request.content()
        );
        return toDetailResponse(notice);
    }

    @Transactional
    public StudentCouncilNoticeDetailResponse createNotice(
            StudentCouncilNoticeRequest request,
            List<MultipartFile> images,
            List<MultipartFile> attachments
    ) {
        StudentCouncilNotice notice = createNoticeEntity(
                request.title(),
                request.author(),
                request.content()
        );
        NewAssets newAssets = storeNewAssets(notice, images, attachments);

        try {
            saveNewAssets(newAssets);
            registerAssetCleanup(newAssets, List.of(), List.of());
            return toDetailResponse(notice);
        } catch (RuntimeException exception) {
            cleanupNewAssets(newAssets);
            throw exception;
        }
    }

    @Transactional
    public StudentCouncilNoticeDetailResponse updateNotice(
            Long noticeId,
            StudentCouncilNoticeRequest request
    ) {
        StudentCouncilNotice notice = findNoticeForUpdate(noticeId);
        notice.update(
                request.title().trim(),
                request.author().trim(),
                request.content()
        );
        studentCouncilNoticeRepository.flush();
        return toDetailResponse(notice);
    }

    @Transactional
    public StudentCouncilNoticeDetailResponse updateNotice(
            Long noticeId,
            StudentCouncilNoticeMultipartRequest request,
            List<MultipartFile> images,
            List<MultipartFile> attachments
    ) {
        StudentCouncilNotice notice = findNoticeForUpdate(noticeId);
        List<StudentCouncilNoticeImage> currentImages =
                studentCouncilNoticeImageRepository.findAllByNoticeIdOrderByIdAsc(noticeId);
        List<StudentCouncilNoticeAttachment> currentAttachments =
                studentCouncilNoticeAttachmentRepository
                        .findAllByNoticeIdOrderByIdAsc(noticeId);

        List<StudentCouncilNoticeImage> removedImages = selectRemovedAssets(
                currentImages,
                request.retainedImageIds(),
                StudentCouncilNoticeImage::getId,
                "유지할 이미지 ID가 해당 공지에 속하지 않습니다."
        );
        List<StudentCouncilNoticeAttachment> removedAttachments = selectRemovedAssets(
                currentAttachments,
                request.retainedAttachmentIds(),
                StudentCouncilNoticeAttachment::getId,
                "유지할 첨부파일 ID가 해당 공지에 속하지 않습니다."
        );
        NewAssets newAssets = storeNewAssets(notice, images, attachments);

        try {
            notice.update(
                    request.title().trim(),
                    request.author().trim(),
                    request.content()
            );
            studentCouncilNoticeImageRepository.deleteAll(removedImages);
            studentCouncilNoticeAttachmentRepository.deleteAll(removedAttachments);
            saveNewAssets(newAssets);
            studentCouncilNoticeRepository.flush();
            registerAssetCleanup(newAssets, removedImages, removedAttachments);
            return toDetailResponse(notice);
        } catch (RuntimeException exception) {
            cleanupNewAssets(newAssets);
            throw exception;
        }
    }

    @Transactional
    public void deleteNotice(Long noticeId) {
        StudentCouncilNotice notice = findNoticeForUpdate(noticeId);
        List<StudentCouncilNoticeImage> images =
                studentCouncilNoticeImageRepository.findAllByNoticeIdOrderByIdAsc(noticeId);
        List<StudentCouncilNoticeAttachment> attachments =
                studentCouncilNoticeAttachmentRepository
                        .findAllByNoticeIdOrderByIdAsc(noticeId);

        studentCouncilNoticeRepository.delete(notice);
        studentCouncilNoticeRepository.flush();
        registerAssetCleanup(NewAssets.empty(), images, attachments);
    }

    private StudentCouncilNotice createNoticeEntity(
            String title,
            String author,
            String content
    ) {
        return studentCouncilNoticeRepository.saveAndFlush(
                StudentCouncilNotice.create(title.trim(), author.trim(), content)
        );
    }

    private NewAssets storeNewAssets(
            StudentCouncilNotice notice,
            List<MultipartFile> imageFiles,
            List<MultipartFile> attachmentFiles
    ) {
        List<StoredImage> storedImages = new ArrayList<>();
        List<StudentCouncilNoticeImage> imageEntities = new ArrayList<>();
        List<StoredAttachment> storedAttachments = new ArrayList<>();
        List<StudentCouncilNoticeAttachment> attachmentEntities = new ArrayList<>();
        NewAssets assets = new NewAssets(
                storedImages,
                imageEntities,
                storedAttachments,
                attachmentEntities
        );

        try {
            for (MultipartFile imageFile : safeFiles(imageFiles)) {
                StoredImage storedImage = imageStorageService.store(
                        imageFile,
                        IMAGE_STORAGE_DIRECTORY.formatted(notice.getId())
                );
                storedImages.add(storedImage);
                imageEntities.add(StudentCouncilNoticeImage.create(
                        notice,
                        storedImage.url(),
                        sanitizeImageFileName(imageFile.getOriginalFilename()),
                        storedImage.contentType(),
                        storedImage.size()
                ));
            }
            for (MultipartFile attachmentFile : safeFiles(attachmentFiles)) {
                StoredAttachment storedAttachment =
                        attachmentStorageService.store(attachmentFile);
                storedAttachments.add(storedAttachment);
                attachmentEntities.add(StudentCouncilNoticeAttachment.create(
                        notice,
                        storedAttachment.storageKey(),
                        storedAttachment.originalFileName(),
                        storedAttachment.contentType(),
                        storedAttachment.size()
                ));
            }
            return assets;
        } catch (RuntimeException exception) {
            cleanupNewAssets(assets);
            throw exception;
        }
    }

    private void saveNewAssets(NewAssets assets) {
        if (!assets.imageEntities().isEmpty()) {
            studentCouncilNoticeImageRepository.saveAllAndFlush(assets.imageEntities());
        }
        if (!assets.attachmentEntities().isEmpty()) {
            studentCouncilNoticeAttachmentRepository.saveAllAndFlush(
                    assets.attachmentEntities()
            );
        }
    }

    private <T> List<T> selectRemovedAssets(
            List<T> currentAssets,
            List<Long> retainedIds,
            Function<T, Long> idExtractor,
            String invalidIdMessage
    ) {
        if (retainedIds == null) {
            return List.of();
        }

        Set<Long> retainedIdSet = new LinkedHashSet<>(retainedIds);
        Set<Long> currentIdSet = currentAssets.stream()
                .map(idExtractor)
                .collect(Collectors.toSet());
        if (!currentIdSet.containsAll(retainedIdSet)) {
            throw new BadRequestException(invalidIdMessage);
        }
        return currentAssets.stream()
                .filter(asset -> !retainedIdSet.contains(idExtractor.apply(asset)))
                .toList();
    }

    private void registerAssetCleanup(
            NewAssets newAssets,
            List<StudentCouncilNoticeImage> removedImages,
            List<StudentCouncilNoticeAttachment> removedAttachments
    ) {
        List<String> removedImageUrls = removedImages.stream()
                .map(StudentCouncilNoticeImage::getImageUrl)
                .toList();
        List<String> removedAttachmentKeys = removedAttachments.stream()
                .map(StudentCouncilNoticeAttachment::getStorageKey)
                .toList();

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cleanupRemovedAssets(removedImageUrls, removedAttachmentKeys);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        cleanupRemovedAssets(
                                removedImageUrls,
                                removedAttachmentKeys
                        );
                    }

                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            cleanupNewAssets(newAssets);
                        }
                    }
                }
        );
    }

    private void cleanupNewAssets(NewAssets assets) {
        assets.storedImages().forEach(imageStorageService::delete);
        assets.storedAttachments().forEach(attachmentStorageService::delete);
    }

    private void cleanupRemovedAssets(
            Collection<String> imageUrls,
            Collection<String> attachmentKeys
    ) {
        imageUrls.forEach(imageStorageService::deleteIfManaged);
        attachmentKeys.forEach(attachmentStorageService::delete);
    }

    private StudentCouncilNoticeDetailResponse toDetailResponse(
            StudentCouncilNotice notice
    ) {
        List<StudentCouncilNoticeImageResponse> images =
                studentCouncilNoticeImageRepository
                        .findAllByNoticeIdOrderByIdAsc(notice.getId()).stream()
                        .map(this::toImageResponse)
                        .toList();
        List<StudentCouncilNoticeAttachmentResponse> attachments =
                studentCouncilNoticeAttachmentRepository
                        .findAllByNoticeIdOrderByIdAsc(notice.getId()).stream()
                        .map(this::toAttachmentResponse)
                        .toList();
        return StudentCouncilNoticeDetailResponse.from(notice, images, attachments);
    }

    private StudentCouncilNoticeAttachmentResponse toAttachmentResponse(
            StudentCouncilNoticeAttachment attachment
    ) {
        Long noticeId = attachment.getNotice().getId();
        String downloadUrl = normalizeBaseUrl(uploadProperties.publicBaseUrl())
                + "/api/notices/categories/admin/"
                + noticeId
                + "/attachments/"
                + attachment.getId()
                + "/download";
        return StudentCouncilNoticeAttachmentResponse.from(attachment, downloadUrl);
    }

    private StudentCouncilNoticeImageResponse toImageResponse(
            StudentCouncilNoticeImage image
    ) {
        return StudentCouncilNoticeImageResponse.from(
                image,
                imageStorageService.currentPublicUrl(image.getImageUrl())
        );
    }

    private StudentCouncilNotice findNotice(Long noticeId) {
        return studentCouncilNoticeRepository.findById(noticeId)
                .orElseThrow(() -> new StudentCouncilNoticeNotFoundException(noticeId));
    }

    private StudentCouncilNotice findNoticeForUpdate(Long noticeId) {
        return studentCouncilNoticeRepository.findByIdForUpdate(noticeId)
                .orElseThrow(() -> new StudentCouncilNoticeNotFoundException(noticeId));
    }

    private List<MultipartFile> safeFiles(List<MultipartFile> files) {
        return files == null ? List.of() : files;
    }

    private String sanitizeImageFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return FALLBACK_IMAGE_FILE_NAME;
        }
        String fileName = Normalizer.normalize(
                originalFileName.replace('\\', '/'),
                Normalizer.Form.NFC
        );
        fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
        StringBuilder sanitized = new StringBuilder(fileName.length());
        fileName.codePoints()
                .filter(codePoint -> codePoint != '/' && codePoint != '\\')
                .filter(codePoint -> !Character.isISOControl(codePoint))
                .limit(MAX_FILE_NAME_LENGTH)
                .forEach(sanitized::appendCodePoint);
        String result = sanitized.toString().trim();
        return result.isBlank() ? FALLBACK_IMAGE_FILE_NAME : result;
    }

    private String normalizeBaseUrl(String baseUrl) {
        return baseUrl.replaceAll("/+$", "");
    }

    private record NewAssets(
            List<StoredImage> storedImages,
            List<StudentCouncilNoticeImage> imageEntities,
            List<StoredAttachment> storedAttachments,
            List<StudentCouncilNoticeAttachment> attachmentEntities
    ) {
        private static NewAssets empty() {
            return new NewAssets(List.of(), List.of(), List.of(), List.of());
        }
    }
}

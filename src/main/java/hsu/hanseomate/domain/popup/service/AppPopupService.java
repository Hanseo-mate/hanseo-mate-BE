package hsu.hanseomate.domain.popup.service;

import hsu.hanseomate.domain.popup.dto.ActiveAppPopupResponse;
import hsu.hanseomate.domain.popup.dto.AppPopupCreateRequest;
import hsu.hanseomate.domain.popup.dto.AppPopupResponse;
import hsu.hanseomate.domain.popup.dto.AppPopupUpdateRequest;
import hsu.hanseomate.domain.popup.entity.AppPopup;
import hsu.hanseomate.domain.popup.exception.AppPopupNotFoundException;
import hsu.hanseomate.domain.popup.model.PopupNavigation;
import hsu.hanseomate.domain.popup.repository.AppPopupRepository;
import hsu.hanseomate.domain.popup.type.PopupImageAction;
import hsu.hanseomate.global.exception.BadRequestException;
import hsu.hanseomate.global.storage.LocalImageStorageService;
import hsu.hanseomate.global.storage.LocalImageStorageService.StoredImage;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AppPopupService {

    private static final String STORAGE_DIRECTORY = "app-popups";
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final AppPopupRepository appPopupRepository;
    private final LocalImageStorageService imageStorageService;
    private final PopupNavigationValidator navigationValidator;
    private final Clock clock;

    public List<ActiveAppPopupResponse> getActivePopups() {
        LocalDateTime now = now();
        return appPopupRepository.findAllActiveAt(now).stream()
                .map(popup -> ActiveAppPopupResponse.from(
                        popup,
                        currentImageUrl(popup)
                ))
                .toList();
    }

    public List<AppPopupResponse> getAdminPopups() {
        LocalDateTime now = now();
        return appPopupRepository.findAllByOrderByCreatedAtDescIdDesc().stream()
                .map(popup -> toAdminResponse(popup, now))
                .toList();
    }

    public AppPopupResponse getAdminPopup(Long popupId) {
        AppPopup popup = appPopupRepository.findById(popupId)
                .orElseThrow(() -> new AppPopupNotFoundException(popupId));
        return toAdminResponse(popup, now());
    }

    @Transactional
    public AppPopupResponse createPopup(
            AppPopupCreateRequest request,
            MultipartFile image
    ) {
        PopupNavigation navigation = navigationValidator.validateRequired(
                request.hasNavigation(),
                request.hasLegacyLinkUrl(),
                request.getNavigation()
        );
        validateSchedule(request.getStartsAt(), request.getEndsAt());
        StoredImage storedImage = image == null
                ? null
                : imageStorageService.store(image, STORAGE_DIRECTORY);

        try {
            AppPopup popup = appPopupRepository.saveAndFlush(AppPopup.create(
                    request.getTitle().trim(),
                    request.getContent(),
                    storedImage == null ? null : storedImage.url(),
                    navigation,
                    request.getEnabled(),
                    request.getStartsAt(),
                    request.getEndsAt(),
                    request.getDisplayOrder()
            ));
            registerCreatedImageCleanup(storedImage);
            return toAdminResponse(popup, now());
        } catch (RuntimeException exception) {
            imageStorageService.delete(storedImage);
            throw exception;
        }
    }

    @Transactional
    public AppPopupResponse updatePopup(
            Long popupId,
            AppPopupUpdateRequest request,
            MultipartFile image
    ) {
        PopupNavigation navigation = navigationValidator.validateRequired(
                request.hasNavigation(),
                request.hasLegacyLinkUrl(),
                request.getNavigation()
        );
        validateSchedule(request.getStartsAt(), request.getEndsAt());
        validateImageRequest(request.getImageAction(), image);

        AppPopup popup = findPopupForUpdate(popupId);
        String previousImageUrl = popup.getImageUrl();
        StoredImage storedImage = request.getImageAction() == PopupImageAction.REPLACE
                ? imageStorageService.store(image, STORAGE_DIRECTORY)
                : null;
        String nextImageUrl = switch (request.getImageAction()) {
            case KEEP -> previousImageUrl;
            case REPLACE -> storedImage.url();
            case REMOVE -> null;
        };

        try {
            popup.update(
                    request.getTitle().trim(),
                    request.getContent(),
                    nextImageUrl,
                    navigation,
                    request.getEnabled(),
                    request.getStartsAt(),
                    request.getEndsAt(),
                    request.getDisplayOrder()
            );
            appPopupRepository.flush();
            registerUpdatedImageCleanup(
                    request.getImageAction(),
                    storedImage,
                    previousImageUrl
            );
            return toAdminResponse(popup, now());
        } catch (RuntimeException exception) {
            imageStorageService.delete(storedImage);
            throw exception;
        }
    }

    @Transactional
    public AppPopupResponse updateEnabled(Long popupId, boolean enabled) {
        AppPopup popup = findPopupForUpdate(popupId);
        popup.updateEnabled(enabled);
        appPopupRepository.flush();
        return toAdminResponse(popup, now());
    }

    @Transactional
    public void deletePopup(Long popupId) {
        AppPopup popup = findPopupForUpdate(popupId);
        String imageUrl = popup.getImageUrl();

        appPopupRepository.delete(popup);
        appPopupRepository.flush();
        registerDeletedImageCleanup(imageUrl);
    }

    private AppPopup findPopupForUpdate(Long popupId) {
        return appPopupRepository.findByIdForUpdate(popupId)
                .orElseThrow(() -> new AppPopupNotFoundException(popupId));
    }

    private AppPopupResponse toAdminResponse(
            AppPopup popup,
            LocalDateTime now
    ) {
        return AppPopupResponse.from(popup, now, currentImageUrl(popup));
    }

    private String currentImageUrl(AppPopup popup) {
        return imageStorageService.currentPublicUrl(popup.getImageUrl());
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), KOREA_ZONE);
    }

    private void validateSchedule(
            LocalDateTime startsAt,
            LocalDateTime endsAt
    ) {
        if (startsAt != null && endsAt != null && !endsAt.isAfter(startsAt)) {
            throw new BadRequestException("노출 종료 시각은 시작 시각보다 뒤여야 합니다.");
        }
    }

    private void validateImageRequest(
            PopupImageAction imageAction,
            MultipartFile image
    ) {
        if (imageAction == PopupImageAction.REPLACE
                && (image == null || image.isEmpty())) {
            throw new BadRequestException("교체할 이미지 파일이 필요합니다.");
        }
        if (imageAction != PopupImageAction.REPLACE
                && image != null
                && !image.isEmpty()) {
            throw new BadRequestException(
                    "이미지를 업로드하려면 imageAction을 REPLACE로 지정해야 합니다."
            );
        }
    }

    private void registerCreatedImageCleanup(StoredImage storedImage) {
        if (storedImage == null
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            imageStorageService.delete(storedImage);
                        }
                    }
                }
        );
    }

    private void registerUpdatedImageCleanup(
            PopupImageAction imageAction,
            StoredImage storedImage,
            String previousImageUrl
    ) {
        if (imageAction == PopupImageAction.KEEP) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            imageStorageService.deleteIfManaged(previousImageUrl);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        imageStorageService.deleteIfManaged(previousImageUrl);
                    }

                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            imageStorageService.delete(storedImage);
                        }
                    }
                }
        );
    }

    private void registerDeletedImageCleanup(String imageUrl) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            imageStorageService.deleteIfManaged(imageUrl);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        imageStorageService.deleteIfManaged(imageUrl);
                    }
                }
        );
    }
}

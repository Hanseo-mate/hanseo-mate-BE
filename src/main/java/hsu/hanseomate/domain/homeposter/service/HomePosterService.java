package hsu.hanseomate.domain.homeposter.service;

import hsu.hanseomate.domain.homeposter.dto.HomePosterResponse;
import hsu.hanseomate.domain.homeposter.entity.HomePoster;
import hsu.hanseomate.domain.homeposter.exception.HomePosterNotFoundException;
import hsu.hanseomate.domain.homeposter.repository.HomePosterRepository;
import hsu.hanseomate.global.storage.LocalImageStorageService;
import hsu.hanseomate.global.storage.LocalImageStorageService.StoredImage;
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
public class HomePosterService {

    private static final String STORAGE_DIRECTORY = "home-posters";

    private final HomePosterRepository homePosterRepository;
    private final LocalImageStorageService imageStorageService;

    public List<HomePosterResponse> getPosters() {
        return homePosterRepository.findAllByOrderByIdAsc().stream()
                .map(HomePosterResponse::from)
                .toList();
    }

    @Transactional
    public HomePosterResponse createPoster(MultipartFile file, String linkUrl) {
        String normalizedLinkUrl = normalizeLinkUrl(linkUrl);
        StoredImage storedImage = imageStorageService.store(file, STORAGE_DIRECTORY);

        try {
            HomePoster poster = homePosterRepository.saveAndFlush(
                    HomePoster.create(storedImage.url(), normalizedLinkUrl)
            );
            registerCreatedImageCleanup(storedImage);
            return HomePosterResponse.from(poster);
        } catch (RuntimeException exception) {
            imageStorageService.delete(storedImage);
            throw exception;
        }
    }

    @Transactional
    public HomePosterResponse replacePoster(
            Long posterId,
            MultipartFile file,
            String linkUrl
    ) {
        HomePoster poster = findPosterForUpdate(posterId);
        String previousImageUrl = poster.getImageUrl();
        String normalizedLinkUrl = normalizeLinkUrl(linkUrl);
        StoredImage storedImage = imageStorageService.store(file, STORAGE_DIRECTORY);

        try {
            poster.update(storedImage.url(), normalizedLinkUrl);
            homePosterRepository.flush();
            registerReplacedImageCleanup(storedImage, previousImageUrl);
            return HomePosterResponse.from(poster);
        } catch (RuntimeException exception) {
            imageStorageService.delete(storedImage);
            throw exception;
        }
    }

    @Transactional
    public void deletePoster(Long posterId) {
        HomePoster poster = findPosterForUpdate(posterId);
        String imageUrl = poster.getImageUrl();

        homePosterRepository.delete(poster);
        homePosterRepository.flush();
        registerDeletedImageCleanup(imageUrl);
    }

    private HomePoster findPosterForUpdate(Long posterId) {
        return homePosterRepository.findByIdForUpdate(posterId)
                .orElseThrow(() -> new HomePosterNotFoundException(posterId));
    }

    private String normalizeLinkUrl(String linkUrl) {
        if (linkUrl == null || linkUrl.isBlank()) {
            return null;
        }
        return linkUrl.trim();
    }

    private void registerCreatedImageCleanup(StoredImage storedImage) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
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

    private void registerReplacedImageCleanup(
            StoredImage storedImage,
            String previousImageUrl
    ) {
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

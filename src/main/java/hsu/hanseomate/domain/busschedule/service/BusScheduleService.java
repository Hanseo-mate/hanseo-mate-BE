package hsu.hanseomate.domain.busschedule.service;

import hsu.hanseomate.domain.busschedule.dto.BusScheduleResponse;
import hsu.hanseomate.domain.busschedule.entity.BusSchedule;
import hsu.hanseomate.domain.busschedule.repository.BusScheduleRepository;
import hsu.hanseomate.domain.busschedule.type.MainCategory;
import hsu.hanseomate.domain.busschedule.type.SubCategory;
import hsu.hanseomate.global.storage.LocalImageStorageService;
import hsu.hanseomate.global.storage.LocalImageStorageService.StoredImage;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BusScheduleService {

    private static final String STORAGE_DIRECTORY = "bus";

    private final BusScheduleRepository busScheduleRepository;
    private final LocalImageStorageService imageStorageService;

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
        Optional<BusSchedule> existing =
                busScheduleRepository.findByMainCategoryAndSubCategory(mainCategory, subCategory);
        StoredImage storedImage = imageStorageService.store(image, STORAGE_DIRECTORY);
        String previousImageUrl = existing.map(BusSchedule::getImageUrl).orElse(null);

        try {
            BusSchedule schedule = existing.orElseGet(() -> busScheduleRepository.save(
                    BusSchedule.create(
                            mainCategory,
                            subCategory,
                            storedImage.url(),
                            storedImage.path().toString()
                    )
            ));
            if (existing.isPresent()) {
                schedule.update(storedImage.url(), storedImage.path().toString());
            }

            busScheduleRepository.flush();
            registerImageCleanup(storedImage, previousImageUrl);
            return BusScheduleResponse.from(schedule);
        } catch (RuntimeException exception) {
            imageStorageService.delete(storedImage);
            throw exception;
        }
    }

    private void registerImageCleanup(
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
}

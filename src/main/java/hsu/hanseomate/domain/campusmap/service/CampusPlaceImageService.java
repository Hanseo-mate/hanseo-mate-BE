package hsu.hanseomate.domain.campusmap.service;

import hsu.hanseomate.domain.campusmap.dto.CampusPlaceImageUploadResponse;
import hsu.hanseomate.global.storage.LocalImageStorageService;
import hsu.hanseomate.global.storage.LocalImageStorageService.StoredImage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class CampusPlaceImageService {

    private static final String STORAGE_DIRECTORY = "campus-places";

    private final LocalImageStorageService imageStorageService;

    public CampusPlaceImageUploadResponse upload(MultipartFile file) {
        StoredImage storedImage = imageStorageService.store(
                file,
                STORAGE_DIRECTORY
        );
        return new CampusPlaceImageUploadResponse(storedImage.url());
    }
}

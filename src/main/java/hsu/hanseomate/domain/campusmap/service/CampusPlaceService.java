package hsu.hanseomate.domain.campusmap.service;

import hsu.hanseomate.domain.campusmap.dto.CampusLectureBuildingDetailResponse;
import hsu.hanseomate.domain.campusmap.dto.CampusPlaceDetailResponse;
import hsu.hanseomate.domain.campusmap.dto.CampusPlaceListResponse;
import hsu.hanseomate.domain.campusmap.dto.CampusPlaceSummaryResponse;
import hsu.hanseomate.domain.campusmap.entity.CampusLectureBuildingDetail;
import hsu.hanseomate.domain.campusmap.entity.CampusPlace;
import hsu.hanseomate.domain.campusmap.repository.CampusLectureBuildingDetailRepository;
import hsu.hanseomate.domain.campusmap.repository.CampusPlaceRepository;
import hsu.hanseomate.domain.campusmap.type.CampusCode;
import hsu.hanseomate.domain.campusmap.type.CampusPlaceCategory;
import hsu.hanseomate.global.exception.ResourceNotFoundException;
import hsu.hanseomate.global.storage.LocalImageStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CampusPlaceService {

    private final CampusPlaceRepository campusPlaceRepository;
    private final CampusLectureBuildingDetailRepository lectureBuildingDetailRepository;
    private final LocalImageStorageService imageStorageService;

    public CampusPlaceListResponse getPlaces(
            CampusCode campusCode,
            CampusPlaceCategory category
    ) {
        return new CampusPlaceListResponse(campusPlaceRepository
                .findAllForMap(campusCode, category)
                .stream()
                .map(this::toSummary)
                .toList());
    }

    public CampusPlaceDetailResponse getPlace(Long placeId) {
        CampusPlace place = campusPlaceRepository.findById(placeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "캠퍼스 장소를 찾을 수 없습니다. id=" + placeId
                ));
        CampusPlaceCategory category = place.getCategory();
        return new CampusPlaceDetailResponse(
                place.getId(),
                place.getCampusCode(),
                place.getPlaceName(),
                category,
                categoryName(category),
                place.getOneLineDescription(),
                imageStorageService.currentPublicUrl(place.getImageUrl()),
                place.getLatitude().doubleValue(),
                place.getLongitude().doubleValue(),
                lectureBuildingDetails(place)
        );
    }

    private CampusPlaceSummaryResponse toSummary(CampusPlace place) {
        CampusPlaceCategory category = place.getCategory();
        return new CampusPlaceSummaryResponse(
                place.getId(),
                place.getCampusCode(),
                place.getPlaceName(),
                category,
                categoryName(category),
                place.getOneLineDescription(),
                imageStorageService.currentPublicUrl(place.getImageUrl()),
                place.getLatitude().doubleValue(),
                place.getLongitude().doubleValue()
        );
    }

    private String categoryName(CampusPlaceCategory category) {
        return category == null ? null : category.getDisplayName();
    }

    private CampusLectureBuildingDetailResponse lectureBuildingDetails(
            CampusPlace place
    ) {
        if (place.getCategory() != CampusPlaceCategory.LECTURE_BUILDING) {
            return null;
        }
        return lectureBuildingDetailRepository.findById(place.getId())
                .map(this::toLectureBuildingDetail)
                .orElse(null);
    }

    private CampusLectureBuildingDetailResponse toLectureBuildingDetail(
            CampusLectureBuildingDetail detail
    ) {
        return new CampusLectureBuildingDetailResponse(
                detail.getLocation(),
                detail.getFloorCount(),
                detail.getHasElevator(),
                detail.getOperatingHours(),
                detail.getDepartments(),
                detail.getMajorFacilities()
        );
    }
}

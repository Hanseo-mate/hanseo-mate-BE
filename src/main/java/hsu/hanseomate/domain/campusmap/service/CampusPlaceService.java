package hsu.hanseomate.domain.campusmap.service;

import hsu.hanseomate.domain.campusmap.dto.CampusLectureBuildingDetailResponse;
import hsu.hanseomate.domain.campusmap.dto.CampusLectureBuildingDetailUpdateRequest;
import hsu.hanseomate.domain.campusmap.dto.CampusPlaceDetailResponse;
import hsu.hanseomate.domain.campusmap.dto.CampusPlaceInformationUpdateRequest;
import hsu.hanseomate.domain.campusmap.dto.CampusPlaceListResponse;
import hsu.hanseomate.domain.campusmap.dto.CampusPlaceSummaryResponse;
import hsu.hanseomate.domain.campusmap.entity.CampusLectureBuildingDetail;
import hsu.hanseomate.domain.campusmap.entity.CampusPlace;
import hsu.hanseomate.domain.campusmap.repository.CampusLectureBuildingDetailRepository;
import hsu.hanseomate.domain.campusmap.repository.CampusPlaceRepository;
import hsu.hanseomate.domain.campusmap.support.CampusLocationNormalizer;
import hsu.hanseomate.domain.campusmap.type.CampusCode;
import hsu.hanseomate.domain.campusmap.type.CampusPlaceCategory;
import hsu.hanseomate.domain.cafeteria.entity.RestaurantType;
import hsu.hanseomate.domain.user.entity.UserAccount;
import hsu.hanseomate.domain.user.repository.UserAccountRepository;
import hsu.hanseomate.global.exception.BadRequestException;
import hsu.hanseomate.global.exception.ResourceNotFoundException;
import hsu.hanseomate.global.storage.LocalImageStorageService;
import jakarta.persistence.EntityManager;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CampusPlaceService {

    private final CampusPlaceRepository campusPlaceRepository;
    private final CampusLectureBuildingDetailRepository lectureBuildingDetailRepository;
    private final UserAccountRepository userAccountRepository;
    private final LocalImageStorageService imageStorageService;
    private final EntityManager entityManager;

    public CampusPlaceListResponse getPlaces(
            Optional<Long> currentUserId,
            CampusCode campusCode,
            CampusPlaceCategory category
    ) {
        CampusCode selectedCampusCode = selectedCampusCode(
                currentUserId,
                campusCode
        );
        return new CampusPlaceListResponse(
                selectedCampusCode,
                campusPlaceRepository
                        .findAllForMap(selectedCampusCode, category)
                        .stream()
                        .map(this::toSummary)
                        .toList()
        );
    }

    public CampusPlaceDetailResponse getPlace(Long placeId) {
        CampusPlace place = findPlace(placeId);
        return toDetail(place);
    }

    @Transactional
    public CampusPlaceDetailResponse createPlace(
            CampusPlaceInformationUpdateRequest request
    ) {
        String placeNameKey = placeNameKey(request.placeName());
        rejectDuplicatePlace(
                request.campusCode(),
                placeNameKey,
                null
        );
        validateCategoryDetails(
                request.category(),
                request.lectureBuildingDetails()
        );
        String address = address(request.category(), request.address());

        CampusPlace place = CampusPlace.create(
                request.campusCode(),
                request.placeName(),
                placeNameKey,
                request.latitude(),
                request.longitude(),
                request.category(),
                request.oneLineDescription(),
                address,
                request.imageUrl()
        );
        campusPlaceRepository.saveAndFlush(place);
        synchronizeLectureBuildingDetails(place, request);
        entityManager.flush();
        return toDetail(place);
    }

    @Transactional
    public CampusPlaceDetailResponse updatePlace(
            Long placeId,
            CampusPlaceInformationUpdateRequest request
    ) {
        CampusPlace place = findPlace(placeId);
        String placeNameKey = placeNameKey(request.placeName());
        rejectDuplicatePlace(
                request.campusCode(),
                placeNameKey,
                placeId
        );
        validateCategoryDetails(
                request.category(),
                request.lectureBuildingDetails()
        );
        String address = address(request.category(), request.address());

        place.update(
                request.campusCode(),
                request.placeName(),
                placeNameKey,
                request.latitude(),
                request.longitude(),
                request.category(),
                request.oneLineDescription(),
                address,
                request.imageUrl()
        );
        synchronizeLectureBuildingDetails(place, request);
        entityManager.flush();
        return toDetail(place);
    }

    @Transactional
    public void deletePlace(Long placeId) {
        CampusPlace place = findPlace(placeId);
        lectureBuildingDetailRepository.findById(placeId)
                .ifPresent(lectureBuildingDetailRepository::delete);
        lectureBuildingDetailRepository.flush();
        campusPlaceRepository.delete(place);
        campusPlaceRepository.flush();
    }

    private void synchronizeLectureBuildingDetails(
            CampusPlace place,
            CampusPlaceInformationUpdateRequest request
    ) {
        if (request.category() == CampusPlaceCategory.LECTURE_BUILDING) {
            saveLectureBuildingDetails(
                    place,
                    request.lectureBuildingDetails()
            );
            return;
        }
        lectureBuildingDetailRepository.findById(place.getId())
                .ifPresent(lectureBuildingDetailRepository::delete);
    }

    private CampusPlace findPlace(Long placeId) {
        return campusPlaceRepository.findById(placeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "캠퍼스 장소를 찾을 수 없습니다. id=" + placeId
                ));
    }

    private CampusPlaceDetailResponse toDetail(CampusPlace place) {
        CampusPlaceCategory category = place.getCategory();
        return new CampusPlaceDetailResponse(
                place.getId(),
                place.getCampusCode(),
                place.getPlaceName(),
                category,
                categoryName(category),
                place.getOneLineDescription(),
                place.getAddress(),
                imageStorageService.currentPublicUrl(place.getImageUrl()),
                place.getLatitude().doubleValue(),
                place.getLongitude().doubleValue(),
                lectureBuildingDetails(place)
        );
    }

    private void validateCategoryDetails(
            CampusPlaceCategory category,
            CampusLectureBuildingDetailUpdateRequest lectureDetails
    ) {
        if (category == CampusPlaceCategory.LECTURE_BUILDING
                && lectureDetails == null) {
            throw new BadRequestException(
                    "강의실 카테고리는 lectureBuildingDetails가 필요합니다."
            );
        }
        if (category != CampusPlaceCategory.LECTURE_BUILDING
                && lectureDetails != null) {
            throw new BadRequestException(
                    "강의실 이외의 카테고리에는 lectureBuildingDetails를 입력할 수 없습니다."
            );
        }
    }

    private String placeNameKey(String placeName) {
        String placeNameKey = CampusLocationNormalizer.normalize(placeName);
        if (placeNameKey.isEmpty()) {
            throw new BadRequestException(
                    "장소명에는 검색 가능한 문자가 필요합니다."
            );
        }
        return placeNameKey;
    }

    private String address(
            CampusPlaceCategory category,
            String address
    ) {
        if (category == CampusPlaceCategory.LECTURE_BUILDING) {
            if (address != null) {
                throw new BadRequestException(
                        "강의실 카테고리에는 address를 입력할 수 없습니다."
                );
            }
            return null;
        }
        if (address == null || address.isBlank()) {
            throw new BadRequestException(
                    "강의실 이외의 카테고리는 address가 필요합니다."
            );
        }
        return address.trim();
    }

    private void rejectDuplicatePlace(
            CampusCode campusCode,
            String placeNameKey,
            Long excludedPlaceId
    ) {
        boolean duplicated = excludedPlaceId == null
                ? campusPlaceRepository.existsByCampusCodeAndPlaceNameKey(
                        campusCode,
                        placeNameKey
                )
                : campusPlaceRepository.existsByCampusCodeAndPlaceNameKeyAndIdNot(
                        campusCode,
                        placeNameKey,
                        excludedPlaceId
                );
        if (duplicated) {
            throw new BadRequestException(
                    "같은 캠퍼스에 같은 이름의 장소가 이미 있습니다."
            );
        }
    }

    private void saveLectureBuildingDetails(
            CampusPlace place,
            CampusLectureBuildingDetailUpdateRequest request
    ) {
        List<String> departments = normalizeUniqueNames(
                request.departments(),
                "학과"
        );
        List<String> majorFacilities = normalizeUniqueNames(
                request.majorFacilities(),
                "주요시설"
        );
        CampusLectureBuildingDetail detail = lectureBuildingDetailRepository
                .findById(place.getId())
                .orElse(null);
        boolean isNew = detail == null;
        if (isNew) {
            detail = CampusLectureBuildingDetail.create(place);
        }
        detail.update(
                request.location().trim(),
                request.floorCount(),
                request.hasElevator(),
                request.operatingHours().trim(),
                departments,
                majorFacilities
        );
        if (isNew) {
            entityManager.persist(detail);
        }
    }

    private List<String> normalizeUniqueNames(
            List<String> names,
            String fieldName
    ) {
        List<String> normalizedNames = names.stream()
                .map(String::trim)
                .toList();
        Set<String> uniqueNames = new HashSet<>(normalizedNames);
        if (uniqueNames.size() != normalizedNames.size()) {
            throw new BadRequestException(fieldName + "에 중복된 이름이 있습니다.");
        }
        return normalizedNames;
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
                place.getAddress(),
                imageStorageService.currentPublicUrl(place.getImageUrl()),
                place.getLatitude().doubleValue(),
                place.getLongitude().doubleValue()
        );
    }

    private String categoryName(CampusPlaceCategory category) {
        return category == null ? null : category.getDisplayName();
    }

    private CampusCode selectedCampusCode(
            Optional<Long> currentUserId,
            CampusCode requestedCampusCode
    ) {
        if (requestedCampusCode != null) {
            return requestedCampusCode;
        }
        return currentUserId.map(this::preferredCampusCode).orElse(null);
    }

    private CampusCode preferredCampusCode(Long userId) {
        RestaurantType preferredRestaurantType = userAccountRepository
                .findById(userId)
                .map(UserAccount::getPreferredRestaurantType)
                .orElseThrow(() ->
                        new AuthenticationCredentialsNotFoundException(
                                "로그인이 필요합니다."
                        ));
        return switch (preferredRestaurantType) {
            case MAIN_STUDENT -> CampusCode.SEOSAN;
            case TAEAN_STUDENT -> CampusCode.TAEAN;
            default -> throw new IllegalStateException(
                    "지원하지 않는 선호 학생식당입니다."
            );
        };
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

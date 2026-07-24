package hsu.hanseomate.domain.club.service;

import hsu.hanseomate.domain.club.dto.ClubCreateRequest;
import hsu.hanseomate.domain.club.dto.ClubCreateResponse;
import hsu.hanseomate.domain.club.dto.ClubDetailResponse;
import hsu.hanseomate.domain.club.dto.ClubImageUploadResponse;
import hsu.hanseomate.domain.club.dto.ClubLikeRequest;
import hsu.hanseomate.domain.club.dto.ClubLikeResponse;
import hsu.hanseomate.domain.club.dto.ClubReviewOptionResponse;
import hsu.hanseomate.domain.club.dto.ClubReviewSaveRequest;
import hsu.hanseomate.domain.club.dto.ClubReviewSaveResponse;
import hsu.hanseomate.domain.club.dto.ClubReviewStatisticsResponse;
import hsu.hanseomate.domain.club.dto.ClubSummaryResponse;
import hsu.hanseomate.domain.club.dto.ClubUpdateRequest;
import hsu.hanseomate.domain.club.entity.Club;
import hsu.hanseomate.domain.club.entity.ClubLike;
import hsu.hanseomate.domain.club.entity.ClubReview;
import hsu.hanseomate.domain.club.repository.ClubLikeCountProjection;
import hsu.hanseomate.domain.club.repository.ClubLikeRepository;
import hsu.hanseomate.domain.club.repository.ClubRepository;
import hsu.hanseomate.domain.club.repository.ClubReviewCountProjection;
import hsu.hanseomate.domain.club.repository.ClubReviewRepository;
import hsu.hanseomate.domain.club.type.ClubCategory;
import hsu.hanseomate.domain.club.type.ClubImageType;
import hsu.hanseomate.domain.club.type.ClubReviewOption;
import hsu.hanseomate.global.exception.BadRequestException;
import hsu.hanseomate.global.exception.ResourceNotFoundException;
import hsu.hanseomate.global.storage.LocalImageStorageService;
import hsu.hanseomate.global.storage.LocalImageStorageService.StoredImage;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClubService {

    private static final int LIST_TOP_REVIEW_LIMIT = 2;
    private static final int DETAIL_TOP_REVIEW_LIMIT = 3;
    private static final int MAX_REVIEW_TAGS = 5;

    private final ClubRepository clubRepository;
    private final ClubLikeRepository clubLikeRepository;
    private final ClubReviewRepository clubReviewRepository;
    private final LocalImageStorageService imageStorageService;

    public List<ClubSummaryResponse> getClubs(String category) {
        List<Club> clubs = category == null || category.isBlank()
                ? clubRepository.findAllByOrderByIdAsc()
                : clubRepository.findAllByCategoryOrderByIdAsc(normalizeCategory(category));

        if (clubs.isEmpty()) {
            return List.of();
        }

        EngagementSnapshot snapshot = loadEngagement(clubIds(clubs));
        return clubs.stream()
                .map(club -> ClubSummaryResponse.from(
                        club,
                        snapshot.likeCount(club.getId()),
                        snapshot.topReviews(club.getId(), LIST_TOP_REVIEW_LIMIT)
                ))
                .toList();
    }

    public ClubDetailResponse getClub(Long clubId) {
        Club club = findClub(clubId);
        EngagementSnapshot snapshot = loadEngagement(List.of(clubId));
        return detailResponse(
                club,
                snapshot,
                clubReviewRepository.countByClubId(clubId)
        );
    }

    @Transactional
    public ClubCreateResponse createClub(ClubCreateRequest request) {
        String name = required(request.name());
        validateUniqueName(name, null);

        Club club = Club.create(
                name,
                normalizeCategory(request.category())
        );

        try {
            Club savedClub = clubRepository.saveAndFlush(club);
            return new ClubCreateResponse(savedClub.getId());
        } catch (DataIntegrityViolationException exception) {
            throw duplicateClubName(name);
        }
    }

    @Transactional
    public ClubImageUploadResponse updateBackgroundImage(Long clubId, MultipartFile file) {
        Club club = findClubForUpdate(clubId);
        return updateClubImage(
                club,
                file,
                ClubImageType.BACKGROUND,
                club.getBackgroundImageUrl(),
                club::updateBackgroundImage
        );
    }

    @Transactional
    public ClubImageUploadResponse updateProfileImage(Long clubId, MultipartFile file) {
        Club club = findClubForUpdate(clubId);
        return updateClubImage(
                club,
                file,
                ClubImageType.PROFILE,
                club.getProfileImageUrl(),
                club::updateProfileImage
        );
    }

    @Transactional
    public void updateClub(Long clubId, ClubUpdateRequest request) {
        Club club = findClubForUpdate(clubId);
        String name = required(request.name());
        validateUniqueName(name, clubId);

        club.update(
                name,
                required(request.shortDescription()),
                content(request.introduction()),
                content(request.activityContent()),
                optional(request.instagramUrl()),
                optional(request.kakaoTalkUrl()),
                content(request.recruitmentContent())
        );

        try {
            clubRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw duplicateClubName(name);
        }
    }

    @Transactional
    public void deleteClub(Long clubId) {
        Club club = findClubForUpdate(clubId);
        String profileImageUrl = club.getProfileImageUrl();
        String backgroundImageUrl = club.getBackgroundImageUrl();
        clubReviewRepository.deleteAllByClubId(clubId);
        clubReviewRepository.flush();
        clubLikeRepository.deleteAllByClubId(clubId);
        clubLikeRepository.flush();
        clubRepository.delete(club);
        clubRepository.flush();
        registerDeletedClubImageCleanup(profileImageUrl, backgroundImageUrl);
    }

    @Transactional
    public ClubLikeResponse setLike(Long clubId, ClubLikeRequest request) {
        Club club = findClubForUpdate(clubId);
        boolean requestedLike = Boolean.TRUE.equals(request.liked());

        if (requestedLike) {
            clubLikeRepository.save(ClubLike.create(club));
        } else {
            clubLikeRepository.findFirstByClubIdOrderByIdDesc(clubId)
                    .ifPresent(clubLikeRepository::delete);
        }
        clubLikeRepository.flush();

        return new ClubLikeResponse(clubId, requestedLike, clubLikeRepository.countByClubId(clubId));
    }

    public ClubReviewStatisticsResponse getReview(Long clubId) {
        findClub(clubId);
        return reviewStatistics(clubId);
    }

    @Transactional
    public ClubReviewSaveResponse saveReview(
            Long clubId,
            ClubReviewSaveRequest request
    ) {
        Club club = findClubForUpdate(clubId);
        List<ClubReviewOption> requestedTags = request == null ? null : request.reviewTags();

        if (requestedTags == null || requestedTags.isEmpty()) {
            clubReviewRepository.findFirstByClubIdOrderByIdDesc(clubId)
                    .ifPresent(clubReviewRepository::delete);
            clubReviewRepository.flush();
            return new ClubReviewSaveResponse("활동 후기가 삭제되었습니다.");
        }

        Set<ClubReviewOption> reviewTags = validateReviewTags(requestedTags);
        clubReviewRepository.saveAndFlush(ClubReview.create(club, reviewTags));
        return new ClubReviewSaveResponse("활동 후기가 등록되었습니다.");
    }

    private ClubReviewStatisticsResponse reviewStatistics(Long clubId) {
        EnumMap<ClubReviewOption, Long> counts = reviewCounts(List.of(clubId))
                .getOrDefault(clubId, new EnumMap<>(ClubReviewOption.class));
        long totalSelectionCount = counts.values().stream().mapToLong(Long::longValue).sum();
        List<ClubReviewOptionResponse> options = List.of(ClubReviewOption.values()).stream()
                .map(option -> ClubReviewOptionResponse.of(
                        option,
                        counts.getOrDefault(option, 0L),
                        totalSelectionCount
                ))
                .toList();

        return new ClubReviewStatisticsResponse(options);
    }

    private EngagementSnapshot loadEngagement(Collection<Long> clubIds) {
        Map<Long, Long> likeCounts = clubLikeRepository.countByClubIds(clubIds).stream()
                .collect(Collectors.toMap(
                        ClubLikeCountProjection::getClubId,
                        ClubLikeCountProjection::getLikeCount
                ));
        return new EngagementSnapshot(likeCounts, reviewCounts(clubIds));
    }

    private Map<Long, EnumMap<ClubReviewOption, Long>> reviewCounts(Collection<Long> clubIds) {
        Map<Long, EnumMap<ClubReviewOption, Long>> counts = new HashMap<>();
        for (ClubReviewCountProjection projection
                : clubReviewRepository.countReviewTagsByClubIds(clubIds)) {
            counts.computeIfAbsent(
                    projection.getClubId(),
                    ignored -> new EnumMap<>(ClubReviewOption.class)
            ).put(projection.getReviewTag(), projection.getReviewCount());
        }
        return counts;
    }

    private ClubDetailResponse detailResponse(
            Club club,
            EngagementSnapshot snapshot,
            long reviewerCount
    ) {
        return ClubDetailResponse.from(
                club,
                snapshot.likeCount(club.getId()),
                snapshot.topReviews(club.getId(), DETAIL_TOP_REVIEW_LIMIT),
                reviewerCount
        );
    }

    private ClubImageUploadResponse updateClubImage(
            Club club,
            MultipartFile file,
            ClubImageType imageType,
            String previousImageUrl,
            Consumer<String> imageUpdater
    ) {
        StoredImage storedImage = imageStorageService.store(
                file,
                imageType.storageDirectory()
        );

        try {
            imageUpdater.accept(storedImage.url());
            clubRepository.flush();
            registerImageCleanup(storedImage, previousImageUrl);
            return new ClubImageUploadResponse(storedImage.id(), storedImage.url());
        } catch (RuntimeException exception) {
            imageStorageService.delete(storedImage);
            throw exception;
        }
    }

    private void registerImageCleanup(StoredImage storedImage, String previousImageUrl) {
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

    private void registerDeletedClubImageCleanup(
            String profileImageUrl,
            String backgroundImageUrl
    ) {
        Runnable cleanup = () -> {
            imageStorageService.deleteIfManaged(profileImageUrl);
            imageStorageService.deleteIfManaged(backgroundImageUrl);
        };

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cleanup.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        cleanup.run();
                    }
                }
        );
    }

    private List<Long> clubIds(List<Club> clubs) {
        return clubs.stream().map(Club::getId).toList();
    }

    private Club findClub(Long clubId) {
        return clubRepository.findById(clubId)
                .orElseThrow(() -> clubNotFound(clubId));
    }

    private Club findClubForUpdate(Long clubId) {
        return clubRepository.findByIdForUpdate(clubId)
                .orElseThrow(() -> clubNotFound(clubId));
    }

    private ResourceNotFoundException clubNotFound(Long clubId) {
        return new ResourceNotFoundException("동아리를 찾을 수 없습니다. clubId=" + clubId);
    }

    private Set<ClubReviewOption> validateReviewTags(List<ClubReviewOption> reviewTags) {
        if (reviewTags == null
                || reviewTags.size() > MAX_REVIEW_TAGS) {
            throw new BadRequestException("활동 후기 태그는 최대 5개까지 선택할 수 있습니다.");
        }
        if (reviewTags.stream().anyMatch(java.util.Objects::isNull)) {
            throw new BadRequestException("활동 후기 태그에는 null을 포함할 수 없습니다.");
        }

        LinkedHashSet<ClubReviewOption> uniqueTags = new LinkedHashSet<>(reviewTags);
        if (uniqueTags.size() != reviewTags.size()) {
            throw new BadRequestException("같은 활동 후기 태그를 중복 선택할 수 없습니다.");
        }
        return EnumSet.copyOf(uniqueTags);
    }

    private void validateUniqueName(String name, Long currentClubId) {
        boolean duplicated = currentClubId == null
                ? clubRepository.existsByName(name)
                : clubRepository.existsByNameAndIdNot(name, currentClubId);
        if (duplicated) {
            throw duplicateClubName(name);
        }
    }

    private BadRequestException duplicateClubName(String name) {
        return new BadRequestException("이미 등록된 동아리명입니다. name=" + name);
    }

    private ClubCategory normalizeCategory(String category) {
        String normalized = required(category).toUpperCase(Locale.ROOT);
        try {
            return ClubCategory.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException(
                    "지원하지 않는 동아리 분과입니다. category=" + normalized
                            + ", allowed=ACADEMIC,VOLUNTEER,SPORTS,RELIGION,PERFORMANCE,HOBBY"
            );
        }
    }

    private String required(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("필수 입력값이 비어 있습니다.");
        }
        return value.trim();
    }

    private String optional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String content(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private record EngagementSnapshot(
            Map<Long, Long> likeCounts,
            Map<Long, EnumMap<ClubReviewOption, Long>> reviewCounts
    ) {

        private EngagementSnapshot {
            likeCounts = Map.copyOf(likeCounts);
            reviewCounts = copyReviewCounts(reviewCounts);
        }

        long likeCount(Long clubId) {
            return likeCounts.getOrDefault(clubId, 0L);
        }

        List<ClubReviewOption> topReviews(Long clubId, int limit) {
            return reviewCounts.getOrDefault(clubId, new EnumMap<>(ClubReviewOption.class))
                    .entrySet()
                    .stream()
                    .filter(entry -> entry.getValue() > 0)
                    .sorted((left, right) -> {
                        int countOrder = Long.compare(right.getValue(), left.getValue());
                        return countOrder != 0
                                ? countOrder
                                : Integer.compare(left.getKey().ordinal(), right.getKey().ordinal());
                    })
                    .limit(limit)
                    .map(Map.Entry::getKey)
                    .toList();
        }

        private static Map<Long, EnumMap<ClubReviewOption, Long>> copyReviewCounts(
                Map<Long, EnumMap<ClubReviewOption, Long>> source
        ) {
            Map<Long, EnumMap<ClubReviewOption, Long>> copy = new HashMap<>();
            source.forEach((clubId, counts) -> copy.put(clubId, new EnumMap<>(counts)));
            return Map.copyOf(copy);
        }
    }
}

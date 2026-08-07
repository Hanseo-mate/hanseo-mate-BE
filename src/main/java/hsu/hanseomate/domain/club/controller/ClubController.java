package hsu.hanseomate.domain.club.controller;

import hsu.hanseomate.domain.club.dto.ClubDetailResponse;
import hsu.hanseomate.domain.club.dto.ClubLikeRequest;
import hsu.hanseomate.domain.club.dto.ClubLikeResponse;
import hsu.hanseomate.domain.club.dto.ClubReviewMeResponse;
import hsu.hanseomate.domain.club.dto.ClubReviewSaveRequest;
import hsu.hanseomate.domain.club.dto.ClubReviewSaveResponse;
import hsu.hanseomate.domain.club.dto.ClubReviewStatisticsResponse;
import hsu.hanseomate.domain.club.dto.ClubSummaryResponse;
import hsu.hanseomate.domain.club.service.ClubService;
import hsu.hanseomate.global.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "사용자 동아리", description = "동아리 조회, 좋아요와 활동 후기를 제공합니다.")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clubs")
public class ClubController {

    private final ClubService clubService;

    @Operation(
            summary = "분과별 동아리 목록 조회",
            description = "분과 필터는 선택입니다. 유효한 JWT를 보내면 각 동아리의 본인 좋아요 여부를 함께 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "지원하지 않는 분과",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "잘못되거나 만료된 토큰",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @GetMapping
    public List<ClubSummaryResponse> getClubs(
            @Parameter(description = "분과 코드(ACADEMIC, VOLUNTEER, SPORTS, RELIGION, PERFORMANCE, HOBBY)")
            @RequestParam(required = false) String category,
            Authentication authentication
    ) {
        return clubService.getClubs(category, optionalCurrentUserId(authentication));
    }

    @Operation(
            summary = "동아리 상세 조회",
            description = "이미지, 이름, 한 줄 소개, 좋아요, 상위 활동 후기 3개, "
                    + "동아리 소개, 활동 내용, 문의 링크, 모집공고와 후기 작성 수를 조회합니다. "
                    + "유효한 JWT를 보내면 본인 좋아요 여부도 함께 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 동아리 ID",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "잘못되거나 만료된 토큰",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "동아리 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @GetMapping("/{clubId}")
    public ClubDetailResponse getClub(
            @Positive(message = "동아리 ID는 1 이상이어야 합니다.") @PathVariable Long clubId,
            Authentication authentication
    ) {
        return clubService.getClub(clubId, optionalCurrentUserId(authentication));
    }

    @Operation(
            summary = "좋아요 상태 설정",
            description = "로그인 사용자의 좋아요 상태를 설정합니다. 같은 상태를 반복 요청해도 중복 반영되지 않습니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "좋아요 상태 반영 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청값",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "로그인 필요",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "동아리 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @PutMapping("/likes/{clubId}")
    public ClubLikeResponse setLike(
            @Positive(message = "동아리 ID는 1 이상이어야 합니다.") @PathVariable Long clubId,
            @Valid @RequestBody ClubLikeRequest request,
            Authentication authentication
    ) {
        return clubService.setLike(clubId, currentUserId(authentication), request);
    }

    @Operation(
            summary = "활동 후기 통계 조회",
            description = "26개 키워드별 전체 선택표 대비 비율만 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 동아리 ID",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "동아리 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @GetMapping("/reviews/{clubId}")
    public ClubReviewStatisticsResponse getReview(
            @Positive(message = "동아리 ID는 1 이상이어야 합니다.") @PathVariable Long clubId
    ) {
        return clubService.getReview(clubId);
    }

    @Operation(
            summary = "내 활동 후기 조회",
            description = "로그인 사용자가 해당 동아리에 등록한 후기 여부와 선택한 태그를 반환합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 동아리 ID",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "로그인 필요",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "동아리 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @GetMapping("/reviews/{clubId}/me")
    public ClubReviewMeResponse getMyReview(
            @Positive(message = "동아리 ID는 1 이상이어야 합니다.") @PathVariable Long clubId,
            Authentication authentication
    ) {
        return clubService.getMyReview(clubId, currentUserId(authentication));
    }

    @Operation(
            summary = "내 활동 후기 등록·수정·제거",
            description = "로그인 사용자는 동아리별 후기 1건을 작성할 수 있습니다. "
                    + "1~5개 태그는 본인 후기를 등록하거나 수정하고, 빈 요청이나 빈 배열은 본인 후기를 제거합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "후기 반영 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 선택 개수, 중복 선택 또는 요청값",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "로그인 필요",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "동아리 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @PutMapping("/reviews/{clubId}")
    public ClubReviewSaveResponse saveReview(
            @Positive(message = "동아리 ID는 1 이상이어야 합니다.") @PathVariable Long clubId,
            @Valid @RequestBody(required = false) ClubReviewSaveRequest request,
            Authentication authentication
    ) {
        return clubService.saveReview(clubId, currentUserId(authentication), request);
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("로그인이 필요합니다.");
        }
        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException exception) {
            throw new AuthenticationCredentialsNotFoundException(
                    "유효하지 않은 인증 정보입니다.",
                    exception
            );
        }
    }

    private Optional<Long> optionalCurrentUserId(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return Optional.of(currentUserId(authentication));
    }
}

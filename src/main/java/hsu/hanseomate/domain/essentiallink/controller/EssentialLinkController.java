package hsu.hanseomate.domain.essentiallink.controller;

import hsu.hanseomate.domain.essentiallink.dto.EssentialLinkResponse;
import hsu.hanseomate.domain.essentiallink.service.EssentialLinkService;
import hsu.hanseomate.global.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "일반 사용자 링크 조회", description = "학교생활 필수 링크를 조회합니다.")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/links")
public class EssentialLinkController {

    private final EssentialLinkService essentialLinkService;

    @Operation(summary = "링크 목록 조회", description = "ID 오름차순으로 조회하며 카테고리 필터를 선택적으로 적용합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 카테고리",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @GetMapping
    public List<EssentialLinkResponse> getLinks(
            @Parameter(description = "조회할 카테고리")
            @RequestParam(required = false) String category
    ) {
        return essentialLinkService.getLinks(category);
    }

    @Operation(summary = "링크 상세 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 링크 ID",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "링크 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @GetMapping("/{linkId}")
    public EssentialLinkResponse getLink(
            @Parameter(description = "링크 ID", required = true)
            @Positive(message = "링크 ID는 1 이상이어야 합니다.")
            @PathVariable Long linkId
    ) {
        return essentialLinkService.getLink(linkId);
    }
}

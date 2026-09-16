package hsu.hanseomate.domain.essentiallink.controller;

import hsu.hanseomate.domain.essentiallink.dto.EssentialLinkCreateRequest;
import hsu.hanseomate.domain.essentiallink.dto.EssentialLinkResponse;
import hsu.hanseomate.domain.essentiallink.dto.EssentialLinkUpdateRequest;
import hsu.hanseomate.domain.essentiallink.service.EssentialLinkService;
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
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자 링크 관리", description = "학교생활 필수 링크를 조회, 등록, 수정, 삭제합니다.")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/links")
public class AdminEssentialLinkController {

    private final EssentialLinkService essentialLinkService;

    @Operation(
            summary = "링크 목록 조회",
            description = "ID 오름차순으로 조회하며 카테고리 필터를 선택적으로 적용합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 카테고리",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "403", description = "관리자 권한 필요")
    })
    @GetMapping
    public List<EssentialLinkResponse> getLinks(
            @Parameter(description = "조회할 카테고리")
            @RequestParam(required = false) String category
    ) {
        return essentialLinkService.getLinks(category);
    }

    @Operation(summary = "링크 등록")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청값",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @PostMapping
    public ResponseEntity<EssentialLinkResponse> createLink(
            @Valid @RequestBody EssentialLinkCreateRequest request
    ) {
        EssentialLinkResponse response = essentialLinkService.createLink(request);
        return ResponseEntity.created(URI.create("/api/links/" + response.id()))
                .body(response);
    }

    @Operation(summary = "링크 전체 수정")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청값 또는 링크 ID",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "링크 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @PutMapping("/{linkId}")
    public EssentialLinkResponse updateLink(
            @Parameter(description = "링크 ID", required = true)
            @Positive(message = "링크 ID는 1 이상이어야 합니다.")
            @PathVariable Long linkId,
            @Valid @RequestBody EssentialLinkUpdateRequest request
    ) {
        return essentialLinkService.updateLink(linkId, request);
    }

    @Operation(summary = "링크 삭제")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
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
    @DeleteMapping("/{linkId}")
    public ResponseEntity<Void> deleteLink(
            @Parameter(description = "링크 ID", required = true)
            @Positive(message = "링크 ID는 1 이상이어야 합니다.")
            @PathVariable Long linkId
    ) {
        essentialLinkService.deleteLink(linkId);
        return ResponseEntity.noContent().build();
    }
}

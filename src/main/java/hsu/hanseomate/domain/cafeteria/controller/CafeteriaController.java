package hsu.hanseomate.domain.cafeteria.controller;

import hsu.hanseomate.domain.cafeteria.dto.CafeteriaMenusResponse;
import hsu.hanseomate.domain.cafeteria.service.CafeteriaService;
import hsu.hanseomate.global.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 모바일/웹 클라이언트에게 식단 데이터를 제공하는 Read-Only API.
 * <p>
 * GET /api/cafeteria/menus
 * - menuDate       (선택): yyyy-MM-dd — 없으면 한국 기준 이번 주 월~금 반환
 */
@RestController
@RequestMapping("/api/cafeteria")
@Tag(name = "학식", description = "서산·태안 학생식당 식단을 조회합니다.")
public class CafeteriaController {

    private final CafeteriaService cafeteriaService;

    public CafeteriaController(CafeteriaService cafeteriaService) {
        this.cafeteriaService = cafeteriaService;
    }

    @Operation(
            summary = "서산·태안 학생식당 통합 조회",
            description = "두 학생식당 버킷을 항상 반환합니다. "
                    + "로그인하면 preferredRestaurantType이, "
                    + "비로그인이면 null이 반환됩니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = CafeteriaMenusResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "날짜 형식 오류",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "잘못되었거나 만료된 토큰",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(
                                    implementation = ApiErrorResponse.class
                            )
                    )
            )
    })
    @GetMapping(value = "/menus", produces = MediaType.APPLICATION_JSON_VALUE)
    public CafeteriaMenusResponse getMenus(
            @Parameter(
                    description = "특정 날짜만 조회합니다. 생략하면 한국 시간 기준 이번 주 월요일부터 금요일까지 조회합니다."
            )
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate menuDate,
            Authentication authentication
    ) {
        return cafeteriaService.getMenus(
                optionalCurrentUserId(authentication),
                menuDate
        );
    }

    private Optional<Long> optionalCurrentUserId(
            Authentication authentication
    ) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.valueOf(authentication.getName()));
        } catch (NumberFormatException exception) {
            throw new AuthenticationCredentialsNotFoundException(
                    "유효하지 않은 인증 정보입니다.",
                    exception
            );
        }
    }
}

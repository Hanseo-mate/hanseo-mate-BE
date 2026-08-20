package hsu.hanseomate.domain.cafeteria.controller;

import hsu.hanseomate.domain.cafeteria.dto.DailyMenuDTO;
import hsu.hanseomate.domain.cafeteria.entity.MenuCategory;
import hsu.hanseomate.domain.cafeteria.entity.RestaurantType;
import hsu.hanseomate.domain.cafeteria.service.CafeteriaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 모바일/웹 클라이언트에게 식단 데이터를 제공하는 Read-Only API.
 * <p>
 * GET /api/cafeteria/menus
 * - restaurantType (필수): MAIN_STUDENT | TAEAN_STUDENT
 * - menuDate       (선택): yyyy-MM-dd — 없으면 한국 기준 이번 주 월~금 반환
 * - menuCategory   (선택): KOREAN | SPECIAL | NORMAL — 없으면 모든 코너 반환
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
            summary = "학생식당별 식단 조회",
            description = "MAIN_STUDENT는 서산 학생식당, TAEAN_STUDENT는 태안 학생식당입니다."
    )
    @GetMapping("/menus")
    public ResponseEntity<List<DailyMenuDTO>> getMenus(
            @Parameter(
                    description = "조회할 학생식당",
                    required = true,
                    schema = @Schema(allowableValues = {
                            "MAIN_STUDENT",
                            "TAEAN_STUDENT"
                    })
            )
            @RequestParam RestaurantType restaurantType,
            @Parameter(
                    description = "특정 날짜만 조회합니다. 생략하면 한국 시간 기준 이번 주 월요일부터 금요일까지 조회합니다."
            )
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate menuDate,
            @RequestParam(required = false) MenuCategory menuCategory
    ) {
        List<DailyMenuDTO> result = cafeteriaService.getMenus(
                restaurantType,
                menuDate,
                menuCategory
        );
        return ResponseEntity.ok(result);
    }
}

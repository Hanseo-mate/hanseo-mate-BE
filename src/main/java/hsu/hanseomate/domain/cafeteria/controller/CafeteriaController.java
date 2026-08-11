package hsu.hanseomate.domain.cafeteria.controller;

import hsu.hanseomate.domain.cafeteria.dto.DailyMenuDTO;
import hsu.hanseomate.domain.cafeteria.entity.MenuCategory;
import hsu.hanseomate.domain.cafeteria.entity.RestaurantType;
import hsu.hanseomate.domain.cafeteria.service.CafeteriaService;
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
 * - restaurantType (필수): MAIN_STUDENT | MAIN_STAFF | TAEAN_STUDENT | TAEAN_STAFF
 * - menuDate       (선택): yyyy-MM-dd — 없으면 이번 주 전체 반환
 * - menuCategory   (선택): KOREAN | SPECIAL | NORMAL — 없으면 모든 코너 반환
 */
@RestController
@RequestMapping("/api/cafeteria")
public class CafeteriaController {

    private final CafeteriaService cafeteriaService;

    public CafeteriaController(CafeteriaService cafeteriaService) {
        this.cafeteriaService = cafeteriaService;
    }

    @GetMapping("/menus")
    public ResponseEntity<List<DailyMenuDTO>> getMenus(
            @RequestParam RestaurantType restaurantType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate menuDate,
            @RequestParam(required = false) MenuCategory menuCategory
    ) {
        List<DailyMenuDTO> result = cafeteriaService.getMenus(restaurantType, menuDate, menuCategory);
        return ResponseEntity.ok(result);
    }
}

package hsu.hanseomate.domain.cafeteria.service;

import hsu.hanseomate.domain.cafeteria.dto.CafeteriaMenusResponse;
import hsu.hanseomate.domain.cafeteria.dto.CafeteriaRestaurantMenusResponse;
import hsu.hanseomate.domain.cafeteria.dto.DailyMenuDTO;
import hsu.hanseomate.domain.cafeteria.entity.DailyMenu;
import hsu.hanseomate.domain.cafeteria.entity.MenuCategory;
import hsu.hanseomate.domain.cafeteria.entity.RestaurantType;
import hsu.hanseomate.domain.cafeteria.repository.DailyMenuRepository;
import hsu.hanseomate.domain.user.entity.UserAccount;
import hsu.hanseomate.domain.user.repository.UserAccountRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CafeteriaService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final List<RestaurantType> STUDENT_RESTAURANTS = List.of(
            RestaurantType.MAIN_STUDENT,
            RestaurantType.TAEAN_STUDENT
    );

    private final DailyMenuRepository dailyMenuRepository;
    private final UserAccountRepository userAccountRepository;
    private final Clock clock;

    public CafeteriaService(
            DailyMenuRepository dailyMenuRepository,
            UserAccountRepository userAccountRepository,
            Clock clock
    ) {
        this.dailyMenuRepository = dailyMenuRepository;
        this.userAccountRepository = userAccountRepository;
        this.clock = clock;
    }

    /**
     * 조건에 맞는 식단 목록을 조회하여 DTO 리스트로 반환한다.
     *
     * @param currentUserId  선택 — 로그인 사용자 ID
     * @param menuDate       선택 — null 이면 한국 기준 이번 주 월~금 조회
     * @param menuCategory   선택 — null 이면 모든 카테고리 반환
     * @return 선호 식당과 서산·태안 식단 버킷
     */
    public CafeteriaMenusResponse getMenus(
            Optional<Long> currentUserId,
            LocalDate menuDate,
            MenuCategory menuCategory
    ) {
        LocalDate startDate = menuDate;
        LocalDate endDate = menuDate;
        if (menuDate == null) {
            LocalDate today = LocalDate.now(clock.withZone(KOREA_ZONE));
            startDate = today.with(DayOfWeek.MONDAY);
            endDate = startDate.plusDays(4);
        }

        List<DailyMenu> dailyMenus = dailyMenuRepository.findMenus(
                STUDENT_RESTAURANTS,
                startDate,
                endDate,
                menuCategory
        );

        Map<RestaurantType, List<DailyMenuDTO>> menusByRestaurant =
                new EnumMap<>(RestaurantType.class);
        for (RestaurantType restaurantType : STUDENT_RESTAURANTS) {
            menusByRestaurant.put(
                    restaurantType,
                    dailyMenus.stream()
                            .filter(menu -> menu.getRestaurantType()
                                    == restaurantType)
                            .map(DailyMenuDTO::from)
                            .toList()
            );
        }

        List<CafeteriaRestaurantMenusResponse> restaurants =
                STUDENT_RESTAURANTS.stream()
                        .map(restaurantType ->
                                new CafeteriaRestaurantMenusResponse(
                                        restaurantType,
                                        menusByRestaurant.get(restaurantType)
                                ))
                        .toList();

        return new CafeteriaMenusResponse(
                preferredRestaurantType(currentUserId),
                restaurants
        );
    }

    private RestaurantType preferredRestaurantType(
            Optional<Long> currentUserId
    ) {
        return currentUserId.map(userId -> userAccountRepository.findById(userId)
                        .map(UserAccount::getPreferredRestaurantType)
                        .orElseThrow(() ->
                                new AuthenticationCredentialsNotFoundException(
                                        "로그인이 필요합니다."
                                )))
                .orElse(null);
    }
}

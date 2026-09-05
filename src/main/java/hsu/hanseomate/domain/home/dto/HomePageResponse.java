package hsu.hanseomate.domain.home.dto;

import hsu.hanseomate.domain.cafeteria.entity.RestaurantType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record HomePageResponse(
        boolean loggedIn,
        @Schema(nullable = true, allowableValues = {"MAIN_STUDENT", "TAEAN_STUDENT"})
        RestaurantType preferredRestaurantType,
        @Schema(nullable = true)
        List<String> posterImageUrls,
        @Schema(nullable = true)
        List<HomePosterItemResponse> posters,
        List<HomeTodayCourseResponse> todayCourses,
        List<HomeNoticeResponse> popularNotices,
        List<HomeCafeteriaMenuResponse> todayCafeteriaMenus,
        @Schema(
                description = "축제 플로팅 버튼 노출 여부. 설정이 없으면 false",
                requiredMode = Schema.RequiredMode.REQUIRED,
                defaultValue = "false"
        )
        boolean festivalFloatingButtonVisible
) {
    public HomePageResponse {
        posterImageUrls = posterImageUrls == null
                ? null
                : List.copyOf(posterImageUrls);
        posters = posters == null ? null : List.copyOf(posters);
        todayCourses = List.copyOf(todayCourses);
        popularNotices = List.copyOf(popularNotices);
        todayCafeteriaMenus = List.copyOf(todayCafeteriaMenus);
    }
}

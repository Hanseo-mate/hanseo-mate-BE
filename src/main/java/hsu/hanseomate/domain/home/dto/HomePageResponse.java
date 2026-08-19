package hsu.hanseomate.domain.home.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record HomePageResponse(
        boolean loggedIn,
        @Schema(nullable = true)
        List<String> posterImageUrls,
        @Schema(nullable = true)
        List<HomePosterItemResponse> posters,
        List<HomeTodayCourseResponse> todayCourses,
        List<HomeNoticeResponse> popularNotices
) {
    public HomePageResponse {
        posterImageUrls = posterImageUrls == null
                ? null
                : List.copyOf(posterImageUrls);
        posters = posters == null ? null : List.copyOf(posters);
        todayCourses = List.copyOf(todayCourses);
        popularNotices = List.copyOf(popularNotices);
    }
}

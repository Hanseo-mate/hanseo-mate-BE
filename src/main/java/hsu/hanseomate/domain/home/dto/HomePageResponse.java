package hsu.hanseomate.domain.home.dto;

import java.util.List;

public record HomePageResponse(
        boolean loggedIn,
        List<String> posterImageUrls,
        List<HomeTodayCourseResponse> todayCourses,
        List<HomeNoticeResponse> popularNotices
) {
    public HomePageResponse {
        posterImageUrls = posterImageUrls == null
                ? null
                : List.copyOf(posterImageUrls);
        todayCourses = List.copyOf(todayCourses);
        popularNotices = List.copyOf(popularNotices);
    }
}

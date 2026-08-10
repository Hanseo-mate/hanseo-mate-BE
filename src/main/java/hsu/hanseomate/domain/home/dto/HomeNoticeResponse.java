package hsu.hanseomate.domain.home.dto;

public record HomeNoticeResponse(
        HomeNoticeType noticeType,
        String title
) {
}

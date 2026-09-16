package hsu.hanseomate.domain.notices.dto;

import java.util.List;

public record CrawlRunRequest(
        List<String> noticeTypes,
        String mode
) {
}

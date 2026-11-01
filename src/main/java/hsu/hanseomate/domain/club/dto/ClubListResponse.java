package hsu.hanseomate.domain.club.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ClubListResponse(
        @Schema(description = "분과와 모집중만 보기 필터를 모두 적용한 검색결과 개수")
        int totalCount,
        @Schema(description = "관리자가 정한 표시 순서의 검색결과 목록")
        List<ClubSummaryResponse> clubs
) {

    public ClubListResponse {
        clubs = List.copyOf(clubs);
    }

    public static ClubListResponse from(List<ClubSummaryResponse> clubs) {
        return new ClubListResponse(clubs.size(), clubs);
    }
}

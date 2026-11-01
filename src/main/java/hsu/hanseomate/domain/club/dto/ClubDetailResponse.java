package hsu.hanseomate.domain.club.dto;

import hsu.hanseomate.domain.club.entity.Club;
import hsu.hanseomate.domain.club.type.ClubReviewOption;
import hsu.hanseomate.domain.club.type.ClubRecruitmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

public record ClubDetailResponse(
        String backgroundImageUrl,
        String profileImageUrl,
        long likeCount,
        boolean likedByMe,
        String name,
        String shortDescription,
        List<ClubReviewOption> topReviewTags,
        String introduction,
        String activityContent,
        String instagramUrl,
        String kakaoTalkUrl,
        String recruitmentContent,
        long reviewerCount,
        LocalDate recruitmentStartDate,
        LocalDate recruitmentEndDate,
        ClubRecruitmentStatus recruitmentStatus,
        String recruitmentUrl,
        @Schema(description = "모집 시작일의 요일. 시작일이 없으면 null이며 조회 화면 표시용입니다.",
                example = "SUNDAY", nullable = true, accessMode = Schema.AccessMode.READ_ONLY)
        DayOfWeek recruitmentStartDayOfWeek,
        @Schema(description = "모집 종료일의 요일. 종료일이 없으면 null이며 조회 화면 표시용입니다.",
                example = "SATURDAY", nullable = true, accessMode = Schema.AccessMode.READ_ONLY)
        DayOfWeek recruitmentEndDayOfWeek
) {

    public ClubDetailResponse {
        topReviewTags = List.copyOf(topReviewTags);
    }

    public static ClubDetailResponse from(
            Club club,
            long likeCount,
            boolean likedByMe,
            List<ClubReviewOption> topReviewTags,
            long reviewerCount,
            LocalDate today
    ) {
        return new ClubDetailResponse(
                club.getBackgroundImageUrl(),
                club.getProfileImageUrl(),
                likeCount,
                likedByMe,
                club.getName(),
                club.getShortDescription(),
                topReviewTags,
                club.getIntroduction(),
                club.getActivityContent(),
                club.getInstagramUrl(),
                club.getKakaoTalkUrl(),
                club.getRecruitmentContent(),
                reviewerCount,
                club.getRecruitmentStartDate(),
                club.getRecruitmentEndDate(),
                club.recruitmentStatus(today),
                club.getRecruitmentUrl(),
                dayOfWeek(club.getRecruitmentStartDate()),
                dayOfWeek(club.getRecruitmentEndDate())
        );
    }

    private static DayOfWeek dayOfWeek(LocalDate date) {
        return date == null ? null : date.getDayOfWeek();
    }
}

package hsu.hanseomate.domain.club.dto;

import hsu.hanseomate.domain.club.type.ClubReviewOption;
import java.math.BigDecimal;
import java.math.RoundingMode;

public record ClubReviewOptionResponse(
        ClubReviewOption reviewTag,
        BigDecimal percentage
) {

    public static ClubReviewOptionResponse of(
            ClubReviewOption reviewTag,
            long count,
            long totalSelectionCount
    ) {
        return new ClubReviewOptionResponse(
                reviewTag,
                percentage(count, totalSelectionCount)
        );
    }

    private static BigDecimal percentage(long count, long totalSelectionCount) {
        if (totalSelectionCount == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(count)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalSelectionCount), 2, RoundingMode.HALF_UP);
    }
}

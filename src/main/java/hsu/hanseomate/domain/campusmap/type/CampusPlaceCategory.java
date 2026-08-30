package hsu.hanseomate.domain.campusmap.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CampusPlaceCategory {
    RESTAURANT("음식점"),
    CAFE("카페"),
    LECTURE_BUILDING("교내시설"),
    CONVENIENCE_FACILITY("편의시설");

    private final String displayName;
}

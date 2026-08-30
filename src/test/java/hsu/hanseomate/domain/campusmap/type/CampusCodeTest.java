package hsu.hanseomate.domain.campusmap.type;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CampusCodeTest {

    @Test
    void mapsVerifiedSeosanBuildingCodesWithoutAddingApiEnumValues() {
        assertThat(CampusCode.from("H01")).contains(CampusCode.SEOSAN);
        assertThat(CampusCode.from("H02")).contains(CampusCode.SEOSAN);
        assertThat(CampusCode.from("H17")).contains(CampusCode.SEOSAN);
        assertThat(CampusCode.from("h 02")).contains(CampusCode.SEOSAN);
        assertThat(CampusCode.values()).containsExactly(
                CampusCode.SEOSAN,
                CampusCode.TAEAN
        );
    }

    @Test
    void rejectsUnverifiedBuildingCodes() {
        assertThat(CampusCode.from("H18")).isEmpty();
        assertThat(CampusCode.from("H99")).isEmpty();
        assertThat(CampusCode.from("")).isEmpty();
        assertThat(CampusCode.from(null)).isEmpty();
    }
}

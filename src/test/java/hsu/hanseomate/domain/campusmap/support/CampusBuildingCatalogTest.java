package hsu.hanseomate.domain.campusmap.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CampusBuildingCatalogTest {

    private final CampusBuildingCatalog catalog = new CampusBuildingCatalog();

    @Test
    void resolvesKnownSeosanAliasWithCanonicalNameAndCoordinates() {
        assertThat(catalog.find("서산캠퍼스", "인문관"))
                .hasValueSatisfying(location -> {
                    assertThat(location.campusCode()).isEqualTo("SEOSAN");
                    assertThat(location.canonicalBuildingName()).isEqualTo("인문사회관");
                    assertThat(location.latitude()).isEqualTo(36.6900568);
                    assertThat(location.longitude()).isEqualTo(126.5858982);
                });
    }

    @Test
    void resolvesKnownTaeanAliasWithCanonicalNameAndCoordinates() {
        assertThat(catalog.find(null, "태안강의동 본관"))
                .hasValueSatisfying(location -> {
                    assertThat(location.campusCode()).isEqualTo("TAEAN");
                    assertThat(location.canonicalBuildingName())
                            .isEqualTo("태안 강의동(본관)");
                    assertThat(location.latitude()).isEqualTo(36.5944988);
                    assertThat(location.longitude()).isEqualTo(126.294045);
                });
    }

    @Test
    void bareMainBuildingWithoutCampusRemainsAmbiguous() {
        assertThat(catalog.find(null, "본관")).isEmpty();
    }

    @Test
    void exactFlightEducationCenterAliasMapsButLongerSpecialTextDoesNot() {
        assertThat(catalog.find("TAEAN", "비행교육원"))
                .hasValueSatisfying(location -> {
                    assertThat(location.campusCode()).isEqualTo("TAEAN");
                    assertThat(location.canonicalBuildingName())
                            .isEqualTo("태안 강의동(본관)");
                    assertThat(location.latitude()).isEqualTo(36.5944988);
                    assertThat(location.longitude()).isEqualTo(126.294045);
                });

        assertThat(catalog.find("TAEAN", "비행교육원자체편성")).isEmpty();
    }

    @Test
    void unknownBuildingRemainsUnmapped() {
        assertThat(catalog.find("SEOSAN", "존재하지않는관")).isEmpty();
    }

    @Test
    void unsupportedNonBlankCampusDoesNotFallBackToUniqueBuildingName() {
        assertThat(catalog.find("TAEAN-TYPO", "공학관")).isEmpty();
    }

    @Test
    void knownConflictingCampusDoesNotMapUniqueBuildingFromOtherCampus() {
        assertThat(catalog.find("TAEAN", "공학관")).isEmpty();
    }
}

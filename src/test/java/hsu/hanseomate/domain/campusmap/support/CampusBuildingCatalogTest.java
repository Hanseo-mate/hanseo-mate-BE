package hsu.hanseomate.domain.campusmap.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hsu.hanseomate.domain.campusmap.entity.CampusBuilding;
import hsu.hanseomate.domain.campusmap.entity.CampusBuildingAlias;
import hsu.hanseomate.domain.campusmap.repository.CampusBuildingAliasRepository;
import hsu.hanseomate.domain.campusmap.repository.CampusBuildingRepository;
import hsu.hanseomate.domain.campusmap.type.CampusCode;
import hsu.hanseomate.global.config.JpaAuditingConfig;
import hsu.hanseomate.global.config.QueryDslConfig;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({
        CampusBuildingCatalog.class,
        JpaAuditingConfig.class,
        QueryDslConfig.class
})
class CampusBuildingCatalogTest {

    @Autowired
    private CampusBuildingCatalog catalog;

    @Autowired
    private CampusBuildingRepository buildingRepository;

    @Autowired
    private CampusBuildingAliasRepository aliasRepository;

    @BeforeEach
    void setUp() {
        CampusBuilding humanities = saveBuilding(
                CampusCode.SEOSAN,
                "인문사회관",
                "36.6900568",
                "126.5858982"
        );
        CampusBuilding seosanMain = saveBuilding(
                CampusCode.SEOSAN,
                "자악관",
                "36.6914647",
                "126.5889642"
        );
        CampusBuilding engineering = saveBuilding(
                CampusCode.SEOSAN,
                "공학관",
                "36.6909679",
                "126.5858094"
        );
        saveBuilding(
                CampusCode.SEOSAN,
                "이학관",
                "36.690669",
                "126.581760"
        );
        saveBuilding(
                CampusCode.SEOSAN,
                "영암관",
                "36.691341",
                "126.582453"
        );
        CampusBuilding taeAnMain = saveBuilding(
                CampusCode.TAEAN,
                "태안 강의동(본관)",
                "36.5944988",
                "126.294045"
        );
        aliasRepository.saveAllAndFlush(List.of(
                CampusBuildingAlias.create(humanities, "인문관"),
                CampusBuildingAlias.create(seosanMain, "본관"),
                CampusBuildingAlias.create(engineering, "공학관"),
                CampusBuildingAlias.create(taeAnMain, "본관"),
                CampusBuildingAlias.create(taeAnMain, "태안강의동 본관"),
                CampusBuildingAlias.create(taeAnMain, "비행교육원")
        ));
    }

    @Test
    void resolvesKnownSeosanAliasWithCanonicalNameAndCoordinates() {
        assertThat(catalog.find("서산캠퍼스", "인문관"))
                .hasValueSatisfying(location -> {
                    assertThat(location.campusCode()).isEqualTo(CampusCode.SEOSAN);
                    assertThat(location.canonicalBuildingName()).isEqualTo("인문사회관");
                    assertThat(location.latitude()).isEqualTo(36.6900568);
                    assertThat(location.longitude()).isEqualTo(126.5858982);
                });
    }

    @Test
    void resolvesCanonicalNameEvenWhenNoMatchingAliasRowExists() {
        assertThat(catalog.find("SEOSAN", "인문사회관"))
                .hasValueSatisfying(location -> {
                    assertThat(location.campusCode()).isEqualTo(CampusCode.SEOSAN);
                    assertThat(location.canonicalBuildingName())
                            .isEqualTo("인문사회관");
                    assertThat(location.latitude()).isEqualTo(36.6900568);
                    assertThat(location.longitude()).isEqualTo(126.5858982);
                });
    }

    @Test
    void resolvesKnownTaeanAliasWithCanonicalNameAndCoordinates() {
        assertThat(catalog.find(null, "태안강의동 본관"))
                .hasValueSatisfying(location -> {
                    assertThat(location.campusCode()).isEqualTo(CampusCode.TAEAN);
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
                    assertThat(location.campusCode()).isEqualTo(CampusCode.TAEAN);
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
    void resolvesVerifiedHBuildingCodesAsSeosanCampus() {
        assertThat(catalog.find("H01", "이학관"))
                .hasValueSatisfying(location -> {
                    assertThat(location.campusCode())
                            .isEqualTo(CampusCode.SEOSAN);
                    assertThat(location.canonicalBuildingName())
                            .isEqualTo("이학관");
                });
        assertThat(catalog.find("H02", "영암관"))
                .hasValueSatisfying(location -> {
                    assertThat(location.campusCode())
                            .isEqualTo(CampusCode.SEOSAN);
                    assertThat(location.canonicalBuildingName())
                            .isEqualTo("영암관");
                });
        assertThat(catalog.find("H02", "태안 강의동(본관)")).isEmpty();
    }

    @Test
    void unsupportedHCodeDoesNotFallBackToUniqueBuildingName() {
        assertThat(catalog.find("H99", "공학관")).isEmpty();
    }

    @Test
    void unsupportedNonBlankCampusDoesNotFallBackToUniqueBuildingName() {
        assertThat(catalog.find("TAEAN-TYPO", "공학관")).isEmpty();
    }

    @Test
    void knownConflictingCampusDoesNotMapUniqueBuildingFromOtherCampus() {
        assertThat(catalog.find("TAEAN", "공학관")).isEmpty();
    }

    @Test
    void sameAliasCanExistAcrossCampusesButNotTwiceInOneCampus() {
        assertThat(catalog.find("SEOSAN", "본관"))
                .get()
                .extracting(CampusBuildingCatalog.CampusBuildingLocation
                        ::canonicalBuildingName)
                .isEqualTo("자악관");
        assertThat(catalog.find("TAEAN", "본관"))
                .get()
                .extracting(CampusBuildingCatalog.CampusBuildingLocation
                        ::canonicalBuildingName)
                .isEqualTo("태안 강의동(본관)");

        CampusBuilding duplicateAliasTarget = saveBuilding(
                CampusCode.SEOSAN,
                "별관",
                "36.6900000",
                "126.5800000"
        );
        assertThatThrownBy(() -> aliasRepository.saveAndFlush(
                CampusBuildingAlias.create(duplicateAliasTarget, "본관")
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingBuildingCascadesItsAliases() {
        long aliasesBeforeDelete = aliasRepository.count();
        CampusBuilding humanities = buildingRepository
                .findAllByCanonicalNameKeyIn(Set.of(
                        CampusLocationNormalizer.normalize("인문사회관")
                ))
                .get(0);

        buildingRepository.delete(humanities);
        buildingRepository.flush();

        assertThat(aliasRepository.count()).isEqualTo(aliasesBeforeDelete - 1);
    }

    private CampusBuilding saveBuilding(
            CampusCode campusCode,
            String canonicalName,
            String latitude,
            String longitude
    ) {
        return buildingRepository.saveAndFlush(CampusBuilding.create(
                campusCode,
                canonicalName,
                new BigDecimal(latitude),
                new BigDecimal(longitude)
        ));
    }
}

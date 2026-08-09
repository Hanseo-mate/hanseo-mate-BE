package hsu.hanseomate.domain.timetable.search.dto;

import static org.assertj.core.api.Assertions.assertThat;

import hsu.hanseomate.domain.courseimport.dto.type.DeliveryProvider;
import hsu.hanseomate.domain.courseimport.dto.type.GeneralArea;
import hsu.hanseomate.domain.courseimport.dto.type.GeneralClassification;
import hsu.hanseomate.domain.timetable.search.type.GeneralCategoryFilter;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CourseOfferingResponseTest {

    @Test
    void cyberProvidersFollowTheResponsePolicy() {
        assertThat(CourseOfferingResponse.isCyberProvider(DeliveryProvider.OCU)).isTrue();
        assertThat(CourseOfferingResponse.isCyberProvider(DeliveryProvider.SDU)).isTrue();
        assertThat(CourseOfferingResponse.isCyberProvider(DeliveryProvider.HSU_CYBER)).isTrue();
        assertThat(CourseOfferingResponse.isCyberProvider(DeliveryProvider.CHUNGNAM_ELEARNING))
                .isTrue();

        assertThat(CourseOfferingResponse.isCyberProvider(DeliveryProvider.ON_CAMPUS)).isFalse();
        assertThat(CourseOfferingResponse.isCyberProvider(DeliveryProvider.E_CLASS)).isFalse();
        assertThat(CourseOfferingResponse.isCyberProvider(DeliveryProvider.OTHER)).isFalse();
        assertThat(CourseOfferingResponse.isCyberProvider(null)).isFalse();
    }

    @Test
    void majorCyberMarkersFollowTheExcelNotePolicy() {
        assertThat(CourseOfferingResponse.containsMajorCyberMarker("온라인수업")).isTrue();
        assertThat(CourseOfferingResponse.containsMajorCyberMarker(
                "미국비행 대면강의, 한국 수강생 온라인 강의"
        )).isTrue();
        assertThat(CourseOfferingResponse.containsMajorCyberMarker("사이버 강좌")).isTrue();
        assertThat(CourseOfferingResponse.containsMajorCyberMarker("원격수업")).isTrue();

        assertThat(CourseOfferingResponse.containsMajorCyberMarker("일반 대면수업")).isFalse();
        assertThat(CourseOfferingResponse.containsMajorCyberMarker(null)).isFalse();
    }

    @Test
    void generalCategoryCollapsesRequiredAndPrioritizesRemoteProvider() {
        assertThat(CourseOfferingResponse.generalCategory(
                GeneralClassification.REQUIRED,
                GeneralArea.EXPLORATION,
                DeliveryProvider.OCU
        )).isEqualTo(GeneralCategoryFilter.REQUIRED);
        assertThat(CourseOfferingResponse.generalCategory(
                GeneralClassification.ELECTIVE,
                GeneralArea.EXPLORATION,
                DeliveryProvider.OCU
        )).isEqualTo(GeneralCategoryFilter.OCU);
        assertThat(CourseOfferingResponse.generalCategory(
                GeneralClassification.ELECTIVE,
                GeneralArea.EXPLORATION,
                DeliveryProvider.ON_CAMPUS
        )).isEqualTo(GeneralCategoryFilter.AREA_1);
        assertThat(CourseOfferingResponse.generalCategory(
                GeneralClassification.ELECTIVE,
                GeneralArea.COEXISTENCE,
                DeliveryProvider.ON_CAMPUS
        )).isEqualTo(GeneralCategoryFilter.AREA_2);
        assertThat(CourseOfferingResponse.generalCategory(
                GeneralClassification.ELECTIVE,
                GeneralArea.INITIATIVE,
                DeliveryProvider.ON_CAMPUS
        )).isEqualTo(GeneralCategoryFilter.AREA_3);
    }

    @Test
    void creditRemovesOnlyUnnecessaryTrailingZeros() {
        BigDecimal wholeCredit = CourseOfferingResponse.displayCredit(new BigDecimal("3.000"));
        BigDecimal fractionalCredit = CourseOfferingResponse.displayCredit(new BigDecimal("2.500"));

        assertThat(wholeCredit).isEqualByComparingTo("3");
        assertThat(wholeCredit.scale()).isZero();
        assertThat(fractionalCredit).isEqualByComparingTo("2.5");
        assertThat(CourseOfferingResponse.displayCredit(null)).isNull();
    }
}

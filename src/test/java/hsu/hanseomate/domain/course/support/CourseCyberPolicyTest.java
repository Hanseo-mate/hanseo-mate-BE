package hsu.hanseomate.domain.course.support;

import static org.assertj.core.api.Assertions.assertThat;

import hsu.hanseomate.domain.courseimport.dto.type.DeliveryProvider;
import org.junit.jupiter.api.Test;

class CourseCyberPolicyTest {

    @Test
    void cyberProvidersFollowTheSharedPolicy() {
        assertThat(CourseCyberPolicy.isCyberProvider(DeliveryProvider.OCU)).isTrue();
        assertThat(CourseCyberPolicy.isCyberProvider(DeliveryProvider.SDU)).isTrue();
        assertThat(CourseCyberPolicy.isCyberProvider(DeliveryProvider.HSU_CYBER)).isTrue();
        assertThat(CourseCyberPolicy.isCyberProvider(DeliveryProvider.CHUNGNAM_ELEARNING))
                .isTrue();

        assertThat(CourseCyberPolicy.isCyberProvider(DeliveryProvider.ON_CAMPUS)).isFalse();
        assertThat(CourseCyberPolicy.isCyberProvider(DeliveryProvider.E_CLASS)).isFalse();
        assertThat(CourseCyberPolicy.isCyberProvider(DeliveryProvider.OTHER)).isFalse();
        assertThat(CourseCyberPolicy.isCyberProvider(null)).isFalse();
    }

    @Test
    void majorCyberMarkersFollowTheExcelNotePolicy() {
        assertThat(CourseCyberPolicy.containsMajorCyberMarker("온라인수업")).isTrue();
        assertThat(CourseCyberPolicy.containsMajorCyberMarker(
                "미국비행 대면강의, 한국 수강생 온라인 강의"
        )).isTrue();
        assertThat(CourseCyberPolicy.containsMajorCyberMarker("사이버 강좌")).isTrue();
        assertThat(CourseCyberPolicy.containsMajorCyberMarker("원격수업")).isTrue();

        assertThat(CourseCyberPolicy.containsMajorCyberMarker("일반 대면수업")).isFalse();
        assertThat(CourseCyberPolicy.containsMajorCyberMarker("")).isFalse();
        assertThat(CourseCyberPolicy.containsMajorCyberMarker(null)).isFalse();
    }
}

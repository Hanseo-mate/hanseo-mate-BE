package hsu.hanseomate.domain.timetable.search.dto;

import static org.assertj.core.api.Assertions.assertThat;

import hsu.hanseomate.domain.courseimport.dto.type.DeliveryProvider;
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
}

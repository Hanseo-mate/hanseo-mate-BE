package hsu.hanseomate.domain.course.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class CoursePeriodTimePolicyTest {

    @Test
    void mapsDayPeriodsToThirtyMinuteClasses() {
        assertThat(CoursePeriodTimePolicy.find(0)).contains(
                new CoursePeriodTimePolicy.PeriodTime(
                        LocalTime.of(9, 0),
                        LocalTime.of(9, 30)
                )
        );
        assertThat(CoursePeriodTimePolicy.find(17)).contains(
                new CoursePeriodTimePolicy.PeriodTime(
                        LocalTime.of(17, 30),
                        LocalTime.of(18, 0)
                )
        );
    }

    @Test
    void mapsNightPeriodsToFortyFiveMinuteClasses() {
        assertThat(CoursePeriodTimePolicy.find(18)).contains(
                new CoursePeriodTimePolicy.PeriodTime(
                        LocalTime.of(18, 0),
                        LocalTime.of(18, 45)
                )
        );
        assertThat(CoursePeriodTimePolicy.find(23)).contains(
                new CoursePeriodTimePolicy.PeriodTime(
                        LocalTime.of(22, 10),
                        LocalTime.of(22, 55)
                )
        );
    }

    @Test
    void returnsEmptyForPeriodsWithoutOfficialTimeDefinition() {
        assertThat(CoursePeriodTimePolicy.find(24)).isEmpty();
        assertThat(CoursePeriodTimePolicy.find(30)).isEmpty();
    }

    @Test
    void combinesFirstStartAndLastEndForConsecutivePeriodRange() {
        assertThat(CoursePeriodTimePolicy.findRange(6, 7)).contains(
                new CoursePeriodTimePolicy.TimeRange(
                        LocalTime.of(12, 0),
                        LocalTime.of(13, 0)
                )
        );
    }

    @Test
    void returnsEmptyForReversedPeriodRange() {
        assertThat(CoursePeriodTimePolicy.findRange(7, 6)).isEmpty();
    }
}

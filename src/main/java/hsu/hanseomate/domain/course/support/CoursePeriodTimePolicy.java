package hsu.hanseomate.domain.course.support;

import java.time.LocalTime;
import java.util.Optional;

public final class CoursePeriodTimePolicy {

    private static final int LAST_DAY_PERIOD = 17;
    private static final int FIRST_NIGHT_PERIOD = 18;
    private static final int LAST_NIGHT_PERIOD = 23;

    private CoursePeriodTimePolicy() {
    }

    public static Optional<PeriodTime> find(int period) {
        if (period >= 0 && period <= LAST_DAY_PERIOD) {
            LocalTime startTime = LocalTime.of(9, 0).plusMinutes(period * 30L);
            return Optional.of(new PeriodTime(startTime, startTime.plusMinutes(30)));
        }
        if (period >= FIRST_NIGHT_PERIOD && period <= LAST_NIGHT_PERIOD) {
            LocalTime startTime = LocalTime.of(18, 0)
                    .plusMinutes((period - FIRST_NIGHT_PERIOD) * 50L);
            return Optional.of(new PeriodTime(startTime, startTime.plusMinutes(45)));
        }
        return Optional.empty();
    }

    public static Optional<TimeRange> findRange(int firstPeriod, int lastPeriod) {
        if (firstPeriod > lastPeriod) {
            return Optional.empty();
        }
        Optional<PeriodTime> first = find(firstPeriod);
        Optional<PeriodTime> last = find(lastPeriod);
        if (first.isEmpty() || last.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new TimeRange(
                first.orElseThrow().startTime(),
                last.orElseThrow().endTime()
        ));
    }

    public record PeriodTime(LocalTime startTime, LocalTime endTime) {
    }

    public record TimeRange(LocalTime startTime, LocalTime endTime) {
    }
}

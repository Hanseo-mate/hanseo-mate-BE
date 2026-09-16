package hsu.hanseomate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;

class HanseomateApplicationTimeZoneTest {

    @Test
    void configuresKoreaTimeZoneBeforeApplicationStarts() {
        TimeZone originalTimeZone = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

            HanseomateApplication.configureDefaultTimeZone();

            assertEquals(
                    HanseomateApplication.APPLICATION_TIME_ZONE,
                    ZoneId.systemDefault()
            );
            assertEquals(
                    LocalDateTime.of(2026, 8, 22, 9, 0),
                    LocalDateTime.ofInstant(
                            Instant.parse("2026-08-22T00:00:00Z"),
                            ZoneId.systemDefault()
                    )
            );
        } finally {
            TimeZone.setDefault(originalTimeZone);
        }
    }
}

package hsu.hanseomate;

import java.time.ZoneId;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HanseomateApplication {

    static final ZoneId APPLICATION_TIME_ZONE = ZoneId.of("Asia/Seoul");

    public static void main(String[] args) {
        // LocalDateTime-based auditing uses the JVM default time zone.
        configureDefaultTimeZone();
        SpringApplication.run(HanseomateApplication.class, args);
    }

    static void configureDefaultTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(APPLICATION_TIME_ZONE));
    }

}

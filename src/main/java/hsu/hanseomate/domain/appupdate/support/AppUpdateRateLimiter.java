package hsu.hanseomate.domain.appupdate.support;

import hsu.hanseomate.domain.appupdate.config.AppUpdateProperties;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AppUpdateRateLimiter {
    private final AppUpdateProperties properties;
    private final Clock clock;
    private final Map<String, Integer> requests = new HashMap<>();
    private long currentMinute = Long.MIN_VALUE;

    // 프로세스별 제한. 프록시에서 검증된 remoteAddr를 사용하며 임의 X-Forwarded-For는 해석하지 않는다.
    public synchronized boolean allow(String ip) {
        long minute = clock.instant().getEpochSecond() / 60;
        if (minute != currentMinute) {
            requests.clear();
            currentMinute = minute;
        }
        int count = requests.getOrDefault(ip, 0);
        if (count == 0 && requests.size() >= properties.getRateLimitMaxIps()) return false;
        if (count >= properties.getCheckRequestsPerMinute()) return false;
        requests.put(ip, count + 1);
        return true;
    }
}

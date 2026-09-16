package hsu.hanseomate.domain.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupScheduler {

    private final RefreshTokenService refreshTokenService;

    @Scheduled(
            cron = "${app.jwt.refresh-token-cleanup-cron:0 30 3 * * *}",
            zone = "Asia/Seoul"
    )
    @Transactional
    public void deleteExpiredTokens() {
        int deletedCount = refreshTokenService.deleteExpiredTokens();
        if (deletedCount > 0) {
            log.info("만료된 Refresh Token {}건을 정리했습니다.", deletedCount);
        }
    }
}

package hsu.hanseomate.domain.cafeteria.service;

import hsu.hanseomate.domain.cafeteria.client.CafeteriaCrawlerClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

/**
 * 평일 한국 시간 01~17시, 2시간 간격으로 당일 미완료 식당 수집을 요청한다.
 * 당일 성공 여부와 DB 저장은 Python 크롤러가 관리한다.
 */
@Component
public class CafeteriaScheduler {

    public static final String CRON = "0 0 1-17/2 * * MON-FRI";
    public static final String ZONE = "Asia/Seoul";

    private static final Logger log = LoggerFactory.getLogger(CafeteriaScheduler.class);

    private final CafeteriaCrawlerClient crawlerClient;

    public CafeteriaScheduler(CafeteriaCrawlerClient crawlerClient) {
        this.crawlerClient = crawlerClient;
    }

    @Scheduled(cron = CRON, zone = ZONE)
    public void triggerPendingCrawl() {
        try {
            crawlerClient.triggerPendingCrawl();
        } catch (HttpClientErrorException.Conflict e) {
            log.info("[CafeteriaScheduler] 이미 크롤링이 실행 중입니다. 이번 시각의 요청을 종료합니다.");
        } catch (Exception e) {
            log.error("[CafeteriaScheduler] 식단 크롤링 요청 실패. 다음 정기 시각에 다시 요청합니다.", e);
        }
    }
}

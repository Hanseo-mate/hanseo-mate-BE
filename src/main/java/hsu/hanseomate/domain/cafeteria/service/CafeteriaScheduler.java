package hsu.hanseomate.domain.cafeteria.service;

import hsu.hanseomate.domain.cafeteria.client.CafeteriaCrawlerClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

/**
 * 매주 월요일 오전 1시(Asia/Seoul)에 4개 식당 전체 크롤링을 자동 트리거하는 스케줄러.
 * <p>
 * Python 크롤러에 {@code POST /cafeteria-crawl/run} (mode=background)를 호출하여
 * 전체 식당 크롤링을 한 번에 요청하고 즉시 반환한다(fire-and-forget).
 * 크롤링 재시도(2시간 간격, 최대 5회)는 Python 크롤러 내부에서 자동 처리되므로
 * Spring 측에서 별도 재시도 스케줄을 추가하지 않는다.
 * <p>
 * 409(이미 실행 중) 응답은 정상 케이스로 간주하여 warn 로그만 남긴다.
 */
@Component
public class CafeteriaScheduler {

    private static final Logger log = LoggerFactory.getLogger(CafeteriaScheduler.class);

    private final CafeteriaCrawlerClient crawlerClient;

    public CafeteriaScheduler(CafeteriaCrawlerClient crawlerClient) {
        this.crawlerClient = crawlerClient;
    }

    /**
     * 매주 월요일 오전 1시(Asia/Seoul)에 실행.
     * cron 표현식: 초 분 시 일 월 요일
     */
    @Scheduled(cron = "0 0 1 * * MON", zone = "Asia/Seoul")
    public void triggerWeeklyCrawl() {
        log.info("[CafeteriaScheduler] 식단 크롤링 시작");
        try {
            crawlerClient.triggerAllCrawl();
            log.info("[CafeteriaScheduler] 식단 크롤링 요청 완료");
        } catch (HttpClientErrorException.Conflict e) {
            log.warn("[CafeteriaScheduler] 이미 크롤링이 실행 중입니다. 스킵합니다.");
        } catch (Exception e) {
            log.error("[CafeteriaScheduler] 식단 크롤링 요청 실패", e);
        }
    }
}

package hsu.hanseomate.domain.cafeteria.service;

import hsu.hanseomate.domain.cafeteria.client.CafeteriaCrawlerClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매주 월요일 오전 9시에 4개 식당의 크롤링을 자동 트리거하는 스케줄러.
 */
@Component
public class CafeteriaScheduler {

    private static final Logger log = LoggerFactory.getLogger(CafeteriaScheduler.class);

    private final CafeteriaCrawlerClient crawlerClient;
    private final String mainStudentUrl;
    private final String mainStaffUrl;
    private final String taeanStudentUrl;
    private final String taeanStaffUrl;

    public CafeteriaScheduler(
            CafeteriaCrawlerClient crawlerClient,
            @Value("${cafeteria.crawler.main-student-url}") String mainStudentUrl,
            @Value("${cafeteria.crawler.main-staff-url}") String mainStaffUrl,
            @Value("${cafeteria.crawler.taean-student-url}") String taeanStudentUrl,
            @Value("${cafeteria.crawler.taean-staff-url}") String taeanStaffUrl
    ) {
        this.crawlerClient = crawlerClient;
        this.mainStudentUrl = mainStudentUrl;
        this.mainStaffUrl = mainStaffUrl;
        this.taeanStudentUrl = taeanStudentUrl;
        this.taeanStaffUrl = taeanStaffUrl;
    }

    /**
     * 매주 월요일 오전 9시(Asia/Seoul)에 실행.
     * cron 표현식: 초 분 시 일 월 요일
     */
    @Scheduled(cron = "0 0 9 * * MON", zone = "Asia/Seoul")
    public void triggerWeeklyCrawl() {
        log.info("[CafeteriaScheduler] Starting weekly cafeteria crawl...");

        triggerWithLogging(mainStudentUrl, "MAIN_STUDENT");
        triggerWithLogging(mainStaffUrl, "MAIN_STAFF");
        triggerWithLogging(taeanStudentUrl, "TAEAN_STUDENT");
        triggerWithLogging(taeanStaffUrl, "TAEAN_STAFF");

        log.info("[CafeteriaScheduler] All crawl triggers dispatched.");
    }

    private void triggerWithLogging(String url, String restaurantType) {
        log.info("[CafeteriaScheduler] Triggering crawl: restaurantType={}", restaurantType);
        crawlerClient.triggerCrawl(url, restaurantType);
    }
}

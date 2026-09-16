package hsu.hanseomate.domain.notices.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NoticeCrawlScheduler {

    private final CrawlOperationService crawlOperationService;

    @Scheduled(
            cron = "${crawler.schedule.cron:0 0 12,18 * * *}",
            zone = "${crawler.schedule.zone:Asia/Seoul}"
    )
    public void runScheduledCrawl() {
        crawlOperationService.triggerBackgroundCrawl();
    }
}

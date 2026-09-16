package hsu.hanseomate.domain.notices.service;

import hsu.hanseomate.domain.notices.dto.CrawlRunRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
public class CrawlOperationService {

    private final RestClient restClient;

    public CrawlOperationService(
            RestClient.Builder restClientBuilder,
            @Value("${crawler.api.base-url:http://34.64.250.12:8000}") String crawlerApiBaseUrl
    ) {
        this.restClient = restClientBuilder.baseUrl(crawlerApiBaseUrl).build();
    }

    public void triggerBackgroundCrawl() {
        restClient.post()
                .uri("/crawl/run")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CrawlRunRequest(null, "background"))
                .retrieve()
                .toBodilessEntity();

        log.info("Triggered crawler run in background mode.");
    }
}

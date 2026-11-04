package hsu.hanseomate.domain.academiccalendar.client;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AcademicCalendarCrawlerClient {
    private final RestClient client;
    public AcademicCalendarCrawlerClient(RestClient.Builder builder,
            @Value("${academic-calendar.crawler.base-url:http://127.0.0.1:8000}") String url) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(15000);
        client = builder.baseUrl(url).requestFactory(factory).build();
    }

    public CrawlReceipt request(int year) {
        var response = client.post().uri("/academic-calendar-crawl/run")
                .body(new CrawlRequest(year, List.of(1, 2), "background"))
                .retrieve().toEntity(tools.jackson.databind.JsonNode.class);
        var body = response.getBody();
        if (response.getStatusCode().value() != 202
                || body == null || !body.path("status").isString()
                || !"starting".equals(body.path("status").asString())
                || !body.path("run_id").isString() || body.path("run_id").asString().isBlank())
            throw new IllegalStateException("Invalid academic calendar crawl receipt");
        return new CrawlReceipt(body.path("status").asString(), body.path("run_id").asString());
    }
    public record CrawlRequest(int academic_year, List<Integer> semesters, String mode) {}
    public record CrawlReceipt(String status, String run_id) {}
}

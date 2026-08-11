package hsu.hanseomate.domain.push.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Expo Push Service와 통신하는 HTTP 클라이언트.
 *
 * <ul>
 *   <li>발송: POST https://exp.host/--/api/v2/push/send (100개 청크 분할)</li>
 *   <li>영수증: POST https://exp.host/--/api/v2/push/getReceipts</li>
 *   <li>HTTP 429 / 5xx 발생 시 지수 백오프(1→2→4→8초) 재시도 (최대 4회)</li>
 * </ul>
 *
 * 비공식 외부 SDK 없이 Spring RestClient만 사용합니다.
 */
@Slf4j
@Component
public class ExpoPushClient {

    private static final String EXPO_SEND_URL = "https://exp.host/--/api/v2/push/send";
    private static final String EXPO_RECEIPT_URL = "https://exp.host/--/api/v2/push/getReceipts";
    private static final int MAX_CHUNK_SIZE = 100;
    /** 재시도 대기 시간(ms): 1초 → 2초 → 4초 → 8초 */
    private static final long[] RETRY_DELAYS_MS = {1_000L, 2_000L, 4_000L, 8_000L};

    private final RestClient restClient;

    public ExpoPushClient(
            RestClient.Builder restClientBuilder,
            @Value("${expo.push.access-token:}") String accessToken
    ) {
        RestClient.Builder builder = restClientBuilder.clone();
        if (accessToken != null && !accessToken.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + accessToken);
        }
        this.restClient = builder.build();
    }

    /**
     * 메시지 목록을 100개 단위 청크로 분할하여 순서대로 발송합니다.
     * 반환된 List<ExpoTicketData>는 입력 messages와 1:1 대응됩니다.
     *
     * @param messages 발송할 메시지 목록 (100개 초과 시 자동 분할)
     * @return 각 메시지에 대응하는 Ticket 결과
     */
    public List<ExpoTicketData> sendMessages(List<ExpoPushMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }
        List<ExpoTicketData> allTickets = new ArrayList<>(messages.size());
        List<List<ExpoPushMessage>> chunks = partition(messages, MAX_CHUNK_SIZE);
        for (List<ExpoPushMessage> chunk : chunks) {
            List<ExpoTicketData> chunkTickets = sendChunkWithRetry(chunk);
            allTickets.addAll(chunkTickets);
        }
        return allTickets;
    }

    /**
     * Receipt API를 호출하여 Ticket들의 FCM/APNs 전달 결과를 반환합니다.
     *
     * @param ticketIds 조회할 Expo Ticket ID 목록
     * @return ticketId → ReceiptData 맵
     */
    public Map<String, ExpoReceiptResponse.ReceiptData> getReceipts(List<String> ticketIds) {
        if (ticketIds == null || ticketIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return getReceiptsWithRetry(ticketIds);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private List<ExpoTicketData> sendChunkWithRetry(List<ExpoPushMessage> chunk) {
        for (int attempt = 0; attempt < RETRY_DELAYS_MS.length; attempt++) {
            try {
                return doSendChunk(chunk);
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                if (isRetryable(status) && attempt < RETRY_DELAYS_MS.length - 1) {
                    log.warn("Expo /send retryable error attempt={}/{} HTTP={}", attempt + 1,
                            RETRY_DELAYS_MS.length, status);
                    sleep(RETRY_DELAYS_MS[attempt]);
                } else {
                    log.error("Expo /send non-retryable error HTTP={} body={}",
                            status, e.getResponseBodyAsString());
                    throw e;
                }
            } catch (Exception e) {
                if (attempt < RETRY_DELAYS_MS.length - 1) {
                    log.warn("Expo /send error attempt={}/{} message={}",
                            attempt + 1, RETRY_DELAYS_MS.length, e.getMessage());
                    sleep(RETRY_DELAYS_MS[attempt]);
                } else {
                    throw e;
                }
            }
        }
        // 최종 시도
        return doSendChunk(chunk);
    }

    private List<ExpoTicketData> doSendChunk(List<ExpoPushMessage> chunk) {
        ExpoSendResponse response = restClient.post()
                .uri(EXPO_SEND_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .body(chunk)
                .retrieve()
                .body(ExpoSendResponse.class);

        if (response == null || response.getData() == null) {
            log.warn("Expo /send returned empty response for chunk size={}", chunk.size());
            return Collections.emptyList();
        }
        return response.getData();
    }

    private Map<String, ExpoReceiptResponse.ReceiptData> getReceiptsWithRetry(List<String> ticketIds) {
        for (int attempt = 0; attempt < RETRY_DELAYS_MS.length; attempt++) {
            try {
                return doGetReceipts(ticketIds);
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                if (isRetryable(status) && attempt < RETRY_DELAYS_MS.length - 1) {
                    log.warn("Expo /getReceipts retryable error attempt={}/{} HTTP={}",
                            attempt + 1, RETRY_DELAYS_MS.length, status);
                    sleep(RETRY_DELAYS_MS[attempt]);
                } else {
                    log.error("Expo /getReceipts non-retryable error HTTP={} body={}",
                            status, e.getResponseBodyAsString());
                    throw e;
                }
            } catch (Exception e) {
                if (attempt < RETRY_DELAYS_MS.length - 1) {
                    log.warn("Expo /getReceipts error attempt={}/{} message={}",
                            attempt + 1, RETRY_DELAYS_MS.length, e.getMessage());
                    sleep(RETRY_DELAYS_MS[attempt]);
                } else {
                    throw e;
                }
            }
        }
        // 최종 시도
        return doGetReceipts(ticketIds);
    }

    private Map<String, ExpoReceiptResponse.ReceiptData> doGetReceipts(List<String> ticketIds) {
        ExpoReceiptResponse response = restClient.post()
                .uri(EXPO_RECEIPT_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("ids", ticketIds))
                .retrieve()
                .body(ExpoReceiptResponse.class);

        if (response == null || response.getData() == null) {
            log.warn("Expo /getReceipts returned empty response for {} ticket ids", ticketIds.size());
            return Collections.emptyMap();
        }
        return response.getData();
    }

    private boolean isRetryable(int httpStatus) {
        return httpStatus == 429 || httpStatus >= 500;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }
}

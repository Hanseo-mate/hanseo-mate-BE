package hsu.hanseomate.domain.push.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Expo Push API POST /getReceipts 응답 DTO.
 * 예: {"data": {"ticketId": {"status": "ok"}, ...}}
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExpoReceiptResponse {

    private Map<String, ReceiptData> data;

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ReceiptData {

        /** "ok" | "error" */
        private String status;

        private String message;

        private ReceiptDetails details;

        @Getter
        @NoArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class ReceiptDetails {
            /** e.g. "DeviceNotRegistered" */
            private String error;
        }

        public boolean isOk() {
            return "ok".equals(status);
        }

        public String getErrorCode() {
            if (details == null) return null;
            return details.getError();
        }
    }
}

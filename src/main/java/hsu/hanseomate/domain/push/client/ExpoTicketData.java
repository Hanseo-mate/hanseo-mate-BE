package hsu.hanseomate.domain.push.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Expo Push Ticket 단건 데이터.
 * status "ok" 시 id(ticket ID)를 DB에 저장한다.
 * status "error" 시 details.error 에 에러 코드가 담긴다.
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExpoTicketData {

    /** "ok" | "error" */
    private String status;

    /** status == "ok" 일 때 Ticket ID */
    private String id;

    private String message;

    private Details details;

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Details {
        /** e.g. "DeviceNotRegistered" */
        private String error;
    }
}

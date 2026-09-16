package hsu.hanseomate.domain.push.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Expo Push API POST /send 응답 DTO.
 * 예: {"data": [{"status": "ok", "id": "..."}, {"status": "error", ...}]}
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExpoSendResponse {

    private List<ExpoTicketData> data;
}

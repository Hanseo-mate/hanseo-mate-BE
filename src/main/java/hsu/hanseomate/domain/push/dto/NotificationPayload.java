package hsu.hanseomate.domain.push.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * NotificationOutbox.payload 컬럼에 JSON으로 직렬화하는 알림 내용 DTO.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NotificationPayload {

    private String title;
    private String body;

    /**
     * Expo data 필드 (version, type, route, entityId 등).
     * 가이드 6번 항목의 알림 데이터 규격을 따릅니다.
     */
    private Map<String, Object> data;
}

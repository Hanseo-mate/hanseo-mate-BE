package hsu.hanseomate.domain.push.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

/**
 * Expo Push API POST /send 단일 메시지 페이로드.
 * 플랫폼별 분기: Android → channelId, iOS → sound.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExpoPushMessage {

    /** 수신 기기의 Expo Push Token */
    private String to;

    private String title;
    private String body;

    /** Android 전용: 앱에 등록된 채널 ID */
    private String channelId;

    /** iOS 전용: 알림음 */
    private String sound;

    /** high | normal | default */
    private String priority;

    /** 앱에 전달할 커스텀 데이터 */
    private Map<String, Object> data;
}

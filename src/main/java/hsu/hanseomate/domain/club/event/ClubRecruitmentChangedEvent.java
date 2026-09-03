package hsu.hanseomate.domain.club.event;

import java.util.List;

/**
 * 동아리 모집공고가 새로 등록되거나 실제로 변경된 경우 발행하는 이벤트입니다.
 *
 * <p>이벤트 생성 시점의 찜 사용자 목록을 함께 보관하여 비동기 발송 전에 찜이
 * 변경되더라도 공고 변경 당시의 수신 대상을 유지합니다.</p>
 */
public record ClubRecruitmentChangedEvent(
        Long clubId,
        String clubName,
        ChangeType changeType,
        List<Long> recipientUserIds
) {

    public ClubRecruitmentChangedEvent {
        recipientUserIds = List.copyOf(recipientUserIds);
    }

    public enum ChangeType {
        CREATED,
        UPDATED
    }
}

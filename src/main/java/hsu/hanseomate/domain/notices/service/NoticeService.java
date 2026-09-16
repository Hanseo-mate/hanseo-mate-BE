package hsu.hanseomate.domain.notices.service;

import hsu.hanseomate.domain.notices.dto.NoticeDetailResponse;
import hsu.hanseomate.domain.notices.dto.NoticePageResponse;
import hsu.hanseomate.domain.notices.entity.Notice;
import hsu.hanseomate.domain.notices.entity.NoticeType;
import hsu.hanseomate.domain.notices.event.NoticeViewCountMilestoneEvent;
import hsu.hanseomate.domain.notices.exception.NoticeNotFoundException;
import hsu.hanseomate.domain.notices.repository.NoticeRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoticeService {

    private static final int NOTICE_PAGE_SIZE = 10;
    private static final long VIEW_COUNT_MILESTONE = 100L;

    private final NoticeRepository noticeRepository;
    private final ApplicationEventPublisher eventPublisher;

    public NoticePageResponse getNoticesByCategory(String noticeType, int page) {
        NoticeType targetType = NoticeType.from(noticeType);
        Pageable pageable = createPageable(page);

        return NoticePageResponse.from(
                noticeRepository.findAllByNoticeTypeOrderByIsHotDescPostDateDescIdDesc(targetType, pageable)
        );
    }

    public NoticePageResponse getAllNotices(int page) {
        Pageable pageable = createPageable(page);

        return NoticePageResponse.from(
                noticeRepository.findAllWithoutTypeOrderByPriority(NoticeType.GRADUATE, pageable)
        );
    }

    public NoticePageResponse searchNotices(String keyword, int page) {
        Pageable pageable = createPageable(page);
        String normalizedKeyword = normalizeKeyword(keyword);
        return NoticePageResponse.from(
                noticeRepository.searchWithoutTypeByKeyword(NoticeType.GRADUATE, normalizedKeyword, pageable)
        );
    }

    @Transactional
    public NoticeDetailResponse getNoticeDetail(Long noticeId) {
        int updatedRows = noticeRepository.incrementViewCount(noticeId);
        if (updatedRows == 0) {
            throw new NoticeNotFoundException(noticeId);
        }

        Notice notice = noticeRepository.findDetailById(noticeId)
                .orElseThrow(() -> new NoticeNotFoundException(noticeId));

        // 조회수가 정확히 100회가 된 순간에만 전체 푸시 알림 트리거
        if (notice.getViewCount() == VIEW_COUNT_MILESTONE) {
            eventPublisher.publishEvent(new NoticeViewCountMilestoneEvent(
                    notice.getId(),
                    notice.getNoticeType(),
                    notice.getTitle(),
                    VIEW_COUNT_MILESTONE
            ));
        }

        return NoticeDetailResponse.from(notice);
    }

    private String normalizeKeyword(String keyword) {
        return keyword.replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT);
    }

    private Pageable createPageable(int page) {
        return PageRequest.of(page, NOTICE_PAGE_SIZE);
    }
}

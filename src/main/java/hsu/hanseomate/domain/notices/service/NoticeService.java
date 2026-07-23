package hsu.hanseomate.domain.notices.service;

import hsu.hanseomate.domain.notices.dto.NoticeDetailResponse;
import hsu.hanseomate.domain.notices.dto.NoticePageResponse;
import hsu.hanseomate.domain.notices.entity.NoticeType;
import hsu.hanseomate.domain.notices.exception.NoticeNotFoundException;
import hsu.hanseomate.domain.notices.repository.NoticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoticeService {

    private static final int NOTICE_PAGE_SIZE = 10;

    private final NoticeRepository noticeRepository;

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

    public NoticeDetailResponse getNoticeDetail(Long noticeId) {
        return noticeRepository.findDetailById(noticeId)
                .map(NoticeDetailResponse::from)
                .orElseThrow(() -> new NoticeNotFoundException(noticeId));
    }

    private Pageable createPageable(int page) {
        return PageRequest.of(page, NOTICE_PAGE_SIZE);
    }
}

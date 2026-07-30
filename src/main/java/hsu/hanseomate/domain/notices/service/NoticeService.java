package hsu.hanseomate.domain.notices.service;

import hsu.hanseomate.domain.notices.dto.NoticeDetailResponse;
import hsu.hanseomate.domain.notices.dto.NoticePageResponse;
import hsu.hanseomate.domain.notices.entity.NoticeType;
import hsu.hanseomate.domain.notices.exception.NoticeNotFoundException;
import hsu.hanseomate.domain.notices.repository.NoticeRepository;
import java.util.Locale;
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

        return noticeRepository.findDetailById(noticeId)
                .map(NoticeDetailResponse::from)
                .orElseThrow(() -> new NoticeNotFoundException(noticeId));
    }

    private String normalizeKeyword(String keyword) {
        return keyword.replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT);
    }

    private Pageable createPageable(int page) {
        return PageRequest.of(page, NOTICE_PAGE_SIZE);
    }
}

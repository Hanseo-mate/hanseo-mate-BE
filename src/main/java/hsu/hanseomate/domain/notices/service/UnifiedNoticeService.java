package hsu.hanseomate.domain.notices.service;

import hsu.hanseomate.domain.notices.dto.UnifiedNoticeListItemResponse;
import hsu.hanseomate.domain.notices.repository.UnifiedNoticeQueryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UnifiedNoticeService {

    private final UnifiedNoticeQueryRepository unifiedNoticeQueryRepository;

    /**
     * 학생회 → HOT(학사·일반·장학·대학원) → 비HOT 전체 순서로 페이지 조회한다.
     * 동일 그룹에서는 게시일 내림차순, ID 내림차순을 적용한다.
     */
    @Transactional(readOnly = true)
    public List<UnifiedNoticeListItemResponse> getUnifiedNotices(String keyword, int page, int size) {
        String processedKeyword = (keyword == null) ? "" : keyword.replace(" ", "");
        return unifiedNoticeQueryRepository.searchByTitleIgnoringSpaces(
                processedKeyword, PageRequest.of(page, size)
        );
    }
}

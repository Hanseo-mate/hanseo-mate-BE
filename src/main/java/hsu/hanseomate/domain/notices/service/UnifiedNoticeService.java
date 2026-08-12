package hsu.hanseomate.domain.notices.service;

import hsu.hanseomate.domain.notices.dto.UnifiedNoticeListItemResponse;
import hsu.hanseomate.domain.notices.entity.Notice;
import hsu.hanseomate.domain.notices.repository.NoticeRepository;
import hsu.hanseomate.domain.studentcouncilnotice.entity.StudentCouncilNotice;
import hsu.hanseomate.domain.studentcouncilnotice.repository.StudentCouncilNoticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UnifiedNoticeService {

    private final NoticeRepository noticeRepository;
    private final StudentCouncilNoticeRepository councilNoticeRepository;

    /**
     * 통합 공지 목록 조회 (검색어 포함, 페이징).
     *
     * <p>정렬 기준 (우선순위 순):
     * <ol>
     *   <li>학생회 공지(STUDENT_COUNCIL) 최우선</li>
     *   <li>isHot=true 인 공지 (학사 → 일반 → 장학 → 대학원)</li>
     *   <li>isHot=false 인 공지 (학사 → 일반 → 장학 → 대학원)</li>
     *   <li>동일 그룹 내에서는 최신순(postDate DESC, id DESC)</li>
     * </ol>
     */
    @Transactional(readOnly = true)
    public List<UnifiedNoticeListItemResponse> getUnifiedNotices(String keyword, int page, int size) {
        String processedKeyword = (keyword == null) ? "" : keyword.replace(" ", "");

        // 학생회 공지는 항상 최상단이므로 전체를 가져온다 (학교 특성상 수백 건 이내)
        int councilFetchLimit = Math.max((page + 1) * size, 500);
        // 일반 공지는 현재 페이지를 커버할 만큼만 가져온다
        int noticeFetchLimit = (page + 1) * size;

        List<Notice> notices = noticeRepository.searchByTitleIgnoringSpaces(
                processedKeyword, PageRequest.of(0, noticeFetchLimit)
        );
        List<StudentCouncilNotice> councilNotices = councilNoticeRepository.searchByTitleIgnoringSpaces(
                processedKeyword, PageRequest.of(0, councilFetchLimit)
        );

        List<UnifiedNoticeListItemResponse> combined = new ArrayList<>(
                notices.size() + councilNotices.size()
        );
        notices.forEach(n -> combined.add(UnifiedNoticeListItemResponse.from(n)));
        councilNotices.forEach(c -> combined.add(UnifiedNoticeListItemResponse.from(c)));

        combined.sort(unifiedComparator());

        int start = Math.min(page * size, combined.size());
        int end   = Math.min(start + size, combined.size());
        return combined.subList(start, end);
    }

    // ── 정렬 기준 ─────────────────────────────────────────────────────────────

    /**
     * 학생회 공지 → isHot 공지(타입 우선순위 순) → 일반 공지(타입 우선순위 순)
     * 동일 그룹 내에서는 postDate DESC, id DESC.
     */
    private Comparator<UnifiedNoticeListItemResponse> unifiedComparator() {
        return Comparator
                // 1. 학생회 공지 최우선
                .comparingInt((UnifiedNoticeListItemResponse r) -> groupPriority(r))
                // 2. 동일 그룹 내 최신순
                .thenComparing(
                        Comparator.comparing(UnifiedNoticeListItemResponse::postDate).reversed()
                )
                .thenComparing(
                        Comparator.comparing(UnifiedNoticeListItemResponse::id).reversed()
                );
    }

    /**
     * 그룹 우선순위 값을 반환합니다. 값이 작을수록 먼저 표시됩니다.
     *
     * <pre>
     * 0  : 학생회 공지 (STUDENT_COUNCIL)
     * 1  : 학사공지   isHot=true  (ACADEMIC)
     * 2  : 일반공지   isHot=true  (GENERAL)
     * 3  : 장학공지   isHot=true  (SCHOLARSHIP)
     * 4  : 대학원공지 isHot=true  (GRADUATE)
     * 5  : 학사공지   isHot=false (ACADEMIC)
     * 6  : 일반공지   isHot=false (GENERAL)
     * 7  : 장학공지   isHot=false (SCHOLARSHIP)
     * 8  : 대학원공지 isHot=false (GRADUATE)
     * 99 : 기타
     * </pre>
     */
    private int groupPriority(UnifiedNoticeListItemResponse r) {
        if ("STUDENT_COUNCIL".equals(r.noticeType())) return 0;

        // isHot=false 는 타입 구분 없이 같은 그룹 → 이후 날짜순으로만 정렬
        if (!r.isHot()) return 5;

        return switch (r.noticeType()) {
            case "ACADEMIC"    -> 1;
            case "GENERAL"     -> 2;
            case "SCHOLARSHIP" -> 3;
            case "GRADUATE"    -> 4;
            default            -> 99;
        };
    }
}
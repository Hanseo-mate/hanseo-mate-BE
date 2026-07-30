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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UnifiedNoticeService {

    private final NoticeRepository noticeRepository;
    private final StudentCouncilNoticeRepository councilNoticeRepository;

    // 생성자 주입 (생략)

    /**
     * 띄어쓰기 무시 통합 검색 및 목록 조회 (페이징 포함)
     */
    @Transactional(readOnly = true)
    public List<UnifiedNoticeListItemResponse> getUnifiedNotices(String keyword, int page, int size) {
        // 1. 검색어 공백 제거 (사용자가 "수 강신청" 이라 쳐도 "수강신청"으로 변환)
        String processedKeyword = (keyword == null) ? "" : keyword.replace(" ", "");

        // 2. 각 DB에서 충분한 양의 데이터를 가져옵니다.
        // 최신순 정렬을 위해 (page + 1) * size 만큼 넉넉하게 가져와야 자바 단에서 정렬 시 누락이 없습니다.
        int fetchLimit = (page + 1) * size;
        PageRequest pageRequest = PageRequest.of(0, fetchLimit); // 각 레포지토리의 최신 N개

        // 공지사항 가져오기
        List<Notice> notices = noticeRepository.searchByTitleIgnoringSpaces(processedKeyword, pageRequest);
        // 학생회 공지 가져오기
        List<StudentCouncilNotice> councilNotices = councilNoticeRepository.searchByTitleIgnoringSpaces(processedKeyword, pageRequest);

        // 3. 자바 메모리에서 리스트 합치기 및 DTO 변환
        List<UnifiedNoticeListItemResponse> combinedList = new ArrayList<>();

        combinedList.addAll(notices.stream()
                .map(UnifiedNoticeListItemResponse::from)
                .toList());

        combinedList.addAll(councilNotices.stream()
                .map(UnifiedNoticeListItemResponse::from)
                .toList());

        // 4. 날짜 기준 최신순(내림차순) 정렬
        combinedList.sort(Comparator.comparing(UnifiedNoticeListItemResponse::postDate).reversed());

        // 5. 요청한 페이지 사이즈에 맞게 자르기 (SubList)
        int start = Math.min(page * size, combinedList.size());
        int end = Math.min(start + size, combinedList.size());

        return combinedList.subList(start, end);
    }
}
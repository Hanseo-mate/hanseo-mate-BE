package hsu.hanseomate.domain.studentcouncilnotice.service;

import hsu.hanseomate.domain.studentcouncilnotice.dto.StudentCouncilNoticeDetailResponse;
import hsu.hanseomate.domain.studentcouncilnotice.dto.StudentCouncilNoticePageResponse;
import hsu.hanseomate.domain.studentcouncilnotice.dto.StudentCouncilNoticeRequest;
import hsu.hanseomate.domain.studentcouncilnotice.entity.StudentCouncilNotice;
import hsu.hanseomate.domain.studentcouncilnotice.exception.StudentCouncilNoticeNotFoundException;
import hsu.hanseomate.domain.studentcouncilnotice.repository.StudentCouncilNoticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentCouncilNoticeService {

    private static final int PAGE_SIZE = 10;

    private final StudentCouncilNoticeRepository studentCouncilNoticeRepository;

    public StudentCouncilNoticePageResponse getNotices(int page) {
        return StudentCouncilNoticePageResponse.from(
                studentCouncilNoticeRepository.findAllByOrderByCreatedAtDescIdDesc(
                        PageRequest.of(page, PAGE_SIZE)
                )
        );
    }

    public StudentCouncilNoticeDetailResponse getNotice(Long noticeId) {
        return StudentCouncilNoticeDetailResponse.from(findNotice(noticeId));
    }

    @Transactional
    public StudentCouncilNoticeDetailResponse createNotice(StudentCouncilNoticeRequest request) {
        StudentCouncilNotice notice = StudentCouncilNotice.create(
                request.title().trim(),
                request.author().trim(),
                request.content()
        );
        return StudentCouncilNoticeDetailResponse.from(
                studentCouncilNoticeRepository.saveAndFlush(notice)
        );
    }

    @Transactional
    public StudentCouncilNoticeDetailResponse updateNotice(
            Long noticeId,
            StudentCouncilNoticeRequest request
    ) {
        StudentCouncilNotice notice = findNotice(noticeId);
        notice.update(
                request.title().trim(),
                request.author().trim(),
                request.content()
        );
        studentCouncilNoticeRepository.flush();
        return StudentCouncilNoticeDetailResponse.from(notice);
    }

    @Transactional
    public void deleteNotice(Long noticeId) {
        StudentCouncilNotice notice = findNotice(noticeId);
        studentCouncilNoticeRepository.delete(notice);
        studentCouncilNoticeRepository.flush();
    }

    private StudentCouncilNotice findNotice(Long noticeId) {
        return studentCouncilNoticeRepository.findById(noticeId)
                .orElseThrow(() -> new StudentCouncilNoticeNotFoundException(noticeId));
    }
}

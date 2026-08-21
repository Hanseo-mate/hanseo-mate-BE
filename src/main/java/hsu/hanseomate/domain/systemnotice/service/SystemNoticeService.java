package hsu.hanseomate.domain.systemnotice.service;

import hsu.hanseomate.domain.systemnotice.dto.SystemNoticeRequest;
import hsu.hanseomate.domain.systemnotice.dto.SystemNoticeResponse;
import hsu.hanseomate.domain.systemnotice.entity.SystemNotice;
import hsu.hanseomate.domain.systemnotice.exception.SystemNoticeNotFoundException;
import hsu.hanseomate.domain.systemnotice.repository.SystemNoticeRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SystemNoticeService {

    private final SystemNoticeRepository systemNoticeRepository;

    public List<SystemNoticeResponse> getNotices() {
        return systemNoticeRepository.findAllByOrderByCreatedAtDescIdDesc()
                .stream()
                .map(SystemNoticeResponse::from)
                .toList();
    }

    @Transactional
    public SystemNoticeResponse createNotice(SystemNoticeRequest request) {
        SystemNotice notice = SystemNotice.create(
                request.title().trim(),
                request.content()
        );
        return SystemNoticeResponse.from(systemNoticeRepository.saveAndFlush(notice));
    }

    @Transactional
    public SystemNoticeResponse updateNotice(
            Long noticeId,
            SystemNoticeRequest request
    ) {
        SystemNotice notice = findNoticeForUpdate(noticeId);
        notice.update(request.title().trim(), request.content());
        systemNoticeRepository.flush();
        return SystemNoticeResponse.from(notice);
    }

    @Transactional
    public void deleteNotice(Long noticeId) {
        SystemNotice notice = findNoticeForUpdate(noticeId);
        systemNoticeRepository.delete(notice);
        systemNoticeRepository.flush();
    }

    private SystemNotice findNoticeForUpdate(Long noticeId) {
        return systemNoticeRepository.findByIdForUpdate(noticeId)
                .orElseThrow(() -> new SystemNoticeNotFoundException(noticeId));
    }
}

package hsu.hanseomate.domain.studentcouncilnotice.repository;

import hsu.hanseomate.domain.studentcouncilnotice.entity.StudentCouncilNoticeAttachment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentCouncilNoticeAttachmentRepository
        extends JpaRepository<StudentCouncilNoticeAttachment, Long> {

    List<StudentCouncilNoticeAttachment> findAllByNoticeIdOrderByIdAsc(Long noticeId);

    Optional<StudentCouncilNoticeAttachment> findByIdAndNoticeId(Long id, Long noticeId);

    @Query("""
            select attachment
            from StudentCouncilNoticeAttachment attachment
            where attachment.notice.id in :noticeIds
            order by attachment.notice.id asc, attachment.id asc
            """)
    List<StudentCouncilNoticeAttachment> findAllByNoticeIds(
            @Param("noticeIds") Collection<Long> noticeIds
    );
}

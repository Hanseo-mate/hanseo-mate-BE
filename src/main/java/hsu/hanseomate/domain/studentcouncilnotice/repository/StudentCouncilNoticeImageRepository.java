package hsu.hanseomate.domain.studentcouncilnotice.repository;

import hsu.hanseomate.domain.studentcouncilnotice.entity.StudentCouncilNoticeImage;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentCouncilNoticeImageRepository
        extends JpaRepository<StudentCouncilNoticeImage, Long> {

    List<StudentCouncilNoticeImage> findAllByNoticeIdOrderByIdAsc(Long noticeId);

    Optional<StudentCouncilNoticeImage> findByIdAndNoticeId(Long id, Long noticeId);

    @Query("""
            select image
            from StudentCouncilNoticeImage image
            where image.notice.id in :noticeIds
            order by image.notice.id asc, image.id asc
            """)
    List<StudentCouncilNoticeImage> findAllByNoticeIds(
            @Param("noticeIds") Collection<Long> noticeIds
    );
}

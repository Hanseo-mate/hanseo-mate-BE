package hsu.hanseomate.domain.systemnotice.repository;

import hsu.hanseomate.domain.systemnotice.entity.SystemNotice;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SystemNoticeRepository extends JpaRepository<SystemNotice, Long> {

    List<SystemNotice> findAllByOrderByCreatedAtDescIdDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select notice from SystemNotice notice where notice.id = :noticeId")
    Optional<SystemNotice> findByIdForUpdate(@Param("noticeId") Long noticeId);
}

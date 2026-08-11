package hsu.hanseomate.domain.push.repository;

import hsu.hanseomate.domain.push.entity.PushTicket;
import hsu.hanseomate.domain.push.entity.TicketStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushTicketRepository extends JpaRepository<PushTicket, Long> {

    /** 생성된 지 15분이 지난 PENDING_RECEIPT 티켓을 조회 */
    @Query("SELECT t FROM PushTicket t WHERE t.status = :status AND t.createdAt <= :before")
    List<PushTicket> findReadyForReceiptCheck(
            @Param("status") TicketStatus status,
            @Param("before") LocalDateTime before
    );
}

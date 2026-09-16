package hsu.hanseomate.domain.popup.repository;

import hsu.hanseomate.domain.popup.entity.AppPopup;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppPopupRepository extends JpaRepository<AppPopup, Long> {

    List<AppPopup> findAllByOrderByCreatedAtDescIdDesc();

    @Query("""
            select popup
            from AppPopup popup
            where popup.enabled = true
              and (popup.startsAt is null or popup.startsAt <= :now)
              and (popup.endsAt is null or popup.endsAt > :now)
            order by popup.displayOrder asc, popup.id asc
            """)
    List<AppPopup> findAllActiveAt(@Param("now") LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select popup from AppPopup popup where popup.id = :popupId")
    Optional<AppPopup> findByIdForUpdate(@Param("popupId") Long popupId);
}

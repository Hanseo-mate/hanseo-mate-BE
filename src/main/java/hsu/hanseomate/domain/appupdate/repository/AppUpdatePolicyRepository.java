package hsu.hanseomate.domain.appupdate.repository;

import hsu.hanseomate.domain.appupdate.entity.AppUpdatePolicy;
import hsu.hanseomate.domain.appupdate.type.AppPlatform;
import hsu.hanseomate.domain.appupdate.type.AppUpdateStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface AppUpdatePolicyRepository extends JpaRepository<AppUpdatePolicy, Long>, JpaSpecificationExecutor<AppUpdatePolicy> {
    Optional<AppUpdatePolicy> findByPlatformAndStatus(AppPlatform platform, AppUpdateStatus status);

    @Query("select p.platform from AppUpdatePolicy p where p.id = :id")
    Optional<AppPlatform> findPlatformById(long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from AppUpdatePolicy p where p.id = :id")
    Optional<AppUpdatePolicy> findForUpdate(long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from AppUpdatePolicy p where p.platform = :platform and p.status = :status")
    Optional<AppUpdatePolicy> findStatusForUpdate(AppPlatform platform, AppUpdateStatus status);

    @Query("select p.id from AppUpdatePolicy p where p.status = 'SCHEDULED' and p.effectiveAt <= :now")
    List<Long> findDueIds(Instant now);
}

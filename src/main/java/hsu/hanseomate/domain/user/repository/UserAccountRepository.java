package hsu.hanseomate.domain.user.repository;

import hsu.hanseomate.domain.user.entity.UserAccount;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    boolean existsByLoginId(String loginId);

    Optional<UserAccount> findByLoginId(String loginId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from UserAccount user where user.id = :userId")
    Optional<UserAccount> findByIdForUpdate(@Param("userId") Long userId);
}

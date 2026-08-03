package hsu.hanseomate.domain.user.repository;

import hsu.hanseomate.domain.user.entity.UserAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    boolean existsByLoginId(String loginId);

    Optional<UserAccount> findByLoginId(String loginId);
}

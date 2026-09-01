package hsu.hanseomate.domain.auth.repository;

import hsu.hanseomate.domain.auth.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select refreshToken
            from RefreshToken refreshToken
            join fetch refreshToken.userAccount
            where refreshToken.tokenHash = :tokenHash
            """)
    Optional<RefreshToken> findByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash
    );

    @Modifying
    @Query("""
            delete from RefreshToken refreshToken
            where refreshToken.userAccount.id = :userId
            """)
    void deleteAllByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("""
            update RefreshToken refreshToken
            set refreshToken.revokedAt = :revokedAt,
                refreshToken.updatedAt = :revokedAt
            where refreshToken.familyId = :familyId
              and refreshToken.revokedAt is null
            """)
    int revokeActiveFamily(
            @Param("familyId") String familyId,
            @Param("revokedAt") LocalDateTime revokedAt
    );

    @Modifying
    @Query("""
            delete from RefreshToken refreshToken
            where refreshToken.expiresAt <= :cutoff
            """)
    int deleteExpiredAtOrBefore(@Param("cutoff") LocalDateTime cutoff);

    long countByUserAccountId(Long userId);
}

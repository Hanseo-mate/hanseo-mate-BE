package hsu.hanseomate.domain.auth.entity;

import hsu.hanseomate.domain.user.entity.UserAccount;
import hsu.hanseomate.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Getter
@Entity
@Table(
        name = "refresh_tokens",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_refresh_token_hash",
                columnNames = "token_hash"
        ),
        indexes = {
                @Index(name = "idx_refresh_tokens_user", columnList = "user_id"),
                @Index(name = "idx_refresh_tokens_family", columnList = "family_id"),
                @Index(name = "idx_refresh_tokens_expires_at", columnList = "expires_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_refresh_tokens_user")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private UserAccount userAccount;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "family_id", nullable = false, length = 36)
    private String familyId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "replaced_by_token_hash", length = 64)
    private String replacedByTokenHash;

    private RefreshToken(
            UserAccount userAccount,
            String tokenHash,
            String familyId,
            LocalDateTime expiresAt
    ) {
        this.userAccount = userAccount;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.expiresAt = expiresAt;
    }

    public static RefreshToken create(
            UserAccount userAccount,
            String tokenHash,
            String familyId,
            LocalDateTime expiresAt
    ) {
        return new RefreshToken(userAccount, tokenHash, familyId, expiresAt);
    }

    public boolean isUsableAt(LocalDateTime now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public boolean wasRotated() {
        return revokedAt != null && replacedByTokenHash != null;
    }

    public void revoke(LocalDateTime revokedAt, String replacementTokenHash) {
        if (this.revokedAt != null) {
            return;
        }
        this.revokedAt = revokedAt;
        this.replacedByTokenHash = replacementTokenHash;
    }
}

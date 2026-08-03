package hsu.hanseomate.domain.user.entity;

import hsu.hanseomate.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "user_accounts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_account_login_id",
                columnNames = "login_id"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAccount extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login_id", nullable = false, length = 100)
    private String loginId;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    private UserAccount(String loginId, String passwordHash) {
        this.loginId = loginId;
        this.passwordHash = passwordHash;
    }

    public static UserAccount create(String loginId, String passwordHash) {
        return new UserAccount(loginId, passwordHash);
    }
}

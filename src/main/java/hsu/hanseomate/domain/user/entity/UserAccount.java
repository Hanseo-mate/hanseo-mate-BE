package hsu.hanseomate.domain.user.entity;

import hsu.hanseomate.domain.cafeteria.entity.RestaurantType;
import hsu.hanseomate.domain.user.type.UserRole;
import hsu.hanseomate.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_restaurant_type", nullable = false, length = 20)
    private RestaurantType preferredRestaurantType;

    private UserAccount(String loginId, String passwordHash) {
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.role = UserRole.USER;
        this.preferredRestaurantType = RestaurantType.MAIN_STUDENT;
    }

    public static UserAccount create(String loginId, String passwordHash) {
        return new UserAccount(loginId, passwordHash);
    }

    public void changePreferredRestaurantType(
            RestaurantType preferredRestaurantType
    ) {
        if (preferredRestaurantType != RestaurantType.MAIN_STUDENT
                && preferredRestaurantType != RestaurantType.TAEAN_STUDENT) {
            throw new IllegalArgumentException(
                    "학생식당만 선호 식당으로 설정할 수 있습니다."
            );
        }
        this.preferredRestaurantType = preferredRestaurantType;
    }
}

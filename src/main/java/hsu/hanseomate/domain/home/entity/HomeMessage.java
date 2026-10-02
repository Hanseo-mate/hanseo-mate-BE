package hsu.hanseomate.domain.home.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "home_messages")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HomeMessage {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(nullable = false, length = 500)
    private String message;

    public static HomeMessage create(String message) {
        HomeMessage homeMessage = new HomeMessage();
        homeMessage.id = SINGLETON_ID;
        homeMessage.message = message;
        return homeMessage;
    }
}

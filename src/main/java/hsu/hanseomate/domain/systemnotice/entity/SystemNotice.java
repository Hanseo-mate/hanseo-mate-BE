package hsu.hanseomate.domain.systemnotice.entity;

import hsu.hanseomate.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "system_notices",
        indexes = @Index(
                name = "idx_system_notices_created_at",
                columnList = "created_at,id"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SystemNotice extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    private SystemNotice(String title, String content) {
        this.title = title;
        this.content = content;
    }

    public static SystemNotice create(String title, String content) {
        return new SystemNotice(title, content);
    }

    public void update(String title, String content) {
        this.title = title;
        this.content = content;
    }
}

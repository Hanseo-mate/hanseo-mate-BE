package hsu.hanseomate.domain.essentiallink.entity;

import hsu.hanseomate.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "essential_links")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EssentialLink extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(nullable = false, length = 50)
    private String category;

    private EssentialLink(String name, String url, String category) {
        this.name = name;
        this.url = url;
        this.category = category;
    }

    public static EssentialLink create(String name, String url, String category) {
        return new EssentialLink(name, url, category);
    }

    public void update(String name, String url, String category) {
        this.name = name;
        this.url = url;
        this.category = category;
    }
}

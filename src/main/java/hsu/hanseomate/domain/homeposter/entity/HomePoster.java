package hsu.hanseomate.domain.homeposter.entity;

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
@Table(name = "home_posters")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HomePoster extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "image_url", nullable = false, length = 2048)
    private String imageUrl;

    @Column(name = "link_url", length = 2048)
    private String linkUrl;

    private HomePoster(String imageUrl, String linkUrl) {
        this.imageUrl = imageUrl;
        this.linkUrl = linkUrl;
    }

    public static HomePoster create(String imageUrl) {
        return create(imageUrl, null);
    }

    public static HomePoster create(String imageUrl, String linkUrl) {
        return new HomePoster(imageUrl, linkUrl);
    }

    public void update(String imageUrl, String linkUrl) {
        this.imageUrl = imageUrl;
        this.linkUrl = linkUrl;
    }
}

package hsu.hanseomate.domain.club.entity;

import hsu.hanseomate.domain.club.type.ClubCategory;
import hsu.hanseomate.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(
        name = "clubs",
        indexes = @Index(name = "idx_clubs_category", columnList = "category"),
        uniqueConstraints = @UniqueConstraint(name = "uk_clubs_name", columnNames = "name")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Club extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private ClubCategory category;

    @Column(name = "profile_image_url", length = 2048)
    private String profileImageUrl;

    @Column(name = "background_image_url", length = 2048)
    private String backgroundImageUrl;

    @Column(name = "short_description", length = 255)
    private String shortDescription;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String introduction;

    @Lob
    @Column(name = "activity_content", columnDefinition = "LONGTEXT")
    private String activityContent;

    @Lob
    @Column(name = "recruitment_content", columnDefinition = "LONGTEXT")
    private String recruitmentContent;

    @Column(name = "instagram_url", length = 2048)
    private String instagramUrl;

    @Column(name = "kakao_talk_url", length = 2048)
    private String kakaoTalkUrl;

    private Club(
            String name,
            ClubCategory category
    ) {
        this.name = name;
        this.category = category;
    }

    public static Club create(
            String name,
            ClubCategory category
    ) {
        return new Club(name, category);
    }

    public void updateBasicInfo(String name, String shortDescription) {
        this.name = name;
        this.shortDescription = shortDescription;
    }

    public void updateProfileImage(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public void updateBackgroundImage(String backgroundImageUrl) {
        this.backgroundImageUrl = backgroundImageUrl;
    }

    public void updateInformation(
            String introduction,
            String activityContent,
            String instagramUrl,
            String kakaoTalkUrl
    ) {
        this.introduction = introduction;
        this.activityContent = activityContent;
        this.instagramUrl = instagramUrl;
        this.kakaoTalkUrl = kakaoTalkUrl;
    }

    public void updateRecruitment(String recruitmentContent) {
        this.recruitmentContent = recruitmentContent;
    }
}

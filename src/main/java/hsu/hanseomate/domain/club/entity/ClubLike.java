package hsu.hanseomate.domain.club.entity;

import hsu.hanseomate.global.common.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Getter
@Entity
@Table(
        name = "club_likes",
        indexes = @Index(name = "idx_club_likes_club", columnList = "club_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClubLike extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    private ClubLike(Club club) {
        this.club = club;
    }

    public static ClubLike create(Club club) {
        return new ClubLike(club);
    }
}

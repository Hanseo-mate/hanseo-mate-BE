package hsu.hanseomate.domain.club.entity;

import hsu.hanseomate.domain.club.type.ClubReviewOption;
import hsu.hanseomate.global.common.BaseTimeEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "club_reviews",
        indexes = @Index(name = "idx_club_reviews_club", columnList = "club_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClubReview extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @ElementCollection(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @CollectionTable(
            name = "club_review_selections",
            joinColumns = @JoinColumn(name = "club_review_id", nullable = false),
            indexes = @Index(
                    name = "idx_club_review_selections_option",
                    columnList = "review_option"
            ),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_club_review_selections_review_option",
                    columnNames = {"club_review_id", "review_option"}
            )
    )
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "review_option", nullable = false, length = 50)
    private Set<ClubReviewOption> reviewTags = new LinkedHashSet<>();

    private ClubReview(Club club, Set<ClubReviewOption> reviewTags) {
        this.club = club;
        this.reviewTags.addAll(reviewTags);
    }

    public static ClubReview create(
            Club club,
            Set<ClubReviewOption> reviewTags
    ) {
        return new ClubReview(club, reviewTags);
    }

    public Long getId() {
        return id;
    }

    public Club getClub() {
        return club;
    }

    public Set<ClubReviewOption> getReviewTags() {
        return Set.copyOf(reviewTags);
    }
}

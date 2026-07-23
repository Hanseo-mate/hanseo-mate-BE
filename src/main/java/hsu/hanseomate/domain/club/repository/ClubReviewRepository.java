package hsu.hanseomate.domain.club.repository;

import hsu.hanseomate.domain.club.entity.ClubReview;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClubReviewRepository extends JpaRepository<ClubReview, Long> {

    Optional<ClubReview> findFirstByClubIdOrderByIdDesc(Long clubId);

    long countByClubId(Long clubId);

    void deleteAllByClubId(Long clubId);

    @Query("""
            select review.club.id as clubId,
                   reviewTag as reviewTag,
                   count(review.id) as reviewCount
            from ClubReview review
            join review.reviewTags reviewTag
            where review.club.id in :clubIds
            group by review.club.id, reviewTag
            """)
    List<ClubReviewCountProjection> countReviewTagsByClubIds(
            @Param("clubIds") Collection<Long> clubIds
    );
}

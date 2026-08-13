package hsu.hanseomate.domain.club.repository;

import hsu.hanseomate.domain.club.entity.ClubLike;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClubLikeRepository extends JpaRepository<ClubLike, Long> {

    Optional<ClubLike> findByClubIdAndLikerId(Long clubId, Long likerId);

    long countByClubId(Long clubId);

    void deleteAllByClubId(Long clubId);

    @Query("""
            select clubLike.club.id as clubId, count(clubLike.id) as likeCount
            from ClubLike clubLike
            where clubLike.club.id in :clubIds
            group by clubLike.club.id
            """)
    List<ClubLikeCountProjection> countByClubIds(
            @Param("clubIds") Collection<Long> clubIds
    );

    @Query("""
            select clubLike.club.id
            from ClubLike clubLike
            where clubLike.liker.id = :likerId
              and clubLike.club.id in :clubIds
            """)
    Set<Long> findLikedClubIds(
            @Param("likerId") Long likerId,
            @Param("clubIds") Collection<Long> clubIds
    );

    @Query("""
            select clubLike
            from ClubLike clubLike
            join fetch clubLike.club
            where clubLike.liker.id = :likerId
            order by clubLike.id desc
            """)
    List<ClubLike> findAllByLikerIdWithClubOrderByIdDesc(
            @Param("likerId") Long likerId
    );
}

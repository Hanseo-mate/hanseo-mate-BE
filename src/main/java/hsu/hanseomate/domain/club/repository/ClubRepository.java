package hsu.hanseomate.domain.club.repository;

import hsu.hanseomate.domain.club.entity.Club;
import hsu.hanseomate.domain.club.type.ClubCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface ClubRepository extends JpaRepository<Club, Long> {

    List<Club> findAllByOrderByIdAsc();

    List<Club> findAllByCategoryOrderByIdAsc(ClubCategory category);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select club from Club club where club.id = :clubId")
    Optional<Club> findByIdForUpdate(@Param("clubId") Long clubId);
}

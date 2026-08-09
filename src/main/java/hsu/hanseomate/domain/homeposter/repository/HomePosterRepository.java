package hsu.hanseomate.domain.homeposter.repository;

import hsu.hanseomate.domain.homeposter.entity.HomePoster;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HomePosterRepository extends JpaRepository<HomePoster, Long> {

    List<HomePoster> findAllByOrderByIdAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select poster from HomePoster poster where poster.id = :posterId")
    Optional<HomePoster> findByIdForUpdate(@Param("posterId") Long posterId);
}

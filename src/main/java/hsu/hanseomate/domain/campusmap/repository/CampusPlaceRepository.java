package hsu.hanseomate.domain.campusmap.repository;

import hsu.hanseomate.domain.campusmap.entity.CampusPlace;
import hsu.hanseomate.domain.campusmap.type.CampusCode;
import hsu.hanseomate.domain.campusmap.type.CampusPlaceCategory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampusPlaceRepository extends JpaRepository<CampusPlace, Long> {

    boolean existsByCampusCodeAndPlaceNameKey(
            CampusCode campusCode,
            String placeNameKey
    );

    boolean existsByCampusCodeAndPlaceNameKeyAndIdNot(
            CampusCode campusCode,
            String placeNameKey,
            Long id
    );

    @Query("""
            SELECT place
            FROM CampusPlace place
            WHERE (:campusCode IS NULL OR place.campusCode = :campusCode)
              AND (:category IS NULL OR place.category = :category)
            ORDER BY place.campusCode, place.placeName, place.id
            """)
    List<CampusPlace> findAllForMap(
            @Param("campusCode") CampusCode campusCode,
            @Param("category") CampusPlaceCategory category
    );
}

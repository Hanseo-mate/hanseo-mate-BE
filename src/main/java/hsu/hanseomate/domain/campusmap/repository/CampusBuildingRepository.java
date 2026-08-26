package hsu.hanseomate.domain.campusmap.repository;

import hsu.hanseomate.domain.campusmap.entity.CampusBuilding;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampusBuildingRepository
        extends JpaRepository<CampusBuilding, Long> {

    @Query("""
            SELECT building
            FROM CampusBuilding building
            WHERE building.canonicalNameKey IN :canonicalNameKeys
            """)
    List<CampusBuilding> findAllByCanonicalNameKeyIn(
            @Param("canonicalNameKeys") Set<String> canonicalNameKeys
    );
}

package hsu.hanseomate.domain.campusmap.repository;

import hsu.hanseomate.domain.campusmap.entity.CampusBuildingAlias;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampusBuildingAliasRepository
        extends JpaRepository<CampusBuildingAlias, Long> {

    @Query("""
            SELECT alias
            FROM CampusBuildingAlias alias
            JOIN FETCH alias.building
            WHERE alias.aliasKey IN :aliasKeys
            """)
    List<CampusBuildingAlias> findAllWithBuildingByAliasKeyIn(
            @Param("aliasKeys") Set<String> aliasKeys
    );
}

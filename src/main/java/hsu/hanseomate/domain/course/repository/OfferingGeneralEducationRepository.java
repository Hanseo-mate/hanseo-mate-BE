package hsu.hanseomate.domain.course.repository;

import hsu.hanseomate.domain.course.entity.OfferingGeneralEducation;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OfferingGeneralEducationRepository extends JpaRepository<OfferingGeneralEducation, UUID> {

    @Modifying
    @Query("delete from OfferingGeneralEducation g where g.offering.id in :offeringIds")
    int deleteByOfferingIds(@Param("offeringIds") Collection<UUID> offeringIds);
}

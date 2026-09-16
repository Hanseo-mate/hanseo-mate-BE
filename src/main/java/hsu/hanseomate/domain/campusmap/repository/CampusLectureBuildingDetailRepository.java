package hsu.hanseomate.domain.campusmap.repository;

import hsu.hanseomate.domain.campusmap.entity.CampusLectureBuildingDetail;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampusLectureBuildingDetailRepository
        extends JpaRepository<CampusLectureBuildingDetail, Long> {
}

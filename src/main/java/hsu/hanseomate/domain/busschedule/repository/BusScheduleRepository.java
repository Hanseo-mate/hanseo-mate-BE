package hsu.hanseomate.domain.busschedule.repository;

import hsu.hanseomate.domain.busschedule.entity.BusSchedule;
import hsu.hanseomate.domain.busschedule.type.MainCategory;
import hsu.hanseomate.domain.busschedule.type.SubCategory;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusScheduleRepository extends JpaRepository<BusSchedule, Long> {

    Optional<BusSchedule> findByMainCategoryAndSubCategory(
            MainCategory mainCategory,
            SubCategory subCategory
    );
}

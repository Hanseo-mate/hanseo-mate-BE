package hsu.hanseomate.domain.course.repository;

import hsu.hanseomate.domain.course.entity.Course;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface CourseRepository extends JpaRepository<Course, UUID> {

    List<Course> findAllByMasterKeyIn(Collection<String> masterKeys);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Course> findAllByCourseCodeIn(Collection<String> courseCodes);
}

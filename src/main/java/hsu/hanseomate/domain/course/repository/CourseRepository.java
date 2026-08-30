package hsu.hanseomate.domain.course.repository;

import hsu.hanseomate.domain.course.entity.Course;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, UUID> {

    List<Course> findAllByMasterKeyIn(Collection<String> masterKeys);
}

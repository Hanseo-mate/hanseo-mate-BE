package hsu.hanseomate.domain.course.repository;

import hsu.hanseomate.domain.course.entity.Classroom;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassroomRepository extends JpaRepository<Classroom, UUID> {

    List<Classroom> findAllByMasterKeyIn(Collection<String> masterKeys);
}

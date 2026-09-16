package hsu.hanseomate.domain.courseimport.repository;

import hsu.hanseomate.domain.courseimport.entity.CourseImportIssue;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseImportIssueRepository extends JpaRepository<CourseImportIssue, UUID> {
}

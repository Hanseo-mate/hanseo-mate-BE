package hsu.hanseomate.domain.studentcouncilnotice.repository;

import hsu.hanseomate.domain.studentcouncilnotice.entity.StudentCouncilNotice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentCouncilNoticeRepository extends JpaRepository<StudentCouncilNotice, Long> {

    Page<StudentCouncilNotice> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}

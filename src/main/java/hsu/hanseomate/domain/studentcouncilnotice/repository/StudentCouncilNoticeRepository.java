package hsu.hanseomate.domain.studentcouncilnotice.repository;

import hsu.hanseomate.domain.studentcouncilnotice.entity.StudentCouncilNotice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StudentCouncilNoticeRepository extends JpaRepository<StudentCouncilNotice, Long> {

    Page<StudentCouncilNotice> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE StudentCouncilNotice s SET s.viewCount = s.viewCount + 1 WHERE s.id = :id")
    void incrementViewCount(@Param("id") Long id);

    // 2. 띄어쓰기 무시 제목 검색
    @Query("SELECT s FROM StudentCouncilNotice s WHERE REPLACE(s.title, ' ', '') LIKE %:keyword%")
    List<StudentCouncilNotice> searchByTitleIgnoringSpaces(@Param("keyword") String keyword, Pageable pageable);
}

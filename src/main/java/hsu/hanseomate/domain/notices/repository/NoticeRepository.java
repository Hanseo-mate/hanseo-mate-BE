package hsu.hanseomate.domain.notices.repository;

import hsu.hanseomate.domain.notices.entity.Notice;
import hsu.hanseomate.domain.notices.entity.NoticeType;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface NoticeRepository extends JpaRepository<Notice, Long> {

    Page<Notice> findAllByNoticeTypeOrderByIsHotDescPostDateDescIdDesc(NoticeType noticeType, Pageable pageable);

    Optional<NoticeTitleProjection>
    findFirstByNoticeTypeOrderByViewCountDescPostDateDescIdDesc(NoticeType noticeType);

    @Query("""
            SELECT n
            FROM Notice n
            WHERE n.noticeType <> :excludedNoticeType
            ORDER BY
                n.isHot DESC,
                CASE
                    WHEN n.noticeType = hsu.hanseomate.domain.notices.entity.NoticeType.ACADEMIC THEN 0
                    WHEN n.noticeType = hsu.hanseomate.domain.notices.entity.NoticeType.GENERAL THEN 1
                    WHEN n.noticeType = hsu.hanseomate.domain.notices.entity.NoticeType.SCHOLARSHIP THEN 2
                    ELSE 3
                END,
                n.postDate DESC,
                n.id DESC
            """)
    Page<Notice> findAllWithoutTypeOrderByPriority(
            @Param("excludedNoticeType") NoticeType excludedNoticeType,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "attachments")
    @Query("SELECT n FROM Notice n WHERE n.id = :noticeId")
    Optional<Notice> findDetailById(@Param("noticeId") Long noticeId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Notice n
            SET n.viewCount = n.viewCount + 1
            WHERE n.id = :noticeId
            """)
    int incrementViewCount(@Param("noticeId") Long noticeId);

    @Query("""
            SELECT n
            FROM Notice n
            WHERE n.noticeType <> :excludedNoticeType
                AND (
                    LOWER(function('replace', n.title, ' ', '')) LIKE CONCAT('%', :normalizedKeyword, '%')
                    OR LOWER(function('replace', n.contentHtml, ' ', '')) LIKE CONCAT('%', :normalizedKeyword, '%')
                    OR LOWER(function('replace', n.author, ' ', '')) LIKE CONCAT('%', :normalizedKeyword, '%')
                )
            ORDER BY
                n.isHot DESC,
                CASE
                    WHEN n.noticeType = hsu.hanseomate.domain.notices.entity.NoticeType.ACADEMIC THEN 0
                    WHEN n.noticeType = hsu.hanseomate.domain.notices.entity.NoticeType.GENERAL THEN 1
                    WHEN n.noticeType = hsu.hanseomate.domain.notices.entity.NoticeType.SCHOLARSHIP THEN 2
                    ELSE 3
                END,
                n.postDate DESC,
                n.id DESC
            """)
    Page<Notice> searchWithoutTypeByKeyword(
            @Param("excludedNoticeType") NoticeType excludedNoticeType,
            @Param("normalizedKeyword") String normalizedKeyword,
            Pageable pageable
    );

    // 2. 띄어쓰기 무시 제목 검색
    @Query("SELECT n FROM Notice n WHERE REPLACE(n.title, ' ', '') LIKE %:keyword%")
    List<Notice> searchByTitleIgnoringSpaces(@Param("keyword") String keyword, Pageable pageable);
}

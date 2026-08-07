package hsu.hanseomate.domain.studentcouncilnotice.entity;

import hsu.hanseomate.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "student_council_notices")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentCouncilNotice extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, length = 100)
    private String author;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "view_count", nullable = false, columnDefinition = "BIGINT DEFAULT 0")
    private long viewCount = 0L;

    private StudentCouncilNotice(String title, String author, String content) {
        this.title = title;
        this.author = author;
        this.content = content;
    }

    public static StudentCouncilNotice create(String title, String author, String content) {
        return new StudentCouncilNotice(title, author, content);
    }

    public void update(String title, String author, String content) {
        this.title = title;
        this.author = author;
        this.content = content;
    }
}

package hsu.hanseomate.domain.courseenrichment.equivalence.entity;

import hsu.hanseomate.domain.courseenrichment.equivalence.support.EquivalentCourseHashing;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@org.hibernate.annotations.Immutable
@Table(name = "equivalent_course_contents")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EquivalentCourseContent {

    @Id
    @Column(name = "content_key", length = 64, nullable = false, updatable = false)
    private String contentKey;

    @Column(name = "course_code", length = 7, nullable = false, updatable = false)
    private String courseCode;

    @Column(name = "course_name", length = 255, nullable = false, updatable = false)
    private String courseName;

    public static EquivalentCourseContent create(String courseCode, String courseName) {
        EquivalentCourseContent content = new EquivalentCourseContent();
        content.contentKey = EquivalentCourseHashing.contentKey(courseCode, courseName);
        content.courseCode = courseCode;
        content.courseName = courseName;
        return content;
    }
}

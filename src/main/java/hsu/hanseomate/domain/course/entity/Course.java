package hsu.hanseomate.domain.course.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "courses")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Course {

    @Id
    private UUID id;

    @Column(name = "master_key", nullable = false, unique = true, length = 64)
    private String masterKey;

    @Column(name = "course_code", length = 100)
    private String courseCode;

    @Column(name = "course_name", length = 255)
    private String courseName;

    private Course(String masterKey, String courseCode, String courseName) {
        this.id = UUID.randomUUID();
        this.masterKey = masterKey;
        this.courseCode = courseCode;
        this.courseName = courseName;
    }

    public static Course create(String masterKey, String courseCode, String courseName) {
        return new Course(masterKey, courseCode, courseName);
    }

    public void updateName(String courseName) {
        if (courseName != null) {
            this.courseName = courseName;
        }
    }
}

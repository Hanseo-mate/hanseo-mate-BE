package hsu.hanseomate.domain.course.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "semesters", uniqueConstraints = {
        @UniqueConstraint(name = "uk_semester_year_term", columnNames = {"academic_year", "semester"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Semester {

    @Id
    private UUID id;

    @Column(name = "academic_year", nullable = false)
    private int academicYear;

    @Column(nullable = false)
    private int semester;

    private Semester(int academicYear, int semester) {
        this.id = UUID.randomUUID();
        this.academicYear = academicYear;
        this.semester = semester;
    }

    public static Semester create(int academicYear, int semester) {
        return new Semester(academicYear, semester);
    }
}

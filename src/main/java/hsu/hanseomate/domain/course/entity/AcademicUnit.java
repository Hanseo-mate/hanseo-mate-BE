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
@Table(name = "academic_units")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AcademicUnit {

    @Id
    private UUID id;

    @Column(name = "master_key", nullable = false, unique = true, length = 64)
    private String masterKey;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "department_name", nullable = false, length = 255)
    private String departmentName;

    @Column(name = "major_name", length = 255)
    private String majorName;

    private AcademicUnit(String masterKey, String originalName, String departmentName, String majorName) {
        this.id = UUID.randomUUID();
        this.masterKey = masterKey;
        this.originalName = originalName;
        this.departmentName = departmentName;
        this.majorName = majorName;
    }

    public static AcademicUnit create(
            String masterKey,
            String originalName,
            String departmentName,
            String majorName
    ) {
        return new AcademicUnit(masterKey, originalName, departmentName, majorName);
    }
}

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
@Table(name = "classrooms")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Classroom {

    @Id
    private UUID id;

    @Column(name = "master_key", nullable = false, unique = true, length = 64)
    private String masterKey;

    @Column(name = "campus_code", length = 100)
    private String campusCode;

    @Column(name = "building_name", length = 255)
    private String buildingName;

    @Column(name = "room_number", length = 100)
    private String roomNumber;

    @Column(name = "original_value", nullable = false, length = 500)
    private String originalValue;

    private Classroom(
            String masterKey,
            String campusCode,
            String buildingName,
            String roomNumber,
            String originalValue
    ) {
        this.id = UUID.randomUUID();
        this.masterKey = masterKey;
        this.campusCode = campusCode;
        this.buildingName = buildingName;
        this.roomNumber = roomNumber;
        this.originalValue = originalValue;
    }

    public static Classroom create(
            String masterKey,
            String campusCode,
            String buildingName,
            String roomNumber,
            String originalValue
    ) {
        return new Classroom(masterKey, campusCode, buildingName, roomNumber, originalValue);
    }
}

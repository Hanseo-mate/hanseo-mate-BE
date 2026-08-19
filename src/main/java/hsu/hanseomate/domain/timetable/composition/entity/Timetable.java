package hsu.hanseomate.domain.timetable.composition.entity;

import hsu.hanseomate.domain.user.entity.UserAccount;
import hsu.hanseomate.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Getter
@Entity
@Table(
        name = "timetables",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_timetable_owner_term",
                columnNames = {"owner_id", "academic_year", "semester"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Timetable extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "owner_id",
            nullable = false,
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_timetables_owner")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    private UserAccount owner;

    @Column(name = "academic_year", nullable = false)
    private int academicYear;

    @Column(nullable = false)
    private int semester;

    private Timetable(Long ownerId, int academicYear, int semester) {
        this.ownerId = ownerId;
        this.academicYear = academicYear;
        this.semester = semester;
    }

    public static Timetable create(Long ownerId, int academicYear, int semester) {
        return new Timetable(ownerId, academicYear, semester);
    }

    public boolean isOwnedBy(Long userId) {
        return ownerId.equals(userId);
    }
}

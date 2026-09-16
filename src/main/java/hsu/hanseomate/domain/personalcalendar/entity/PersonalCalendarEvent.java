package hsu.hanseomate.domain.personalcalendar.entity;

import hsu.hanseomate.domain.user.entity.UserAccount;
import hsu.hanseomate.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Getter
@Entity
@Table(
        name = "personal_calendar_events",
        indexes = @Index(
                name = "idx_personal_calendar_events_owner_dates",
                columnList = "owner_id,start_date,end_date,id"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PersonalCalendarEvent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "owner_id", nullable = false)
    private UserAccount owner;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false, length = 500)
    private String title;

    private PersonalCalendarEvent(
            UserAccount owner,
            LocalDate startDate,
            LocalDate endDate,
            String title
    ) {
        this.owner = owner;
        this.startDate = startDate;
        this.endDate = endDate;
        this.title = title;
    }

    public static PersonalCalendarEvent create(
            UserAccount owner,
            LocalDate startDate,
            LocalDate endDate,
            String title
    ) {
        return new PersonalCalendarEvent(owner, startDate, endDate, title);
    }

    public void update(LocalDate startDate, LocalDate endDate, String title) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.title = title;
    }
}

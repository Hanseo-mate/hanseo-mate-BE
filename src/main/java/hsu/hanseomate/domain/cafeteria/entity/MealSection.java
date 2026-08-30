package hsu.hanseomate.domain.cafeteria.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

/**
 * 식사 시간·코너 단위의 식단 구역.
 * <p>
 * 3-tier(DailyMenu → MealSection → Dish) 구조에서 2-tier 로 재편되었다.
 * 개별 Dish 엔티티 대신 {@code dishes} 를 MySQL JSON 컬럼에 문자열 리스트로 저장한다.
 * ({@code corner_name}, {@code price}, {@code dishes}, {@code raw_text} 컬럼 추가,
 * 기존 {@code menu_category} 컬럼 제거)
 */
@Entity
@Table(name = "meal_sections")
public class MealSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "daily_menu_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private DailyMenu dailyMenu;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_time", nullable = false, length = 10)
    private MealTime mealTime;

    @Column(name = "corner_name", nullable = false, length = 100)
    private String cornerName;

    @Column(name = "price")
    private Integer price;

    // Hibernate 6+/7 JSON 매핑 관용구. columnDefinition="json" 으로 MySQL/H2 모두
    // JSON 컬럼 타입을 강제하여 ddl-auto=update 와 수동 마이그레이션 스키마를 일치시킨다.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dishes", columnDefinition = "json", nullable = false)
    private List<String> dishes = new ArrayList<>();

    @Column(name = "raw_text", columnDefinition = "text", nullable = false)
    private String rawText;

    protected MealSection() {
    }

    static MealSection create(
            DailyMenu dailyMenu,
            MealTime mealTime,
            String cornerName,
            Integer price,
            List<String> dishes,
            String rawText
    ) {
        MealSection section = new MealSection();
        section.dailyMenu = dailyMenu;
        section.mealTime = mealTime;
        section.cornerName = cornerName;
        section.price = price;
        section.dishes = new ArrayList<>(dishes == null ? List.of() : dishes);
        section.rawText = rawText;
        return section;
    }

    public Long getId() {
        return id;
    }

    public DailyMenu getDailyMenu() {
        return dailyMenu;
    }

    public MealTime getMealTime() {
        return mealTime;
    }

    public String getCornerName() {
        return cornerName;
    }

    public Integer getPrice() {
        return price;
    }

    public List<String> getDishes() {
        return Collections.unmodifiableList(dishes);
    }

    public String getRawText() {
        return rawText;
    }
}

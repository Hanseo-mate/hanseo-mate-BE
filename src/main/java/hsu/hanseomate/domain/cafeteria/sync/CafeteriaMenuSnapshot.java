package hsu.hanseomate.domain.cafeteria.sync;

import hsu.hanseomate.domain.cafeteria.client.CafeteriaDailyMenuCrawlDto;
import hsu.hanseomate.domain.cafeteria.client.CafeteriaMealSectionCrawlDto;
import hsu.hanseomate.domain.cafeteria.entity.DailyMenu;
import hsu.hanseomate.domain.cafeteria.entity.MealSection;
import hsu.hanseomate.domain.cafeteria.entity.MealTime;
import hsu.hanseomate.domain.cafeteria.entity.RestaurantType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * DB 로딩 엔티티와 크롤 DTO 양쪽에서 만들 수 있는, 방향성 JPA 연관을 참조하지 않는
 * 불변 비교 스냅샷.
 * <p>
 * 정렬 기준: (menuDate) → 각 날짜 안에서 (mealTime, 원본 코너 순서). Python 의
 * {@code mealSections} 배열 순서가 의미를 가지므로 알파벳 등으로 재정렬하지 않고
 * 원본 리스트 순서를 코너 정렬 키로 사용한다(엔티티 쪽은 삽입 순서 = id 순서).
 * <p>
 * 비교는 고정 포맷 UTF-8 canonical 문자열의 SHA-256 해시로 하며, 안전망으로
 * {@link #equals(Object)} (record 값 동등성)까지 함께 확인한다. {@code Object.hashCode()}
 * 나 JSON 프로퍼티 순서에 의존하지 않는다.
 */
public record CafeteriaMenuSnapshot(
        RestaurantType restaurantType,
        List<CanonicalDay> days
) {

    private static final char FIELD_SEP = '\u001F';
    private static final char RECORD_SEP = '\u001E';
    private static final char DISH_SEP = '\u001D';

    public CafeteriaMenuSnapshot {
        days = List.copyOf(days);
    }

    public record CanonicalDay(
            LocalDate menuDate,
            List<CanonicalSection> sections
    ) {
        public CanonicalDay {
            sections = List.copyOf(sections);
        }
    }

    public record CanonicalSection(
            MealTime mealTime,
            String cornerName,
            Integer price,
            List<String> dishes,
            String rawText
    ) {
        public CanonicalSection {
            dishes = List.copyOf(dishes);
        }
    }

    public static CafeteriaMenuSnapshot fromEntities(
            RestaurantType restaurantType,
            List<DailyMenu> dailyMenus
    ) {
        List<CanonicalDay> days = new ArrayList<>();
        dailyMenus.stream()
                .sorted(Comparator.comparing(DailyMenu::getMenuDate))
                .forEach(dailyMenu -> {
                    List<CanonicalSection> sections = dailyMenu.getMealSections().stream()
                            .sorted(sectionEntityOrder())
                            .map(section -> new CanonicalSection(
                                    section.getMealTime(),
                                    section.getCornerName(),
                                    section.getPrice(),
                                    List.copyOf(section.getDishes()),
                                    section.getRawText()
                            ))
                            .toList();
                    days.add(new CanonicalDay(dailyMenu.getMenuDate(), sections));
                });
        return new CafeteriaMenuSnapshot(restaurantType, days);
    }

    public static CafeteriaMenuSnapshot fromCrawl(
            RestaurantType restaurantType,
            List<CafeteriaDailyMenuCrawlDto> menus
    ) {
        List<CanonicalDay> days = new ArrayList<>();
        menus.stream()
                .sorted(Comparator.comparing(CafeteriaDailyMenuCrawlDto::menuDate))
                .forEach(dto -> {
                    List<CafeteriaMealSectionCrawlDto> ordered =
                            new ArrayList<>(dto.mealSections() == null
                                    ? List.of() : dto.mealSections());
                    // stable sort: 같은 mealTime 은 원본 배열 순서를 보존한다.
                    ordered.sort(Comparator.comparingInt(
                            section -> section.mealTime().ordinal()));
                    List<CanonicalSection> sections = ordered.stream()
                            .map(section -> new CanonicalSection(
                                    section.mealTime(),
                                    section.cornerName(),
                                    section.price(),
                                    section.dishes() == null
                                            ? List.of() : List.copyOf(section.dishes()),
                                    section.rawText()
                            ))
                            .toList();
                    days.add(new CanonicalDay(dto.menuDate(), sections));
                });
        return new CafeteriaMenuSnapshot(restaurantType, days);
    }

    private static Comparator<MealSection> sectionEntityOrder() {
        return Comparator
                .comparingInt((MealSection section) -> section.getMealTime().ordinal())
                .thenComparing(MealSection::getId,
                        Comparator.nullsLast(Comparator.naturalOrder()));
    }

    /**
     * 고정 포맷 canonical 문자열의 SHA-256 16진 해시.
     */
    public String contentHash() {
        String canonical = canonicalString();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 은 모든 JVM 에 존재하므로 실질적으로 발생하지 않는다.
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private String canonicalString() {
        StringBuilder sb = new StringBuilder();
        sb.append(restaurantType.name()).append(RECORD_SEP);
        for (CanonicalDay day : days) {
            sb.append(day.menuDate().toString()).append(FIELD_SEP);
            for (CanonicalSection section : day.sections()) {
                sb.append(section.mealTime().name()).append(FIELD_SEP);
                sb.append(nullSafe(section.cornerName())).append(FIELD_SEP);
                sb.append(section.price() == null ? "" : section.price().toString())
                        .append(FIELD_SEP);
                sb.append(String.join(String.valueOf(DISH_SEP), section.dishes()))
                        .append(FIELD_SEP);
                sb.append(nullSafe(section.rawText())).append(RECORD_SEP);
            }
            sb.append(RECORD_SEP);
        }
        return sb.toString();
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}

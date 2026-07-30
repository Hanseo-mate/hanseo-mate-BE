package hsu.hanseomate.domain.timetable.search.specification;

import hsu.hanseomate.domain.course.entity.Classroom;
import hsu.hanseomate.domain.course.entity.CourseOffering;
import hsu.hanseomate.domain.course.entity.CourseSchedule;
import hsu.hanseomate.domain.course.entity.OfferingGeneralEducation;
import hsu.hanseomate.domain.courseimport.dto.type.CurriculumType;
import hsu.hanseomate.domain.courseimport.dto.type.DeliveryProvider;
import hsu.hanseomate.domain.courseimport.dto.type.GeneralArea;
import hsu.hanseomate.domain.courseimport.dto.type.GeneralClassification;
import hsu.hanseomate.domain.timetable.search.dto.CourseSearchCondition;
import hsu.hanseomate.domain.timetable.search.type.CourseCreditFilter;
import hsu.hanseomate.domain.timetable.search.type.CourseGradeFilter;
import hsu.hanseomate.domain.timetable.search.type.GeneralCategoryFilter;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class CourseSearchSpecifications {

    private static final List<Integer> STANDARD_GRADES = List.of(1, 2, 3, 4);
    private static final char LIKE_ESCAPE = '\\';

    private CourseSearchSpecifications() {
    }

    public static Specification<CourseOffering> from(CourseSearchCondition condition) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (condition.academicYear() != null) {
                predicates.add(criteriaBuilder.equal(
                        root.get("semester").get("academicYear"),
                        condition.academicYear()
                ));
            }
            if (condition.semester() != null) {
                predicates.add(criteriaBuilder.equal(
                        root.get("semester").get("semester"),
                        condition.semester()
                ));
            }
            if (condition.curriculumType() != null) {
                predicates.add(criteriaBuilder.equal(
                        root.get("curriculumType"),
                        condition.curriculumType()
                ));
            }

            addCurriculumSelectionPredicate(condition, root, criteriaBuilder, predicates);
            addKeywordPredicate(condition, root, query, criteriaBuilder, predicates);
            addGradePredicate(condition.grades(), root, criteriaBuilder, predicates);
            addCreditPredicate(condition.credits(), root, criteriaBuilder, predicates);
            addTimeRangePredicate(condition, root, query, criteriaBuilder, predicates);

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static void addCurriculumSelectionPredicate(
            CourseSearchCondition condition,
            Root<CourseOffering> root,
            CriteriaBuilder criteriaBuilder,
            List<Predicate> predicates
    ) {
        boolean hasAcademicUnits = !condition.academicUnits().isEmpty();
        boolean hasGeneralCategories = !condition.generalCategories().isEmpty();
        if (!hasAcademicUnits && !hasGeneralCategories) {
            return;
        }

        Predicate majorSelection = null;
        if (hasAcademicUnits) {
            Join<CourseOffering, ?> academicUnit = root.join("academicUnit", JoinType.LEFT);
            Predicate matchesAcademicUnit = criteriaBuilder.or(
                    criteriaBuilder.lower(academicUnit.get("originalName"))
                            .in(condition.academicUnits()),
                    criteriaBuilder.lower(academicUnit.get("departmentName"))
                            .in(condition.academicUnits()),
                    criteriaBuilder.lower(academicUnit.get("majorName"))
                            .in(condition.academicUnits())
            );
            majorSelection = criteriaBuilder.and(
                    criteriaBuilder.equal(root.get("curriculumType"), CurriculumType.MAJOR),
                    matchesAcademicUnit
            );
        }

        Predicate generalSelection = null;
        if (hasGeneralCategories) {
            Join<CourseOffering, OfferingGeneralEducation> generalEducation =
                    root.join("generalEducation", JoinType.LEFT);
            generalSelection = criteriaBuilder.and(
                    criteriaBuilder.equal(
                            root.get("curriculumType"),
                            CurriculumType.GENERAL_EDUCATION
                    ),
                    generalCategoryPredicate(
                            condition.generalCategories(),
                            generalEducation,
                            criteriaBuilder
                    )
            );
        }

        if (majorSelection != null && generalSelection != null) {
            predicates.add(criteriaBuilder.or(majorSelection, generalSelection));
        } else {
            predicates.add(majorSelection != null ? majorSelection : generalSelection);
        }
    }

    private static Predicate generalCategoryPredicate(
            Set<GeneralCategoryFilter> categories,
            Join<CourseOffering, OfferingGeneralEducation> generalEducation,
            CriteriaBuilder criteriaBuilder
    ) {
        List<Predicate> categoryPredicates = categories.stream()
                .map(category -> switch (category) {
                    case REQUIRED -> criteriaBuilder.equal(
                            generalEducation.get("classification"),
                            GeneralClassification.REQUIRED
                    );
                    case AREA_1 -> criteriaBuilder.equal(
                            generalEducation.get("area"),
                            GeneralArea.EXPLORATION
                    );
                    case AREA_2 -> criteriaBuilder.equal(
                            generalEducation.get("area"),
                            GeneralArea.COEXISTENCE
                    );
                    case AREA_3 -> criteriaBuilder.equal(
                            generalEducation.get("area"),
                            GeneralArea.INITIATIVE
                    );
                    case E_CLASS -> criteriaBuilder.equal(
                            generalEducation.get("deliveryProvider"),
                            DeliveryProvider.E_CLASS
                    );
                    case HSU_CYBER -> criteriaBuilder.equal(
                            generalEducation.get("deliveryProvider"),
                            DeliveryProvider.HSU_CYBER
                    );
                    case OCU -> criteriaBuilder.equal(
                            generalEducation.get("deliveryProvider"),
                            DeliveryProvider.OCU
                    );
                    case CHUNGNAM_ELEARNING -> criteriaBuilder.equal(
                            generalEducation.get("deliveryProvider"),
                            DeliveryProvider.CHUNGNAM_ELEARNING
                    );
                    case SDU -> criteriaBuilder.equal(
                            generalEducation.get("deliveryProvider"),
                            DeliveryProvider.SDU
                    );
                    case OTHER -> criteriaBuilder.equal(
                            generalEducation.get("deliveryProvider"),
                            DeliveryProvider.OTHER
                    );
                })
                .toList();
        return criteriaBuilder.or(categoryPredicates.toArray(Predicate[]::new));
    }

    private static void addKeywordPredicate(
            CourseSearchCondition condition,
            Root<CourseOffering> root,
            CriteriaQuery<?> query,
            CriteriaBuilder criteriaBuilder,
            List<Predicate> predicates
    ) {
        if (condition.keyword() == null) {
            return;
        }

        Predicate keywordPredicate = switch (condition.searchField()) {
            case COURSE_NAME -> contains(
                    root.get("courseName"),
                    condition.keyword(),
                    criteriaBuilder
            );
            case INSTRUCTOR_NAME -> contains(
                    root.get("instructorName"),
                    condition.keyword(),
                    criteriaBuilder
            );
            case COURSE_CODE -> contains(
                    root.get("courseCode"),
                    condition.keyword(),
                    criteriaBuilder
            );
            case LOCATION -> locationContains(
                    root,
                    query,
                    criteriaBuilder,
                    condition.keyword()
            );
        };
        predicates.add(keywordPredicate);
    }

    private static Predicate locationContains(
            Root<CourseOffering> offering,
            CriteriaQuery<?> query,
            CriteriaBuilder criteriaBuilder,
            String keyword
    ) {
        Subquery<UUID> locationQuery = query.subquery(UUID.class);
        Root<CourseSchedule> schedule = locationQuery.from(CourseSchedule.class);
        Join<CourseSchedule, Classroom> classroom = schedule.join("classroom", JoinType.LEFT);
        Predicate structuredLocation = criteriaBuilder.or(
                contains(classroom.get("campusCode"), keyword, criteriaBuilder),
                contains(classroom.get("buildingName"), keyword, criteriaBuilder),
                contains(classroom.get("roomNumber"), keyword, criteriaBuilder),
                contains(classroom.get("originalValue"), keyword, criteriaBuilder)
        );
        locationQuery.select(schedule.get("id"))
                .where(
                        criteriaBuilder.equal(schedule.get("offering"), offering),
                        structuredLocation
                );

        return criteriaBuilder.or(
                contains(offering.get("classroomText"), keyword, criteriaBuilder),
                criteriaBuilder.exists(locationQuery)
        );
    }

    private static Predicate contains(
            Expression<String> expression,
            String escapedKeyword,
            CriteriaBuilder criteriaBuilder
    ) {
        return criteriaBuilder.like(
                criteriaBuilder.lower(expression),
                "%" + escapedKeyword + "%",
                LIKE_ESCAPE
        );
    }

    private static void addGradePredicate(
            Set<CourseGradeFilter> grades,
            Root<CourseOffering> root,
            CriteriaBuilder criteriaBuilder,
            List<Predicate> predicates
    ) {
        if (grades.isEmpty()) {
            return;
        }

        List<Integer> selectedGrades = grades.stream()
                .map(CourseGradeFilter::getGrade)
                .filter(grade -> grade != null)
                .toList();
        List<Predicate> gradePredicates = new ArrayList<>();

        if (!selectedGrades.isEmpty()) {
            gradePredicates.add(criteriaBuilder.or(
                    criteriaBuilder.isTrue(root.get("commonGrade")),
                    root.get("targetGrade").in(selectedGrades)
            ));
        }
        if (grades.contains(CourseGradeFilter.OTHER)) {
            gradePredicates.add(criteriaBuilder.and(
                    criteriaBuilder.isFalse(root.get("commonGrade")),
                    criteriaBuilder.or(
                            criteriaBuilder.isNull(root.get("targetGrade")),
                            criteriaBuilder.not(root.get("targetGrade").in(STANDARD_GRADES))
                    )
            ));
        }
        predicates.add(criteriaBuilder.or(gradePredicates.toArray(Predicate[]::new)));
    }

    private static void addCreditPredicate(
            Set<CourseCreditFilter> credits,
            Root<CourseOffering> root,
            CriteriaBuilder criteriaBuilder,
            List<Predicate> predicates
    ) {
        if (credits.isEmpty()) {
            return;
        }

        List<Predicate> creditPredicates = credits.stream()
                .map(credit -> switch (credit) {
                    case CREDIT_1 -> criteriaBuilder.equal(
                            root.get("credit"),
                            BigDecimal.ONE
                    );
                    case CREDIT_2 -> criteriaBuilder.equal(
                            root.get("credit"),
                            BigDecimal.valueOf(2)
                    );
                    case CREDIT_3 -> criteriaBuilder.equal(
                            root.get("credit"),
                            BigDecimal.valueOf(3)
                    );
                    case CREDIT_4_OR_MORE -> criteriaBuilder.greaterThanOrEqualTo(
                            root.get("credit"),
                            BigDecimal.valueOf(4)
                    );
                })
                .toList();
        predicates.add(criteriaBuilder.or(creditPredicates.toArray(Predicate[]::new)));
    }

    private static void addTimeRangePredicate(
            CourseSearchCondition condition,
            Root<CourseOffering> offering,
            CriteriaQuery<?> query,
            CriteriaBuilder criteriaBuilder,
            List<Predicate> predicates
    ) {
        if (condition.startPeriod() == null) {
            return;
        }

        Subquery<UUID> anyScheduleQuery = query.subquery(UUID.class);
        Root<CourseSchedule> anySchedule = anyScheduleQuery.from(CourseSchedule.class);
        anyScheduleQuery.select(anySchedule.get("id"))
                .where(criteriaBuilder.equal(anySchedule.get("offering"), offering));
        predicates.add(criteriaBuilder.exists(anyScheduleQuery));

        List<Integer> outsidePeriods = new ArrayList<>();
        for (int period = hsu.hanseomate.domain.course.support.CoursePeriodPolicy.MIN_PERIOD;
                period < condition.startPeriod();
                period++) {
            outsidePeriods.add(period);
        }
        for (int period = condition.endPeriod() + 1;
                period <= hsu.hanseomate.domain.course.support.CoursePeriodPolicy.MAX_PERIOD;
                period++) {
            outsidePeriods.add(period);
        }
        if (outsidePeriods.isEmpty()) {
            return;
        }

        Subquery<UUID> outsideScheduleQuery = query.subquery(UUID.class);
        Root<CourseSchedule> outsideSchedule = outsideScheduleQuery.from(CourseSchedule.class);
        Expression<String> delimitedPeriods = criteriaBuilder.concat(
                criteriaBuilder.concat(",", outsideSchedule.get("periodsValue")),
                ","
        );
        Predicate outsideSelectedRange = criteriaBuilder.or(
                outsidePeriods.stream()
                        .map(period -> criteriaBuilder.like(
                                delimitedPeriods,
                                "%," + period + ",%"
                        ))
                        .toArray(Predicate[]::new)
        );
        outsideScheduleQuery.select(outsideSchedule.get("id"))
                .where(
                        criteriaBuilder.equal(outsideSchedule.get("offering"), offering),
                        outsideSelectedRange
                );
        predicates.add(criteriaBuilder.not(criteriaBuilder.exists(outsideScheduleQuery)));
    }
}

package hsu.hanseomate.domain.home.service;

import hsu.hanseomate.domain.cafeteria.entity.DailyMenu;
import hsu.hanseomate.domain.cafeteria.entity.RestaurantType;
import hsu.hanseomate.domain.cafeteria.repository.DailyMenuRepository;
import hsu.hanseomate.domain.course.entity.Classroom;
import hsu.hanseomate.domain.course.entity.CourseSchedule;
import hsu.hanseomate.domain.course.repository.CourseScheduleRepository;
import hsu.hanseomate.domain.course.support.CoursePeriodTimePolicy;
import hsu.hanseomate.domain.course.support.CoursePeriodTimePolicy.TimeRange;
import hsu.hanseomate.domain.courseimport.dto.type.DayOfWeek;
import hsu.hanseomate.domain.home.dto.HomeCafeteriaMenuResponse;
import hsu.hanseomate.domain.home.dto.HomeNoticeResponse;
import hsu.hanseomate.domain.home.dto.HomeNoticeType;
import hsu.hanseomate.domain.home.dto.HomePageResponse;
import hsu.hanseomate.domain.home.dto.HomePosterItemResponse;
import hsu.hanseomate.domain.home.dto.HomeTodayCourseResponse;
import hsu.hanseomate.domain.homeposter.dto.HomePosterResponse;
import hsu.hanseomate.domain.homeposter.service.HomePosterService;
import hsu.hanseomate.domain.notices.entity.NoticeType;
import hsu.hanseomate.domain.notices.repository.NoticeRepository;
import hsu.hanseomate.domain.notices.repository.NoticeTitleProjection;
import hsu.hanseomate.domain.studentcouncilnotice.repository.StudentCouncilNoticeRepository;
import hsu.hanseomate.domain.studentcouncilnotice.repository.StudentCouncilNoticeTitleProjection;
import hsu.hanseomate.domain.user.entity.UserAccount;
import hsu.hanseomate.domain.user.repository.UserAccountRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomePageService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm");

    private final HomePosterService homePosterService;
    private final DailyMenuRepository dailyMenuRepository;
    private final CourseScheduleRepository courseScheduleRepository;
    private final NoticeRepository noticeRepository;
    private final StudentCouncilNoticeRepository studentCouncilNoticeRepository;
    private final UserAccountRepository userAccountRepository;
    private final Clock clock;

    public HomePageResponse getHome(Optional<Long> currentUserId) {
        LocalDate today = LocalDate.now(clock.withZone(KOREA_ZONE));
        List<HomePosterResponse> posterResponses = homePosterService.getPosters();
        List<String> posterImageUrls = posterResponses.stream()
                .map(HomePosterResponse::imageUrl)
                .toList();
        List<HomePosterItemResponse> posters = posterResponses.stream()
                .map(HomePosterItemResponse::from)
                .toList();
        List<HomeTodayCourseResponse> todayCourses = currentUserId
                .map(ownerId -> todayCourses(ownerId, today))
                .orElseGet(List::of);
        RestaurantType preferredRestaurantType = currentUserId
                .map(this::preferredRestaurantType)
                .orElse(null);
        Optional<RestaurantType> selectedRestaurantType = currentUserId.isPresent()
                ? Optional.ofNullable(preferredRestaurantType)
                : Optional.of(RestaurantType.MAIN_STUDENT);

        return new HomePageResponse(
                currentUserId.isPresent(),
                preferredRestaurantType,
                posterImageUrls.isEmpty() ? null : posterImageUrls,
                posters.isEmpty() ? null : posters,
                todayCourses,
                popularNotices(),
                todayCafeteriaMenus(today, selectedRestaurantType)
        );
    }

    private List<HomeTodayCourseResponse> todayCourses(
            Long ownerId,
            LocalDate today
    ) {
        int semester = today.getMonthValue() <= 6 ? 1 : 2;
        DayOfWeek dayOfWeek = DayOfWeek.valueOf(today.getDayOfWeek().name());

        return courseScheduleRepository.findHomeSchedules(
                        ownerId,
                        today.getYear(),
                        semester,
                        dayOfWeek
                ).stream()
                .flatMap(schedule -> toCourseSlots(schedule).stream())
                .sorted(Comparator
                        .comparingInt(CourseSlot::firstPeriod)
                        .thenComparing(CourseSlot::courseName,
                                Comparator.nullsLast(String::compareTo))
                        .thenComparingInt(CourseSlot::scheduleOrder))
                .map(CourseSlot::response)
                .toList();
    }

    private RestaurantType preferredRestaurantType(Long userId) {
        UserAccount userAccount = userAccountRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException(
                        "로그인이 필요합니다."
                ));
        return userAccount.getPreferredRestaurantType();
    }

    private List<HomeCafeteriaMenuResponse> todayCafeteriaMenus(
            LocalDate today,
            Optional<RestaurantType> selectedRestaurantType
    ) {
        return selectedRestaurantType
                .filter(this::isSupportedStudentRestaurant)
                .flatMap(restaurantType -> findTodayCafeteriaMenu(today, restaurantType)
                        .map(HomeCafeteriaMenuResponse::from))
                .stream()
                .toList();
    }

    private Optional<DailyMenu> findTodayCafeteriaMenu(
            LocalDate today,
            RestaurantType restaurantType
    ) {
        return dailyMenuRepository
                .findAllByMenuDateAndRestaurantTypeInOrderByIdAsc(
                        today,
                        List.of(restaurantType)
                )
                .stream()
                .findFirst();
    }

    private boolean isSupportedStudentRestaurant(RestaurantType restaurantType) {
        return restaurantType == RestaurantType.MAIN_STUDENT
                || restaurantType == RestaurantType.TAEAN_STUDENT;
    }

    private List<CourseSlot> toCourseSlots(CourseSchedule schedule) {
        List<List<Integer>> periodBlocks = consecutivePeriodBlocks(schedule.getPeriods());
        if (periodBlocks.isEmpty()) {
            return List.of(toCourseSlot(schedule, List.of()));
        }
        return periodBlocks.stream()
                .map(periods -> toCourseSlot(schedule, periods))
                .toList();
    }

    private CourseSlot toCourseSlot(
            CourseSchedule schedule,
            List<Integer> periods
    ) {
        String startTime = null;
        String endTime = null;
        int firstPeriod = Integer.MAX_VALUE;

        if (!periods.isEmpty()) {
            firstPeriod = periods.get(0);
            Optional<TimeRange> range = CoursePeriodTimePolicy.findRange(
                    firstPeriod,
                    periods.get(periods.size() - 1)
            );
            if (range.isPresent()) {
                startTime = range.orElseThrow().startTime().format(TIME_FORMATTER);
                endTime = range.orElseThrow().endTime().format(TIME_FORMATTER);
            }
        }

        Classroom classroom = schedule.getClassroom();
        HomeTodayCourseResponse response = new HomeTodayCourseResponse(
                startTime,
                endTime,
                schedule.getCourse().getCourseName(),
                classroom == null ? null : classroom.getBuildingName(),
                classroom == null ? null : classroom.getRoomNumber()
        );
        return new CourseSlot(
                firstPeriod,
                schedule.getScheduleOrder(),
                schedule.getCourse().getCourseName(),
                response
        );
    }

    private List<List<Integer>> consecutivePeriodBlocks(List<Integer> periods) {
        List<Integer> sortedPeriods = periods.stream()
                .distinct()
                .sorted()
                .toList();
        if (sortedPeriods.isEmpty()) {
            return List.of();
        }

        List<List<Integer>> blocks = new ArrayList<>();
        List<Integer> currentBlock = new ArrayList<>();
        for (Integer period : sortedPeriods) {
            if (!currentBlock.isEmpty()
                    && shouldStartNewBlock(
                            currentBlock.get(currentBlock.size() - 1),
                            period
                    )) {
                blocks.add(List.copyOf(currentBlock));
                currentBlock.clear();
            }
            currentBlock.add(period);
        }
        blocks.add(List.copyOf(currentBlock));
        return List.copyOf(blocks);
    }

    private boolean shouldStartNewBlock(int previousPeriod, int currentPeriod) {
        if (currentPeriod != previousPeriod + 1) {
            return true;
        }
        return CoursePeriodTimePolicy.find(previousPeriod).isPresent()
                != CoursePeriodTimePolicy.find(currentPeriod).isPresent();
    }

    private List<HomeNoticeResponse> popularNotices() {
        String studentCouncilTitle = studentCouncilNoticeRepository
                .findFirstByOrderByViewCountDescCreatedAtDescIdDesc()
                .map(StudentCouncilNoticeTitleProjection::getTitle)
                .orElse(null);
        String academicTitle = noticeRepository
                .findFirstByNoticeTypeOrderByViewCountDescPostDateDescIdDesc(
                        NoticeType.ACADEMIC
                )
                .map(NoticeTitleProjection::getTitle)
                .orElse(null);
        String scholarshipTitle = noticeRepository
                .findFirstByNoticeTypeOrderByViewCountDescPostDateDescIdDesc(
                        NoticeType.SCHOLARSHIP
                )
                .map(NoticeTitleProjection::getTitle)
                .orElse(null);

        return List.of(
                new HomeNoticeResponse(
                        HomeNoticeType.STUDENT_COUNCIL,
                        studentCouncilTitle
                ),
                new HomeNoticeResponse(HomeNoticeType.ACADEMIC, academicTitle),
                new HomeNoticeResponse(HomeNoticeType.SCHOLARSHIP, scholarshipTitle)
        );
    }

    private record CourseSlot(
            int firstPeriod,
            int scheduleOrder,
            String courseName,
            HomeTodayCourseResponse response
    ) {
    }
}

package hsu.hanseomate.domain.timetable.reminder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import hsu.hanseomate.domain.course.entity.Course;
import hsu.hanseomate.domain.course.entity.CourseOffering;
import hsu.hanseomate.domain.course.entity.CourseSchedule;
import hsu.hanseomate.domain.course.entity.Semester;
import hsu.hanseomate.domain.course.repository.CourseRepository;
import hsu.hanseomate.domain.course.repository.CourseOfferingRepository;
import hsu.hanseomate.domain.course.repository.CourseScheduleRepository;
import hsu.hanseomate.domain.course.repository.SemesterRepository;
import hsu.hanseomate.domain.courseimport.dto.type.CurriculumType;
import hsu.hanseomate.domain.courseimport.dto.type.DayOfWeek;
import hsu.hanseomate.domain.courseimport.entity.CourseImportHistory;
import hsu.hanseomate.domain.courseimport.repository.CourseImportHistoryRepository;
import hsu.hanseomate.domain.notification.entity.Notification;
import hsu.hanseomate.domain.notification.repository.NotificationRepository;
import hsu.hanseomate.domain.push.client.ExpoPushClient;
import hsu.hanseomate.domain.push.client.ExpoPushMessage;
import hsu.hanseomate.domain.push.entity.NotificationOutbox;
import hsu.hanseomate.domain.push.entity.OutboxStatus;
import hsu.hanseomate.domain.push.entity.PushDevice;
import hsu.hanseomate.domain.push.repository.NotificationOutboxRepository;
import hsu.hanseomate.domain.push.repository.PushDeviceRepository;
import hsu.hanseomate.domain.push.service.NotificationDispatchService;
import hsu.hanseomate.domain.push.service.NotificationService;
import hsu.hanseomate.domain.push.worker.NotificationSendWorker;
import hsu.hanseomate.domain.timetable.composition.entity.Timetable;
import hsu.hanseomate.domain.timetable.composition.entity.TimetableCourse;
import hsu.hanseomate.domain.timetable.composition.repository.TimetableCourseRepository;
import hsu.hanseomate.domain.timetable.composition.repository.TimetableRepository;
import hsu.hanseomate.domain.timetable.reminder.repository.TimetableClassReminderRepository;
import hsu.hanseomate.domain.timetable.reminder.service.TimetableReminderEnqueueService;
import hsu.hanseomate.domain.timetable.reminder.service.TimetableReminderQueryService;
import hsu.hanseomate.domain.timetable.reminder.service.TimetableReminderQueryService.Candidate;
import hsu.hanseomate.domain.timetable.reminder.worker.TimetableReminderWorker;
import hsu.hanseomate.domain.user.entity.UserAccount;
import hsu.hanseomate.domain.user.repository.UserAccountRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:reminder;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TimetableReminderIntegrationTest.TimeConfiguration.class)
class TimetableReminderIntegrationTest {

    @Autowired TimetableReminderQueryService queryService;
    @Autowired TimetableReminderEnqueueService enqueueService;
    @Autowired TimetableClassReminderRepository reminderRepository;
    @Autowired TimetableRepository timetableRepository;
    @Autowired TimetableCourseRepository entryRepository;
    @Autowired UserAccountRepository userRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired CourseOfferingRepository offeringRepository;
    @Autowired CourseScheduleRepository scheduleRepository;
    @Autowired SemesterRepository semesterRepository;
    @Autowired CourseImportHistoryRepository historyRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired NotificationOutboxRepository outboxRepository;
    @Autowired PushDeviceRepository deviceRepository;
    @Autowired NotificationDispatchService dispatchService;
    @Autowired NotificationService notificationService;
    @Autowired MutableClock clock;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean NotificationSendWorker scheduledSender;
    @MockitoBean ExpoPushClient expoClient;

    private TimetableReminderWorker worker;
    private Timetable timetable;
    private Long ownerId;

    @BeforeEach
    void setUp() {
        for (String table : List.of("timetable_class_reminders", "notification_reads",
                "notifications", "notification_outbox", "push_tickets", "push_devices",
                "timetable_courses", "timetables", "course_schedules", "course_offerings",
                "courses", "course_import_histories", "semesters", "user_accounts")) {
            jdbc.execute("DELETE FROM " + table);
        }
        at("2026-09-07T09:00:00+09:00");
        worker = new TimetableReminderWorker(queryService, enqueueService, clock);
        ownerId = userRepository.saveAndFlush(UserAccount.create("reminder-owner", "hash")).getId();
        timetable = timetableRepository.saveAndFlush(Timetable.create(ownerId, 2026, 2));
    }

    @Test
    void sendsOnlyToOwnersActiveDevicesAndOnlyOwnerCanSeeInboxEntry() throws Exception {
        TimetableCourse entry = custom(timetable, DayOfWeek.MONDAY, "10:00");
        Long otherId = userRepository.saveAndFlush(UserAccount.create("other-user", "hash")).getId();
        deviceRepository.saveAndFlush(PushDevice.create(ownerId, "owner-phone", "ExponentPushToken[owner]",
                "android", "project", "1.0"));
        deviceRepository.saveAndFlush(PushDevice.create(otherId, "other-phone", "ExponentPushToken[other]",
                "android", "project", "1.0"));
        deviceRepository.saveAndFlush(PushDevice.create(null, "anonymous", "ExponentPushToken[anon]",
                "ios", "project", "1.0"));
        PushDevice inactive = PushDevice.create(ownerId, "old-phone", "ExponentPushToken[old]",
                "android", "project", "1.0");
        inactive.deactivate("DeviceNotRegistered");
        deviceRepository.saveAndFlush(inactive);

        worker.enqueueDueReminders();

        Notification alert = notificationRepository.findAll().get(0);
        assertThat(alert.getTargetUserId()).isEqualTo(ownerId);
        assertThat(alert.getBody()).isEqualTo("자료구조 수업 시작 1시간 전입니다.");
        assertThat(new ObjectMapper().readTree(alert.getPayloadData()).path("startsAt").asText())
                .isEqualTo("2026-09-07T10:00:00+09:00");
        assertThat(new ObjectMapper().readTree(alert.getPayloadData()).path("timetableCourseId").asText())
                .isEqualTo(entry.getId().toString());
        assertThat(outboxRepository.findAll()).singleElement().satisfies(outbox -> {
            assertThat(outbox.getTargetUserId()).isEqualTo(ownerId);
            assertThat(outbox.getExpiresAt()).isEqualTo(LocalDateTime.parse("2026-09-07T00:05:00"));
        });
        mvc.perform(get("/api/v1/notifications").param("installationId", "owner-phone"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/notifications").param("installationId", "other-phone"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/notifications").param("installationId", "anonymous"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        when(expoClient.sendMessages(anyList())).thenReturn(List.of());
        new NotificationSendWorker(dispatchService, expoClient, new ObjectMapper(), clock)
                .processPendingNotifications();
        ArgumentCaptor<List<ExpoPushMessage>> sent = ArgumentCaptor.forClass(List.class);
        verify(expoClient).sendMessages(sent.capture());
        assertThat(sent.getValue()).singleElement().satisfies(message -> {
            assertThat(message.getTo()).isEqualTo("ExponentPushToken[owner]");
            assertThat(message.getData()).containsEntry("type", "schedule")
                    .containsEntry("subType", "class_start_reminder");
        });
    }

    @Test
    void repeatedRunsCreateOneOccurrenceAndNextWeekCreatesAnother() {
        custom(timetable, DayOfWeek.MONDAY, "10:00");
        worker.enqueueDueReminders();
        worker.enqueueDueReminders();
        at("2026-09-07T09:01:00+09:00");
        worker.enqueueDueReminders();
        assertThat(reminderRepository.count()).isEqualTo(1);
        at("2026-09-14T09:00:00+09:00");
        worker.enqueueDueReminders();
        assertThat(reminderRepository.count()).isEqualTo(2);
        assertThat(notificationRepository.count()).isEqualTo(2);
        assertThat(outboxRepository.count()).isEqualTo(2);
    }

    @Test
    void neverSendsEarlyAndCatchesOnlyTwoMinutesOfDelay() {
        custom(timetable, DayOfWeek.MONDAY, "10:00");
        at("2026-09-07T08:59:59+09:00");
        worker.enqueueDueReminders();
        assertThat(notificationRepository.count()).isZero();
        at("2026-09-07T09:01:59+09:00");
        worker.enqueueDueReminders();
        assertThat(notificationRepository.count()).isEqualTo(1);
        at("2026-09-14T09:02:00+09:00");
        worker.enqueueDueReminders();
        assertThat(notificationRepository.count()).isEqualTo(1);
    }

    @Test
    void schoolCourseMergesAdjacentScheduleRowsAndNotifiesSeparatedBlock() {
        CourseOffering offering = schoolCourse("자료구조", null);
        entryRepository.saveAndFlush(TimetableCourse.create(timetable, offering));
        scheduleRepository.saveAllAndFlush(List.of(
                CourseSchedule.create(offering, 1, DayOfWeek.MONDAY, List.of(2, 3), null),
                CourseSchedule.create(offering, 2, DayOfWeek.MONDAY, List.of(4, 5), null),
                CourseSchedule.create(offering, 3, DayOfWeek.MONDAY, List.of(8, 9), null)
        ));
        worker.enqueueDueReminders();
        at("2026-09-07T10:00:00+09:00"); // 11시(4교시)는 같은 연속 수업의 중간
        worker.enqueueDueReminders();
        assertThat(notificationRepository.count()).isEqualTo(1);
        at("2026-09-07T12:00:00+09:00"); // 13시(8교시)는 별개 수업 시작
        worker.enqueueDueReminders();
        assertThat(notificationRepository.count()).isEqualTo(2);
    }

    @Test
    void excludesOtherTermsDaysCyberInactiveAndUnmappedPeriods() {
        Timetable old = timetableRepository.saveAndFlush(Timetable.create(ownerId, 2025, 2));
        Timetable next = timetableRepository.saveAndFlush(Timetable.create(ownerId, 2027, 2));
        Timetable first = timetableRepository.saveAndFlush(Timetable.create(ownerId, 2026, 1));
        custom(old, DayOfWeek.MONDAY, "10:00");
        custom(next, DayOfWeek.MONDAY, "10:00");
        custom(first, DayOfWeek.MONDAY, "10:00");
        custom(timetable, DayOfWeek.TUESDAY, "10:00");
        CourseOffering cyber = schoolCourse("온라인강의", "온라인");
        CourseOffering inactive = schoolCourse("폐강강의", null);
        CourseOffering unknown = schoolCourse("시간미정", null);
        for (CourseOffering offering : List.of(cyber, inactive, unknown)) {
            entryRepository.saveAndFlush(TimetableCourse.create(timetable, offering));
            scheduleRepository.saveAndFlush(CourseSchedule.create(offering, 1, DayOfWeek.MONDAY,
                    offering == unknown ? List.of(24, 25) : List.of(2, 3), null));
        }
        inactive.deactivate();
        offeringRepository.saveAndFlush(inactive);
        worker.enqueueDueReminders();
        assertThat(notificationRepository.count()).isZero();
    }

    @Test
    void nightPeriodUsesExistingFiftyMinuteStartInterval() {
        CourseOffering offering = schoolCourse("야간수업", null);
        entryRepository.saveAndFlush(TimetableCourse.create(timetable, offering));
        scheduleRepository.saveAndFlush(CourseSchedule.create(offering, 1, DayOfWeek.MONDAY,
                List.of(19, 20), null)); // 18:50 시작
        at("2026-09-07T17:49:59+09:00");
        worker.enqueueDueReminders();
        assertThat(notificationRepository.count()).isZero();
        at("2026-09-07T17:50:00+09:00");
        worker.enqueueDueReminders();
        assertThat(notificationRepository.count()).isEqualTo(1);
    }

    @Test
    void midnightUsesNextClassDateYearAndTermEvenOnUtcServer() {
        Timetable nextYear = timetableRepository.saveAndFlush(Timetable.create(ownerId, 2027, 1));
        custom(nextYear, DayOfWeek.FRIDAY, "00:00");
        at("2026-12-31T23:00:30+09:00");
        worker.enqueueDueReminders();
        assertThat(notificationRepository.findAll()).singleElement()
                .satisfies(alert -> assertThat(alert.getPayloadData())
                        .contains("2027-01-01T00:00:00+09:00"));
    }

    @Test
    void deletedEntryIsRecheckedAndReminderHistoryCascadesOnDeletion() {
        TimetableCourse entry = custom(timetable, DayOfWeek.MONDAY, "10:00");
        Candidate candidate = new Candidate(timetable.getId(), entry.getId(),
                LocalDateTime.parse("2026-09-07T10:00:00"));
        entryRepository.deleteById(entry.getId());
        assertThat(enqueueService.enqueue(candidate)).isFalse();
        TimetableCourse second = custom(timetable, DayOfWeek.MONDAY, "10:00");
        worker.enqueueDueReminders();
        assertThat(reminderRepository.count()).isEqualTo(1);
        entryRepository.deleteById(second.getId());
        assertThat(reminderRepository.count()).isZero();
    }

    @Test
    void concurrentSchedulersCreateOnlyOneAlert() throws Exception {
        TimetableCourse entry = custom(timetable, DayOfWeek.MONDAY, "10:00");
        Candidate candidate = new Candidate(timetable.getId(), entry.getId(),
                LocalDateTime.parse("2026-09-07T10:00:00"));
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> { start.await(); return enqueueService.enqueue(candidate); });
            var second = executor.submit(() -> { start.await(); return enqueueService.enqueue(candidate); });
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(notificationRepository.count()).isEqualTo(1);
            assertThat(outboxRepository.count()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void failedTransactionRollsBackDeduplicationAndBothNotificationStores() {
        TimetableCourse entry = custom(timetable, DayOfWeek.MONDAY, "10:00");
        Candidate candidate = new Candidate(timetable.getId(), entry.getId(),
                LocalDateTime.parse("2026-09-07T10:00:00"));
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            enqueueService.enqueue(candidate);
            throw new IllegalStateException("simulated transaction failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(reminderRepository.count()).isZero();
        assertThat(notificationRepository.count()).isZero();
        assertThat(outboxRepository.count()).isZero();
        assertThat(enqueueService.enqueue(candidate)).isTrue();
    }

    @Test
    void expiredReminderDoesNotGoToExpoAndGlobalNotificationsStayGlobal() {
        custom(timetable, DayOfWeek.MONDAY, "10:00");
        worker.enqueueDueReminders();
        at("2026-09-07T09:05:00+09:00");
        new NotificationSendWorker(dispatchService, expoClient, new ObjectMapper(), clock)
                .processPendingNotifications();
        assertThat(outboxRepository.findAll()).singleElement()
                .extracting(NotificationOutbox::getStatus).isEqualTo(OutboxStatus.EXPIRED);
        notificationService.enqueueNoticeNotification("공지", "내용", "123");
        assertThat(outboxRepository.findAll()).anySatisfy(outbox -> {
            assertThat(outbox.getTargetUserId()).isNull();
            assertThat(outbox.getExpiresAt()).isNull();
        });
        org.mockito.Mockito.verifyNoInteractions(expoClient);
    }

    @Test
    void simultaneousOutboxClaimsReturnEachPendingRowOnlyOnce() throws Exception {
        custom(timetable, DayOfWeek.MONDAY, "10:00");
        worker.enqueueDueReminders();
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> { start.await(); return dispatchService.claimPendingOutboxes(); });
            var second = executor.submit(() -> { start.await(); return dispatchService.claimPendingOutboxes(); });
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS).size() + second.get(10, TimeUnit.SECONDS).size())
                    .isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void scansNextPageEvenWhenFirstPageHasNoDueClasses() {
        List<TimetableCourse> entries = java.util.stream.IntStream.range(0, 200)
                .mapToObj(index -> TimetableCourse.createCustom(timetable, "later-" + index,
                        BigDecimal.ONE, DayOfWeek.MONDAY, LocalTime.of(11, 0), LocalTime.of(12, 0)))
                .toList();
        entryRepository.saveAllAndFlush(entries);
        custom(timetable, DayOfWeek.MONDAY, "10:00");
        worker.enqueueDueReminders();
        assertThat(notificationRepository.count()).isEqualTo(1);
    }

    @Test
    void sameSchoolClassCreatesSeparatePersonalNotificationsForEachEnrolledOwner() {
        Long secondOwner = userRepository.saveAndFlush(UserAccount.create("second-owner", "hash")).getId();
        Timetable secondTable = timetableRepository.saveAndFlush(Timetable.create(secondOwner, 2026, 2));
        CourseOffering offering = schoolCourse("공통수업", null);
        entryRepository.saveAllAndFlush(List.of(TimetableCourse.create(timetable, offering),
                TimetableCourse.create(secondTable, offering)));
        scheduleRepository.saveAndFlush(CourseSchedule.create(offering, 1, DayOfWeek.MONDAY,
                List.of(2, 3), null));
        worker.enqueueDueReminders();
        assertThat(notificationRepository.findAll()).extracting(Notification::getTargetUserId)
                .containsExactlyInAnyOrder(ownerId, secondOwner);
    }

    private TimetableCourse custom(Timetable target, DayOfWeek day, String start) {
        LocalTime time = LocalTime.parse(start);
        return entryRepository.saveAndFlush(TimetableCourse.createCustom(target, "자료구조",
                BigDecimal.valueOf(3), day, time, time.plusMinutes(30)));
    }

    private CourseOffering schoolCourse(String name, String note) {
        String key = UUID.randomUUID().toString();
        Course course = courseRepository.saveAndFlush(Course.createWithDetails(
                key, key, name, null, CurriculumType.MAJOR, "01", BigDecimal.valueOf(3),
                BigDecimal.valueOf(3), "교수", 1, false, false, note, null, null, null));
        Semester semester = semesterRepository.findAll().stream().findFirst()
                .orElseGet(() -> semesterRepository.saveAndFlush(Semester.create(2026, 2)));
        CourseImportHistory history = historyRepository.saveAndFlush(CourseImportHistory.stored(
                key, key, "test.xlsx", "a".repeat(64), "1.2", "test", 2026, 2,
                CurriculumType.MAJOR, "test", BigDecimal.ONE, 1, "{}"));
        return offeringRepository.saveAndFlush(CourseOffering.link(
                semester, course, history, CurriculumType.MAJOR, "sheet", 1));
    }

    private void at(String offsetDateTime) {
        clock.value = java.time.OffsetDateTime.parse(offsetDateTime).toInstant();
    }

    static class MutableClock extends Clock {
        volatile Instant value = Instant.parse("2026-09-07T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(value, zone); }
        @Override public Instant instant() { return value; }
    }

    @TestConfiguration
    static class TimeConfiguration {
        @Bean @Primary MutableClock reminderClock() { return new MutableClock(); }
    }
}

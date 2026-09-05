package hsu.hanseomate.domain.timetable.reminder.worker;

import hsu.hanseomate.domain.timetable.reminder.service.TimetableReminderEnqueueService;
import hsu.hanseomate.domain.timetable.reminder.service.TimetableReminderQueryService;
import hsu.hanseomate.domain.timetable.reminder.service.TimetableReminderQueryService.Candidate;
import hsu.hanseomate.domain.timetable.reminder.service.TimetableReminderQueryService.CandidatePage;
import hsu.hanseomate.domain.timetable.reminder.support.TimetableReminderPolicy;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.timetable-reminder.enabled", havingValue = "true", matchIfMissing = true)
public class TimetableReminderWorker {

    private final TimetableReminderQueryService queryService;
    private final TimetableReminderEnqueueService enqueueService;
    private final Clock clock;

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void enqueueDueReminders() {
        LocalDateTime now = LocalDateTime.now(clock.withZone(TimetableReminderPolicy.ZONE));
        LocalDate firstDate = now.plusMinutes(TimetableReminderPolicy.MINUTES_BEFORE)
                .minusMinutes(TimetableReminderPolicy.CATCH_UP_MINUTES).toLocalDate();
        LocalDate lastDate = now.plusMinutes(TimetableReminderPolicy.MINUTES_BEFORE).toLocalDate();
        int created = 0;
        // 자정 수업은 전날, 연도/학기 경계에서는 수업 날짜의 시간표로 조회합니다.
        for (LocalDate date = firstDate; !date.isAfter(lastDate); date = date.plusDays(1)) {
            long afterId = 0;
            CandidatePage page;
            do {
                page = queryService.findCandidates(date, now, afterId);
                for (Candidate candidate : page.candidates()) {
                    try {
                        if (enqueueService.enqueue(candidate)) {
                            created++;
                        }
                    } catch (RuntimeException exception) {
                        log.error("Failed to enqueue class reminder: timetableCourseId={}, startsAt={}",
                                candidate.timetableCourseId(), candidate.startsAt(), exception);
                    }
                }
                afterId = page.lastId();
            } while (page.hasNext());
        }
        if (created > 0) {
            log.info("Enqueued {} timetable class reminder(s)", created);
        }
    }
}

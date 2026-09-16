package hsu.hanseomate.domain.personalcalendar.service;

import hsu.hanseomate.domain.personalcalendar.dto.PersonalCalendarEventRequest;
import hsu.hanseomate.domain.personalcalendar.dto.PersonalCalendarEventResponse;
import hsu.hanseomate.domain.personalcalendar.entity.PersonalCalendarEvent;
import hsu.hanseomate.domain.personalcalendar.exception.PersonalCalendarEventNotFoundException;
import hsu.hanseomate.domain.personalcalendar.repository.PersonalCalendarEventRepository;
import hsu.hanseomate.domain.user.entity.UserAccount;
import hsu.hanseomate.domain.user.repository.UserAccountRepository;
import hsu.hanseomate.global.exception.BadRequestException;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PersonalCalendarEventService {

    private final PersonalCalendarEventRepository personalCalendarEventRepository;
    private final UserAccountRepository userAccountRepository;

    public List<PersonalCalendarEventResponse> getEvents(Long ownerId) {
        findAuthenticatedUser(ownerId);
        return personalCalendarEventRepository
                .findAllByOwner_IdOrderByStartDateAscEndDateAscIdAsc(ownerId)
                .stream()
                .map(PersonalCalendarEventResponse::from)
                .toList();
    }

    @Transactional
    public PersonalCalendarEventResponse createEvent(
            Long ownerId,
            PersonalCalendarEventRequest request
    ) {
        UserAccount owner = findAuthenticatedUser(ownerId);
        validateDateRange(request.startDate(), request.endDate());
        PersonalCalendarEvent event = PersonalCalendarEvent.create(
                owner,
                request.startDate(),
                request.endDate(),
                request.title().trim()
        );
        return PersonalCalendarEventResponse.from(
                personalCalendarEventRepository.saveAndFlush(event)
        );
    }

    @Transactional
    public PersonalCalendarEventResponse updateEvent(
            Long ownerId,
            Long calendarId,
            PersonalCalendarEventRequest request
    ) {
        findAuthenticatedUser(ownerId);
        PersonalCalendarEvent event = findOwnedEventForUpdate(calendarId, ownerId);
        validateDateRange(request.startDate(), request.endDate());
        event.update(
                request.startDate(),
                request.endDate(),
                request.title().trim()
        );
        personalCalendarEventRepository.flush();
        return PersonalCalendarEventResponse.from(event);
    }

    @Transactional
    public void deleteEvent(Long ownerId, Long calendarId) {
        findAuthenticatedUser(ownerId);
        PersonalCalendarEvent event = findOwnedEventForUpdate(calendarId, ownerId);
        personalCalendarEventRepository.delete(event);
        personalCalendarEventRepository.flush();
    }

    private UserAccount findAuthenticatedUser(Long ownerId) {
        return userAccountRepository.findById(ownerId)
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException(
                        "로그인이 필요합니다."
                ));
    }

    private PersonalCalendarEvent findOwnedEventForUpdate(
            Long calendarId,
            Long ownerId
    ) {
        return personalCalendarEventRepository
                .findOwnedByIdForUpdate(calendarId, ownerId)
                .orElseThrow(() -> new PersonalCalendarEventNotFoundException(calendarId));
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new BadRequestException(
                    "일정 종료일은 시작일보다 빠를 수 없습니다."
            );
        }
    }
}

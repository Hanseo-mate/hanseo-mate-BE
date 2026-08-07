package hsu.hanseomate.domain.timetable.composition.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TimetableErrorCode {

    TIMETABLE_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "해당 연도와 학기의 시간표가 이미 존재합니다."
    ),
    TIMETABLE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "시간표를 찾을 수 없습니다."
    ),
    TIMETABLE_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "해당 시간표에 접근할 권한이 없습니다."
    ),
    COURSE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "과목을 찾을 수 없습니다."
    ),
    COURSE_ALREADY_ADDED(
            HttpStatus.CONFLICT,
            "이미 시간표에 추가된 과목입니다."
    ),
    COURSE_TERM_MISMATCH(
            HttpStatus.BAD_REQUEST,
            "시간표와 과목의 연도 또는 학기가 일치하지 않습니다."
    ),
    TIMETABLE_TIME_CONFLICT(
            HttpStatus.CONFLICT,
            "기존 과목과 수업 시간이 겹칩니다."
    ),
    TIMETABLE_COURSE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "시간표에서 해당 과목을 찾을 수 없습니다."
    ),
    INVALID_TIMETABLE_TERM(
            HttpStatus.BAD_REQUEST,
            "시간표의 연도 또는 학기 값이 유효하지 않습니다."
    );

    private final HttpStatus status;
    private final String message;
}

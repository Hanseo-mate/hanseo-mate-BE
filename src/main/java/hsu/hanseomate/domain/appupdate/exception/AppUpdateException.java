package hsu.hanseomate.domain.appupdate.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AppUpdateException extends RuntimeException {
    private final HttpStatus status;

    public AppUpdateException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static AppUpdateException badRequest(String message) {
        return new AppUpdateException(HttpStatus.BAD_REQUEST, message);
    }

    public static AppUpdateException conflict(String message) {
        return new AppUpdateException(HttpStatus.CONFLICT, message);
    }

    public static AppUpdateException notFound() {
        return new AppUpdateException(HttpStatus.NOT_FOUND, "앱 업데이트 정책을 찾을 수 없습니다.");
    }
}

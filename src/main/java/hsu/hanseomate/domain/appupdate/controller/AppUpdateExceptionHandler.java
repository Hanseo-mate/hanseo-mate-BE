package hsu.hanseomate.domain.appupdate.controller;

import hsu.hanseomate.domain.appupdate.exception.AppUpdateException;
import hsu.hanseomate.global.exception.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackageClasses = AppUpdateController.class)
public class AppUpdateExceptionHandler {
    @ExceptionHandler(AppUpdateException.class)
    public ResponseEntity<ApiErrorResponse> domain(AppUpdateException error, HttpServletRequest request) {
        return ResponseEntity.status(error.getStatus())
                .body(ApiErrorResponse.of(error.getStatus(), error.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> concurrent(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiErrorResponse.of(
                HttpStatus.CONFLICT, "다른 정책 작업이 진행 중입니다. 같은 멱등 키로 다시 시도해 주세요.", request.getRequestURI()));
    }
}

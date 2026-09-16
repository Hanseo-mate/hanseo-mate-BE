package hsu.hanseomate.global.exception;

/**
 * Bean Validation만으로 표현하기 어려운 요청 규칙 위반에 사용하는 공통 예외입니다.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}

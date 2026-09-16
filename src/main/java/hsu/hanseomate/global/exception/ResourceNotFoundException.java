package hsu.hanseomate.global.exception;

/**
 * 조회 대상이 존재하지 않을 때 사용하는 공통 예외입니다.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}

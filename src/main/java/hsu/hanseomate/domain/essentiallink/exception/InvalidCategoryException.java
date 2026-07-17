package hsu.hanseomate.domain.essentiallink.exception;

public class InvalidCategoryException extends RuntimeException {

    public InvalidCategoryException() {
        super("카테고리는 공백일 수 없으며 50자 이하여야 합니다.");
    }
}

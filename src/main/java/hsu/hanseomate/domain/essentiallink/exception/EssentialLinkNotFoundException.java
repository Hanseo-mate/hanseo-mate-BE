package hsu.hanseomate.domain.essentiallink.exception;

public class EssentialLinkNotFoundException extends RuntimeException {

    public EssentialLinkNotFoundException(Long linkId) {
        super("링크를 찾을 수 없습니다. linkId=" + linkId);
    }
}

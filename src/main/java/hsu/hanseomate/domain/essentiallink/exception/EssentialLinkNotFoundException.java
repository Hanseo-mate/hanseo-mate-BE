package hsu.hanseomate.domain.essentiallink.exception;

import hsu.hanseomate.global.exception.ResourceNotFoundException;

public class EssentialLinkNotFoundException extends ResourceNotFoundException {

    public EssentialLinkNotFoundException(Long linkId) {
        super("링크를 찾을 수 없습니다. linkId=" + linkId);
    }
}

package hsu.hanseomate.domain.popup.exception;

import hsu.hanseomate.global.exception.ResourceNotFoundException;

public class AppPopupNotFoundException extends ResourceNotFoundException {

    public AppPopupNotFoundException(Long popupId) {
        super("앱 팝업을 찾을 수 없습니다. popupId=" + popupId);
    }
}

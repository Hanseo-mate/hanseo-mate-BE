package hsu.hanseomate.domain.homeposter.exception;

import hsu.hanseomate.global.exception.ResourceNotFoundException;

public class HomePosterNotFoundException extends ResourceNotFoundException {

    public HomePosterNotFoundException(Long posterId) {
        super("홈 포스터를 찾을 수 없습니다. posterId=" + posterId);
    }
}

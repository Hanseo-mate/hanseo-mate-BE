package hsu.hanseomate.domain.home.service;

import hsu.hanseomate.domain.home.entity.HomeMessage;
import hsu.hanseomate.domain.home.repository.HomeMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeMessageService {

    private final HomeMessageRepository homeMessageRepository;

    public String getMessage() {
        return homeMessageRepository.findById(HomeMessage.SINGLETON_ID)
                .map(HomeMessage::getMessage)
                .orElse("");
    }

    @Transactional
    public String setMessage(String message) {
        return homeMessageRepository.save(HomeMessage.create(message.strip())).getMessage();
    }
}

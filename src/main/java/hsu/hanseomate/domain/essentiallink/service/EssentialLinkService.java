package hsu.hanseomate.domain.essentiallink.service;

import hsu.hanseomate.domain.essentiallink.dto.EssentialLinkCreateRequest;
import hsu.hanseomate.domain.essentiallink.dto.EssentialLinkResponse;
import hsu.hanseomate.domain.essentiallink.dto.EssentialLinkUpdateRequest;
import hsu.hanseomate.domain.essentiallink.entity.EssentialLink;
import hsu.hanseomate.domain.essentiallink.exception.EssentialLinkNotFoundException;
import hsu.hanseomate.domain.essentiallink.exception.InvalidCategoryException;
import hsu.hanseomate.domain.essentiallink.repository.EssentialLinkRepository;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EssentialLinkService {

    private final EssentialLinkRepository essentialLinkRepository;

    public List<EssentialLinkResponse> getLinks(String category) {
        List<EssentialLink> links = category == null
                ? essentialLinkRepository.findAllByOrderByIdAsc()
                : essentialLinkRepository.findAllByCategoryOrderByIdAsc(normalizeCategory(category));

        return links.stream()
                .map(EssentialLinkResponse::from)
                .toList();
    }

    public EssentialLinkResponse getLink(Long linkId) {
        return EssentialLinkResponse.from(findLink(linkId));
    }

    @Transactional
    public EssentialLinkResponse createLink(EssentialLinkCreateRequest request) {
        EssentialLink essentialLink = EssentialLink.create(
                request.name().trim(),
                request.url().trim(),
                normalizeCategory(request.category())
        );

        return EssentialLinkResponse.from(essentialLinkRepository.saveAndFlush(essentialLink));
    }

    @Transactional
    public EssentialLinkResponse updateLink(Long linkId, EssentialLinkUpdateRequest request) {
        EssentialLink essentialLink = findLink(linkId);
        essentialLink.update(
                request.name().trim(),
                request.url().trim(),
                normalizeCategory(request.category())
        );
        essentialLinkRepository.flush();

        return EssentialLinkResponse.from(essentialLink);
    }

    @Transactional
    public void deleteLink(Long linkId) {
        EssentialLink essentialLink = findLink(linkId);
        essentialLinkRepository.delete(essentialLink);
        essentialLinkRepository.flush();
    }

    private EssentialLink findLink(Long linkId) {
        return essentialLinkRepository.findById(linkId)
                .orElseThrow(() -> new EssentialLinkNotFoundException(linkId));
    }

    private String normalizeCategory(String category) {
        String normalized = category.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > 50) {
            throw new InvalidCategoryException();
        }
        return normalized;
    }
}

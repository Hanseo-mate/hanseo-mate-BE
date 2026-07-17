package hsu.hanseomate.domain.essentiallink.repository;

import hsu.hanseomate.domain.essentiallink.entity.EssentialLink;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EssentialLinkRepository extends JpaRepository<EssentialLink, Long> {

    List<EssentialLink> findAllByOrderByIdAsc();

    List<EssentialLink> findAllByCategoryOrderByIdAsc(String category);
}

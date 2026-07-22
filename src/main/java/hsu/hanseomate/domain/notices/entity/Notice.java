package hsu.hanseomate.domain.notices.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "notices")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = NoticeTypeConverter.class)
    @Column(name = "notice_type", nullable = false, length = 50)
    private NoticeType noticeType;

    @Column(name = "origin_notice_id", nullable = false, length = 32)
    private String originNoticeId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(name = "source_url", nullable = false, length = 1024)
    private String sourceUrl;

    @Column(name = "content_html", nullable = false, columnDefinition = "LONGTEXT")
    private String contentHtml;

    @Column(nullable = false, length = 100)
    private String author;

    @Column(name = "post_date", nullable = false)
    private LocalDate postDate;

    @Column(name = "is_hot", nullable = false)
    private boolean isHot;

    @OneToMany(mappedBy = "notice")
    private final List<NoticeFile> attachments = new ArrayList<>();
}

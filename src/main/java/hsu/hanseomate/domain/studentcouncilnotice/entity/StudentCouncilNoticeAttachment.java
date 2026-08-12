package hsu.hanseomate.domain.studentcouncilnotice.entity;

import hsu.hanseomate.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Getter
@Entity
@Table(
        name = "student_council_notice_attachments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_student_council_notice_attachment_storage_key",
                columnNames = "storage_key"
        ),
        indexes = @Index(
                name = "idx_student_council_notice_attachments_notice",
                columnList = "notice_id,id"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentCouncilNoticeAttachment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notice_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private StudentCouncilNotice notice;

    @Column(name = "storage_key", nullable = false, length = 255)
    private String storageKey;

    @Column(name = "original_file_name", nullable = false, length = 500)
    private String originalFileName;

    @Column(name = "content_type", nullable = false, length = 255)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    private StudentCouncilNoticeAttachment(
            StudentCouncilNotice notice,
            String storageKey,
            String originalFileName,
            String contentType,
            long fileSize
    ) {
        this.notice = notice;
        this.storageKey = storageKey;
        this.originalFileName = originalFileName;
        this.contentType = contentType;
        this.fileSize = fileSize;
    }

    public static StudentCouncilNoticeAttachment create(
            StudentCouncilNotice notice,
            String storageKey,
            String originalFileName,
            String contentType,
            long fileSize
    ) {
        return new StudentCouncilNoticeAttachment(
                notice,
                storageKey,
                originalFileName,
                contentType,
                fileSize
        );
    }
}

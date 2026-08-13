package hsu.hanseomate.domain.courseenrichment.equivalence.entity;

import hsu.hanseomate.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(
        name = "equivalent_course_import_histories",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_equivalent_import_id",
                        columnNames = "import_id"
                ),
                @UniqueConstraint(
                        name = "uk_equivalent_active_scope",
                        columnNames = "active_scope_key"
                )
        },
        indexes = @Index(
                name = "ix_equivalent_import_scope",
                columnList = "academic_year,semester"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EquivalentCourseImportHistory extends BaseTimeEntity {

    @Id
    private UUID id;

    @Column(name = "import_id", nullable = false, length = 100)
    private String importId;

    @Column(name = "active_scope_key", length = 30)
    private String activeScopeKey;

    @Column(name = "canonical_hash", nullable = false, length = 64)
    private String canonicalHash;

    @Column(name = "raw_file_sha256", nullable = false, length = 64)
    private String rawFileSha256;

    @Column(name = "file_name", nullable = false, length = 500)
    private String fileName;

    @Column(name = "schema_version", nullable = false, length = 20)
    private String schemaVersion;

    @Column(name = "parser_version", nullable = false, length = 100)
    private String parserVersion;

    @Column(name = "academic_year", nullable = false)
    private int academicYear;

    @Column(nullable = false)
    private int semester;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "history_status", nullable = false, length = 30)
    private EquivalentCourseHistoryStatus historyStatus;

    @Column(name = "group_count", nullable = false)
    private int groupCount;

    @Column(name = "member_count", nullable = false)
    private int memberCount;

    @Lob
    @Column(name = "raw_payload_json", nullable = false, columnDefinition = "LONGTEXT")
    private String rawPayloadJson;

    @Lob
    @Column(name = "raw_issues_json", nullable = false, columnDefinition = "LONGTEXT")
    private String rawIssuesJson;

    private EquivalentCourseImportHistory(
            String importId,
            String activeScopeKey,
            String canonicalHash,
            String rawFileSha256,
            String fileName,
            String schemaVersion,
            String parserVersion,
            int academicYear,
            int semester,
            EquivalentCourseHistoryStatus historyStatus,
            int groupCount,
            int memberCount,
            String rawPayloadJson,
            String rawIssuesJson
    ) {
        this.id = UUID.randomUUID();
        this.importId = importId;
        this.activeScopeKey = activeScopeKey;
        this.canonicalHash = canonicalHash;
        this.rawFileSha256 = rawFileSha256;
        this.fileName = fileName;
        this.schemaVersion = schemaVersion;
        this.parserVersion = parserVersion;
        this.academicYear = academicYear;
        this.semester = semester;
        this.historyStatus = historyStatus;
        this.groupCount = groupCount;
        this.memberCount = memberCount;
        this.rawPayloadJson = rawPayloadJson;
        this.rawIssuesJson = rawIssuesJson;
    }

    public static EquivalentCourseImportHistory stored(
            String importId,
            String activeScopeKey,
            String canonicalHash,
            String rawFileSha256,
            String fileName,
            String schemaVersion,
            String parserVersion,
            int academicYear,
            int semester,
            int groupCount,
            int memberCount,
            String rawPayloadJson,
            String rawIssuesJson
    ) {
        return new EquivalentCourseImportHistory(
                importId,
                activeScopeKey,
                canonicalHash,
                rawFileSha256,
                fileName,
                schemaVersion,
                parserVersion,
                academicYear,
                semester,
                EquivalentCourseHistoryStatus.ACTIVE,
                groupCount,
                memberCount,
                rawPayloadJson,
                rawIssuesJson
        );
    }

    public static EquivalentCourseImportHistory reviewRequired(
            String importId,
            String canonicalHash,
            String rawFileSha256,
            String fileName,
            String schemaVersion,
            String parserVersion,
            int academicYear,
            int semester,
            int groupCount,
            int memberCount,
            String rawPayloadJson,
            String rawIssuesJson
    ) {
        return new EquivalentCourseImportHistory(
                importId,
                null,
                canonicalHash,
                rawFileSha256,
                fileName,
                schemaVersion,
                parserVersion,
                academicYear,
                semester,
                EquivalentCourseHistoryStatus.REVIEW_REQUIRED,
                groupCount,
                memberCount,
                rawPayloadJson,
                rawIssuesJson
        );
    }

    public void deactivate() {
        this.activeScopeKey = null;
        this.historyStatus = EquivalentCourseHistoryStatus.SUPERSEDED;
    }
}

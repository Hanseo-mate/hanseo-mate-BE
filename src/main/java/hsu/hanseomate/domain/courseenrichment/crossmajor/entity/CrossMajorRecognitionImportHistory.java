package hsu.hanseomate.domain.courseenrichment.crossmajor.entity;

import hsu.hanseomate.domain.courseenrichment.crossmajor.type.CrossMajorRecognitionImportStatus;
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
        name = "cross_major_recognition_import_histories",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cross_major_active_scope",
                columnNames = "active_scope_key"
        ),
        indexes = @Index(
                name = "ix_cross_major_import_policy_year",
                columnList = "policy_year,status"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CrossMajorRecognitionImportHistory extends BaseTimeEntity {

    @Id
    private UUID id;

    @Column(name = "policy_year", nullable = false)
    private int policyYear;

    @Column(name = "uploaded_semester", nullable = false)
    private int uploadedSemester;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private CrossMajorRecognitionImportStatus status;

    @Column(name = "active_scope_key", length = 100)
    private String activeScopeKey;

    @Column(name = "file_name", nullable = false, length = 500)
    private String fileName;

    @Column(name = "raw_file_sha256", nullable = false, length = 64)
    private String rawFileSha256;

    @Column(name = "canonical_data_sha256", nullable = false, length = 64)
    private String canonicalDataSha256;

    @Column(name = "source_sheet", nullable = false, length = 255)
    private String sourceSheet;

    @Column(name = "raw_row_count", nullable = false)
    private int rawRowCount;

    @Column(name = "rule_count", nullable = false)
    private int ruleCount;

    @Column(name = "warning_count", nullable = false)
    private int warningCount;

    @Lob
    @Column(name = "issues_json", nullable = false, columnDefinition = "LONGTEXT")
    private String issuesJson;

    @Lob
    @Column(name = "raw_payload_json", nullable = false, columnDefinition = "LONGTEXT")
    private String rawPayloadJson;

    private CrossMajorRecognitionImportHistory(
            int policyYear,
            int uploadedSemester,
            CrossMajorRecognitionImportStatus status,
            String activeScopeKey,
            String fileName,
            String rawFileSha256,
            String canonicalDataSha256,
            String sourceSheet,
            int rawRowCount,
            int ruleCount,
            int warningCount,
            String issuesJson,
            String rawPayloadJson
    ) {
        this.id = UUID.randomUUID();
        this.policyYear = policyYear;
        this.uploadedSemester = uploadedSemester;
        this.status = status;
        this.activeScopeKey = activeScopeKey;
        this.fileName = fileName;
        this.rawFileSha256 = rawFileSha256;
        this.canonicalDataSha256 = canonicalDataSha256;
        this.sourceSheet = sourceSheet;
        this.rawRowCount = rawRowCount;
        this.ruleCount = ruleCount;
        this.warningCount = warningCount;
        this.issuesJson = issuesJson;
        this.rawPayloadJson = rawPayloadJson;
    }

    public static CrossMajorRecognitionImportHistory active(
            int policyYear,
            int uploadedSemester,
            String activeScopeKey,
            String fileName,
            String rawFileSha256,
            String canonicalDataSha256,
            String sourceSheet,
            int rawRowCount,
            int ruleCount,
            int warningCount,
            String issuesJson,
            String rawPayloadJson
    ) {
        return new CrossMajorRecognitionImportHistory(
                policyYear, uploadedSemester, CrossMajorRecognitionImportStatus.ACTIVE,
                activeScopeKey, fileName, rawFileSha256, canonicalDataSha256,
                sourceSheet, rawRowCount, ruleCount, warningCount, issuesJson, rawPayloadJson
        );
    }

    public static CrossMajorRecognitionImportHistory reviewRequired(
            int policyYear,
            int uploadedSemester,
            String fileName,
            String rawFileSha256,
            String canonicalDataSha256,
            String sourceSheet,
            int rawRowCount,
            int ruleCount,
            int warningCount,
            String issuesJson,
            String rawPayloadJson
    ) {
        return new CrossMajorRecognitionImportHistory(
                policyYear, uploadedSemester,
                CrossMajorRecognitionImportStatus.REVIEW_REQUIRED,
                null, fileName, rawFileSha256, canonicalDataSha256,
                sourceSheet, rawRowCount, ruleCount, warningCount, issuesJson, rawPayloadJson
        );
    }

    public void markSuperseded() {
        status = CrossMajorRecognitionImportStatus.SUPERSEDED;
        activeScopeKey = null;
    }
}

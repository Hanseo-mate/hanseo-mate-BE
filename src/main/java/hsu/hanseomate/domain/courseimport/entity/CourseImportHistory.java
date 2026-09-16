package hsu.hanseomate.domain.courseimport.entity;

import hsu.hanseomate.domain.courseimport.dto.type.CurriculumType;
import hsu.hanseomate.domain.courseimport.dto.type.StorageStatus;
import hsu.hanseomate.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(
        name = "course_import_histories",
        indexes = @Index(
                name = "ix_course_import_scope",
                columnList = "academic_year,semester,curriculum_type"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseImportHistory extends BaseTimeEntity {

    @Id
    private UUID id;

    @Column(name = "import_id", nullable = false, unique = true, length = 100)
    private String importId;

    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "successful_dedup_key", unique = true, length = 255)
    private String successfulDedupKey;

    @Column(name = "file_name", nullable = false, length = 500)
    private String fileName;

    @Column(name = "file_sha256", nullable = false, length = 64)
    private String fileSha256;

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
    @Column(name = "curriculum_type", nullable = false, length = 30)
    private CurriculumType curriculumType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "storage_status", nullable = false, length = 30)
    private StorageStatus storageStatus;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "offering_count", nullable = false)
    private int offeringCount;

    @Lob
    @Column(name = "raw_payload_json", nullable = false, columnDefinition = "LONGTEXT")
    private String rawPayloadJson;

    private CourseImportHistory(
            String importId,
            String idempotencyKey,
            String successfulDedupKey,
            String fileName,
            String fileSha256,
            String schemaVersion,
            String parserVersion,
            int academicYear,
            int semester,
            CurriculumType curriculumType,
            StorageStatus storageStatus,
            String displayName,
            BigDecimal confidence,
            int offeringCount,
            String rawPayloadJson
    ) {
        this.id = UUID.randomUUID();
        this.importId = importId;
        this.idempotencyKey = idempotencyKey;
        this.successfulDedupKey = successfulDedupKey;
        this.fileName = fileName;
        this.fileSha256 = fileSha256;
        this.schemaVersion = schemaVersion;
        this.parserVersion = parserVersion;
        this.academicYear = academicYear;
        this.semester = semester;
        this.curriculumType = curriculumType;
        this.storageStatus = storageStatus;
        this.displayName = displayName;
        this.confidence = confidence;
        this.offeringCount = offeringCount;
        this.rawPayloadJson = rawPayloadJson;
    }

    public static CourseImportHistory stored(
            String importId,
            String idempotencyKey,
            String fileName,
            String fileSha256,
            String schemaVersion,
            String parserVersion,
            int academicYear,
            int semester,
            CurriculumType curriculumType,
            String displayName,
            BigDecimal confidence,
            int offeringCount,
            String rawPayloadJson
    ) {
        return new CourseImportHistory(
                importId, idempotencyKey, idempotencyKey, fileName, fileSha256,
                schemaVersion, parserVersion, academicYear, semester, curriculumType,
                StorageStatus.STORED, displayName, confidence, offeringCount, rawPayloadJson
        );
    }

    public static CourseImportHistory reviewRequired(
            String importId,
            String idempotencyKey,
            String fileName,
            String fileSha256,
            String schemaVersion,
            String parserVersion,
            int academicYear,
            int semester,
            CurriculumType curriculumType,
            String displayName,
            BigDecimal confidence,
            String rawPayloadJson
    ) {
        return new CourseImportHistory(
                importId, idempotencyKey, null, fileName, fileSha256,
                schemaVersion, parserVersion, academicYear, semester, curriculumType,
                StorageStatus.REVIEW_REQUIRED, displayName, confidence, 0, rawPayloadJson
        );
    }

    public void markSuperseded() {
        this.successfulDedupKey = null;
    }
}

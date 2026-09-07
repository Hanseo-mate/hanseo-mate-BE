package hsu.hanseomate.domain.courseenrichment.crossmajor.entity;

import hsu.hanseomate.domain.courseenrichment.crossmajor.dto.CrossMajorRecognitionRuleData;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "cross_major_rule_memberships",
        uniqueConstraints = @UniqueConstraint(name = "uk_cross_major_membership_rule",
                columnNames = {"import_history_id", "rule_key"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CrossMajorRuleMembership {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "import_history_id", nullable = false)
    private CrossMajorRecognitionImportHistory importHistory;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_key", nullable = false, updatable = false)
    private CrossMajorRuleContent content;

    @Column(name = "source_sheet", nullable = false, length = 255)
    private String sourceSheet;

    @Column(name = "source_row", nullable = false)
    private int sourceRow;

    public static CrossMajorRuleMembership create(
            CrossMajorRecognitionImportHistory history,
            CrossMajorRuleContent content,
            CrossMajorRecognitionRuleData data
    ) {
        CrossMajorRuleMembership membership = new CrossMajorRuleMembership();
        membership.id = UUID.randomUUID();
        membership.importHistory = history;
        membership.content = content;
        membership.sourceSheet = data.sourceSheet();
        membership.sourceRow = data.sourceRow();
        return membership;
    }
}

package hsu.hanseomate.domain.popup.entity;

import hsu.hanseomate.domain.popup.model.PopupNavigation;
import hsu.hanseomate.domain.popup.type.AppPopupStatus;
import hsu.hanseomate.domain.popup.type.PopupNavigationType;
import hsu.hanseomate.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(
        name = "app_popups",
        indexes = {
                @Index(
                        name = "idx_app_popups_exposure",
                        columnList = "enabled,starts_at,ends_at,display_order,id"
                ),
                @Index(
                        name = "idx_app_popups_created_at",
                        columnList = "created_at,id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppPopup extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    @Column(name = "link_url", length = 2048)
    private String linkUrl;

    @Column(name = "navigation_schema_version", columnDefinition = "smallint")
    private Short navigationSchemaVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "navigation_type", length = 40)
    private PopupNavigationType navigationType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "navigation_params", columnDefinition = "json")
    private Map<String, Object> navigationParams;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "starts_at")
    private LocalDateTime startsAt;

    @Column(name = "ends_at")
    private LocalDateTime endsAt;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private long revision;

    private AppPopup(
            String title,
            String content,
            String imageUrl,
            String linkUrl,
            PopupNavigation navigation,
            boolean enabled,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            int displayOrder
    ) {
        this.title = title;
        this.content = content;
        this.imageUrl = imageUrl;
        this.linkUrl = linkUrl;
        applyNavigation(navigation);
        this.enabled = enabled;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.displayOrder = displayOrder;
        this.revision = 1L;
    }

    public static AppPopup create(
            String title,
            String content,
            String imageUrl,
            String linkUrl,
            PopupNavigation navigation,
            boolean enabled,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            int displayOrder
    ) {
        return new AppPopup(
                title,
                content,
                imageUrl,
                linkUrl,
                navigation,
                enabled,
                startsAt,
                endsAt,
                displayOrder
        );
    }

    public void update(
            String title,
            String content,
            String imageUrl,
            String linkUrl,
            PopupNavigation navigation,
            boolean enabled,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            int displayOrder
    ) {
        this.title = title;
        this.content = content;
        this.imageUrl = imageUrl;
        this.linkUrl = linkUrl;
        applyNavigation(navigation);
        this.enabled = enabled;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.displayOrder = displayOrder;
        this.revision++;
    }

    public PopupNavigation navigation() {
        if (navigationSchemaVersion == null && navigationType == null) {
            return null;
        }
        if (navigationSchemaVersion == null || navigationType == null) {
            throw new IllegalStateException("팝업 이동 정보가 불완전합니다. popupId=" + id);
        }
        return new PopupNavigation(
                navigationSchemaVersion,
                navigationType,
                navigationParams
        );
    }

    private void applyNavigation(PopupNavigation navigation) {
        if (navigation == null) {
            this.navigationSchemaVersion = null;
            this.navigationType = null;
            this.navigationParams = null;
            return;
        }
        this.navigationSchemaVersion = navigation.schemaVersion();
        this.navigationType = navigation.type();
        this.navigationParams = navigation.params() == null
                ? null
                : new LinkedHashMap<>(navigation.params());
    }

    public void updateEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        this.revision++;
    }

    public boolean isActiveAt(LocalDateTime now) {
        return enabled
                && (startsAt == null || !startsAt.isAfter(now))
                && (endsAt == null || endsAt.isAfter(now));
    }

    public AppPopupStatus statusAt(LocalDateTime now) {
        if (!enabled) {
            return AppPopupStatus.INACTIVE;
        }
        if (startsAt != null && startsAt.isAfter(now)) {
            return AppPopupStatus.SCHEDULED;
        }
        if (endsAt != null && !endsAt.isAfter(now)) {
            return AppPopupStatus.EXPIRED;
        }
        return AppPopupStatus.ACTIVE;
    }
}

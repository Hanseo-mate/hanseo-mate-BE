package hsu.hanseomate.domain.appsetting.repository;

import hsu.hanseomate.domain.appsetting.entity.AppFeatureSettingAudit;
import org.springframework.data.repository.Repository;

// 감사 이력에는 수정/삭제 연산을 노출하지 않는다.
public interface AppFeatureSettingAuditRepository extends Repository<AppFeatureSettingAudit, Long> {

    AppFeatureSettingAudit saveAndFlush(AppFeatureSettingAudit audit);
}

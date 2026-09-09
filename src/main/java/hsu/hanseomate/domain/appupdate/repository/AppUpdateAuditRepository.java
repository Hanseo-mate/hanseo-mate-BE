package hsu.hanseomate.domain.appupdate.repository;

import hsu.hanseomate.domain.appupdate.entity.AppUpdateAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AppUpdateAuditRepository extends JpaRepository<AppUpdateAudit, Long>, JpaSpecificationExecutor<AppUpdateAudit> {}

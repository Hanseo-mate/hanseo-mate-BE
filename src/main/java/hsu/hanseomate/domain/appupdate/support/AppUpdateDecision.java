package hsu.hanseomate.domain.appupdate.support;

import hsu.hanseomate.domain.appupdate.dto.PolicyContent;
import hsu.hanseomate.domain.appupdate.type.AppUpdateAction;

public final class AppUpdateDecision {
    private AppUpdateDecision() {}

    public static AppUpdateAction decide(PolicyContent policy, long installedBuild) {
        if (policy == null) return AppUpdateAction.NONE;
        if (policy.forceUpdateEnabled() && installedBuild < policy.minimumSupportedBuild()) {
            return AppUpdateAction.REQUIRED;
        }
        if (policy.optionalUpdateEnabled() && installedBuild < policy.latestBuild()) {
            return AppUpdateAction.OPTIONAL;
        }
        return AppUpdateAction.NONE;
    }
}

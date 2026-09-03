package hsu.hanseomate.domain.popup.model;

import hsu.hanseomate.domain.popup.type.PopupNavigationType;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record PopupNavigation(
        short schemaVersion,
        PopupNavigationType type,
        Map<String, Object> params
) {

    public PopupNavigation {
        params = params == null
                ? null
                : Collections.unmodifiableMap(new LinkedHashMap<>(params));
    }
}

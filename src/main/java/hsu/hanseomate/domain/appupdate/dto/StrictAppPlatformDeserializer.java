package hsu.hanseomate.domain.appupdate.dto;

import hsu.hanseomate.domain.appupdate.type.AppPlatform;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public final class StrictAppPlatformDeserializer extends ValueDeserializer<AppPlatform> {
    @Override
    public AppPlatform deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.currentToken() != JsonToken.VALUE_STRING) {
            return (AppPlatform) context.handleUnexpectedToken(AppPlatform.class, parser);
        }
        String value = parser.getString();
        try {
            return AppPlatform.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return (AppPlatform) context.handleWeirdStringValue(AppPlatform.class, value, "지원하지 않는 AppPlatform 값입니다.");
        }
    }
}

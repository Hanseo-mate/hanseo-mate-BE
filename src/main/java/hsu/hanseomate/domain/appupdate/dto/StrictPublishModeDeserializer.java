package hsu.hanseomate.domain.appupdate.dto;

import hsu.hanseomate.domain.appupdate.type.PublishMode;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public final class StrictPublishModeDeserializer extends ValueDeserializer<PublishMode> {
    @Override
    public PublishMode deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.currentToken() != JsonToken.VALUE_STRING) {
            return (PublishMode) context.handleUnexpectedToken(PublishMode.class, parser);
        }
        String value = parser.getString();
        try {
            return PublishMode.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return (PublishMode) context.handleWeirdStringValue(PublishMode.class, value, "지원하지 않는 PublishMode 값입니다.");
        }
    }
}

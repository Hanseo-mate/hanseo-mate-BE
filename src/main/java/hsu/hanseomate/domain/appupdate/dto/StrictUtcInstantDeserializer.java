package hsu.hanseomate.domain.appupdate.dto;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public final class StrictUtcInstantDeserializer extends ValueDeserializer<Instant> {
    @Override
    public Instant deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.currentToken() != JsonToken.VALUE_STRING) {
            return (Instant) context.handleUnexpectedToken(Instant.class, parser);
        }
        String value = parser.getString();
        try {
            if (!value.endsWith("Z")) {
                throw new DateTimeParseException("UTC Z 시각이 필요합니다.", value, 0);
            }
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            return (Instant) context.handleWeirdStringValue(Instant.class, value, "ISO 8601 UTC(Z) 시각이 필요합니다.");
        }
    }
}

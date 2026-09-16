package hsu.hanseomate.domain.courseimport.dto;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * 숫자 JSON 값을 문자열로 묵시적으로 변환하지 않는다.
 * 과목 코드와 분반의 선행 0은 JSON 문자열로 전달될 때만 보존할 수 있다.
 */
public final class StrictNullableStringDeserializer extends ValueDeserializer<String> {

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
        if (parser.currentToken() == JsonToken.VALUE_STRING) {
            return parser.getString();
        }
        if (parser.currentToken() == JsonToken.VALUE_NULL) {
            return null;
        }
        return (String) context.handleUnexpectedToken(String.class, parser);
    }

    @Override
    public String getNullValue(DeserializationContext context) {
        return null;
    }
}

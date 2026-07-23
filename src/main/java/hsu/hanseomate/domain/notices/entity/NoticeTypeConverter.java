package hsu.hanseomate.domain.notices.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class NoticeTypeConverter implements AttributeConverter<NoticeType, String> {

    @Override
    public String convertToDatabaseColumn(NoticeType attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public NoticeType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : NoticeType.from(dbData);
    }
}

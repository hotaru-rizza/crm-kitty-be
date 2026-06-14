package com.inkflow.crm.domain.converter;

import com.inkflow.crm.domain.enums.SupportedLocale;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class SupportedLocaleConverter implements AttributeConverter<SupportedLocale, String> {

    @Override
    public String convertToDatabaseColumn(SupportedLocale attribute) {
        return attribute != null ? attribute.getCode() : SupportedLocale.UK.getCode();
    }

    @Override
    public SupportedLocale convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return SupportedLocale.UK;
        }
        return SupportedLocale.fromCode(dbData);
    }
}

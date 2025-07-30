package com.reviewsystem.common.converter;

import com.reviewsystem.common.enums.ProviderType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ProviderTypeConverter implements AttributeConverter<ProviderType, String> {

  @Override
  public String convertToDatabaseColumn(ProviderType attribute) {
    return attribute == null ? null : attribute.name(); // or attribute.toString()
  }

  @Override
  public ProviderType convertToEntityAttribute(String dbData) {
    if (dbData == null) {
      return null;
    }
    for (ProviderType type : ProviderType.values()) {
      if (type.name().equalsIgnoreCase(dbData)) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unknown ProviderType value: " + dbData);
  }
}

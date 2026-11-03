package com.github.theguuholi.rentcars.cars;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Persists a rental charge in the existing integer-cent columns. */
@Converter(autoApply = true)
public class BrlAmountConverter implements AttributeConverter<BrlAmount, Integer> {
  @Override
  public Integer convertToDatabaseColumn(BrlAmount amount) {
    return amount == null ? null : amount.cents();
  }

  @Override
  public BrlAmount convertToEntityAttribute(Integer cents) {
    return cents == null ? null : new BrlAmount(cents);
  }
}

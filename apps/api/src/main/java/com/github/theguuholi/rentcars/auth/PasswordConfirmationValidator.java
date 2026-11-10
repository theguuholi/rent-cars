package com.github.theguuholi.rentcars.auth;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordConfirmationValidator
    implements ConstraintValidator<PasswordConfirmed, RegistrationRequest> {
  public PasswordConfirmationValidator() {}

  @Override
  public boolean isValid(RegistrationRequest input, ConstraintValidatorContext context) {
    if (input == null
        || input.password() == null
        || input.passwordConfirmation() == null
        || input.password().equals(input.passwordConfirmation())) {
      return true;
    }
    context.disableDefaultConstraintViolation();
    context
        .buildConstraintViolationWithTemplate("Passwords do not match")
        .addPropertyNode("passwordConfirmation")
        .addConstraintViolation();
    return false;
  }
}

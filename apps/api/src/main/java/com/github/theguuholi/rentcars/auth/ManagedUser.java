package com.github.theguuholi.rentcars.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(
    description = "Account details visible only to administrators. Passwords are never returned.")
record ManagedUser(
    UUID id,
    String email,
    String role,
    String firstName,
    String lastName,
    String userName,
    String driveLicense) {
  static ManagedUser from(Account account) {
    DriverProfile driver = account.driverProfile();
    UserProfile profile = account.profile();
    return new ManagedUser(
        account.id(),
        profile.email(),
        account.role(),
        profile.firstName(),
        profile.lastName(),
        profile.userName(),
        driver == null ? null : driver.driveLicense());
  }
}

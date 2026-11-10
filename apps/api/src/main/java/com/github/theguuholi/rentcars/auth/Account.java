package com.github.theguuholi.rentcars.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Persisted login account. The normalized email identifies an account at sign-in, while the UUID
 * remains its stable JWT subject. Public registration assigns USER. Only the public profile is
 * returned over HTTP.
 */
@Schema(hidden = true, description = "Internal login account.")
@Entity
@Table(name = "accounts")
class Account {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true, length = 254)
  private String email;

  /** Encoded password used by the authentication provider to verify sign-in attempts. */
  @Column(nullable = false, length = 100)
  private String passwordHash;

  @Column(nullable = false, length = 5)
  private String role = "USER";

  @OneToOne(mappedBy = "account", cascade = CascadeType.ALL)
  private DriverProfile driverProfile;

  protected Account() {}

  Account(String email, String passwordHash) {
    this.email = email;
    this.passwordHash = passwordHash;
  }

  Account(RegistrationRequest input, String passwordHash) {
    this(input.email(), passwordHash);
    this.driverProfile = new DriverProfile(this, input);
  }

  UUID id() {
    return id;
  }

  void changePassword(String encodedPassword) {
    this.passwordHash = encodedPassword;
  }

  /** Supplies Spring Security with the account ID, encoded password and granted role. */
  UserDetails principal() {
    return User.withUsername(id.toString()).password(passwordHash).roles(role).build();
  }

  UserProfile profile() {
    return new UserProfile(
        id,
        email,
        role,
        driverProfile == null ? null : driverProfile.firstName(),
        driverProfile == null ? null : driverProfile.lastName(),
        driverProfile == null ? null : driverProfile.userName());
  }
}

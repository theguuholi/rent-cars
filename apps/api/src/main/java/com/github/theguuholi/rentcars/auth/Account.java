package com.github.theguuholi.rentcars.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

/** Internal credential record. Only the public profile leaves the API. */
@Entity
@Table(name = "accounts")
class Account {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true, length = 254)
  private String email;

  @Column(nullable = false, length = 100)
  private String passwordHash;

  @Column(nullable = false, length = 5)
  private String role = "USER";

  protected Account() {}

  Account(String email, String passwordHash) {
    this.email = email;
    this.passwordHash = passwordHash;
  }

  UserDetails principal() {
    return User.withUsername(id.toString()).password(passwordHash).roles(role).build();
  }

  UserProfile profile() {
    return new UserProfile(id, email, role);
  }
}

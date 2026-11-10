package com.github.theguuholi.rentcars.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/** Private driver details. The CNH number is never part of the public account response. */
@Schema(hidden = true)
@Entity
@Table(name = "driver_profiles")
class DriverProfile {
  @Id private UUID accountId;

  @MapsId
  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "account_id", nullable = false)
  private Account account;

  @Column(nullable = false, length = 80)
  private String firstName;

  @Column(nullable = false, length = 80)
  private String lastName;

  @Column(nullable = false, unique = true, length = 40)
  private String userName;

  @Column(nullable = false, unique = true, length = 11)
  private String driveLicense;

  protected DriverProfile() {}

  DriverProfile(Account account, RegistrationRequest input) {
    this.account = account;
    this.firstName = input.firstName();
    this.lastName = input.lastName();
    this.userName = input.userName();
    this.driveLicense = input.driveLicense();
  }

  String firstName() {
    return firstName;
  }

  String lastName() {
    return lastName;
  }

  String userName() {
    return userName;
  }
}

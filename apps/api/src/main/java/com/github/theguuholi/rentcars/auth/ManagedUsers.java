package com.github.theguuholi.rentcars.auth;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
class ManagedUsers {
  private final AccountRepository accounts;
  private final PasswordEncoder passwords;
  private final PasswordResetTokens tokens;

  ManagedUsers(AccountRepository accounts, PasswordEncoder passwords, PasswordResetTokens tokens) {
    this.accounts = accounts;
    this.passwords = passwords;
    this.tokens = tokens;
  }

  List<ManagedUser> list() {
    return accounts.findAll(Sort.by("email", "id")).stream().map(ManagedUser::from).toList();
  }

  ManagedUser get(UUID id) {
    return ManagedUser.from(find(id));
  }

  @Transactional
  ManagedUser create(ManagedUserCreateRequest input) {
    Account account = new Account(input.user(), passwords.encode(input.user().password()));
    account.manage(input.user().email(), input.role());
    return ManagedUser.from(accounts.saveAndFlush(account));
  }

  @Transactional
  ManagedUser update(UUID actorId, UUID id, ManagedUserUpdateRequest input) {
    Account account = find(id);
    if (id.equals(actorId) && !"ADMIN".equals(input.role())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "You cannot remove your own admin role");
    }
    DriverProfile driver = account.driverProfile();
    if (driver != null) {
      if (input.firstName() == null
          || input.firstName().isBlank()
          || input.lastName() == null
          || input.lastName().isBlank()
          || input.userName() == null
          || input.driveLicense() == null) {
        throw new ResponseStatusException(
            HttpStatus.UNPROCESSABLE_CONTENT, "Driver details are required");
      }
      driver.manage(input.firstName(), input.lastName(), input.userName(), input.driveLicense());
    } else if (input.firstName() != null
        || input.lastName() != null
        || input.userName() != null
        || input.driveLicense() != null) {
      throw new ResponseStatusException(
          HttpStatus.UNPROCESSABLE_CONTENT, "This account has no driver profile");
    }
    boolean roleChanged = !account.role().equals(input.role());
    account.manage(input.email(), input.role());
    ManagedUser saved = ManagedUser.from(accounts.saveAndFlush(account));
    if (roleChanged) tokens.revokeSessions(id);
    return saved;
  }

  @Transactional
  void delete(UUID actorId, UUID id) {
    Account account = find(id);
    if (id.equals(actorId)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot delete your own account");
    }
    if (accounts.hasRentalHistory(id)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "An account with rental history cannot be deleted");
    }
    accounts.delete(account);
  }

  private Account find(UUID id) {
    return accounts
        .findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
  }
}

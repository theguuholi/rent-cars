package com.github.theguuholi.rentcars.auth;

import com.github.theguuholi.rentcars.shared.mail.EmailSender;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PasswordRecovery {
  private final AccountRepository accounts;
  private final PasswordResetTokens tokens;
  private final PasswordRecoveryEmail email;
  private final EmailSender sender;
  private final PasswordEncoder passwords;

  PasswordRecovery(
      AccountRepository accounts,
      PasswordResetTokens tokens,
      PasswordRecoveryEmail email,
      EmailSender sender,
      PasswordEncoder passwords) {
    this.accounts = accounts;
    this.tokens = tokens;
    this.email = email;
    this.sender = sender;
    this.passwords = passwords;
  }

  @Transactional
  void request(String address) {
    accounts
        .findByEmail(address)
        .ifPresent(
            account -> {
              String secret = tokens.issue(account.id());
              try {
                sender.send(email.message(address, secret));
              } catch (MailException failure) {
                // The public response must not reveal whether this address belongs to an account.
                tokens.invalidate(account.id());
              }
            });
  }

  @Transactional
  boolean reset(String secret, String password) {
    var owner = tokens.consume(secret);
    if (owner.isEmpty()) return false;
    var account = accounts.findById(owner.orElseThrow()).orElseThrow();
    account.changePassword(passwords.encode(password));
    tokens.revokeSessions(account.id());
    return true;
  }
}
